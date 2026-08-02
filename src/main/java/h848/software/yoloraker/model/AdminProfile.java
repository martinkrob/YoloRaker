package h848.software.yoloraker.model;

public class AdminProfile {
    private String username;
    private String displayName;
    
    // We explicitly do not send the password to the frontend, 
    // but we can use this class to receive the new password from the frontend.
    private String password;
    
    private boolean authDisabled;
    
    /**
     * How many of the most recent prints to keep, per printer. Everything belonging to those
     * prints - telemetry, alarms, snapshots - is kept; everything older is dropped. One number
     * instead of the three separate row caps this replaced, which could disagree with each other
     * and leave a print with history but no telemetry, or the reverse.
     */
    private int retentionPrintCount = 20;

    /** OFF | SHADOW | ACTIVE. Carried on the profile so it is settable through the existing endpoint. */
    private String fusionMode = "SHADOW";

    public AdminProfile() {
    }

    public AdminProfile(String username, String displayName, boolean authDisabled, int retentionPrintCount) {
        this.username = username;
        this.displayName = displayName;
        this.authDisabled = authDisabled;
        this.retentionPrintCount = retentionPrintCount;
    }

    public String getFusionMode() {
        return fusionMode;
    }

    public void setFusionMode(String fusionMode) {
        this.fusionMode = fusionMode;
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

    public int getRetentionPrintCount() {
        return retentionPrintCount;
    }

    public void setRetentionPrintCount(int retentionPrintCount) {
        this.retentionPrintCount = retentionPrintCount;
    }
}
