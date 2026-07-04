package h848.software.yoloraker;

import h848.software.yoloraker.db.DatabaseManager;
import h848.software.yoloraker.web.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    // Path to the database (in a future container, this could be a mapped volume, e.g., /data/yoloraker)
    private static final String DB_PATH = System.getenv().getOrDefault("YOLORAKER_DB_PATH", "./data/yoloraker");
    // Default port for the web server
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        logger.info("=========================================");
        logger.info("      Starting YoloRaker Microservice    ");
        logger.info("=========================================");

        // 1. Get HTTP port (first from environment variable, then fallback to default)
        int port = getPort();

        // 2. Initialize database connection
        DatabaseManager dbManager = new DatabaseManager(DB_PATH);

        // 3. Start the web server
        WebServer webServer = new WebServer(port, dbManager);

        // 4. Set up Graceful Shutdown hook (close DB when application exits)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Performing graceful shutdown...");
            webServer.stop();
            dbManager.close();
            logger.info("Application shut down successfully.");
        }));
    }

    private static int getPort() {
        String portStr = System.getenv("YOLORAKER_PORT");
        if (portStr != null) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                logger.warn("Invalid port format in YOLORAKER_PORT: {}. Using default: {}", portStr, DEFAULT_PORT);
            }
        }
        return DEFAULT_PORT;
    }
}
