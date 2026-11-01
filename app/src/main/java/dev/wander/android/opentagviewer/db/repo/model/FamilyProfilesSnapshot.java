package dev.wander.android.opentagviewer.db.repo.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every real profile plus the explicit set of AirTags which currently have no valid profile. */
public final class FamilyProfilesSnapshot {
    private final List<FamilyProfileWithAirTags> profiles;
    private final List<String> unassignedAirTagIds;

    public FamilyProfilesSnapshot(
            final List<FamilyProfileWithAirTags> profiles,
            final List<String> unassignedAirTagIds) {
        this.profiles = Collections.unmodifiableList(new ArrayList<>(profiles));
        this.unassignedAirTagIds = Collections.unmodifiableList(
                new ArrayList<>(unassignedAirTagIds));
    }

    public List<FamilyProfileWithAirTags> getProfiles() {
        return this.profiles;
    }

    public List<String> getUnassignedAirTagIds() {
        return this.unassignedAirTagIds;
    }
}
