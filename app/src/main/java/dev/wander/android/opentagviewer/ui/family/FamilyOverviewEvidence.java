package dev.wander.android.opentagviewer.ui.family;

import java.util.concurrent.TimeUnit;

import dev.wander.android.opentagviewer.ble.NearbyTagSightings;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;
import dev.wander.android.opentagviewer.ui.evidence.AirTagEvidence;

/** One shared, deliberately conservative answer to how current an overview row may look. */
public final class FamilyOverviewEvidence {
    /** A network report older than this is still useful, but must say that it is not current. */
    public static final long CURRENT_REPORT_FOR_MS = AirTagEvidence.CURRENT_REPORT_FOR_MS;
    public static final long WARNING_AFTER_MS = TimeUnit.MINUTES.toMillis(30);
    public static final long OLD_AFTER_MS = TimeUnit.HOURS.toMillis(2);

    public enum State {
        NEARBY,
        CURRENT_REPORT,
        STALE_REPORT,
        UNKNOWN
    }

    public enum AgeTone {
        FRESH,
        WARNING,
        OLD,
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

    /**
     * Classify the age of the timestamp the row actually names. A nearby row names the BLE
     * sighting; every other timed row names its saved report.
     */
    public static AgeTone ageTone(
            final State state,
            final BeaconLocationReport report,
            final LastSightingData sighting,
            final long nowMs) {
        final long timestamp;
        if (state == State.NEARBY && sighting != null) {
            timestamp = sighting.getHeardAtMs();
        } else if (report != null) {
            timestamp = report.getTimestamp();
        } else {
            return AgeTone.UNKNOWN;
        }
        if (timestamp > nowMs) {
            return AgeTone.UNKNOWN;
        }

        final long ageMs = nowMs - timestamp;
        if (ageMs < WARNING_AFTER_MS) {
            return AgeTone.FRESH;
        }
        if (ageMs < OLD_AFTER_MS) {
            return AgeTone.WARNING;
        }
        return AgeTone.OLD;
    }

}
