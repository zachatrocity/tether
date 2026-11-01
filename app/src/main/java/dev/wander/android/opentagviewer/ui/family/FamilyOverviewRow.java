package dev.wander.android.opentagviewer.ui.family;

import androidx.annotation.Nullable;

import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;

/** Immutable source data for one visible Family Overview row, except for optional geocoding. */
public final class FamilyOverviewRow {
    public enum Kind {
        AIR_TAG,
        EMPTY_PROFILE,
        SECTION_HEADER
    }

    private final Kind kind;
    @Nullable private final String profileId;
    private final String personName;
    @Nullable private final BeaconInformation airTag;
    @Nullable private final BeaconLocationReport report;
    @Nullable private final LastSightingData lastSighting;
    @Nullable private String placeName;

    private FamilyOverviewRow(
            final Kind kind,
            @Nullable final String profileId,
            final String personName,
            @Nullable final BeaconInformation airTag,
            @Nullable final BeaconLocationReport report,
            @Nullable final LastSightingData lastSighting) {
        this.kind = kind;
        this.profileId = profileId;
        this.personName = personName;
        this.airTag = airTag;
        this.report = report;
        this.lastSighting = lastSighting;
    }

    public static FamilyOverviewRow airTag(
            @Nullable final String profileId,
            final String personName,
            final BeaconInformation airTag,
            @Nullable final BeaconLocationReport report,
            @Nullable final LastSightingData lastSighting) {
        return new FamilyOverviewRow(
                Kind.AIR_TAG, profileId, personName, airTag, report, lastSighting);
    }

    public static FamilyOverviewRow emptyProfile(final String profileId, final String personName) {
        return new FamilyOverviewRow(
                Kind.EMPTY_PROFILE, profileId, personName, null, null, null);
    }

    public static FamilyOverviewRow sectionHeader(final String title) {
        return new FamilyOverviewRow(
                Kind.SECTION_HEADER, null, title, null, null, null);
    }

    public Kind getKind() {
        return this.kind;
    }

    @Nullable
    public String getProfileId() {
        return this.profileId;
    }

    public String getPersonName() {
        return this.personName;
    }

    @Nullable
    public BeaconInformation getAirTag() {
        return this.airTag;
    }

    @Nullable
    public BeaconLocationReport getReport() {
        return this.report;
    }

    @Nullable
    public LastSightingData getLastSighting() {
        return this.lastSighting;
    }

    @Nullable
    public String getPlaceName() {
        return this.placeName;
    }

    void setPlaceName(@Nullable final String placeName) {
        this.placeName = placeName;
    }
}
