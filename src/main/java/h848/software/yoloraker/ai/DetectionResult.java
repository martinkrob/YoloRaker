package h848.software.yoloraker.ai;

public class DetectionResult {
    private final boolean spaghettiDetected;
    private final float maxConfidence;

    public DetectionResult(boolean spaghettiDetected, float maxConfidence) {
        this.spaghettiDetected = spaghettiDetected;
        this.maxConfidence = maxConfidence;
    }

    public boolean isSpaghettiDetected() {
        return spaghettiDetected;
    }

    public float getMaxConfidence() {
        return maxConfidence;
    }
}
