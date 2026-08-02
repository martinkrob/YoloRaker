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
        // NOTE: H2 forbids DB_CLOSE_ON_EXIT=FALSE together with AUTO_SERVER=TRUE, so we keep
        // AUTO_SERVER (lets external tools connect while running). The harmless "Database is
        // already closed" message that can appear during JVM shutdown is a benign hook-ordering race.
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
                "mqtt_telemetry_enabled BOOLEAN DEFAULT FALSE, " +
                "detect_spaghetti BOOLEAN DEFAULT TRUE, " +
                "detect_stringing BOOLEAN DEFAULT TRUE, " +
                "detect_zits BOOLEAN DEFAULT TRUE, " +
                "klipper_screen_telemetry_enabled BOOLEAN DEFAULT FALSE, " +
                "ai_model VARCHAR(100) DEFAULT 'INBUILT')"
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
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS detect_spaghetti BOOLEAN DEFAULT TRUE");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS detect_stringing BOOLEAN DEFAULT TRUE");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS detect_zits BOOLEAN DEFAULT TRUE");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS klipper_screen_telemetry_enabled BOOLEAN DEFAULT FALSE");
            handle.execute("ALTER TABLE printers ADD COLUMN IF NOT EXISTS ai_model VARCHAR(100) DEFAULT 'INBUILT'");
            
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
            
            // Sensor fusion columns. Existing rows keep NULL, which reads as "recorded before
            // fusion existed" rather than as a zero measurement.
            handle.execute("ALTER TABLE ai_alarms ADD COLUMN IF NOT EXISTS action VARCHAR(20) DEFAULT 'PAUSED'");
            handle.execute("ALTER TABLE telemetry_logs ADD COLUMN IF NOT EXISTS ref_spaghetti FLOAT");
            handle.execute("ALTER TABLE telemetry_logs ADD COLUMN IF NOT EXISTS ref_stringing FLOAT");
            handle.execute("ALTER TABLE telemetry_logs ADD COLUMN IF NOT EXISTS ref_zits FLOAT");
            handle.execute("ALTER TABLE telemetry_logs ADD COLUMN IF NOT EXISTS suppression FLOAT");
            handle.execute("ALTER TABLE telemetry_logs ADD COLUMN IF NOT EXISTS fusion_rules VARCHAR(500)");
            handle.execute("ALTER TABLE telemetry_logs ADD COLUMN IF NOT EXISTS telemetry_window CLOB");
            handle.execute("ALTER TABLE telemetry_logs ADD COLUMN IF NOT EXISTS anchors_spaghetti INT");
            handle.execute("ALTER TABLE telemetry_logs ADD COLUMN IF NOT EXISTS anchors_stringing INT");
            handle.execute("ALTER TABLE telemetry_logs ADD COLUMN IF NOT EXISTS anchors_zits INT");

            // Human review of an incident: TRUE_POSITIVE | FALSE_POSITIVE | NULL (not yet judged).
            // NULL is meaningful - it is the review queue.
            handle.execute("ALTER TABLE ai_alarms ADD COLUMN IF NOT EXISTS ground_truth VARCHAR(20)");
            handle.execute("ALTER TABLE ai_alarms ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP");

            // Seed default admin user if not exists
            if (!hasConfig(handle, "admin_user")) {
                setConfig(handle, "admin_user", "admin");
                setConfig(handle, "admin_pass", PasswordUtil.hash("admin"));
                setConfig(handle, "admin_display_name", "Administrátor");
                // Authentication is ON by default. The user logs in with admin/admin and is
                // expected to change the password immediately (see README).
                setConfig(handle, "auth_disabled", "false");
                
                // Retention: keep everything belonging to the last N prints per printer.
                setConfig(handle, "retention_print_count", String.valueOf(DEFAULT_RETENTION_PRINTS));
                
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
            
            AdminProfile profile = new AdminProfile(user, display, authDisabled, readRetentionPrintCount(h));
            profile.setFusionMode(h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'fusion_mode'")
                           .mapTo(String.class).findOne().orElse("SHADOW"));
            return profile;
        });
    }
    
    public void updateAdminProfile(AdminProfile profile) {
        jdbi.useHandle(h -> {
            setConfig(h, "admin_user", profile.getUsername());
            setConfig(h, "admin_display_name", profile.getDisplayName());
            setConfig(h, "auth_disabled", String.valueOf(profile.isAuthDisabled()));
            
            setConfig(h, "retention_print_count", String.valueOf(Math.max(1, profile.getRetentionPrintCount())));

            if (profile.getFusionMode() != null) {
                // Normalise through the enum so an unrecognised value cannot be persisted.
                setConfig(h, "fusion_mode",
                        h848.software.yoloraker.fusion.FusionMode.parse(profile.getFusionMode()).name());
            }

            if (profile.getPassword() != null && !profile.getPassword().trim().isEmpty()) {
                setConfig(h, "admin_pass", PasswordUtil.hash(profile.getPassword()));
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


    /** Fresh installs keep this many prints per printer. */
    private static final int DEFAULT_RETENTION_PRINTS = 20;

    /**
     * Reads the print-retention limit, carrying an older install's setting across on first read.
     * <p>
     * Retention used to be three separate row caps. Simply defaulting the new single limit would
     * have silently deleted the history of anyone who had raised the old print-job cap, so the
     * old value is adopted instead and only a missing one falls back to the default.
     */
    private int readRetentionPrintCount(org.jdbi.v3.core.Handle h) {
        Optional<String> current = h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'retention_print_count'")
                                    .mapTo(String.class).findOne();
        if (current.isPresent()) {
            return parsePositiveInt(current.get(), DEFAULT_RETENTION_PRINTS);
        }

        Optional<String> legacy = h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'retention_jobs_count'")
                                   .mapTo(String.class).findOne();
        int adopted = legacy.map(v -> parsePositiveInt(v, DEFAULT_RETENTION_PRINTS)).orElse(DEFAULT_RETENTION_PRINTS);
        setConfig(h, "retention_print_count", String.valueOf(adopted));
        if (legacy.isPresent()) {
            logger.info("Retention simplified to a print count. Adopted the previous print-job limit of {} "
                      + "so no history is dropped; lower it in Settings if that is more than you need.", adopted);
        }
        return adopted;
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // --- Sensor fusion mode ---

    /** Defaults to SHADOW: an upgraded install starts recording fusion without acting on it. */
    public h848.software.yoloraker.fusion.FusionMode getFusionMode() {
        return jdbi.withHandle(h -> h848.software.yoloraker.fusion.FusionMode.parse(
                h.createQuery("SELECT config_value FROM app_config WHERE config_key = 'fusion_mode'")
                 .mapTo(String.class).findOne().orElse("SHADOW")));
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

            if (dbUser.isEmpty() || dbPass.isEmpty() || username == null || password == null
                    || !dbUser.get().equals(username)) {
                return false;
            }

            String stored = dbPass.get();
            if (PasswordUtil.isHashed(stored)) {
                return PasswordUtil.verify(password, stored);
            }

            // Legacy plaintext password: verify, then transparently upgrade it to a PBKDF2 hash.
            if (stored.equals(password)) {
                setConfig(h, "admin_pass", PasswordUtil.hash(password));
                return true;
            }
            return false;
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
        p.setDetectSpaghetti(rs.getBoolean("detect_spaghetti"));
        p.setDetectStringing(rs.getBoolean("detect_stringing"));
        p.setDetectZits(rs.getBoolean("detect_zits"));
        p.setKlipperScreenTelemetryEnabled(rs.getBoolean("klipper_screen_telemetry_enabled"));
        p.setAiModel(rs.getString("ai_model"));
        return p;
    }

    public void addPrinter(Printer p) {
        jdbi.useHandle(handle -> 
            handle.createUpdate("INSERT INTO printers (id, name, hostname, api_key, webcam_url, webhook_url, webhook_telemetry_enabled, enabled, threshold_spaghetti, threshold_stringing, threshold_zits, mqtt_broker, mqtt_topic, mqtt_username, mqtt_password, mqtt_client_id, mqtt_telemetry_enabled, detect_spaghetti, detect_stringing, detect_zits, klipper_screen_telemetry_enabled, ai_model) " +
                                "VALUES (:id, :name, :hostname, :apiKey, :webcamUrl, :webhookUrl, :webhookTelemetryEnabled, :enabled, :thresholdSpaghetti, :thresholdStringing, :thresholdZits, :mqttBroker, :mqttTopic, :mqttUsername, :mqttPassword, :mqttClientId, :mqttTelemetryEnabled, :detectSpaghetti, :detectStringing, :detectZits, :klipperScreenTelemetryEnabled, :aiModel)")
                  .bindBean(p)
                  .execute()
        );
    }

    public void updatePrinter(Printer p) {
        jdbi.useHandle(handle -> 
            handle.createUpdate("UPDATE printers SET name=:name, hostname=:hostname, api_key=:apiKey, " +
                                "webcam_url=:webcamUrl, webhook_url=:webhookUrl, webhook_telemetry_enabled=:webhookTelemetryEnabled, enabled=:enabled, " +
                                "threshold_spaghetti=:thresholdSpaghetti, threshold_stringing=:thresholdStringing, threshold_zits=:thresholdZits, " +
                                "mqtt_broker=:mqttBroker, mqtt_topic=:mqttTopic, mqtt_username=:mqttUsername, mqtt_password=:mqttPassword, mqtt_client_id=:mqttClientId, mqtt_telemetry_enabled=:mqttTelemetryEnabled, " +
                                "detect_spaghetti=:detectSpaghetti, detect_stringing=:detectStringing, detect_zits=:detectZits, klipper_screen_telemetry_enabled=:klipperScreenTelemetryEnabled, " +
                                "ai_model=:aiModel " +
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
            h.createUpdate("INSERT INTO ai_alarms (printer_id, filename, trigger_type, confidence, image_data, action) " +
                           "VALUES (:printerId, :filename, :triggerType, :confidence, :imageData, :action)")
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
            h.createUpdate("INSERT INTO telemetry_logs (printer_id, extruder_temp, bed_temp, print_progress, conf_spaghetti, conf_stringing, conf_zits, " +
                           "ref_spaghetti, ref_stringing, ref_zits, suppression, fusion_rules, telemetry_window, anchors_spaghetti, anchors_stringing, anchors_zits) " +
                           "VALUES (:printerId, :extruderTemp, :bedTemp, :printProgress, :confSpaghetti, :confStringing, :confZits, " +
                           ":refSpaghetti, :refStringing, :refZits, :suppression, :fusionRules, :telemetryWindow, :anchorsSpaghetti, :anchorsStringing, :anchorsZits)")
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

    /** What a purge removed, for the log line. */
    public record PurgeResult(int jobs, int telemetry, int alarms, int reviewedKept) {}

    /**
     * Keeps everything belonging to the most recent {@code keepPrints} prints of each printer
     * and drops the rest.
     * <p>
     * The cut is made in time rather than by row counts, anchored on the start of the oldest
     * print being kept: telemetry and alarms carry only a printer id and a timestamp, with no
     * foreign key to {@code print_jobs}. Anchoring on the job start also means telemetry recorded
     * between prints falls on the correct side of the line.
     * <p>
     * Two things deliberately survive the cut: alarms carrying a human verdict, which are
     * training data, and - via {@link #TELEMETRY_SAFETY_CAP} - a bound on printers that have
     * never printed, where there is no job to anchor to and telemetry would otherwise grow
     * without limit.
     */
    public PurgeResult purgeToLastPrints(int keepPrints) {
        int keep = Math.max(1, keepPrints);
        String dataPath = System.getenv().getOrDefault("YOLORAKER_DATA_PATH", "./data");

        return jdbi.inTransaction(h -> {
            List<String> printers = h.createQuery("SELECT id FROM printers").mapTo(String.class).list();
            int jobs = 0, telemetry = 0, alarms = 0;

            for (String pid : printers) {
                // Start of the oldest print we are keeping. Absent when the printer has fewer
                // prints than the limit, in which case nothing is old enough to drop.
                java.sql.Timestamp cutoff = h.createQuery(
                        "SELECT start_time FROM print_jobs WHERE printer_id = :pid AND start_time IS NOT NULL "
                      + "ORDER BY id DESC LIMIT 1 OFFSET :skip")
                        .bind("pid", pid)
                        .bind("skip", keep - 1)
                        .mapTo(java.sql.Timestamp.class)
                        .findFirst()
                        .orElse(null);

                if (cutoff != null) {
                    List<Long> doomed = h.createQuery(
                            "SELECT id FROM print_jobs WHERE printer_id = :pid AND start_time < :cutoff")
                            .bind("pid", pid).bind("cutoff", cutoff).mapTo(Long.class).list();
                    for (Long jobId : doomed) {
                        deleteSnapshotDir(dataPath, pid, jobId);
                    }

                    jobs += h.createUpdate("DELETE FROM print_jobs WHERE printer_id = :pid AND start_time < :cutoff")
                             .bind("pid", pid).bind("cutoff", cutoff).execute();

                    telemetry += h.createUpdate("DELETE FROM telemetry_logs WHERE printer_id = :pid AND timestamp < :cutoff")
                                  .bind("pid", pid).bind("cutoff", cutoff).execute();

                    alarms += h.createUpdate("DELETE FROM ai_alarms WHERE printer_id = :pid AND timestamp < :cutoff "
                                           + "AND ground_truth IS NULL")
                               .bind("pid", pid).bind("cutoff", cutoff).execute();
                }

                // Backstop for a printer that has never printed: no job to anchor to, yet the
                // telemetry poller keeps writing a row every cycle.
                telemetry += h.createUpdate(
                        "DELETE FROM telemetry_logs WHERE printer_id = :pid AND id NOT IN "
                      + "(SELECT id FROM telemetry_logs WHERE printer_id = :pid ORDER BY id DESC LIMIT :cap)")
                        .bind("pid", pid).bind("cap", TELEMETRY_SAFETY_CAP).execute();
            }

            removeOrphanSnapshotDirs(h, dataPath);

            int reviewed = h.createQuery("SELECT count(*) FROM ai_alarms WHERE ground_truth IS NOT NULL")
                            .mapTo(Integer.class).one();
            return new PurgeResult(jobs, telemetry, alarms, reviewed);
        });
    }

    /**
     * Upper bound on telemetry rows per printer, applied regardless of the print limit. At the
     * 10 s logging cadence this is a bit over two days of continuous idling.
     */
    private static final int TELEMETRY_SAFETY_CAP = 20000;

    private static void deleteSnapshotDir(String dataPath, String printerId, Long jobId) {
        java.io.File dir = new java.io.File(dataPath + "/snapshots/" + printerId + "/" + jobId);
        if (dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    f.delete();
                }
            }
            dir.delete();
        }
    }

    /** Snapshot folders whose print job no longer exists - left behind by older versions. */
    private static void removeOrphanSnapshotDirs(org.jdbi.v3.core.Handle h, String dataPath) {
        java.io.File root = new java.io.File(dataPath + "/snapshots");
        java.io.File[] printerDirs = root.listFiles(java.io.File::isDirectory);
        if (printerDirs == null) {
            return;
        }
        for (java.io.File printerDir : printerDirs) {
            java.io.File[] jobDirs = printerDir.listFiles(java.io.File::isDirectory);
            if (jobDirs == null) {
                continue;
            }
            for (java.io.File jobDir : jobDirs) {
                long jobId;
                try {
                    jobId = Long.parseLong(jobDir.getName());
                } catch (NumberFormatException e) {
                    continue; // not one of ours
                }
                boolean exists = h.createQuery("SELECT count(*) FROM print_jobs WHERE id = :id")
                                  .bind("id", jobId).mapTo(Integer.class).one() > 0;
                if (!exists) {
                    deleteSnapshotDir(dataPath, printerDir.getName(), jobId);
                }
            }
        }
    }

    /** How many alarms are being kept purely because they carry a human label. */
    public int countReviewedAlarms() {
        return jdbi.withHandle(h ->
            h.createQuery("SELECT count(*) FROM ai_alarms WHERE ground_truth IS NOT NULL")
             .mapTo(Integer.class).one()
        );
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
            h.createQuery("SELECT id, printer_id, timestamp, filename, trigger_type, confidence, action, "
                        + "ground_truth, reviewed_at FROM ai_alarms WHERE printer_id = :printerId ORDER BY id DESC LIMIT :limit")
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
                 a.setAction(rs.getString("action"));
                 a.setGroundTruth(rs.getString("ground_truth"));
                 a.setReviewedAt(rs.getTimestamp("reviewed_at"));
                 // Do not load BLOB data here to save memory. Fetched separately via getAiAlarmImage.
                 return a;
             })
             .list()
        );
    }

    /**
     * Records the operator's verdict on an incident. These labels are the training data the
     * fusion rules will eventually be fitted to, so they are also what protects a row from
     * {@link #purgeToLastPrints}.
     *
     * @return true if the alarm existed
     */
    public boolean setAlarmReview(long alarmId, String groundTruth) {
        return jdbi.withHandle(h ->
            h.createUpdate("UPDATE ai_alarms SET ground_truth = :gt, reviewed_at = CURRENT_TIMESTAMP WHERE id = :id")
             .bind("gt", groundTruth)
             .bind("id", alarmId)
             .execute() > 0
        );
    }

    /** The printer an alarm belongs to, for event logging. Null if the alarm is gone. */
    public String getAlarmPrinterId(long alarmId) {
        return jdbi.withHandle(h ->
            h.createQuery("SELECT printer_id FROM ai_alarms WHERE id = :id")
             .bind("id", alarmId)
             .mapTo(String.class)
             .findFirst()
             .orElse(null)
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
            // Explicit column list rather than SELECT *: telemetry_window is a CLOB of a few
            // hundred bytes per row and the default limit is 2880 rows. The charts never read it,
            // so shipping it would add megabytes to every history request for nothing.
            h.createQuery("SELECT id, printer_id, timestamp, extruder_temp, bed_temp, print_progress, "
                        + "conf_spaghetti, conf_stringing, conf_zits, ref_spaghetti, ref_stringing, ref_zits, "
                        + "suppression, fusion_rules, anchors_spaghetti, anchors_stringing, anchors_zits "
                        + "FROM telemetry_logs WHERE printer_id = :printerId ORDER BY id DESC LIMIT :limit")
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
                 // Nullable on purpose: null means "no baseline yet", which is not the same as 0.
                 t.setRefSpaghetti(nullableFloat(rs, "ref_spaghetti"));
                 t.setRefStringing(nullableFloat(rs, "ref_stringing"));
                 t.setRefZits(nullableFloat(rs, "ref_zits"));
                 t.setSuppression(nullableFloat(rs, "suppression"));
                 t.setFusionRules(rs.getString("fusion_rules"));
                 t.setAnchorsSpaghetti(nullableInt(rs, "anchors_spaghetti"));
                 t.setAnchorsStringing(nullableInt(rs, "anchors_stringing"));
                 t.setAnchorsZits(nullableInt(rs, "anchors_zits"));
                 return t;
             })
             .list()
        );
    }

    private static Float nullableFloat(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        float v = rs.getFloat(column);
        return rs.wasNull() ? null : v;
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
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
