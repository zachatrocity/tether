package dev.wander.android.opentagviewer.db.repo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import dev.wander.android.opentagviewer.db.repo.model.FamilyProfileWithAirTags;
import dev.wander.android.opentagviewer.db.repo.model.FamilyProfilesSnapshot;
import dev.wander.android.opentagviewer.db.room.OpenTagViewerDatabase;
import dev.wander.android.opentagviewer.db.room.dao.FamilyProfileDao;
import dev.wander.android.opentagviewer.db.room.entity.AirTagProfileAssignment;
import dev.wander.android.opentagviewer.db.room.entity.FamilyProfile;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Local, on-device family profiles and their AirTag associations. */
public final class FamilyProfileRepository {
    private final FamilyProfileDao dao;
    private final Supplier<String> profileIdSupplier;

    public FamilyProfileRepository(final OpenTagViewerDatabase db) {
        this(db.familyProfileDao(), () -> UUID.randomUUID().toString());
    }

    FamilyProfileRepository(
            final FamilyProfileDao dao,
            final Supplier<String> profileIdSupplier) {
        this.dao = dao;
        this.profileIdSupplier = profileIdSupplier;
    }

    /** Creates a profile with a stable opaque id; no profile is created implicitly. */
    public Single<FamilyProfile> createProfile(final String displayName) {
        return Single.fromCallable(() -> {
            final FamilyProfile profile = new FamilyProfile(
                    requireId(this.profileIdSupplier.get(), "generated profile id"),
                    normalizeDisplayName(displayName));
            this.dao.insertProfile(profile);
            return profile;
        }).subscribeOn(Schedulers.io());
    }

    public Completable renameProfile(final String profileId, final String displayName) {
        return Completable.fromAction(() -> {
            final String id = requireId(profileId, "profile id");
            if (this.dao.renameProfile(id, normalizeDisplayName(displayName)) == 0) {
                throw new IllegalArgumentException("Unknown family profile: " + id);
            }
        }).subscribeOn(Schedulers.io());
    }

    /** Deletes only the profile. Its AirTags survive and become unassigned via the foreign key. */
    public Completable deleteProfile(final String profileId) {
        return Completable.fromAction(() -> {
            final String id = requireId(profileId, "profile id");
            if (this.dao.deleteProfile(id) == 0) {
                throw new IllegalArgumentException("Unknown family profile: " + id);
            }
        }).subscribeOn(Schedulers.io());
    }

    /** Atomically creates or replaces the AirTag's sole profile association. */
    public Completable assignAirTag(final String beaconId, final String profileId) {
        return Completable.fromAction(() -> this.dao.assignAirTag(new AirTagProfileAssignment(
                requireId(beaconId, "AirTag beacon id"),
                requireId(profileId, "profile id"))))
                .subscribeOn(Schedulers.io());
    }

    /** Idempotent: an already-unassigned AirTag remains unassigned. */
    public Completable unassignAirTag(final String beaconId) {
        return Completable.fromAction(() -> this.dao.unassignAirTag(
                requireId(beaconId, "AirTag beacon id")))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Lists every real profile and the explicit unassigned fallback. Invalid dangling profile
     * data is omitted from profile rows; the DAO returns its still-existing AirTags as unassigned.
     */
    public Single<FamilyProfilesSnapshot> listProfilesWithAirTags() {
        return Single.fromCallable(() -> {
            final List<FamilyProfile> profiles = this.dao.getProfiles();
            final Map<String, List<String>> airTagsByProfile = new LinkedHashMap<>();
            for (final FamilyProfile profile : profiles) {
                airTagsByProfile.put(profile.id, new ArrayList<>());
            }

            for (final AirTagProfileAssignment assignment : this.dao.getValidAssignments()) {
                final List<String> airTagIds = airTagsByProfile.get(assignment.profileId);
                if (airTagIds != null) {
                    airTagIds.add(assignment.beaconId);
                }
            }

            final List<FamilyProfileWithAirTags> result = new ArrayList<>(profiles.size());
            for (final FamilyProfile profile : profiles) {
                result.add(new FamilyProfileWithAirTags(
                        profile, airTagsByProfile.get(profile.id)));
            }
            return new FamilyProfilesSnapshot(result, this.dao.getUnassignedAirTagIds());
        }).subscribeOn(Schedulers.io());
    }

    private static String normalizeDisplayName(final String displayName) {
        if (displayName == null) {
            throw new IllegalArgumentException("display name is required");
        }
        final String normalized = displayName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("display name must not be blank");
        }
        return normalized;
    }

    private static String requireId(final String value, final String label) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }
}
