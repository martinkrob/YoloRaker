package h848.software.yoloraker.ai;

public class DetectionResult {
    
    public enum FailureType {
        NONE, SPAGHETTI, STRINGING, ZITS
    }

    private final float confSpaghetti;
    private final float confStringing;
    private final float confZits;
    
    private final FailureType highestFailureType;
    private final float highestConfidence;

    public DetectionResult(float confSpaghetti, float confStringing, float confZits, 
                           FailureType highestFailureType, float highestConfidence) {
        this.confSpaghetti = confSpaghetti;
        this.confStringing = confStringing;
        this.confZits = confZits;
        this.highestFailureType = highestFailureType;
        this.highestConfidence = highestConfidence;
    }

    public float getConfSpaghetti() {
        return confSpaghetti;
    }

    public float getConfStringing() {
        return confStringing;
    }

    public float getConfZits() {
        return confZits;
    }

    public FailureType getHighestFailureType() {
        return highestFailureType;
    }

    public float getHighestConfidence() {
        return highestConfidence;
    }
}
