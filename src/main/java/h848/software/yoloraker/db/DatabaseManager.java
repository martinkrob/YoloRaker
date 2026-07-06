package h848.software.yoloraker.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import h848.software.yoloraker.model.AdminProfile;
import h848.software.yoloraker.model.Printer;
import h848.software.yoloraker.model.AiAlarm;
import h848.software.yoloraker.model.PrintJob;
import h848.software.yoloraker.model.TelemetryLog;
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
                "webhook_telemetry_enabled BOOLEAN DEFAULT FALSE, " +
                "enabled BOOLEAN DEFAULT TRUE, " +
                "threshold_spaghetti FLOAT DEFAULT 0.60, " +
                "threshold_stringing FLOAT DEFAULT 0.70, " +
                "threshold_zits FLOAT DEFAULT 0.70, " +
                "mqtt_broker VARCHAR(255), " +
                "mqtt_topic VARCHAR(255), " +
                "mqtt_username VARCHAR(255), " +
                "mqtt_password VARCHAR(255), " +
                "mqtt_client_id VARCHAR(255), " +
                "mqtt_telemetry_enabled BOOLEAN DEFAULT FALSE)"
            );
            
            // Alter table for existing databases
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS threshold_spaghetti FLOAT DEFAULT 0.60");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS threshold_stringing FLOAT DEFAULT 0.70");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS threshold_zits FLOAT DEFAULT 0.70");
            
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS mqtt_broker VARCHAR(255)");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS mqtt_topic VARCHAR(255)");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS mqtt_username VARCHAR(255)");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS mqtt_password VARCHAR(255)");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS mqtt_client_id VARCHAR(255)");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS webhook_telemetry_enabled BOOLEAN DEFAULT FALSE");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS mqtt_telemetry_enabled BOOLEAN DEFAULT FALSE");
            
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

            // AI Alarms
            handle.execute(
                "CREATE TABLE IF NOT EXISTS ai_alarms (" +
                "id IDENTITY PRIMARY KEY, " +
                "printer_id VARCHAR(36), " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "filename VARCHAR(255), " +
                "trigger_type VARCHAR(50), " +
                "confidence FLOAT, " +
                "image_data BLOB, " +
                "FOREIGN KEY (printer_id) REFERENCES printers(id) ON DELETE CASCADE)"
            );

            // Print Jobs
            handle.execute(
                "CREATE TABLE IF NOT EXISTS print_jobs (" +
                "id IDENTITY PRIMARY KEY, " +
                "printer_id VARCHAR(36), " +
                "filename VARCHAR(255), " +
                "start_time TIMESTAMP, " +
                "end_time TIMESTAMP, " +
                "status VARCHAR(50), " +
                "duration_seconds DOUBLE, " +
                "extruded_filament DOUBLE, " +
                "FOREIGN KEY (printer_id) REFERENCES printers(id) ON DELETE CASCADE)"
            );

            // Telemetry Logs
            handle.execute(
                "CREATE TABLE IF NOT EXISTS telemetry_logs (" +
                "id IDENTITY PRIMARY KEY, " +
                "printer_id VARCHAR(36), " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "extruder_temp DOUBLE, " +
                "bed_temp DOUBLE, " +
                "print_progress DOUBLE, " +
                "conf_spaghetti FLOAT, " +
                "conf_stringing FLOAT, " +
                "conf_zits FLOAT, " +
                "FOREIGN KEY (printer_id) REFERENCES printers(id) ON DELETE CASCADE)"
            );
            
            // Seed default admin user if not exists
            if (!hasConfig(handle, "admin_user")) {
                setConfig(handle, "admin_user", "admin");
                setConfig(handle, "admin_pass", "admin");
                setConfig(handle, "admin_display_name", "Administrátor");
                setConfig(handle, "auth_disabled", "true");
                
                // Defaults for retention
                setConfig(handle, "retention_telemetry_count", "10000");
                setConfig(handle, "retention_alarms_count", "500");
                setConfig(handle, "retention_jobs_count", "1000");
                
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
            
            int retTelemetry = Integer.parseInt(h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'retention_telemetry_count'")
                           .mapTo(String.class).findOne().orElse("10000"));
            int retAlarms = Integer.parseInt(h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'retention_alarms_count'")
                           .mapTo(String.class).findOne().orElse("500"));
            int retJobs = Integer.parseInt(h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'retention_jobs_count'")
                           .mapTo(String.class).findOne().orElse("1000"));
                           
            return new AdminProfile(user, display, authDisabled, retTelemetry, retAlarms, retJobs);
        });
    }
    
    public void updateAdminProfile(AdminProfile profile) {
        jdbi.useHandle(h -> {
            setConfig(h, "admin_user", profile.getUsername());
            setConfig(h, "admin_display_name", profile.getDisplayName());
            setConfig(h, "auth_disabled", String.valueOf(profile.isAuthDisabled()));
            
            setConfig(h, "retention_telemetry_count", String.valueOf(profile.getRetentionTelemetryCount()));
            setConfig(h, "retention_alarms_count", String.valueOf(profile.getRetentionAlarmsCount()));
            setConfig(h, "retention_jobs_count", String.valueOf(profile.getRetentionJobsCount()));
            
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
        p.setWebhookTelemetryEnabled(rs.getBoolean("webhook_telemetry_enabled"));
        p.setEnabled(rs.getBoolean("enabled"));
        p.setThresholdSpaghetti(rs.getFloat("threshold_spaghetti"));
        p.setThresholdStringing(rs.getFloat("threshold_stringing"));
        p.setThresholdZits(rs.getFloat("threshold_zits"));
        p.setMqttBroker(rs.getString("mqtt_broker"));
        p.setMqttTopic(rs.getString("mqtt_topic"));
        p.setMqttUsername(rs.getString("mqtt_username"));
        p.setMqttPassword(rs.getString("mqtt_password"));
        p.setMqttClientId(rs.getString("mqtt_client_id"));
        p.setMqttTelemetryEnabled(rs.getBoolean("mqtt_telemetry_enabled"));
        return p;
    }

    public void addPrinter(Printer p) {
        jdbi.useHandle(handle -> 
            handle.createUpdate("INSERT INTO printers (id, name, hostname, api_key, webcam_url, webhook_url, webhook_telemetry_enabled, enabled, threshold_spaghetti, threshold_stringing, threshold_zits, mqtt_broker, mqtt_topic, mqtt_username, mqtt_password, mqtt_client_id, mqtt_telemetry_enabled) " +
                                "VALUES (:id, :name, :hostname, :apiKey, :webcamUrl, :webhookUrl, :webhookTelemetryEnabled, :enabled, :thresholdSpaghetti, :thresholdStringing, :thresholdZits, :mqttBroker, :mqttTopic, :mqttUsername, :mqttPassword, :mqttClientId, :mqttTelemetryEnabled)")
                  .bindBean(p)
                  .execute()
        );
    }

    public void updatePrinter(Printer p) {
        jdbi.useHandle(handle -> 
            handle.createUpdate("UPDATE printers SET name=:name, hostname=:hostname, api_key=:apiKey, " +
                                "webcam_url=:webcamUrl, webhook_url=:webhookUrl, webhook_telemetry_enabled=:webhookTelemetryEnabled, enabled=:enabled, " +
                                "threshold_spaghetti=:thresholdSpaghetti, threshold_stringing=:thresholdStringing, threshold_zits=:thresholdZits, " +
                                "mqtt_broker=:mqttBroker, mqtt_topic=:mqttTopic, mqtt_username=:mqttUsername, mqtt_password=:mqttPassword, mqtt_client_id=:mqttClientId, mqtt_telemetry_enabled=:mqttTelemetryEnabled " +
                                "WHERE id=:id")
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

    // --- History DAOs ---

    public void saveAiAlarm(AiAlarm alarm) {
        jdbi.useHandle(h -> 
            h.createUpdate("INSERT INTO ai_alarms (printer_id, filename, trigger_type, confidence, image_data) " +
                           "VALUES (:printerId, :filename, :triggerType, :confidence, :imageData)")
             .bindBean(alarm)
             .execute()
        );
    }

    public void savePrintJob(PrintJob job) {
        Long id = jdbi.withHandle(h -> 
            h.createUpdate("INSERT INTO print_jobs (printer_id, filename, start_time, status) " +
                           "VALUES (:printerId, :filename, :startTime, :status)")
             .bindBean(job)
             .executeAndReturnGeneratedKeys("id")
             .mapTo(Long.class)
             .one()
        );
        job.setId(id);
    }

    public void updatePrintJob(PrintJob job) {
        jdbi.useHandle(h -> 
            h.createUpdate("UPDATE print_jobs SET end_time=:endTime, status=:status, " +
                           "duration_seconds=:durationSeconds, extruded_filament=:extrudedFilament " +
                           "WHERE id=:id")
             .bindBean(job)
             .execute()
        );
    }

    public PrintJob getLatestActivePrintJob(String printerId) {
        return jdbi.withHandle(h -> 
            h.createQuery("SELECT * FROM print_jobs WHERE printer_id = :printerId AND status = 'printing' ORDER BY id DESC LIMIT 1")
             .bind("printerId", printerId)
             .map((rs, ctx) -> {
                 PrintJob pj = new PrintJob();
                 pj.setId(rs.getLong("id"));
                 pj.setPrinterId(rs.getString("printer_id"));
                 pj.setFilename(rs.getString("filename"));
                 pj.setStartTime(rs.getTimestamp("start_time"));
                 pj.setEndTime(rs.getTimestamp("end_time"));
                 pj.setStatus(rs.getString("status"));
                 pj.setDurationSeconds(rs.getDouble("duration_seconds"));
                 pj.setExtrudedFilament(rs.getDouble("extruded_filament"));
                 return pj;
             })
             .findFirst()
             .orElse(null)
        );
    }

    public void saveTelemetryLog(TelemetryLog log) {
        jdbi.useHandle(h -> 
            h.createUpdate("INSERT INTO telemetry_logs (printer_id, extruder_temp, bed_temp, print_progress, conf_spaghetti, conf_stringing, conf_zits) " +
                           "VALUES (:printerId, :extruderTemp, :bedTemp, :printProgress, :confSpaghetti, :confStringing, :confZits)")
             .bindBean(log)
             .execute()
        );
    }

    public void logEvent(String printerId, String eventType, String notes) {
        jdbi.useHandle(h -> 
            h.createUpdate("INSERT INTO events (printer_id, event_type, notes) VALUES (:printerId, :eventType, :notes)")
             .bind("printerId", printerId)
             .bind("eventType", eventType)
             .bind("notes", notes)
             .execute()
        );
    }

    // --- Retention Purging ---
    
    public int purgeOldTelemetry(int keepCount) {
        return jdbi.withHandle(h -> {
            List<String> printers = h.createQuery("SELECT id FROM printers").mapTo(String.class).list();
            int deleted = 0;
            for (String pid : printers) {
                deleted += h.createUpdate("DELETE FROM telemetry_logs WHERE printer_id = :pid AND id NOT IN (SELECT id FROM telemetry_logs WHERE printer_id = :pid ORDER BY id DESC LIMIT :keepCount)")
                            .bind("pid", pid)
                            .bind("keepCount", keepCount)
                            .execute();
            }
            return deleted;
        });
    }
    
    public int purgeOldAiAlarms(int keepCount) {
        return jdbi.withHandle(h -> {
            List<String> printers = h.createQuery("SELECT id FROM printers").mapTo(String.class).list();
            int deleted = 0;
            for (String pid : printers) {
                deleted += h.createUpdate("DELETE FROM ai_alarms WHERE printer_id = :pid AND id NOT IN (SELECT id FROM ai_alarms WHERE printer_id = :pid ORDER BY id DESC LIMIT :keepCount)")
                            .bind("pid", pid)
                            .bind("keepCount", keepCount)
                            .execute();
            }
            return deleted;
        });
    }
    
    public int purgeOldPrintJobs(int keepCount) {
        return jdbi.withHandle(h -> {
            List<String> printers = h.createQuery("SELECT id FROM printers").mapTo(String.class).list();
            int deleted = 0;
            String dataPath = System.getenv().getOrDefault("YOLORAKER_DATA_PATH", "./data");
            
            for (String pid : printers) {
                // Find jobs that are going to be deleted
                List<Long> jobsToDelete = h.createQuery("SELECT id FROM print_jobs WHERE printer_id = :pid AND id NOT IN (SELECT id FROM print_jobs WHERE printer_id = :pid ORDER BY id DESC LIMIT :keepCount)")
                                           .bind("pid", pid)
                                           .bind("keepCount", keepCount)
                                           .mapTo(Long.class)
                                           .list();
                                           
                for (Long jobId : jobsToDelete) {
                    // Delete snapshot folder from disk
                    java.io.File jobDir = new java.io.File(dataPath + "/snapshots/" + pid + "/" + jobId);
                    if (jobDir.exists() && jobDir.isDirectory()) {
                        java.io.File[] files = jobDir.listFiles();
                        if (files != null) {
                            for (java.io.File f : files) f.delete();
                        }
                        jobDir.delete();
                    }
                }
                
                // Now delete from DB
                deleted += h.createUpdate("DELETE FROM print_jobs WHERE printer_id = :pid AND id IN (<jobs>)")
                            .bind("pid", pid)
                            .bindList("jobs", jobsToDelete.isEmpty() ? List.of(-1L) : jobsToDelete)
                            .execute();
            }
            return deleted;
        });
    }
    
    // --- History Fetching (Read) ---
    
    public List<PrintJob> getPrintJobs(String printerId, int limit) {
        return jdbi.withHandle(h -> 
            h.createQuery("SELECT * FROM print_jobs WHERE printer_id = :printerId ORDER BY id DESC LIMIT :limit")
             .bind("printerId", printerId)
             .bind("limit", limit)
             .map((rs, ctx) -> {
                 PrintJob pj = new PrintJob();
                 pj.setId(rs.getLong("id"));
                 pj.setPrinterId(rs.getString("printer_id"));
                 pj.setFilename(rs.getString("filename"));
                 pj.setStartTime(rs.getTimestamp("start_time"));
                 pj.setEndTime(rs.getTimestamp("end_time"));
                 pj.setStatus(rs.getString("status"));
                 pj.setDurationSeconds(rs.getDouble("duration_seconds"));
                 pj.setExtrudedFilament(rs.getDouble("extruded_filament"));
                 return pj;
             })
             .list()
        );
    }
    
    public List<AiAlarm> getAiAlarms(String printerId, int limit) {
        return jdbi.withHandle(h -> 
            h.createQuery("SELECT id, printer_id, timestamp, filename, trigger_type, confidence FROM ai_alarms WHERE printer_id = :printerId ORDER BY id DESC LIMIT :limit")
             .bind("printerId", printerId)
             .bind("limit", limit)
             .map((rs, ctx) -> {
                 AiAlarm a = new AiAlarm();
                 a.setId(rs.getLong("id"));
                 a.setPrinterId(rs.getString("printer_id"));
                 a.setTimestamp(rs.getTimestamp("timestamp"));
                 a.setFilename(rs.getString("filename"));
                 a.setTriggerType(rs.getString("trigger_type"));
                 a.setConfidence(rs.getFloat("confidence"));
                 // Do not load BLOB data here to save memory. Fetched separately via getAiAlarmImage.
                 return a;
             })
             .list()
        );
    }
    
    public byte[] getAiAlarmImage(long alarmId) {
        return jdbi.withHandle(h -> 
            h.createQuery("SELECT image_data FROM ai_alarms WHERE id = :id")
             .bind("id", alarmId)
             .mapTo(byte[].class)
             .findFirst()
             .orElse(null)
        );
    }
    
    public List<TelemetryLog> getTelemetryLogs(String printerId, int limit) {
        return jdbi.withHandle(h -> 
            h.createQuery("SELECT * FROM telemetry_logs WHERE printer_id = :printerId ORDER BY id DESC LIMIT :limit")
             .bind("printerId", printerId)
             .bind("limit", limit)
             .map((rs, ctx) -> {
                 TelemetryLog t = new TelemetryLog();
                 t.setId(rs.getLong("id"));
                 t.setPrinterId(rs.getString("printer_id"));
                 t.setTimestamp(rs.getTimestamp("timestamp"));
                 t.setExtruderTemp(rs.getDouble("extruder_temp"));
                 t.setBedTemp(rs.getDouble("bed_temp"));
                 t.setPrintProgress(rs.getDouble("print_progress"));
                 t.setConfSpaghetti(rs.getFloat("conf_spaghetti"));
                 t.setConfStringing(rs.getFloat("conf_stringing"));
                 t.setConfZits(rs.getFloat("conf_zits"));
                 return t;
             })
             .list()
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
