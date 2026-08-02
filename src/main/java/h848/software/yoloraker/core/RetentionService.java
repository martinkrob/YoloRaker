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
            AdminProfile profile = dbManager.getAdminProfile();
            int keep = profile.getRetentionPrintCount();

            DatabaseManager.PurgeResult r = dbManager.purgeToLastPrints(keep);

            if (r.jobs() == 0 && r.telemetry() == 0 && r.alarms() == 0) {
                logger.info("Retention: nothing to purge, keeping the last {} prints per printer "
                        + "({} reviewed alarms held as training data).", keep, r.reviewedKept());
            } else {
                logger.info("Retention: keeping the last {} prints per printer. Dropped {} prints "
                        + "with {} telemetry rows and {} alarms. {} reviewed alarms held as training data.",
                        keep, r.jobs(), r.telemetry(), r.alarms(), r.reviewedKept());
            }
        } catch (Exception e) {
            logger.error("Error during data retention purge", e);
        }
    }
}
