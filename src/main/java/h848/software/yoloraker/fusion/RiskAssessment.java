package h848.software.yoloraker.fusion;

import h848.software.yoloraker.ai.DetectionResult.FailureType;
import java.util.Map;
import java.util.Optional;

/**
 * The fusion layer's verdict on one frame, across all classes.
 *
 * @param telemetryReliable whether the telemetry window backing this was complete enough to be
 *                          used; when false no suppressor was applied and the raw model score
 *                          stands on its own
 */
public record RiskAssessment(
        Map<FailureType, ClassAssessment> classes,
        boolean telemetryReliable) {

    public Optional<ClassAssessment> get(FailureType type) {
        return Optional.ofNullable(classes.get(type));
    }

    public double gainFor(FailureType type) {
        ClassAssessment a = classes.get(type);
        return a == null ? -1.0 : a.gain();
    }

    public String explain() {
        return classes.values().stream()
                .map(ClassAssessment::explain)
                .reduce((a, b) -> a + " | " + b)
                .orElse("no classes evaluated");
    }
}
