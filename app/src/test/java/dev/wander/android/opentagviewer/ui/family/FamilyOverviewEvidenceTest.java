package dev.wander.android.opentagviewer.ui.family;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

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
    public void ageToneChangesAtThirtyMinutesAndTwoHours() {
        assertEquals(FamilyOverviewEvidence.AgeTone.FRESH,
                toneForReport(TimeUnit.MINUTES.toMillis(30) - 1));
        assertEquals(FamilyOverviewEvidence.AgeTone.WARNING,
                toneForReport(TimeUnit.MINUTES.toMillis(30)));
        assertEquals(FamilyOverviewEvidence.AgeTone.WARNING,
                toneForReport(TimeUnit.HOURS.toMillis(2) - 1));
        assertEquals(FamilyOverviewEvidence.AgeTone.OLD,
                toneForReport(TimeUnit.HOURS.toMillis(2)));
    }

    @Test
    public void nearbySightingAgeWinsOverAnOldReport() {
        final BeaconLocationReport oldReport = reportAt(
                NOW - TimeUnit.DAYS.toMillis(2));
        final LastSightingData recentSighting = sightingAt(
                NOW - TimeUnit.SECONDS.toMillis(1));
        final FamilyOverviewEvidence.State state = FamilyOverviewEvidence.classify(
                oldReport, recentSighting, NOW);

        assertEquals(FamilyOverviewEvidence.AgeTone.FRESH,
                FamilyOverviewEvidence.ageTone(
                        state, oldReport, recentSighting, NOW));
    }

    @Test
    public void futureAndMissingEvidenceStayNeutral() {
        assertEquals(FamilyOverviewEvidence.AgeTone.UNKNOWN,
                FamilyOverviewEvidence.ageTone(
                        FamilyOverviewEvidence.State.UNKNOWN,
                        reportAt(NOW + 1),
                        null,
                        NOW));
        assertEquals(FamilyOverviewEvidence.AgeTone.UNKNOWN,
                FamilyOverviewEvidence.ageTone(
                        FamilyOverviewEvidence.State.UNKNOWN,
                        null,
                        null,
                        NOW));
    }

    @Test
    public void noSavedEvidenceIsUnknown() {
        assertEquals(FamilyOverviewEvidence.State.UNKNOWN,
                FamilyOverviewEvidence.classify(null, null, NOW));
    }

    private static FamilyOverviewEvidence.AgeTone toneForReport(final long ageMs) {
        final BeaconLocationReport report = reportAt(NOW - ageMs);
        final FamilyOverviewEvidence.State state =
                FamilyOverviewEvidence.classify(report, null, NOW);
        return FamilyOverviewEvidence.ageTone(state, report, null, NOW);
    }

    private static BeaconLocationReport reportAt(final long timestamp) {
        return BeaconLocationReport.builder().timestamp(timestamp).build();
    }

    private static LastSightingData sightingAt(final long timestamp) {
        return new LastSightingData(timestamp, null, 0);
    }
}
