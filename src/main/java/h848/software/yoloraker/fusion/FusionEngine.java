package h848.software.yoloraker.fusion;

import h848.software.yoloraker.ai.DetectionResult;
import h848.software.yoloraker.ai.DetectionResult.FailureType;
import h848.software.yoloraker.model.Printer;
import h848.software.yoloraker.telemetry.TelemetryWindow;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Decides how fast a detection should be allowed to accumulate towards an alarm.
 * <p>
 * Two design choices drive everything here.
 * <p>
 * <b>Rules slow the rate, they do not edit the score.</b> Multiplying the confidence down risks
 * pushing a genuine failure permanently under the threshold, which turns a false-positive fix
 * into a missed-failure bug. Scaling the confirmation rate instead means a real failure - which
 * persists frame after frame - still accumulates and still fires, only later. Suppression delays;
 * it can never mask.
 * <p>
 * <b>Measurement beats inference.</b> The reference baseline from {@link DetectionHistory} is a
 * measurement of what this print actually looks like, so it gates every decision including the
 * high-confidence override. The telemetry suppressors are inferences about context and can be
 * wrong, so the override discards them.
 */
public final class FusionEngine {

    /** Confirmation level at which an alarm fires. At the 10 s cadence, ~50 s of marginal detections. */
    public static final double ALARM_AT = 5.0;

    /** A detection must clear the printer's threshold <em>and</em> rise this far above the baseline. */
    private static final double EXCURSION_MARGIN = 0.15;

    /**
     * The baseline is never allowed to eat more headroom than this. Beyond it the max-over-anchors
     * score is effectively saturated by scenery and no amount of arithmetic recovers a usable
     * signal - see {@link #referenceIsSaturating}.
     */
    private static final double REFERENCE_CAP = 0.75;

    /** Above this the model is sure enough that context inferences are not allowed to slow it down. */
    private static final double HIGH_CONFIDENCE_OVERRIDE = 0.90;

    /** Filament over a 10 s window below which nothing is meaningfully being extruded. */
    private static final double NO_EXTRUSION_MM = 1.0;

    private static final double EARLY_PRINT_SEC = 90;
    private static final double RESUME_GRACE_SEC = 30;
    private static final double COLD_NOZZLE_ERROR = -15.0;

    /**
     * Evaluates one frame.
     *
     * @param window            aggregated telemetry ending at the frame, may be null
     * @param secondsSinceResume seconds since the print was last resumed, or
     *                           {@link Long#MAX_VALUE} if it has not been paused on this job
     */
    public RiskAssessment assess(Printer printer,
                                 DetectionResult detection,
                                 TelemetryWindow window,
                                 DetectionHistory history,
                                 long secondsSinceResume) {

        boolean telemetryReliable = window != null && window.isReliable();
        Map<FailureType, ClassAssessment> results = new EnumMap<>(FailureType.class);

        if (printer.isDetectSpaghetti()) {
            results.put(FailureType.SPAGHETTI, assessFailureClass(
                    printer, detection.getConfSpaghetti(), printer.getThresholdSpaghetti(),
                    history.reference(printer.getId(), FailureType.SPAGHETTI),
                    window, telemetryReliable, secondsSinceResume));
        }
        if (printer.isDetectStringing()) {
            results.put(FailureType.STRINGING, assessQualityClass(
                    printer, FailureType.STRINGING,
                    detection.getConfStringing(), printer.getThresholdStringing(),
                    history.reference(printer.getId(), FailureType.STRINGING),
                    window, telemetryReliable));
        }
        if (printer.isDetectZits()) {
            results.put(FailureType.ZITS, assessQualityClass(
                    printer, FailureType.ZITS,
                    detection.getConfZits(), printer.getThresholdZits(),
                    history.reference(printer.getId(), FailureType.ZITS),
                    window, telemetryReliable));
        }

        return new RiskAssessment(results, telemetryReliable);
    }

    /**
     * Spaghetti: the only class allowed to stop a print, so it gets the full treatment. A miss
     * here costs a ruined print and possibly a clogged hotend, which is why every suppressor is
     * capped in effect and none of them can veto outright.
     */
    private ClassAssessment assessFailureClass(Printer printer, float raw, double threshold,
                                               double reference, TelemetryWindow window,
                                               boolean telemetryReliable, long secondsSinceResume) {
        List<RuleHit> hits = new ArrayList<>();
        double excursion = excursion(raw, reference, hits);
        boolean gated = isGated(raw, threshold, excursion);
        boolean overridden = gated && raw >= HIGH_CONFIDENCE_OVERRIDE;

        double suppression = 1.0;
        if (overridden) {
            hits.add(RuleHit.note("HIGH_CONF_OVERRIDE",
                    "raw >= " + HIGH_CONFIDENCE_OVERRIDE + ", context suppressors ignored"));
        } else if (!telemetryReliable) {
            hits.add(RuleHit.note("NO_TELEMETRY",
                    "telemetry window missing or incomplete, model score stands alone"));
        } else {
            suppression = spaghettiSuppressors(window, secondsSinceResume, hits);
        }

        return build(FailureType.SPAGHETTI, raw, reference, excursion, threshold,
                gated, suppression, overridden, hits);
    }

    private double spaghettiSuppressors(TelemetryWindow w, long secondsSinceResume, List<RuleHit> hits) {
        double factor = 1.0;

        // Spaghetti is extruded filament that missed the part. Without extrusion it cannot grow,
        // so whatever the camera is looking at was already there.
        if (w.extrudedFilamentMm() < NO_EXTRUSION_MM) {
            factor *= applyRule(hits, "NO_EXTRUSION", 0.4, String.format(Locale.US,
                    "only %.2f mm extruded in the window", w.extrudedFilamentMm()));
        }

        // Still heating: the print has technically started but nothing is being laid down yet.
        if (w.extruderTempError() < COLD_NOZZLE_ERROR) {
            factor *= applyRule(hits, "NOT_AT_TEMP", 0.3, String.format(Locale.US,
                    "nozzle %.1f C below target", -w.extruderTempError()));
        }

        // Purge lines and prime blobs happen here and look exactly like a small tangle. Only a
        // partial suppression though: first-layer adhesion failure is a real and common way for a
        // print to die in its first minute, and it must not be silenced.
        if (w.printDurationSec() < EARLY_PRINT_SEC) {
            factor *= applyRule(hits, "EARLY_PRINT", 0.5, String.format(Locale.US,
                    "%.0f s into the print", w.printDurationSec()));
        }

        // A resume is followed by ooze and a purge, and often by leftover mess from whatever the
        // operator just cleared.
        if (secondsSinceResume < RESUME_GRACE_SEC) {
            factor *= applyRule(hits, "JUST_RESUMED", 0.4,
                    secondsSinceResume + " s since resume");
        }

        return factor;
    }

    /**
     * Stringing and zits are surface-quality defects, not failures. They never pause a print, so
     * the cost of suppressing one is that the operator is not told about a cosmetic issue - which
     * is close to nothing. That asymmetry means the baseline gate does nearly all the work here,
     * and no telemetry suppressor is needed.
     */
    private ClassAssessment assessQualityClass(Printer printer, FailureType type,
                                               float raw, double threshold, double reference,
                                               TelemetryWindow window, boolean telemetryReliable) {
        List<RuleHit> hits = new ArrayList<>();
        double excursion = excursion(raw, reference, hits);
        boolean gated = isGated(raw, threshold, excursion);

        double suppression = 1.0;
        // Strings are drawn during travel moves, so a window that is all travel and no extrusion
        // makes stringing more plausible, not less - the opposite of what it means for spaghetti.
        if (telemetryReliable && type == FailureType.STRINGING
                && window.extrudedFilamentMm() < NO_EXTRUSION_MM
                && window.distanceTravelledMm() > 20.0) {
            suppression *= applyRule(hits, "TRAVEL_HEAVY", 1.2, String.format(Locale.US,
                    "%.0f mm travelled with no extrusion", window.distanceTravelledMm()));
        }

        return build(type, raw, reference, excursion, threshold, gated, suppression, false, hits);
    }

    /**
     * How far the score rose above this print's own baseline. Falls back to the raw score when
     * there is no baseline yet, which is the correct conservative choice: an unknown scene is
     * treated as a clean one.
     */
    private double excursion(float raw, double reference, List<RuleHit> hits) {
        if (Double.isNaN(reference)) {
            hits.add(RuleHit.note("NO_BASELINE", "not enough history yet, judging on raw score"));
            return raw;
        }
        double capped = Math.min(reference, REFERENCE_CAP);
        if (referenceIsSaturating(reference)) {
            hits.add(RuleHit.note("BASELINE_SATURATED", String.format(Locale.US,
                    "baseline %.2f is very high - the camera view likely contains permanent "
                    + "scenery the model reads as a defect", reference)));
        }
        return raw - capped;
    }

    private boolean isGated(float raw, double threshold, double excursion) {
        return raw >= threshold && excursion >= EXCURSION_MARGIN;
    }

    /**
     * A baseline this high means the model already scores the clean print near its own threshold,
     * leaving no headroom for a real event to stand out. Worth surfacing: the fix is a better
     * camera angle or a crop, not a rule.
     */
    public static boolean referenceIsSaturating(double reference) {
        return !Double.isNaN(reference) && reference >= REFERENCE_CAP;
    }

    private ClassAssessment build(FailureType type, float raw, double reference, double excursion,
                                  double threshold, boolean gated, double suppression,
                                  boolean overridden, List<RuleHit> hits) {
        double gain;
        if (gated) {
            // Steeper for confident detections: anything from ~0.80 up confirms in about 30 s,
            // while a marginal 0.61 still takes the full ~50 s.
            gain = (1.0 + 4.0 * (raw - threshold)) * suppression;
        } else {
            gain = -1.0;
        }
        return new ClassAssessment(type, raw, reference, excursion, threshold,
                gated, suppression, overridden, gain, List.copyOf(hits));
    }

    private double applyRule(List<RuleHit> hits, String name, double factor, String reason) {
        hits.add(new RuleHit(name, factor, reason));
        return factor;
    }
}
