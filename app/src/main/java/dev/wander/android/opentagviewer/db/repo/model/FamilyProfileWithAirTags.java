package dev.wander.android.opentagviewer.db.repo.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.wander.android.opentagviewer.db.room.entity.FamilyProfile;

/** One local family profile and the stable ids of its currently associated AirTags. */
public final class FamilyProfileWithAirTags {
    private final FamilyProfile profile;
    private final List<String> airTagIds;

    public FamilyProfileWithAirTags(
            final FamilyProfile profile,
            final List<String> airTagIds) {
        this.profile = profile;
        this.airTagIds = Collections.unmodifiableList(new ArrayList<>(airTagIds));
    }

    public FamilyProfile getProfile() {
        return this.profile;
    }

    public List<String> getAirTagIds() {
        return this.airTagIds;
    }
}
