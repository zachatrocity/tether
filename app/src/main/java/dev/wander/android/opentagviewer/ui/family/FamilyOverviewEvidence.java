package dev.wander.android.opentagviewer.ui.family;

import java.util.concurrent.TimeUnit;

import dev.wander.android.opentagviewer.ble.NearbyTagSightings;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;

/** One shared, deliberately conservative answer to how current an overview row may look. */
public final class FamilyOverviewEvidence {
    /** A network report older than this is still useful, but must say that it is not current. */
    public static final long CURRENT_REPORT_FOR_MS = TimeUnit.HOURS.toMillis(1);

    public enum State {
        NEARBY,
        CURRENT_REPORT,
        STALE_REPORT,
        UNKNOWN
    }

    private FamilyOverviewEvidence() {}

    public static State classify(
            final BeaconLocationReport report,
            final LastSightingData sighting,
            final long nowMs) {
        if (sighting != null
                && sighting.getHeardAtMs() <= nowMs
                && nowMs - sighting.getHeardAtMs() < NearbyTagSightings.FRESH_FOR_MS) {
            return State.NEARBY;
        }
        if (report == null) {
            return State.UNKNOWN;
        }
        return nowMs - report.getTimestamp() >= CURRENT_REPORT_FOR_MS
                ? State.STALE_REPORT : State.CURRENT_REPORT;
    }
}
