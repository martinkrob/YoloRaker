package h848.software.yoloraker.ai;

import h848.software.yoloraker.db.DatabaseManager;
import h848.software.yoloraker.model.Printer;
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
        scheduler.scheduleAtFixedRate(this::checkPrinters, 5, 15, TimeUnit.SECONDS);
        logger.info("DetectionService started. AI checking interval set to 15 seconds.");
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

            if (!"printing".equalsIgnoreCase(telemetry.getPrintState())) {
                detectionCountMap.put(printer.getId(), 0);
                latestResultsMap.remove(printer.getId());
                return;
            }

            if (printer.getWebcamUrl() == null || printer.getWebcamUrl().isEmpty()) {
                return;
            }

            byte[] snapshot = cameraClient.getSnapshot(printer.getWebcamUrl());
            DetectionResult result = aiDetector.detect(snapshot);

            // Save to memory for Live UI
            latestResultsMap.put(printer.getId(), result);

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
                    logger.error("ALARM: {} confirmed on {}. Pausing print and firing webhook!", triggerType, printer.getName());

                    alertClient.sendWebhook(printer, result);
                    moonrakerClient.pausePrint(printer);
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
}
