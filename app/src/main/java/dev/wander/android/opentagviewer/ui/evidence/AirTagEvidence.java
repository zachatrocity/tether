package dev.wander.android.opentagviewer.ui.evidence;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

import dev.wander.android.opentagviewer.ble.NearbyTagSightings;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;

/** Conservative freshness and formatting rules shared by AirTag detail and report history. */
public final class AirTagEvidence {
    /** A network report at or beyond this age is useful history, but not current evidence. */
    public static final long CURRENT_REPORT_FOR_MS = TimeUnit.HOURS.toMillis(1);

    public enum LocalState {
        NEARBY,
        LAST_HEARD,
        UNKNOWN
    }

    public enum NetworkState {
        CURRENT,
        STALE,
        UNKNOWN
    }

    private AirTagEvidence() {}

    public static LocalState local(final LastSightingData sighting, final long nowMs) {
        if (sighting == null || sighting.getHeardAtMs() > nowMs) {
            return LocalState.UNKNOWN;
        }
        return nowMs - sighting.getHeardAtMs() < NearbyTagSightings.FRESH_FOR_MS
                ? LocalState.NEARBY : LocalState.LAST_HEARD;
    }

    public static NetworkState network(
            final BeaconLocationReport report,
            final long nowMs) {
        if (report == null) {
            return NetworkState.UNKNOWN;
        }
        final long ageMs = nowMs - report.getTimestamp();
        return ageMs < 0 || ageMs >= CURRENT_REPORT_FOR_MS
                ? NetworkState.STALE : NetworkState.CURRENT;
    }

    /**
     * Formats horizontal accuracy and nothing else. A non-positive value means the source did not
     * provide one, so the row is omitted instead of being presented as zero metres or distance.
     */
    public static String formatAccuracy(
            final long horizontalAccuracyMeters,
            final LongFunction<String> formatter) {
        if (horizontalAccuracyMeters <= 0) {
            return null;
        }
        return formatter.apply(horizontalAccuracyMeters);
    }

    /** Stable coordinate fallback for a report whose place name cannot be resolved. */
    public static String formatCoordinates(final double latitude, final double longitude) {
        return String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude);
    }
}
