package h848.software.yoloraker.web;

import h848.software.yoloraker.db.DatabaseManager;
import h848.software.yoloraker.model.Printer;
import h848.software.yoloraker.moonraker.MoonrakerClient;
import h848.software.yoloraker.moonraker.PrinterTelemetry;
import h848.software.yoloraker.ai.DetectionService;
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
    private final DetectionService detectionService;

    public WebServer(int port, DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.moonrakerClient = new MoonrakerClient();
        this.detectionService = new DetectionService(dbManager, this.moonrakerClient);
        this.detectionService.start();
        
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
                ctx.status(200);
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
                ctx.status(201).json(p);
            });
            
            config.routes.put("/api/printers/{id}", ctx -> {
                String id = ctx.pathParam("id");
                Printer p = ctx.bodyAsClass(Printer.class);
                p.setId(id);
                dbManager.updatePrinter(p);
                ctx.status(200).json(p);
            });
            
            config.routes.delete("/api/printers/{id}", ctx -> {
                String id = ctx.pathParam("id");
                dbManager.deletePrinter(id);
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
                    ctx.json(telemetry);
                }
            });
        });

        app.start(port);
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
        if (app != null) {
            logger.info("Stopping web server.");
            app.stop();
        }
    }
}
