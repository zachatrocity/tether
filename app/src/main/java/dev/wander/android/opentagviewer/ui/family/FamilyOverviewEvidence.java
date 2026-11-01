package dev.wander.android.opentagviewer.ui.family;

import dev.wander.android.opentagviewer.ble.NearbyTagSightings;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;
import dev.wander.android.opentagviewer.ui.evidence.AirTagEvidence;

/** One shared, deliberately conservative answer to how current an overview row may look. */
public final class FamilyOverviewEvidence {
    /** A network report older than this is still useful, but must say that it is not current. */
    public static final long CURRENT_REPORT_FOR_MS = AirTagEvidence.CURRENT_REPORT_FOR_MS;

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
        final AirTagEvidence.NetworkState network = AirTagEvidence.network(report, nowMs);
        if (network == AirTagEvidence.NetworkState.UNKNOWN) {
            return State.UNKNOWN;
        }
        return network == AirTagEvidence.NetworkState.STALE
                ? State.STALE_REPORT : State.CURRENT_REPORT;
    }
}
