package h848.software.yoloraker.ai;

import h848.software.yoloraker.db.DatabaseManager;
import h848.software.yoloraker.model.Printer;
import h848.software.yoloraker.moonraker.PrinterTelemetry;
import h848.software.yoloraker.moonraker.MoonrakerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private static final int DETECTION_THRESHOLD = 3;

    public DetectionService(DatabaseManager dbManager, MoonrakerClient moonrakerClient) {
        this.dbManager = dbManager;
        this.moonrakerClient = moonrakerClient;
        this.cameraClient = new CameraClient();
        this.alertClient = new AlertClient();
        
        // We initialize the AiDetector pointing to a default location (e.g., models/spaghetti.onnx)
        // with a confidence threshold of 0.60 (60%).
        this.aiDetector = new AiDetector("models/spaghetti.onnx", 0.60f);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        // Run the check loop every 15 seconds
        scheduler.scheduleAtFixedRate(this::checkPrinters, 5, 15, TimeUnit.SECONDS);
        logger.info("DetectionService started. AI checking interval set to 15 seconds.");
    }

    public void stop() {
        scheduler.shutdown();
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
            // Step 1: Check if printing
            PrinterTelemetry telemetry = moonrakerClient.getTelemetry(printer);
            if (telemetry == null || telemetry.getPrintState() == null) {
                return;
            }

            if (!"printing".equalsIgnoreCase(telemetry.getPrintState())) {
                // Printer is not printing, reset detection count and skip
                detectionCountMap.put(printer.getId(), 0);
                return;
            }

            if (printer.getWebcamUrl() == null || printer.getWebcamUrl().isEmpty()) {
                logger.debug("Printer {} has no webcam URL configured, skipping AI.", printer.getName());
                return;
            }

            // Step 2: Download snapshot
            byte[] snapshot = cameraClient.getSnapshot(printer.getWebcamUrl());

            // Step 3: Run inference
            DetectionResult result = aiDetector.detect(snapshot);

            // Step 4: State machine logic
            int count = detectionCountMap.getOrDefault(printer.getId(), 0);
            if (result.isSpaghettiDetected()) {
                count++;
                logger.warn("Spaghetti detected for {} (Count: {}/{}) with confidence {}", 
                            printer.getName(), count, DETECTION_THRESHOLD, result.getMaxConfidence());
                
                if (count >= DETECTION_THRESHOLD) {
                    logger.error("ALARM: Spaghetti confirmed on {}. Pausing print and firing webhook!", printer.getName());
                    
                    // Fire Webhook
                    alertClient.sendWebhook(printer, result);
                    
                    // Pause Print
                    moonrakerClient.pausePrint(printer);
                    
                    // Reset count to avoid spamming the pause command immediately
                    // Alternatively, Moonraker will change state to 'paused', so next loop it will skip anyway.
                    count = 0;
                }
            } else {
                if (count > 0) {
                    logger.debug("Clean frame for {}, decreasing detection count.", printer.getName());
                    // Decrease count instead of completely resetting it to be more robust
                    count--; 
                }
            }
            
            detectionCountMap.put(printer.getId(), count);

        } catch (Exception e) {
            logger.error("Failed to check AI for printer {}", printer.getName(), e);
        }
    }
}
