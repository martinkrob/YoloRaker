package h848.software.yoloraker.fusion;

/**
 * A single rule that fired, and what it did.
 * <p>
 * These are kept so every decision can be explained after the fact. When someone asks "why did
 * it not pause?", the answer has to be a list of named reasons, not an opaque number.
 *
 * @param factor multiplier applied to the confirmation rate; 1.0 for rules that only annotate
 */
public record RuleHit(String name, double factor, String reason) {

    public static RuleHit note(String name, String reason) {
        return new RuleHit(name, 1.0, reason);
    }

    @Override
    public String toString() {
        return factor == 1.0 ? name : name + "x" + String.format(java.util.Locale.US, "%.2f", factor);
    }
}
