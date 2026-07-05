package h848.software.yoloraker.model;

public class AdminProfile {
    private String username;
    private String displayName;
    
    // We explicitly do not send the password to the frontend, 
    // but we can use this class to receive the new password from the frontend.
    private String password;
    
    private boolean authDisabled;
    
    private int retentionTelemetryCount = 10000;
    private int retentionAlarmsCount = 500;
    private int retentionJobsCount = 1000;

    public AdminProfile() {
    }

    public AdminProfile(String username, String displayName, boolean authDisabled, int retTelemetry, int retAlarms, int retJobs) {
        this.username = username;
        this.displayName = displayName;
        this.authDisabled = authDisabled;
        this.retentionTelemetryCount = retTelemetry;
        this.retentionAlarmsCount = retAlarms;
        this.retentionJobsCount = retJobs;
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

    public int getRetentionTelemetryCount() {
        return retentionTelemetryCount;
    }

    public void setRetentionTelemetryCount(int retentionTelemetryCount) {
        this.retentionTelemetryCount = retentionTelemetryCount;
    }

    public int getRetentionAlarmsCount() {
        return retentionAlarmsCount;
    }

    public void setRetentionAlarmsCount(int retentionAlarmsCount) {
        this.retentionAlarmsCount = retentionAlarmsCount;
    }

    public int getRetentionJobsCount() {
        return retentionJobsCount;
    }

    public void setRetentionJobsCount(int retentionJobsCount) {
        this.retentionJobsCount = retentionJobsCount;
    }
}
