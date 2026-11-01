package dev.wander.android.opentagviewer.ui.evidence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import dev.wander.android.opentagviewer.ble.NearbyTagSightings;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;

/** Boundaries which decide whether Tether may call evidence current and how accuracy is named. */
public class AirTagEvidenceTest {
    private static final long NOW = 10_000_000L;

    @Test
    public void nearbyExpiresAtTheBluetoothFreshnessBoundary() {
        assertEquals(AirTagEvidence.LocalState.NEARBY,
                AirTagEvidence.local(
                        sightingAt(NOW - NearbyTagSightings.FRESH_FOR_MS + 1), NOW));
        assertEquals(AirTagEvidence.LocalState.LAST_HEARD,
                AirTagEvidence.local(
                        sightingAt(NOW - NearbyTagSightings.FRESH_FOR_MS), NOW));
        assertEquals(AirTagEvidence.LocalState.UNKNOWN,
                AirTagEvidence.local(sightingAt(NOW + 1), NOW));
    }

    @Test
    public void networkReportIsNotCurrentAtTheHourBoundaryOrInTheFuture() {
        assertEquals(AirTagEvidence.NetworkState.CURRENT,
                AirTagEvidence.network(
                        reportAt(NOW - AirTagEvidence.CURRENT_REPORT_FOR_MS + 1), NOW));
        assertEquals(AirTagEvidence.NetworkState.STALE,
                AirTagEvidence.network(
                        reportAt(NOW - AirTagEvidence.CURRENT_REPORT_FOR_MS), NOW));
        assertEquals(AirTagEvidence.NetworkState.STALE,
                AirTagEvidence.network(reportAt(NOW + 1), NOW));
        assertEquals(AirTagEvidence.NetworkState.UNKNOWN,
                AirTagEvidence.network(null, NOW));
    }

    @Test
    public void horizontalAccuracyIsOmittedWhenUnknownAndNeverNamedDistance() {
        assertNull(AirTagEvidence.formatAccuracy(0, meters -> "unused"));
        assertNull(AirTagEvidence.formatAccuracy(-1, meters -> "unused"));

        final String rendered = AirTagEvidence.formatAccuracy(
                80, meters -> "accuracy ~" + meters + " m");
        assertEquals("accuracy ~80 m", rendered);
        assertFalse(rendered.toLowerCase().contains("distance"));
    }

    @Test
    public void coordinateFallbackHasStablePrecision() {
        assertEquals("48.85837, 2.29448",
                AirTagEvidence.formatCoordinates(48.8583701, 2.2944812));
    }

    private static BeaconLocationReport reportAt(final long timestamp) {
        return BeaconLocationReport.builder().timestamp(timestamp).build();
    }

    private static LastSightingData sightingAt(final long timestamp) {
        return new LastSightingData(timestamp, null, 0);
    }
}
