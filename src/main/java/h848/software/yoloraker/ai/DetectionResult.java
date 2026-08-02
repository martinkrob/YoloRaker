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

    // How many anchors scored above ANCHOR_COUNT_THRESHOLD for each class. The confidences above
    // are a max over every anchor, which fires on a single noisy cell anywhere in the frame; a
    // count says whether the model saw one large object or a scattering of weak hits. Recorded
    // only - nothing decides on it yet - so the two can be compared on real data.
    private final int anchorsSpaghetti;
    private final int anchorsStringing;
    private final int anchorsZits;

    public DetectionResult(float confSpaghetti, float confStringing, float confZits,
                           FailureType highestFailureType, float highestConfidence) {
        this(confSpaghetti, confStringing, confZits, highestFailureType, highestConfidence, 0, 0, 0);
    }

    public DetectionResult(float confSpaghetti, float confStringing, float confZits,
                           FailureType highestFailureType, float highestConfidence,
                           int anchorsSpaghetti, int anchorsStringing, int anchorsZits) {
        this.confSpaghetti = confSpaghetti;
        this.confStringing = confStringing;
        this.confZits = confZits;
        this.highestFailureType = highestFailureType;
        this.highestConfidence = highestConfidence;
        this.anchorsSpaghetti = anchorsSpaghetti;
        this.anchorsStringing = anchorsStringing;
        this.anchorsZits = anchorsZits;
    }

    public int getAnchorsSpaghetti() {
        return anchorsSpaghetti;
    }

    public int getAnchorsStringing() {
        return anchorsStringing;
    }

    public int getAnchorsZits() {
        return anchorsZits;
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
