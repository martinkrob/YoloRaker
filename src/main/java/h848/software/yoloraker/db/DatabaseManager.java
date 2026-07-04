package h848.software.yoloraker.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import h848.software.yoloraker.model.AdminProfile;
import h848.software.yoloraker.model.Printer;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private Jdbi jdbi;
    private HikariDataSource dataSource;

    public DatabaseManager(String dbPath) {
        initDataSource(dbPath);
        initTables();
    }

    private void initDataSource(String dbPath) {
        logger.info("Initializing H2 database at path: {}", dbPath);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:file:" + dbPath + ";AUTO_SERVER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(10);

        this.dataSource = new HikariDataSource(config);
        this.jdbi = Jdbi.create(dataSource);
    }

    private void initTables() {
        logger.info("Verifying database tables existence...");
        jdbi.useHandle(handle -> {
            // Table for global Key-Value settings (e.g. auth)
            handle.execute(
                "CREATE TABLE IF NOT EXISTS app_config (" +
                "config_key VARCHAR(100) PRIMARY KEY, " +
                "config_value TEXT NOT NULL)"
            );

            // Table for individual printers
            handle.execute(
                "CREATE TABLE IF NOT EXISTS printers (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "hostname VARCHAR(255) NOT NULL, " +
                "api_key VARCHAR(255), " +
                "webcam_url VARCHAR(255), " +
                "webhook_url VARCHAR(255), " +
                "enabled BOOLEAN DEFAULT TRUE)"
            );
            
            // Table for event history
            handle.execute(
                "CREATE TABLE IF NOT EXISTS events (" +
                "id IDENTITY PRIMARY KEY, " +
                "printer_id VARCHAR(36), " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "event_type VARCHAR(50) NOT NULL, " +
                "image_path VARCHAR(255), " +
                "notes TEXT, " +
                "FOREIGN KEY (printer_id) REFERENCES printers(id) ON DELETE CASCADE)"
            );
            
            // Seed default admin user if not exists
            if (!hasConfig(handle, "admin_user")) {
                setConfig(handle, "admin_user", "admin");
                setConfig(handle, "admin_pass", "admin");
                setConfig(handle, "admin_display_name", "Administrátor");
                setConfig(handle, "auth_disabled", "false");
                logger.info("Created default admin credentials (admin/admin)");
            }
        });
        logger.info("Database tables are ready.");
    }

    // --- Config and Profile Methods ---
    
    public AdminProfile getAdminProfile() {
        return jdbi.withHandle(h -> {
            String user = h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'admin_user'")
                           .mapTo(String.class).findOne().orElse("admin");
            String display = h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'admin_display_name'")
                           .mapTo(String.class).findOne().orElse("Administrátor");
            boolean authDisabled = Boolean.parseBoolean(h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'auth_disabled'")
                           .mapTo(String.class).findOne().orElse("false"));
            return new AdminProfile(user, display, authDisabled);
        });
    }
    
    public void updateAdminProfile(AdminProfile profile) {
        jdbi.useHandle(h -> {
            setConfig(h, "admin_user", profile.getUsername());
            setConfig(h, "admin_display_name", profile.getDisplayName());
            setConfig(h, "auth_disabled", String.valueOf(profile.isAuthDisabled()));
            if (profile.getPassword() != null && !profile.getPassword().trim().isEmpty()) {
                setConfig(h, "admin_pass", profile.getPassword());
            }
        });
    }

    private boolean hasConfig(org.jdbi.v3.core.Handle h, String key) {
        return h.createQuery("SELECT count(*) FROM app_config WHERE config_key = :key")
                .bind("key", key)
                .mapTo(Long.class)
                .one() > 0;
    }

    private void setConfig(org.jdbi.v3.core.Handle h, String key, String value) {
        h.createUpdate("MERGE INTO app_config (config_key, config_value) KEY(config_key) VALUES (:key, :value)")
         .bind("key", key)
         .bind("value", value)
         .execute();
    }

    // --- Authentication ---
    
    public boolean verifyAdmin(String username, String password) {
        return jdbi.withHandle(h -> {
            boolean authDisabled = Boolean.parseBoolean(
                h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'auth_disabled'")
                 .mapTo(String.class).findOne().orElse("false")
            );
            
            if (authDisabled) {
                return true; // Bypass authentication
            }

            Optional<String> dbUser = h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'admin_user'").mapTo(String.class).findFirst();
            Optional<String> dbPass = h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'admin_pass'").mapTo(String.class).findFirst();
            
            return dbUser.isPresent() && dbPass.isPresent() 
                && dbUser.get().equals(username) && dbPass.get().equals(password);
        });
    }

    // --- Printer CRUD ---

    public List<Printer> getAllPrinters() {
        return jdbi.withHandle(handle -> 
            handle.createQuery("SELECT * FROM printers")
                  .map((rs, ctx) -> mapPrinter(rs))
                  .list()
        );
    }

    public Printer getPrinterById(String id) {
        return jdbi.withHandle(handle -> 
            handle.createQuery("SELECT * FROM printers WHERE id = :id")
                  .bind("id", id)
                  .map((rs, ctx) -> mapPrinter(rs))
                  .findFirst()
                  .orElse(null)
        );
    }

    private Printer mapPrinter(java.sql.ResultSet rs) throws java.sql.SQLException {
        Printer p = new Printer();
        p.setId(rs.getString("id"));
        p.setName(rs.getString("name"));
        p.setHostname(rs.getString("hostname"));
        p.setApiKey(rs.getString("api_key"));
        p.setWebcamUrl(rs.getString("webcam_url"));
        p.setWebhookUrl(rs.getString("webhook_url"));
        p.setEnabled(rs.getBoolean("enabled"));
        return p;
    }

    public void addPrinter(Printer p) {
        jdbi.useHandle(handle -> 
            handle.createUpdate("INSERT INTO printers (id, name, hostname, api_key, webcam_url, webhook_url, enabled) " +
                                "VALUES (:id, :name, :hostname, :apiKey, :webcamUrl, :webhookUrl, :enabled)")
                  .bindBean(p)
                  .execute()
        );
    }

    public void updatePrinter(Printer p) {
        jdbi.useHandle(handle -> 
            handle.createUpdate("UPDATE printers SET name=:name, hostname=:hostname, api_key=:apiKey, " +
                                "webcam_url=:webcamUrl, webhook_url=:webhookUrl, enabled=:enabled WHERE id=:id")
                  .bindBean(p)
                  .execute()
        );
    }

    public void deletePrinter(String id) {
        jdbi.useHandle(handle -> 
            handle.createUpdate("DELETE FROM printers WHERE id = :id")
                  .bind("id", id)
                  .execute()
        );
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing database connection.");
            dataSource.close();
        }
    }
}
