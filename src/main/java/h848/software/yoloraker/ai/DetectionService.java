package h848.software.yoloraker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import h848.software.yoloraker.ai.DetectionResult.FailureType;
import h848.software.yoloraker.db.DatabaseManager;
import h848.software.yoloraker.fusion.ClassAssessment;
import h848.software.yoloraker.fusion.DetectionHistory;
import h848.software.yoloraker.fusion.FusionEngine;
import h848.software.yoloraker.fusion.FusionMode;
import h848.software.yoloraker.fusion.RiskAssessment;
import h848.software.yoloraker.model.Printer;
import h848.software.yoloraker.model.AiAlarm;
import h848.software.yoloraker.model.AiClassStatus;
import h848.software.yoloraker.model.PrintJob;
import h848.software.yoloraker.model.TelemetryLog;
import h848.software.yoloraker.moonraker.MoonrakerClient;
import h848.software.yoloraker.moonraker.PrinterTelemetry;
import h848.software.yoloraker.telemetry.TelemetryService;
import h848.software.yoloraker.telemetry.TelemetryWindow;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DetectionService {

    private static final Logger logger = LoggerFactory.getLogger(DetectionService.class);

    private final DatabaseManager dbManager;
    private final MoonrakerClient moonrakerClient;
    private final TelemetryService telemetryService;
    private final CameraClient cameraClient;
    private final Map<String, AiDetector> detectors = new ConcurrentHashMap<>();
    private final AlertClient alertClient;
    private final ScheduledExecutorService scheduler;

    // Worker pool so that a single slow/offline printer does not block checks for the others.
    private final ExecutorService workerPool;
    // Guard to make sure the same printer is never processed by two overlapping cycles at once.
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    // --- Sensor fusion ---
    private final FusionEngine fusionEngine = new FusionEngine();
    private final DetectionHistory detectionHistory = new DetectionHistory();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Confirmation level per printer and class. A detection adds to it at a rate the fusion layer
     * controls and a clean frame subtracts from it; an alarm fires when it reaches
     * {@link FusionEngine#ALARM_AT}. This replaces the old fixed "5 consecutive detections",
     * which took the same 50 s whether the model was 61% or 99% sure.
     */
    private final Map<String, EnumMap<FailureType, Double>> levels = new ConcurrentHashMap<>();

    // Print state transitions, needed for the JUST_RESUMED suppressor.
    private final Map<String, String> lastPrintState = new ConcurrentHashMap<>();
    private final Map<String, Long> lastResumeTs = new ConcurrentHashMap<>();

    // Quality-class notifications are rate limited to one per class per print job.
    private final Map<String, Long> notifyJobId = new ConcurrentHashMap<>();
    private final Map<String, EnumSet<FailureType>> notifiedClasses = new ConcurrentHashMap<>();

    // Store latest results for the Live UI Dashboard
    private final Map<String, DetectionResult> latestResultsMap = new ConcurrentHashMap<>();
    private final Map<String, RiskAssessment> latestAssessmentMap = new ConcurrentHashMap<>();

    // Throttle telemetry saving
    private final Map<String, Long> lastTelemetrySaveMap = new ConcurrentHashMap<>();

    private final ModelService modelService;

    public DetectionService(DatabaseManager dbManager, MoonrakerClient moonrakerClient,
                            TelemetryService telemetryService, ModelService modelService) {
        this.dbManager = dbManager;
        this.moonrakerClient = moonrakerClient;
        this.telemetryService = telemetryService;
        this.cameraClient = new CameraClient();
        this.alertClient = new AlertClient();
        this.modelService = modelService;

        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.workerPool = Executors.newFixedThreadPool(4);
    }

    public void invalidatePrinterModel(String printerId) {
        logger.info("Invalidating AI model for printer: {}", printerId);
        AiDetector removed = detectors.remove(printerId);
        if (removed != null) {
            removed.close(); // release native ONNX session memory
        }
        // A different model scores the same scene differently, so the learned baseline no longer
        // applies and has to be rebuilt.
        detectionHistory.forget(printerId);
    }

    /** Detection cycle period; also the unit the confirmation level advances in. */
    private static final int CYCLE_SECONDS = 10;

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkPrinters, 5, CYCLE_SECONDS, TimeUnit.SECONDS);
        logger.info("DetectionService started. AI checking interval set to {} seconds.", CYCLE_SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
        workerPool.shutdown();
        // Release native ONNX session memory held by every detector.
        detectors.values().forEach(AiDetector::close);
        detectors.clear();
    }

    public DetectionResult getLatestResult(String printerId) {
        return latestResultsMap.get(printerId);
    }

    /**
     * Live per-class state for the dashboard: how far each class has accumulated towards an
     * alarm, and why it is or is not moving.
     * <p>
     * Returns an entry for every enabled class even when nothing has been detected yet, so the
     * UI can render a stable set of meters rather than rows appearing and vanishing.
     */
    public List<AiClassStatus> getClassStatus(Printer printer) {
        DetectionResult result = latestResultsMap.get(printer.getId());
        RiskAssessment assessment = latestAssessmentMap.get(printer.getId());
        EnumMap<FailureType, Double> printerLevels = levels.get(printer.getId());
        List<AiClassStatus> out = new java.util.ArrayList<>(3);

        for (FailureType type : List.of(FailureType.SPAGHETTI, FailureType.STRINGING, FailureType.ZITS)) {
            if (!isClassEnabled(printer, type)) {
                continue;
            }
            AiClassStatus s = new AiClassStatus();
            s.setType(type.name().toLowerCase());
            s.setThreshold(thresholdOf(printer, type));
            s.setAlarmAt(FusionEngine.ALARM_AT);
            s.setConfidence(result != null ? confidenceOf(result, type) : 0f);

            double level = printerLevels != null ? printerLevels.getOrDefault(type, 0.0) : 0.0;
            s.setLevel(level);

            ClassAssessment ca = assessment != null ? assessment.get(type).orElse(null) : null;
            double gain;
            if (ca != null) {
                s.setReference(ca.hasReference() ? (float) ca.reference() : null);
                s.setSuppression(ca.suppression());
                s.setSaturated(FusionEngine.referenceIsSaturating(ca.reference()));
                s.setRules(ca.ruleSummary());
                gain = ca.gain();
                if (!ca.gated() && ca.raw() >= ca.threshold()) {
                    // Over the threshold yet held back - the baseline says this is scenery.
                    s.setState(AiClassStatus.STATE_SCENERY);
                }
            } else {
                gain = result != null ? legacyGain(result, printer, type) : -1.0;
            }

            if (!AiClassStatus.STATE_SCENERY.equals(s.getState())) {
                if (gain <= 0) {
                    s.setState(AiClassStatus.STATE_IDLE);
                } else if (level >= 0.6 * FusionEngine.ALARM_AT) {
                    s.setState(AiClassStatus.STATE_IMMINENT);
                } else if (s.getSuppression() < 1.0) {
                    s.setState(AiClassStatus.STATE_SUPPRESSED);
                } else {
                    s.setState(AiClassStatus.STATE_BUILDING);
                }
            }

            if (gain > 0) {
                int cycles = (int) Math.ceil((FusionEngine.ALARM_AT - level) / gain);
                s.setSecondsToAlarm(Math.max(0, cycles) * CYCLE_SECONDS);
            }
            out.add(s);
        }
        return out;
    }

    private void checkPrinters() {
        try {
            // Read once per cycle rather than once per printer: one query per 10 s instead of N.
            FusionMode mode = dbManager.getFusionMode();
            List<Printer> printers = dbManager.getAllPrinters();
            for (Printer printer : printers) {
                if (!printer.isEnabled()) {
                    continue;
                }
                // Skip if a previous cycle for this printer is still running (slow or offline host).
                if (!inProgress.add(printer.getId())) {
                    continue;
                }
                workerPool.submit(() -> {
                    try {
                        checkPrinter(printer, mode);
                    } catch (Exception e) {
                        logger.error("Unhandled error checking printer {}", printer.getName(), e);
                    } finally {
                        inProgress.remove(printer.getId());
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Error in AI detection loop", e);
        }
    }

    private void checkPrinter(Printer printer, FusionMode mode) {
        try {
            PrinterTelemetry telemetry = currentTelemetry(printer);
            if (telemetry == null || telemetry.getPrintState() == null) {
                return;
            }

            // --- AI snapshot + detection ---
            // Isolated in its own try/catch: a webcam hiccup must NOT stop telemetry, job tracking
            // or history logging below. The webcam is also optional.
            byte[] snapshot = null;
            DetectionResult result = null;
            long frameTs = 0;
            if (printer.getWebcamUrl() != null && !printer.getWebcamUrl().isEmpty()) {
                try {
                    snapshot = cameraClient.getSnapshot(printer.getWebcamUrl());
                    // Stamped as soon as the bytes are in hand, so the telemetry window can be
                    // aligned to when the picture was actually taken.
                    frameTs = System.currentTimeMillis();
                } catch (Exception e) {
                    logger.warn("Failed to fetch webcam snapshot for {}: {}", printer.getName(), e.getMessage());
                }
                if (snapshot != null) {
                    AiDetector detector = detectors.computeIfAbsent(printer.getId(), id -> {
                        String modelName = printer.getAiModel() != null ? printer.getAiModel() : "INBUILT";
                        logger.info("Initializing AiDetector for printer {} with model {}", printer.getName(), modelName);
                        return new AiDetector(modelName, this.modelService);
                    });

                    result = detector.detect(snapshot);
                    latestResultsMap.put(printer.getId(), result);
                }
            }

            // --- History: Print Job Tracking ---
            PrintJob activeJob = dbManager.getLatestActivePrintJob(printer.getId());
            boolean isPrinting = "printing".equalsIgnoreCase(telemetry.getPrintState());
            // A pause is NOT the end of a job. Treat it as ongoing so pausing/resuming does not
            // fragment a single physical print into multiple history records.
            boolean isPaused = "paused".equalsIgnoreCase(telemetry.getPrintState());

            trackResume(printer.getId(), telemetry.getPrintState());

            if (isPrinting && activeJob == null) {
                // New job started
                PrintJob newJob = new PrintJob();
                newJob.setPrinterId(printer.getId());
                newJob.setFilename(telemetry.getFilename());
                newJob.setStartTime(new java.sql.Timestamp(System.currentTimeMillis()));
                newJob.setStatus("printing");
                dbManager.savePrintJob(newJob);
                activeJob = newJob;
            } else if (!isPrinting && !isPaused && activeJob != null) {
                // Job reached a terminal state (complete, cancelled, error, standby)
                activeJob.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
                activeJob.setStatus(telemetry.getPrintState()); // e.g. "complete", "cancelled"
                activeJob.setDurationSeconds(telemetry.getPrintDuration());
                activeJob.setExtrudedFilament(telemetry.getFilamentUsed());
                dbManager.updatePrintJob(activeJob);
                activeJob = null;
            }

            // --- Sensor fusion ---
            Long jobId = activeJob != null ? activeJob.getId() : null;
            TelemetryWindow window = frameTs > 0
                    ? telemetryService.windowEndingAt(printer.getId(), frameTs).orElse(null)
                    : null;

            RiskAssessment assessment = null;
            if (mode != FusionMode.OFF && result != null && isPrinting) {
                // Assess against the history as it stood before this frame, then fold this frame in.
                assessment = fusionEngine.assess(printer, result, window, detectionHistory,
                        secondsSinceResume(printer.getId()));
                detectionHistory.record(printer.getId(), telemetry.getFilename(),
                        telemetry.getPrintDuration(), System.currentTimeMillis(), result);
                latestAssessmentMap.put(printer.getId(), assessment);
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
                    log.setAnchorsSpaghetti(result.getAnchorsSpaghetti());
                    log.setAnchorsStringing(result.getAnchorsStringing());
                    log.setAnchorsZits(result.getAnchorsZits());
                }
                recordFusion(log, assessment, window);
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

                // KlipperScreen / Mainsail M117 telemetry
                if (printer.isKlipperScreenTelemetryEnabled() && result != null && isPrinting) {
                    int spag = (int)(result.getConfSpaghetti() * 100);
                    int str = (int)(result.getConfStringing() * 100);
                    int zits = (int)(result.getConfZits() * 100);
                    String msg = String.format("AI: Spag %d%% | Str %d%% | Zits %d%%", spag, str, zits);
                    moonrakerClient.sendM117(printer, msg);
                }

                lastTelemetrySaveMap.put(printer.getId(), now);
            }

            if (!isPrinting || result == null) {
                levels.remove(printer.getId());
                latestAssessmentMap.remove(printer.getId());
                return;
            }

            evaluateClasses(printer, telemetry, result, assessment, activeJob, snapshot, mode);

        } catch (Exception e) {
            logger.error("Failed to check AI for printer {}", printer.getName(), e);
        }
    }

    /**
     * Advances the confirmation level for every enabled class and fires whatever crosses the line.
     * <p>
     * Spaghetti is the only class that stops a print. Stringing and zits are surface-quality
     * defects: pausing does not fix them and the print is usually still usable, so they inform
     * and are recorded instead.
     */
    private void evaluateClasses(Printer printer, PrinterTelemetry telemetry, DetectionResult result,
                                 RiskAssessment assessment, PrintJob activeJob, byte[] snapshot,
                                 FusionMode mode) {

        EnumMap<FailureType, Double> printerLevels =
                levels.computeIfAbsent(printer.getId(), id -> new EnumMap<>(FailureType.class));

        for (FailureType type : List.of(FailureType.SPAGHETTI, FailureType.STRINGING, FailureType.ZITS)) {
            if (!isClassEnabled(printer, type)) {
                continue;
            }

            ClassAssessment ca = assessment != null ? assessment.get(type).orElse(null) : null;
            double gain = (mode == FusionMode.ACTIVE && ca != null)
                    ? ca.gain()
                    : legacyGain(result, printer, type);

            double level = Math.clamp(printerLevels.getOrDefault(type, 0.0) + gain, 0.0, FusionEngine.ALARM_AT);
            printerLevels.put(type, level);

            if (gain > 0) {
                logger.debug("{} on {}: level {}/{} ({})", type, printer.getName(),
                        String.format(java.util.Locale.US, "%.1f", level), FusionEngine.ALARM_AT,
                        ca != null ? ca.explain() : "fusion off");
            }

            if (level < FusionEngine.ALARM_AT) {
                continue;
            }

            boolean pause = type == FailureType.SPAGHETTI;
            if (!pause && !claimNotification(printer.getId(), activeJob, type)) {
                // Already reported this defect on this print - keep quiet rather than buzz the
                // operator's phone every cycle for a cosmetic issue that will not change.
                printerLevels.put(type, 0.0);
                continue;
            }

            fireAlarm(printer, telemetry, result, type, activeJob, snapshot, pause, ca);
            printerLevels.put(type, 0.0);
        }
    }

    private void fireAlarm(Printer printer, PrinterTelemetry telemetry, DetectionResult result,
                           FailureType type, PrintJob activeJob, byte[] snapshot, boolean pause,
                           ClassAssessment ca) {

        float confidence = confidenceOf(result, type);
        if (pause) {
            logger.error("ALARM: {} confirmed on {} (conf {}). Pausing print. [{}]",
                    type, printer.getName(), confidence, ca != null ? ca.explain() : "fusion off");
        } else {
            logger.warn("NOTICE: {} detected on {} (conf {}). Print continues - quality defect. [{}]",
                    type, printer.getName(), confidence, ca != null ? ca.explain() : "fusion off");
        }

        AiAlarm alarm = new AiAlarm();
        alarm.setPrinterId(printer.getId());
        alarm.setFilename(telemetry.getFilename());
        alarm.setTriggerType(type.name().toLowerCase());
        alarm.setConfidence(confidence);
        alarm.setImageData(snapshot);
        alarm.setAction(pause ? "PAUSED" : "NOTIFIED");
        dbManager.saveAiAlarm(alarm);

        DetectionResult reported = singleClassResult(result, type, confidence);
        alertClient.sendWebhook(printer, reported);
        alertClient.sendMqttMessage(printer, reported);

        if (pause) {
            if (activeJob != null) {
                activeJob.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
                activeJob.setStatus("paused_by_ai");
                activeJob.setDurationSeconds(telemetry.getPrintDuration());
                activeJob.setExtrudedFilament(telemetry.getFilamentUsed());
                dbManager.updatePrintJob(activeJob);
            }
            moonrakerClient.pausePrint(printer);
            dbManager.logEvent(printer.getId(), "AI_ALARM", "Print paused due to " + type.name());
        } else {
            dbManager.logEvent(printer.getId(), "AI_NOTICE",
                    type.name() + " detected, print not interrupted");
        }
    }

    /**
     * The pre-fusion rule: one step per frame over the printer's threshold, one step back
     * otherwise. Used in OFF and SHADOW so the old scoring keeps deciding while fusion is only
     * being measured. Note the action policy above applies in every mode - stringing and zits
     * no longer pause regardless of how the score was reached.
     */
    private double legacyGain(DetectionResult result, Printer printer, FailureType type) {
        return confidenceOf(result, type) >= thresholdOf(printer, type) ? 1.0 : -1.0;
    }

    /** Records what fusion concluded, whether or not it was allowed to act on it. */
    private void recordFusion(TelemetryLog log, RiskAssessment assessment, TelemetryWindow window) {
        if (assessment != null) {
            assessment.get(FailureType.SPAGHETTI).ifPresent(a -> {
                log.setRefSpaghetti(a.hasReference() ? (float) a.reference() : null);
                log.setSuppression((float) a.suppression());
                log.setFusionRules(truncate(a.ruleSummary(), 500));
            });
            assessment.get(FailureType.STRINGING)
                    .ifPresent(a -> log.setRefStringing(a.hasReference() ? (float) a.reference() : null));
            assessment.get(FailureType.ZITS)
                    .ifPresent(a -> log.setRefZits(a.hasReference() ? (float) a.reference() : null));
        }
        if (window != null) {
            try {
                log.setTelemetryWindow(jsonMapper.writeValueAsString(window));
            } catch (Exception e) {
                logger.debug("Could not serialise telemetry window", e);
            }
        }
    }

    /** @return true if this class has not yet been reported on the current job */
    private boolean claimNotification(String printerId, PrintJob activeJob, FailureType type) {
        Long jobId = activeJob != null ? activeJob.getId() : null;
        if (!Objects.equals(notifyJobId.get(printerId), jobId)) {
            notifyJobId.put(printerId, jobId == null ? -1L : jobId);
            notifiedClasses.put(printerId, EnumSet.noneOf(FailureType.class));
        }
        return notifiedClasses.get(printerId).add(type);
    }

    private void trackResume(String printerId, String printState) {
        String previous = lastPrintState.put(printerId, printState);
        if ("paused".equalsIgnoreCase(previous) && "printing".equalsIgnoreCase(printState)) {
            lastResumeTs.put(printerId, System.currentTimeMillis());
        }
    }

    private long secondsSinceResume(String printerId) {
        Long ts = lastResumeTs.get(printerId);
        return ts == null ? Long.MAX_VALUE : (System.currentTimeMillis() - ts) / 1000;
    }

    private static boolean isClassEnabled(Printer printer, FailureType type) {
        return switch (type) {
            case SPAGHETTI -> printer.isDetectSpaghetti();
            case STRINGING -> printer.isDetectStringing();
            case ZITS -> printer.isDetectZits();
            case NONE -> false;
        };
    }

    private static float thresholdOf(Printer printer, FailureType type) {
        return switch (type) {
            case SPAGHETTI -> printer.getThresholdSpaghetti();
            case STRINGING -> printer.getThresholdStringing();
            case ZITS -> printer.getThresholdZits();
            case NONE -> 1f;
        };
    }

    private static float confidenceOf(DetectionResult result, FailureType type) {
        return switch (type) {
            case SPAGHETTI -> result.getConfSpaghetti();
            case STRINGING -> result.getConfStringing();
            case ZITS -> result.getConfZits();
            case NONE -> 0f;
        };
    }

    /** Alert payloads name the class that actually fired, not whichever class scored highest. */
    private static DetectionResult singleClassResult(DetectionResult result, FailureType type, float confidence) {
        return new DetectionResult(result.getConfSpaghetti(), result.getConfStringing(), result.getConfZits(),
                type, confidence,
                result.getAnchorsSpaghetti(), result.getAnchorsStringing(), result.getAnchorsZits());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * The printer's current state, taken from the continuous telemetry cache so that this loop
     * does not spend two HTTP round-trips per printer per cycle waiting on the network.
     * <p>
     * Falls back to a direct query when the printer is not being sampled - either because
     * continuous collection is switched off, or because polling has not started for it yet.
     */
    private PrinterTelemetry currentTelemetry(Printer printer) {
        if (telemetryService.isSampling(printer.getId())) {
            PrinterTelemetry cached = telemetryService.latestFull(printer.getId()).orElse(null);
            if (cached != null) {
                return cached;
            }
        }
        return moonrakerClient.getTelemetry(printer);
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
