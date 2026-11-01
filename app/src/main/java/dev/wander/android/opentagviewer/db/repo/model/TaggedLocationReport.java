package dev.wander.android.opentagviewer.db.repo.model;

import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;

/** A saved location report together with the AirTag row which owns it. */
public final class TaggedLocationReport {
    private final String beaconId;
    private final BeaconLocationReport report;

    public TaggedLocationReport(final String beaconId, final BeaconLocationReport report) {
        this.beaconId = beaconId;
        this.report = report;
    }

    public String getBeaconId() {
        return this.beaconId;
    }

    public BeaconLocationReport getReport() {
        return this.report;
    }
}
