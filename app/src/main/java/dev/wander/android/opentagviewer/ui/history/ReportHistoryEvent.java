package dev.wander.android.opentagviewer.ui.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;
import dev.wander.android.opentagviewer.db.repo.model.TaggedLocationReport;
import dev.wander.android.opentagviewer.ui.evidence.AirTagEvidence;

/** One factual feed entry. No type represents an inferred movement or person state. */
public final class ReportHistoryEvent {
    public enum Type {
        LOCAL_SIGHTING,
        APPLE_REPORT,
        STALE_REPORT,
        REFRESH_FAILURE
    }

    private final Type type;
    private final String beaconId;
    private final String airTagName;
    private final long timestampMs;
    private final BeaconLocationReport report;
    private final String failureMessage;

    private ReportHistoryEvent(
            final Type type,
            final String beaconId,
            final String airTagName,
            final long timestampMs,
            final BeaconLocationReport report,
            final String failureMessage) {
        this.type = type;
        this.beaconId = beaconId;
        this.airTagName = airTagName;
        this.timestampMs = timestampMs;
        this.report = report;
        this.failureMessage = failureMessage;
    }

    /** Saved local and Apple evidence for known AirTags, newest factual event first. */
    public static List<ReportHistoryEvent> fromSavedData(
            final List<BeaconInformation> airTags,
            final List<TaggedLocationReport> appleReports,
            final List<TaggedLocationReport> localReports,
            final Map<String, LastSightingData> localSightings,
            final long nowMs) {
        final Map<String, String> names = new HashMap<>();
        for (final BeaconInformation airTag : airTags) {
            names.put(airTag.getBeaconId(), airTag.getName());
        }

        final List<ReportHistoryEvent> result = new ArrayList<>();
        final Set<String> positionedLocalSightings = new HashSet<>();
        for (final TaggedLocationReport tagged : localReports) {
            final String beaconId = tagged.getBeaconId();
            final BeaconLocationReport report = tagged.getReport();
            final String name = names.get(beaconId);
            if (name == null || report.getTimestamp() > nowMs) {
                continue;
            }
            positionedLocalSightings.add(localKey(beaconId, report.getTimestamp()));
            result.add(new ReportHistoryEvent(
                    Type.LOCAL_SIGHTING,
                    beaconId,
                    name,
                    report.getTimestamp(),
                    report,
                    null));
        }
        for (final Map.Entry<String, LastSightingData> entry : localSightings.entrySet()) {
            final String name = names.get(entry.getKey());
            final long timestamp = entry.getValue().getHeardAtMs();
            if (name == null
                    || timestamp > nowMs
                    || positionedLocalSightings.contains(localKey(entry.getKey(), timestamp))) {
                continue;
            }
            result.add(new ReportHistoryEvent(
                    Type.LOCAL_SIGHTING,
                    entry.getKey(),
                    name,
                    timestamp,
                    null,
                    null));
        }
        for (final TaggedLocationReport tagged : appleReports) {
            final String beaconId = tagged.getBeaconId();
            final BeaconLocationReport report = tagged.getReport();
            final String name = names.get(beaconId);
            if (name == null) {
                continue;
            }
            final Type type = AirTagEvidence.network(report, nowMs)
                    == AirTagEvidence.NetworkState.STALE
                    ? Type.STALE_REPORT : Type.APPLE_REPORT;
            result.add(new ReportHistoryEvent(
                    type,
                    beaconId,
                    name,
                    report.getTimestamp(),
                    report,
                    null));
        }
        result.sort(Comparator.comparingLong(ReportHistoryEvent::getTimestampMs).reversed());
        return result;
    }

    private static String localKey(final String beaconId, final long timestampMs) {
        return beaconId + ':' + timestampMs;
    }

    public static ReportHistoryEvent refreshFailure(
            final long timestampMs,
            final String message) {
        return new ReportHistoryEvent(
                Type.REFRESH_FAILURE, null, null, timestampMs, null, message);
    }

    public Type getType() {
        return this.type;
    }

    public String getBeaconId() {
        return this.beaconId;
    }

    public String getAirTagName() {
        return this.airTagName;
    }

    public long getTimestampMs() {
        return this.timestampMs;
    }

    public BeaconLocationReport getReport() {
        return this.report;
    }

    public String getFailureMessage() {
        return this.failureMessage;
    }
}
