package h848.software.yoloraker.model;

public class AdminProfile {
    private String username;
    private String displayName;
    
    // We explicitly do not send the password to the frontend, 
    // but we can use this class to receive the new password from the frontend.
    private String password;
    
    private boolean authDisabled;
    
    private int retentionTelemetryDays = 14;
    private int retentionAlarmsDays = 90;
    private int retentionJobsDays = 365;

    public AdminProfile() {
    }

    public AdminProfile(String username, String displayName, boolean authDisabled, int retTelemetry, int retAlarms, int retJobs) {
        this.username = username;
        this.displayName = displayName;
        this.authDisabled = authDisabled;
        this.retentionTelemetryDays = retTelemetry;
        this.retentionAlarmsDays = retAlarms;
        this.retentionJobsDays = retJobs;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAuthDisabled() {
        return authDisabled;
    }

    public void setAuthDisabled(boolean authDisabled) {
        this.authDisabled = authDisabled;
    }

    public int getRetentionTelemetryDays() {
        return retentionTelemetryDays;
    }

    public void setRetentionTelemetryDays(int retentionTelemetryDays) {
        this.retentionTelemetryDays = retentionTelemetryDays;
    }

    public int getRetentionAlarmsDays() {
        return retentionAlarmsDays;
    }

    public void setRetentionAlarmsDays(int retentionAlarmsDays) {
        this.retentionAlarmsDays = retentionAlarmsDays;
    }

    public int getRetentionJobsDays() {
        return retentionJobsDays;
    }

    public void setRetentionJobsDays(int retentionJobsDays) {
        this.retentionJobsDays = retentionJobsDays;
    }
}
