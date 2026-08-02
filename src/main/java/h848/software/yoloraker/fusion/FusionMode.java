package h848.software.yoloraker.fusion;

/**
 * How much authority the fusion layer has over the alarm decision.
 * <p>
 * Note this controls scoring only. Whether a class pauses the print or merely notifies is a
 * property of the class itself and is not affected by the mode.
 */
public enum FusionMode {

    /** Fusion is not evaluated at all. Raw model scores drive the old counter. */
    OFF,

    /**
     * Fusion is evaluated and recorded, but the old raw-score logic still decides. The safe way
     * to gather a few days of real data and see what fusion <em>would</em> have done.
     */
    SHADOW,

    /** Fusion drives the decision. */
    ACTIVE;

    public static FusionMode parse(String raw) {
        if (raw == null) {
            return SHADOW;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SHADOW;
        }
    }
}
