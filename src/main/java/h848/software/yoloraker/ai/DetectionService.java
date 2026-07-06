package h848.software.yoloraker.ai;

import h848.software.yoloraker.db.DatabaseManager;
import h848.software.yoloraker.model.Printer;
import h848.software.yoloraker.model.AiAlarm;
import h848.software.yoloraker.model.PrintJob;
import h848.software.yoloraker.model.TelemetryLog;
import h848.software.yoloraker.moonraker.MoonrakerClient;
import h848.software.yoloraker.moonraker.PrinterTelemetry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DetectionService {

    private static final Logger logger = LoggerFactory.getLogger(DetectionService.class);

    private final DatabaseManager dbManager;
    private final MoonrakerClient moonrakerClient;
    private final CameraClient cameraClient;
    private final AiDetector aiDetector;
    private final AlertClient alertClient;
    private final ScheduledExecutorService scheduler;

    // To prevent false positives, we keep track of how many consecutive detections happened per printer
    private final Map<String, Integer> detectionCountMap = new ConcurrentHashMap<>();

    // Store latest results for the Live UI Dashboard
    private final Map<String, DetectionResult> latestResultsMap = new ConcurrentHashMap<>();

    // Throttle telemetry saving
    private final Map<String, Long> lastTelemetrySaveMap = new ConcurrentHashMap<>();

    private static final int DETECTION_THRESHOLD = 3;

    public DetectionService(DatabaseManager dbManager, MoonrakerClient moonrakerClient) {
        this.dbManager = dbManager;
        this.moonrakerClient = moonrakerClient;
        this.cameraClient = new CameraClient();
        this.alertClient = new AlertClient();

        this.aiDetector = new AiDetector("models/yolov11-3d-print-failure-detection.onnx");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkPrinters, 5, 10, TimeUnit.SECONDS);
        logger.info("DetectionService started. AI checking interval set to 10 seconds.");
    }

    public void stop() {
        scheduler.shutdown();
    }

    public DetectionResult getLatestResult(String printerId) {
        return latestResultsMap.get(printerId);
    }

    private void checkPrinters() {
        try {
            List<Printer> printers = dbManager.getAllPrinters();
            for (Printer printer : printers) {
                if (printer.isEnabled()) {
                    checkPrinter(printer);
                }
            }
        } catch (Exception e) {
            logger.error("Error in AI detection loop", e);
        }
    }

    private void checkPrinter(Printer printer) {
        try {
            PrinterTelemetry telemetry = moonrakerClient.getTelemetry(printer);
            if (telemetry == null || telemetry.getPrintState() == null) {
                return;
            }

            if (printer.getWebcamUrl() == null || printer.getWebcamUrl().isEmpty()) {
                return;
            }

            byte[] snapshot = cameraClient.getSnapshot(printer.getWebcamUrl());
            DetectionResult result = null;
            if (snapshot != null) {
                result = aiDetector.detect(snapshot);
                latestResultsMap.put(printer.getId(), result);
            }

            // --- History: Print Job Tracking ---
            PrintJob activeJob = dbManager.getLatestActivePrintJob(printer.getId());
            boolean isPrinting = "printing".equalsIgnoreCase(telemetry.getPrintState());
            
            if (isPrinting && activeJob == null) {
                // New job started
                PrintJob newJob = new PrintJob();
                newJob.setPrinterId(printer.getId());
                newJob.setFilename(telemetry.getFilename());
                newJob.setStartTime(new java.sql.Timestamp(System.currentTimeMillis()));
                newJob.setStatus("printing");
                dbManager.savePrintJob(newJob);
                activeJob = newJob;
            } else if (!isPrinting && activeJob != null) {
                // Job finished or cancelled
                activeJob.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
                activeJob.setStatus(telemetry.getPrintState()); // e.g. "complete", "cancelled"
                activeJob.setDurationSeconds(telemetry.getPrintDuration());
                activeJob.setExtrudedFilament(telemetry.getFilamentUsed());
                dbManager.updatePrintJob(activeJob);
            }

            // --- History: Telemetry & Snapshots ---
            long now = System.currentTimeMillis();
            long lastSave = lastTelemetrySaveMap.getOrDefault(printer.getId(), 0L);
            if (now - lastSave >= 10000) {
                TelemetryLog log = new TelemetryLog();
                log.setPrinterId(printer.getId());
                log.setExtruderTemp(telemetry.getExtruderTemp());
                log.setBedTemp(telemetry.getBedTemp());
                log.setPrintProgress(telemetry.getProgress());
                if (result != null) {
                    log.setConfSpaghetti(result.getConfSpaghetti());
                    log.setConfStringing(result.getConfStringing());
                    log.setConfZits(result.getConfZits());
                }
                dbManager.saveTelemetryLog(log);
                
                // Save snapshot if printing
                if (isPrinting && activeJob != null && snapshot != null) {
                    saveSnapshotToDisk(printer.getId(), activeJob.getId(), now, snapshot);
                }
                
                // Send telemetry via Webhook/MQTT
                if (printer.isWebhookTelemetryEnabled()) {
                    alertClient.sendTelemetryWebhook(printer, telemetry, result);
                }
                if (printer.isMqttTelemetryEnabled()) {
                    alertClient.sendTelemetryMqtt(printer, telemetry, result);
                }
                
                lastTelemetrySaveMap.put(printer.getId(), now);
            }

            if (!isPrinting || result == null) {
                detectionCountMap.put(printer.getId(), 0);
                return;
            }

            int count = detectionCountMap.getOrDefault(printer.getId(), 0);

            // Check if ANY of the classes exceeds the printer's specific threshold
            boolean isFailureOverThreshold = false;
            DetectionResult.FailureType triggerType = DetectionResult.FailureType.NONE;

            if (result.getConfSpaghetti() >= printer.getThresholdSpaghetti()) {
                isFailureOverThreshold = true;
                triggerType = DetectionResult.FailureType.SPAGHETTI;
            } else if (result.getConfStringing() >= printer.getThresholdStringing()) {
                isFailureOverThreshold = true;
                triggerType = DetectionResult.FailureType.STRINGING;
            } else if (result.getConfZits() >= printer.getThresholdZits()) {
                isFailureOverThreshold = true;
                triggerType = DetectionResult.FailureType.ZITS;
            }

            if (isFailureOverThreshold) {
                count++;
                logger.warn("{} detected for {} (Count: {}/{})",
                        triggerType, printer.getName(), count, DETECTION_THRESHOLD);

                if (count >= DETECTION_THRESHOLD) {
                    logger.error("ALARM: {} confirmed on {}. Pausing print and firing webhook/mqtt!", triggerType, printer.getName());

                    // --- History: Save AI Alarm with image BLOB ---
                    AiAlarm alarm = new AiAlarm();
                    alarm.setPrinterId(printer.getId());
                    alarm.setFilename(telemetry.getFilename());
                    alarm.setTriggerType(triggerType.name().toLowerCase());
                    alarm.setConfidence(result.getHighestConfidence());
                    alarm.setImageData(snapshot);
                    dbManager.saveAiAlarm(alarm);

                    // Update PrintJob status to paused_by_ai
                    if (activeJob != null) {
                        activeJob.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
                        activeJob.setStatus("paused_by_ai");
                        activeJob.setDurationSeconds(telemetry.getPrintDuration());
                        activeJob.setExtrudedFilament(telemetry.getFilamentUsed());
                        dbManager.updatePrintJob(activeJob);
                    }

                    alertClient.sendWebhook(printer, result);
                    alertClient.sendMqttMessage(printer, result);
                    moonrakerClient.pausePrint(printer);
                    
                    dbManager.logEvent(printer.getId(), "AI_ALARM", "Print paused due to " + triggerType.name());
                    
                    count = 0;
                }
            } else {
                if (count > 0) {
                    count--;
                }
            }

            detectionCountMap.put(printer.getId(), count);

        } catch (Exception e) {
            logger.error("Failed to check AI for printer {}", printer.getName(), e);
        }
    }
    
    private static final String DATA_PATH = System.getenv().getOrDefault("YOLORAKER_DATA_PATH", "./data");

    private void saveSnapshotToDisk(String printerId, Long jobId, long timestamp, byte[] snapshotData) {
        try {
            java.io.File dir = new java.io.File(DATA_PATH + "/snapshots/" + printerId + "/" + jobId);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            java.io.File file = new java.io.File(dir, timestamp + ".jpg");
            java.nio.file.Files.write(file.toPath(), snapshotData);
        } catch (java.io.IOException e) {
            logger.error("Failed to save snapshot to disk", e);
        }
    }
}
