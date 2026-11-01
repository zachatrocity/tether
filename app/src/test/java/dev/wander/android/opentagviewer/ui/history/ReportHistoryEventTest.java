package dev.wander.android.opentagviewer.ui.history;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;
import dev.wander.android.opentagviewer.db.repo.model.TaggedLocationReport;
import dev.wander.android.opentagviewer.ui.evidence.AirTagEvidence;

/** The global feed is a ledger of source evidence, never a movement or person-state inference. */
public class ReportHistoryEventTest {
    private static final long NOW = 20_000_000L;

    @Test
    public void savedLocalAndAppleEvidenceRemainSeparateAndNewestFirst() {
        final BeaconInformation airTag = BeaconInformation.builder()
                .beaconId("tag-1")
                .originalName("Backpack AirTag")
                .build();
        final BeaconLocationReport network = BeaconLocationReport.builder()
                .timestamp(NOW - 20_000)
                .latitude(48.8)
                .longitude(2.2)
                .horizontalAccuracy(80)
                .build();
        final LastSightingData local = new LastSightingData(NOW - 10_000, null, 0);

        final List<ReportHistoryEvent> events = ReportHistoryEvent.fromSavedData(
                List.of(airTag),
                List.of(tagged("tag-1", network)),
                List.of(),
                Map.of("tag-1", local),
                NOW);

        assertEquals(2, events.size());
        assertEquals(ReportHistoryEvent.Type.LOCAL_SIGHTING, events.get(0).getType());
        assertEquals(ReportHistoryEvent.Type.APPLE_REPORT, events.get(1).getType());
        assertEquals("tag-1", events.get(0).getBeaconId());
        assertEquals("tag-1", events.get(1).getBeaconId());
    }

    @Test
    public void oldAppleEvidenceBecomesAStaleReportEvent() {
        final BeaconInformation airTag = BeaconInformation.builder()
                .beaconId("tag-1")
                .originalName("Satchel AirTag")
                .build();
        final BeaconLocationReport old = BeaconLocationReport.builder()
                .timestamp(NOW - AirTagEvidence.CURRENT_REPORT_FOR_MS)
                .build();

        final List<ReportHistoryEvent> events = ReportHistoryEvent.fromSavedData(
                List.of(airTag), List.of(tagged("tag-1", old)), List.of(), Map.of(), NOW);

        assertEquals(1, events.size());
        assertEquals(ReportHistoryEvent.Type.STALE_REPORT, events.get(0).getType());
    }

    @Test
    public void unknownAirTagsAndFutureLocalSightingsAreNotInventedIntoEvents() {
        final BeaconInformation known = BeaconInformation.builder()
                .beaconId("known")
                .originalName("Known AirTag")
                .build();

        final List<ReportHistoryEvent> events = ReportHistoryEvent.fromSavedData(
                List.of(known),
                List.of(tagged("missing", BeaconLocationReport.builder()
                        .timestamp(NOW)
                        .build())),
                List.of(),
                Map.of("known", new LastSightingData(NOW + 1, null, 0)),
                NOW);

        assertEquals(0, events.size());
    }

    @Test
    public void localPositionIsKeptOnlyWhenItBelongsToThatSighting() {
        final BeaconInformation airTag = BeaconInformation.builder()
                .beaconId("tag-1")
                .originalName("Backpack AirTag")
                .build();
        final long heardAt = NOW - 5_000;
        final BeaconLocationReport localPosition = BeaconLocationReport.builder()
                .timestamp(heardAt)
                .latitude(48.8)
                .longitude(2.2)
                .build();

        final List<ReportHistoryEvent> events = ReportHistoryEvent.fromSavedData(
                List.of(airTag),
                List.of(),
                List.of(tagged("tag-1", localPosition)),
                Map.of("tag-1", new LastSightingData(heardAt, null, 0)),
                NOW);

        assertEquals(1, events.size());
        assertEquals(localPosition, events.get(0).getReport());
    }

    @Test
    public void everySavedAppleReportRemainsInTheChronologicalFeed() {
        final BeaconInformation airTag = BeaconInformation.builder()
                .beaconId("tag-1")
                .originalName("Backpack AirTag")
                .build();
        final BeaconLocationReport newer = BeaconLocationReport.builder()
                .timestamp(NOW - 1_000)
                .build();
        final BeaconLocationReport older = BeaconLocationReport.builder()
                .timestamp(NOW - 2_000)
                .build();

        final List<ReportHistoryEvent> events = ReportHistoryEvent.fromSavedData(
                List.of(airTag),
                List.of(tagged("tag-1", older), tagged("tag-1", newer)),
                List.of(),
                Map.of(),
                NOW);

        assertEquals(2, events.size());
        assertEquals(newer, events.get(0).getReport());
        assertEquals(older, events.get(1).getReport());
    }

    private static TaggedLocationReport tagged(
            final String beaconId,
            final BeaconLocationReport report) {
        return new TaggedLocationReport(beaconId, report);
    }

    @Test
    public void anActualRefreshFailureHasItsOwnFactualType() {
        final ReportHistoryEvent event = ReportHistoryEvent.refreshFailure(
                NOW, "Saved evidence could not be loaded");

        assertEquals(ReportHistoryEvent.Type.REFRESH_FAILURE, event.getType());
        assertEquals("Saved evidence could not be loaded", event.getFailureMessage());
    }
}
