package h848.software.yoloraker.model;

/**
 * Live per-class detection state for the UI.
 * <p>
 * The instantaneous confidence is not what decides anything - the accumulating confirmation
 * {@link #level} is. This carries both, plus the reason the level is doing what it is doing, so
 * the dashboard can answer "why is nothing happening at 68%?" without the operator guessing.
 */
public class AiClassStatus {

    /** Nothing is accumulating. */
    public static final String STATE_IDLE = "IDLE";
    /** Over the threshold, but no higher than this print's own baseline - almost certainly scenery. */
    public static final String STATE_SCENERY = "SCENERY";
    /** Accumulating, but slowed down by telemetry context. */
    public static final String STATE_SUPPRESSED = "SUPPRESSED";
    /** Accumulating normally. */
    public static final String STATE_BUILDING = "BUILDING";
    /** Close enough to the alarm that the operator should look now. */
    public static final String STATE_IMMINENT = "IMMINENT";

    private String type;
    private float confidence;
    /** This print's learned baseline, or null while there is not enough history. */
    private Float reference;
    private double threshold;
    private double level;
    private double alarmAt;
    private double suppression = 1.0;
    /** Estimated seconds until the alarm fires, or -1 when the level is not rising. */
    private int secondsToAlarm = -1;
    private String state = STATE_IDLE;
    /** Baseline so high the model has no headroom left - the camera view needs attention. */
    private boolean saturated;
    private String rules;

    public AiClassStatus() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public Float getReference() {
        return reference;
    }

    public void setReference(Float reference) {
        this.reference = reference;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public double getLevel() {
        return level;
    }

    public void setLevel(double level) {
        this.level = level;
    }

    public double getAlarmAt() {
        return alarmAt;
    }

    public void setAlarmAt(double alarmAt) {
        this.alarmAt = alarmAt;
    }

    public double getSuppression() {
        return suppression;
    }

    public void setSuppression(double suppression) {
        this.suppression = suppression;
    }

    public int getSecondsToAlarm() {
        return secondsToAlarm;
    }

    public void setSecondsToAlarm(int secondsToAlarm) {
        this.secondsToAlarm = secondsToAlarm;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isSaturated() {
        return saturated;
    }

    public void setSaturated(boolean saturated) {
        this.saturated = saturated;
    }

    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }
}
