package dev.wander.android.opentagviewer.ui.family;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import dev.wander.android.opentagviewer.ble.NearbyTagSightings;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;

/** The overview must never present old or future evidence as current. */
public class FamilyOverviewEvidenceTest {

    private static final long NOW = 10_000_000L;

    @Test
    public void aFreshLocalSightingTakesPrecedenceOverAReport() {
        assertEquals(FamilyOverviewEvidence.State.NEARBY,
                FamilyOverviewEvidence.classify(
                        reportAt(NOW - FamilyOverviewEvidence.CURRENT_REPORT_FOR_MS * 2),
                        sightingAt(NOW - NearbyTagSightings.FRESH_FOR_MS + 1),
                        NOW));
    }

    @Test
    public void aSightingAtTheExpiryBoundaryNoLongerClaimsNearby() {
        assertEquals(FamilyOverviewEvidence.State.CURRENT_REPORT,
                FamilyOverviewEvidence.classify(
                        reportAt(NOW),
                        sightingAt(NOW - NearbyTagSightings.FRESH_FOR_MS),
                        NOW));
    }

    @Test
    public void aFutureSightingIsIgnored() {
        assertEquals(FamilyOverviewEvidence.State.CURRENT_REPORT,
                FamilyOverviewEvidence.classify(reportAt(NOW), sightingAt(NOW + 1), NOW));
    }

    @Test
    public void aReportAtTheCurrentBoundaryIsStale() {
        assertEquals(FamilyOverviewEvidence.State.STALE_REPORT,
                FamilyOverviewEvidence.classify(
                        reportAt(NOW - FamilyOverviewEvidence.CURRENT_REPORT_FOR_MS), null, NOW));
    }

    @Test
    public void noSavedEvidenceIsUnknown() {
        assertEquals(FamilyOverviewEvidence.State.UNKNOWN,
                FamilyOverviewEvidence.classify(null, null, NOW));
    }

    private static BeaconLocationReport reportAt(final long timestamp) {
        return BeaconLocationReport.builder().timestamp(timestamp).build();
    }

    private static LastSightingData sightingAt(final long timestamp) {
        return new LastSightingData(timestamp, null, 0);
    }
}
