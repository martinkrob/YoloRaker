package h848.software.yoloraker.fusion;

import h848.software.yoloraker.ai.DetectionResult.FailureType;
import java.util.List;
import java.util.Locale;

/**
 * What the fusion layer concluded about one failure class on one frame.
 * <p>
 * Deliberately carries the raw model score untouched alongside everything that was derived from
 * it. Storing a single blended number would make the shadow-mode data useless for working out
 * whether the model or the rules were at fault.
 *
 * @param reference baseline for this print, or NaN when there is not enough history yet
 * @param excursion how far the raw score rose above that baseline
 * @param gated     whether the score cleared both the absolute threshold and the excursion margin
 * @param gain      what this frame adds to (or subtracts from) the confirmation level
 */
public record ClassAssessment(
        FailureType type,
        float raw,
        double reference,
        double excursion,
        double threshold,
        boolean gated,
        double suppression,
        boolean overridden,
        double gain,
        List<RuleHit> rules) {

    public boolean hasReference() {
        return !Double.isNaN(reference);
    }

    /** Compact rule summary for logs and the shadow-mode record, e.g. {@code "EARLY_PRINTx0.50,NO_EXTRUSIONx0.40"}. */
    public String ruleSummary() {
        return rules.stream().map(RuleHit::toString).reduce((a, b) -> a + "," + b).orElse("");
    }

    public String explain() {
        return String.format(Locale.US,
                "%s raw=%.2f ref=%s exc=%.2f thr=%.2f gated=%s supp=%.2f gain=%+.2f [%s]",
                type, raw, hasReference() ? String.format(Locale.US, "%.2f", reference) : "n/a",
                excursion, threshold, gated, suppression, gain, ruleSummary());
    }
}
