package h848.software.yoloraker.fusion;

import h848.software.yoloraker.ai.DetectionResult;
import h848.software.yoloraker.ai.DetectionResult.FailureType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-print score history, used to work out what "normal" looks like for <em>this</em> print.
 * <p>
 * The problem it solves: a purge line sitting in the camera's view, exposed zigzag infill or tree
 * supports all look like a failure to the model, and no amount of printer telemetry can tell them
 * apart from a real one - the printer behaves perfectly in every case. What does tell them apart
 * is time. Scenery is present from the start or grows over hours; a real failure is a sudden
 * departure from whatever this print has looked like so far.
 * <p>
 * The reference is a low percentile over a long window, so it tracks slowly-changing scenery but
 * lags far behind a failure that develops in a minute or two.
 */
public final class DetectionHistory {

    /** Long enough that a failure developing over 1-3 minutes cannot move the reference. */
    private static final long WINDOW_MS = 20 * 60 * 1000;

    /** Below this the reference would be noise. At the 10 s detection cadence this is ~2 minutes. */
    private static final int MIN_SAMPLES = 12;

    /** Low percentile: the reference should describe the quiet baseline, not the peaks. */
    private static final double PERCENTILE = 0.20;

    private final Map<String, Track> tracks = new ConcurrentHashMap<>();

    /** A print restarting always rewinds its duration; allow for jitter in the reading. */
    private static final double DURATION_REWIND_TOLERANCE_SEC = 5.0;

    /**
     * Records a detection, resetting the baseline when a genuinely new print begins - a new scene
     * must not inherit the previous one's baseline.
     * <p>
     * Identity is deliberately taken from the print itself (filename, and the duration counter
     * rewinding) rather than from the {@code print_jobs} row id. An AI pause closes its job row
     * and the next cycle opens a fresh one, so keying on the row would wipe the baseline on every
     * pause - exactly when the scene is least likely to have changed and most likely to contain
     * leftover mess the operator has yet to clear.
     */
    public void record(String printerId, String filename, double printDurationSec,
                       long ts, DetectionResult result) {
        Track track = tracks.computeIfAbsent(printerId, id -> new Track());
        synchronized (track) {
            boolean newPrint = !Objects.equals(track.filename, filename)
                    || printDurationSec < track.lastDurationSec - DURATION_REWIND_TOLERANCE_SEC;
            if (newPrint) {
                track.points.clear();
            }
            track.filename = filename;
            track.lastDurationSec = printDurationSec;
            track.points.addLast(new Point(ts,
                    result.getConfSpaghetti(), result.getConfStringing(), result.getConfZits()));
            long cutoff = ts - WINDOW_MS;
            while (!track.points.isEmpty() && track.points.peekFirst().ts < cutoff) {
                track.points.removeFirst();
            }
        }
    }

    /**
     * The baseline score for a class on the current print.
     *
     * @return {@link Double#NaN} when there is not yet enough history to say
     */
    public double reference(String printerId, FailureType type) {
        Track track = tracks.get(printerId);
        if (track == null) {
            return Double.NaN;
        }

        List<Float> values;
        synchronized (track) {
            if (track.points.size() < MIN_SAMPLES) {
                return Double.NaN;
            }
            values = new ArrayList<>(track.points.size());
            for (Point p : track.points) {
                values.add(p.of(type));
            }
        }

        values.sort(null);
        int index = (int) (PERCENTILE * values.size());
        return values.get(Math.min(index, values.size() - 1));
    }

    public int sampleCount(String printerId) {
        Track track = tracks.get(printerId);
        if (track == null) {
            return 0;
        }
        synchronized (track) {
            return track.points.size();
        }
    }

    public void forget(String printerId) {
        tracks.remove(printerId);
    }

    private static final class Track {
        String filename;
        double lastDurationSec;
        final Deque<Point> points = new ArrayDeque<>();
    }

    private record Point(long ts, float spaghetti, float stringing, float zits) {
        float of(FailureType type) {
            return switch (type) {
                case SPAGHETTI -> spaghetti;
                case STRINGING -> stringing;
                case ZITS -> zits;
                case NONE -> 0f;
            };
        }
    }
}
