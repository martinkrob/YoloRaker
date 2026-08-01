package h848.software.yoloraker.web;

import h848.software.yoloraker.db.DatabaseManager;
import h848.software.yoloraker.model.Printer;
import h848.software.yoloraker.moonraker.MoonrakerClient;
import h848.software.yoloraker.moonraker.PrinterTelemetry;
import h848.software.yoloraker.ai.DetectionResult;
import h848.software.yoloraker.ai.DetectionService;
import h848.software.yoloraker.ai.ModelService;
import h848.software.yoloraker.core.RetentionService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServer {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);
    private final Javalin app;
    private final DatabaseManager dbManager;
    private final MoonrakerClient moonrakerClient;
    private final ModelService modelService;
    private final DetectionService detectionService;
    private final RetentionService retentionService;

    public WebServer(int port, DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.moonrakerClient = new MoonrakerClient();
        this.modelService = new ModelService();
        this.detectionService = new DetectionService(dbManager, this.moonrakerClient, this.modelService);
        this.detectionService.start();
        
        this.retentionService = new RetentionService(dbManager);
        this.retentionService.start();
        
        logger.info("Initializing Javalin web server on port: {}", port);
        
        this.app = Javalin.create(config -> {
            // Serve static files from src/main/resources/public
            config.staticFiles.add("/public", Location.CLASSPATH);

            // Basic request logging
            config.requestLogger.http((ctx, ms) -> {
                logger.info("{} {} - {} ({}ms)", ctx.method(), ctx.path(), ctx.status(), ms);
            });

            // Define routes upfront
            config.routes.before("/api/*", this::handleAuth);
            
            // System endpoints
            config.routes.get("/api/status", ctx -> ctx.json("{\"status\": \"OK\"}"));

            // API: Get Admin Profile
            config.routes.get("/api/profile", ctx -> {
                ctx.json(dbManager.getAdminProfile());
            });
            
            // API: Update Admin Profile
            config.routes.put("/api/profile", ctx -> {
                var profile = ctx.bodyAsClass(h848.software.yoloraker.model.AdminProfile.class);
                if (profile.getUsername() == null || profile.getUsername().trim().isEmpty() ||
                    profile.getDisplayName() == null || profile.getDisplayName().trim().isEmpty()) {
                    ctx.status(400).result("Username and Display Name are required");
                    return;
                }
                dbManager.updateAdminProfile(profile);
                dbManager.logEvent(null, "PROFILE_UPDATED", "Admin profile updated");
                ctx.status(200);
            });
            
            // API: Model Management
            config.routes.get("/api/models", ctx -> {
                ctx.json(modelService.getAvailableModels());
            });
            
            config.routes.post("/api/models/upload", ctx -> {
                io.javalin.http.UploadedFile uploadedFile = ctx.uploadedFile("file");
                if (uploadedFile != null) {
                    boolean success = modelService.saveModel(uploadedFile.filename(), uploadedFile.content());
                    if (success) {
                        ctx.status(200).result("Model uploaded successfully");
                    } else {
                        ctx.status(500).result("Failed to save model");
                    }
                } else {
                    ctx.status(400).result("No file uploaded");
                }
            });
            
            config.routes.delete("/api/models/{filename}", ctx -> {
                String filename = ctx.pathParam("filename");
                boolean success = modelService.deleteModel(filename);
                if (success) {
                    ctx.status(204);
                } else {
                    ctx.status(404).result("Model not found or cannot be deleted");
                }
            });

            // Printer CRUD endpoints
            config.routes.get("/api/printers", ctx -> {
                ctx.json(dbManager.getAllPrinters());
            });
            
            config.routes.post("/api/printers", ctx -> {
                Printer p = ctx.bodyAsClass(Printer.class);
                if (p.getId() == null || p.getId().isEmpty()) {
                    p.setId(java.util.UUID.randomUUID().toString());
                }
                dbManager.addPrinter(p);
                dbManager.logEvent(p.getId(), "PRINTER_ADDED", "Printer added: " + p.getName());
                ctx.status(201).json(p);
            });
            
            config.routes.put("/api/printers/{id}", ctx -> {
                String id = ctx.pathParam("id");
                Printer p = ctx.bodyAsClass(Printer.class);
                p.setId(id);
                dbManager.updatePrinter(p);
                detectionService.invalidatePrinterModel(id);
                dbManager.logEvent(p.getId(), "PRINTER_UPDATED", "Printer updated: " + p.getName());
                ctx.status(200).json(p);
            });
            
            config.routes.delete("/api/printers/{id}", ctx -> {
                String id = ctx.pathParam("id");
                dbManager.deletePrinter(id);
                detectionService.invalidatePrinterModel(id);
                // Log with null printer_id: the events row has an ON DELETE CASCADE FK to printers,
                // so it cannot reference the now-deleted printer. The id is kept in the notes instead.
                dbManager.logEvent(null, "PRINTER_DELETED", "Printer deleted: " + id);
                ctx.status(204);
            });

            // Telemetry Endpoint
            config.routes.get("/api/printers/{id}/telemetry", ctx -> {
                String id = ctx.pathParam("id");
                Printer p = dbManager.getPrinterById(id);
                if (p == null) {
                    ctx.status(404).json("{\"error\": \"Printer not found\"}");
                } else {
                    PrinterTelemetry telemetry = moonrakerClient.getTelemetry(p);
                    if (telemetry != null) {
                        DetectionResult aiResult = detectionService.getLatestResult(id);
                        if (aiResult != null) {
                            telemetry.setAiSpaghettiConf(aiResult.getConfSpaghetti());
                            telemetry.setAiStringingConf(aiResult.getConfStringing());
                            telemetry.setAiZitsConf(aiResult.getConfZits());
                        }
                        telemetry.setActiveModelName(p.getAiModel() != null ? p.getAiModel() : "INBUILT");
                    }
                    ctx.json(telemetry);
                }
            });

            // --- History Endpoints ---
            config.routes.get("/api/printers/{id}/history/jobs", ctx -> {
                String id = ctx.pathParam("id");
                int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100);
                ctx.json(dbManager.getPrintJobs(id, limit));
            });

            config.routes.get("/api/printers/{id}/history/alarms", ctx -> {
                String id = ctx.pathParam("id");
                int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
                ctx.json(dbManager.getAiAlarms(id, limit));
            });

            config.routes.get("/api/printers/{id}/history/telemetry", ctx -> {
                String id = ctx.pathParam("id");
                int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(2880); // 1 day at 30s
                ctx.json(dbManager.getTelemetryLogs(id, limit));
            });
            
            // --- Snapshots ---
            config.routes.get("/api/printers/{id}/history/jobs/{jobId}/snapshots", ctx -> {
                String printerId = ctx.pathParam("id");
                String jobId = ctx.pathParam("jobId");

                if (!isSafePathSegment(printerId) || !isSafePathSegment(jobId)) {
                    ctx.status(400).result("Invalid path");
                    return;
                }

                String dataPath = System.getenv().getOrDefault("YOLORAKER_DATA_PATH", "./data");
                java.io.File dir = new java.io.File(dataPath + "/snapshots/" + printerId + "/" + jobId);

                java.util.List<String> files = new java.util.ArrayList<>();
                if (dir.exists() && dir.isDirectory()) {
                    java.io.File[] list = dir.listFiles((d, name) -> name.endsWith(".jpg"));
                    if (list != null) {
                        java.util.Arrays.sort(list, java.util.Comparator.comparing(java.io.File::getName));
                        for (java.io.File f : list) {
                            files.add(f.getName());
                        }
                    }
                }
                ctx.json(files);
            });
            
            config.routes.get("/api/printers/{id}/history/jobs/{jobId}/snapshots/{filename}", ctx -> {
                String printerId = ctx.pathParam("id");
                String jobId = ctx.pathParam("jobId");
                String filename = ctx.pathParam("filename");

                // Reject path traversal / separators in user-supplied path segments.
                if (!isSafePathSegment(printerId) || !isSafePathSegment(jobId) || !isSafePathSegment(filename)) {
                    ctx.status(400).result("Invalid path");
                    return;
                }

                String dataPath = System.getenv().getOrDefault("YOLORAKER_DATA_PATH", "./data");
                java.io.File file = new java.io.File(dataPath + "/snapshots/" + printerId + "/" + jobId + "/" + filename);

                if (file.exists() && file.isFile()) {
                    try {
                        ctx.contentType("image/jpeg");
                        ctx.result(java.nio.file.Files.readAllBytes(file.toPath()));
                    } catch (Exception e) {
                        ctx.status(500).result("Error reading image");
                    }
                } else {
                    ctx.status(404).result("Snapshot not found");
                }
            });

            // Public image endpoint (requires auth like the rest due to wildcard, which is fine)
            config.routes.get("/api/alarms/{alarmId}/image", ctx -> {
                try {
                    long alarmId = Long.parseLong(ctx.pathParam("alarmId"));
                    byte[] img = dbManager.getAiAlarmImage(alarmId);
                    if (img != null) {
                        ctx.contentType("image/jpeg");
                        ctx.result(img);
                    } else {
                        ctx.status(404).result("Image not found");
                    }
                } catch (NumberFormatException e) {
                    ctx.status(400).result("Invalid alarm ID");
                }
            });

            // Test Notifications Endpoint
            config.routes.post("/api/test-alert", ctx -> {
                try {
                    Printer p = ctx.bodyAsClass(Printer.class);
                    if (p.getId() == null || p.getId().isEmpty()) {
                        p.setId("test_printer");
                        p.setName("Test Printer");
                    }
                    
                    h848.software.yoloraker.ai.AlertClient alertClient = new h848.software.yoloraker.ai.AlertClient();
                    h848.software.yoloraker.ai.DetectionResult testResult = new h848.software.yoloraker.ai.DetectionResult(
                        0.99f, 0.05f, 0.02f, h848.software.yoloraker.ai.DetectionResult.FailureType.SPAGHETTI, 0.99f
                    );
                    
                    alertClient.sendWebhook(p, testResult);
                    alertClient.sendMqttMessage(p, testResult);
                    
                    ctx.status(200).result("Test alerts triggered (Check console/backend logs for errors).");
                } catch (Exception e) {
                    logger.error("Failed to process test alert", e);
                    ctx.status(500).result("Error triggering test: " + e.getMessage());
                }
            });

        });

        app.start(port);
    }

    /** Guards against path traversal in user-supplied path segments used to build file paths. */
    private static boolean isSafePathSegment(String segment) {
        return segment != null
                && !segment.isEmpty()
                && !segment.contains("/")
                && !segment.contains("\\")
                && !segment.contains("..");
    }

    private void handleAuth(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            ctx.header("WWW-Authenticate", "Basic realm=\"YoloRaker Admin\"");
            throw new UnauthorizedResponse("Credentials required");
        }
        
        var credentials = ctx.basicAuthCredentials();
        boolean valid = dbManager.verifyAdmin(credentials.getUsername(), credentials.getPassword());
        
        if (!valid) {
            ctx.header("WWW-Authenticate", "Basic realm=\"YoloRaker Admin\"");
            throw new UnauthorizedResponse("Invalid credentials");
        }
    }

    public void stop() {
        if (detectionService != null) {
            detectionService.stop();
        }
        if (retentionService != null) {
            retentionService.stop();
        }
        if (app != null) {
            logger.info("Stopping web server.");
            app.stop();
        }
    }
}
