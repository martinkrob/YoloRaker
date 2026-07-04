package h848.software.yoloraker.core;

import h848.software.yoloraker.db.DatabaseManager;
import h848.software.yoloraker.model.AdminProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RetentionService {

    private static final Logger logger = LoggerFactory.getLogger(RetentionService.class);
    
    private final DatabaseManager dbManager;
    private final ScheduledExecutorService scheduler;

    public RetentionService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        // Run immediately upon start, then every 1 hour
        scheduler.scheduleAtFixedRate(this::purgeOldData, 0, 1, TimeUnit.HOURS);
        logger.info("RetentionService started. Purge job scheduled every 1 hour.");
    }

    public void stop() {
        scheduler.shutdown();
        logger.info("RetentionService stopped.");
    }

    private void purgeOldData() {
        try {
            logger.info("Starting data retention purge...");
            AdminProfile profile = dbManager.getAdminProfile();

            int deletedTelemetry = dbManager.purgeOldTelemetry(profile.getRetentionTelemetryDays());
            int deletedAlarms = dbManager.purgeOldAiAlarms(profile.getRetentionAlarmsDays());
            int deletedJobs = dbManager.purgeOldPrintJobs(profile.getRetentionJobsDays());

            logger.info("Data retention purge complete. Deleted {} telemetry logs (older than {} days), {} AI alarms (older than {} days), {} print jobs (older than {} days).",
                    deletedTelemetry, profile.getRetentionTelemetryDays(),
                    deletedAlarms, profile.getRetentionAlarmsDays(),
                    deletedJobs, profile.getRetentionJobsDays());
        } catch (Exception e) {
            logger.error("Error during data retention purge", e);
        }
    }
}
