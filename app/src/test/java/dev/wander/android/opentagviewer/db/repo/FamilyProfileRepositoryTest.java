package dev.wander.android.opentagviewer.db.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import dev.wander.android.opentagviewer.db.repo.model.FamilyProfileWithAirTags;
import dev.wander.android.opentagviewer.db.repo.model.FamilyProfilesSnapshot;
import dev.wander.android.opentagviewer.db.room.dao.FamilyProfileDao;
import dev.wander.android.opentagviewer.db.room.entity.AirTagProfileAssignment;
import dev.wander.android.opentagviewer.db.room.entity.FamilyProfile;

/** Pure repository behavior; SQLite constraints and migration behavior are covered on Android. */
public class FamilyProfileRepositoryTest {
    private FakeFamilyProfileDao dao;
    private FamilyProfileRepository repository;

    @Before
    public void setUp() {
        this.dao = new FakeFamilyProfileDao();
        final AtomicInteger nextId = new AtomicInteger();
        this.repository = new FamilyProfileRepository(
                this.dao, () -> "profile-" + nextId.incrementAndGet());
    }

    @Test
    public void noProfilesMeansEveryExistingAirTagIsExplicitlyUnassigned() {
        this.dao.addAirTags("tag-b", "tag-a");

        final FamilyProfilesSnapshot snapshot = this.repository
                .listProfilesWithAirTags().blockingGet();

        assertEquals(List.of(), snapshot.getProfiles());
        assertEquals(List.of("tag-a", "tag-b"), snapshot.getUnassignedAirTagIds());
    }

    @Test
    public void createAndRenameKeepTheStableProfileId() {
        final FamilyProfile created = this.repository.createProfile("  Avery  ").blockingGet();
        this.repository.renameProfile(created.id, "Avery R.").blockingAwait();

        final FamilyProfile stored = this.repository
                .listProfilesWithAirTags().blockingGet()
                .getProfiles().get(0).getProfile();
        assertEquals("profile-1", created.id);
        assertEquals(created.id, stored.id);
        assertEquals("Avery R.", stored.displayName);
    }

    @Test
    public void oneProfileCanHoldManyAirTagsAndReassignmentMovesOnlyOne() {
        this.dao.addAirTags("tag-a", "tag-b");
        final FamilyProfile avery = this.repository.createProfile("Avery").blockingGet();
        final FamilyProfile jordan = this.repository.createProfile("Jordan").blockingGet();
        this.repository.assignAirTag("tag-a", avery.id).blockingAwait();
        this.repository.assignAirTag("tag-b", avery.id).blockingAwait();

        this.repository.assignAirTag("tag-b", jordan.id).blockingAwait();

        final FamilyProfilesSnapshot snapshot = this.repository
                .listProfilesWithAirTags().blockingGet();
        assertEquals(List.of("tag-a"), airTagsFor(snapshot, avery.id));
        assertEquals(List.of("tag-b"), airTagsFor(snapshot, jordan.id));
        assertEquals(List.of(), snapshot.getUnassignedAirTagIds());
    }

    @Test
    public void deletingAProfileLeavesItsAirTagsUnassignedAndIntact() {
        this.dao.addAirTags("tag-a", "tag-b");
        final FamilyProfile profile = this.repository.createProfile("Avery").blockingGet();
        this.repository.assignAirTag("tag-a", profile.id).blockingAwait();
        this.repository.assignAirTag("tag-b", profile.id).blockingAwait();

        this.repository.deleteProfile(profile.id).blockingAwait();

        final FamilyProfilesSnapshot snapshot = this.repository
                .listProfilesWithAirTags().blockingGet();
        assertEquals(List.of(), snapshot.getProfiles());
        assertEquals(List.of("tag-a", "tag-b"), snapshot.getUnassignedAirTagIds());
        assertEquals(Set.of("tag-a", "tag-b"), this.dao.airTagIds);
    }

    @Test
    public void unassignIsIdempotentAndReturnsTheAirTagToTheFallback() {
        this.dao.addAirTags("tag-a");
        final FamilyProfile profile = this.repository.createProfile("Avery").blockingGet();
        this.repository.assignAirTag("tag-a", profile.id).blockingAwait();

        this.repository.unassignAirTag("tag-a").blockingAwait();
        this.repository.unassignAirTag("tag-a").blockingAwait();

        assertEquals(List.of("tag-a"), this.repository
                .listProfilesWithAirTags().blockingGet().getUnassignedAirTagIds());
    }

    @Test
    public void danglingProfileDataFallsBackToUnassigned() {
        this.dao.addAirTags("tag-a");
        this.dao.assignments.put("tag-a", "missing-profile");

        final FamilyProfilesSnapshot snapshot = this.repository
                .listProfilesWithAirTags().blockingGet();

        assertEquals(List.of(), snapshot.getProfiles());
        assertEquals(List.of("tag-a"), snapshot.getUnassignedAirTagIds());
    }

    @Test
    public void blankNamesNeverCreateOrRenameProfiles() {
        assertThrows(IllegalArgumentException.class,
                () -> this.repository.createProfile("  ").blockingGet());
        final FamilyProfile profile = this.repository.createProfile("Avery").blockingGet();
        assertThrows(IllegalArgumentException.class,
                () -> this.repository.renameProfile(profile.id, "\t").blockingAwait());
        assertEquals("Avery", this.dao.profiles.get(profile.id).displayName);
    }

    private static List<String> airTagsFor(
            final FamilyProfilesSnapshot snapshot,
            final String profileId) {
        for (final FamilyProfileWithAirTags profile : snapshot.getProfiles()) {
            if (profile.getProfile().id.equals(profileId)) {
                return profile.getAirTagIds();
            }
        }
        throw new AssertionError("Profile not listed: " + profileId);
    }

    /** A stateful substitute for Room; SQL behavior itself is intentionally not copied here. */
    private static final class FakeFamilyProfileDao implements FamilyProfileDao {
        private final Map<String, FamilyProfile> profiles = new LinkedHashMap<>();
        private final Set<String> airTagIds = new LinkedHashSet<>();
        private final Map<String, String> assignments = new LinkedHashMap<>();

        void addAirTags(final String... ids) {
            for (final String id : ids) {
                this.airTagIds.add(id);
            }
        }

        @Override
        public void insertProfile(final FamilyProfile profile) {
            if (this.profiles.putIfAbsent(profile.id, profile) != null) {
                throw new IllegalStateException("duplicate profile id");
            }
        }

        @Override
        public int renameProfile(final String profileId, final String displayName) {
            if (!this.profiles.containsKey(profileId)) {
                return 0;
            }
            this.profiles.put(profileId, new FamilyProfile(profileId, displayName));
            return 1;
        }

        @Override
        public int deleteProfile(final String profileId) {
            if (this.profiles.remove(profileId) == null) {
                return 0;
            }
            this.assignments.values().removeIf(profileId::equals);
            return 1;
        }

        @Override
        public void assignAirTag(final AirTagProfileAssignment assignment) {
            if (!this.airTagIds.contains(assignment.beaconId)
                    || !this.profiles.containsKey(assignment.profileId)) {
                throw new IllegalStateException("foreign key violation");
            }
            this.assignments.put(assignment.beaconId, assignment.profileId);
        }

        @Override
        public int unassignAirTag(final String beaconId) {
            return this.assignments.remove(beaconId) == null ? 0 : 1;
        }

        @Override
        public List<FamilyProfile> getProfiles() {
            final List<FamilyProfile> result = new ArrayList<>(this.profiles.values());
            result.sort(Comparator.comparing(
                    (FamilyProfile profile) -> profile.displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(profile -> profile.id));
            return result;
        }

        @Override
        public List<AirTagProfileAssignment> getValidAssignments() {
            final List<AirTagProfileAssignment> result = new ArrayList<>();
            for (final Map.Entry<String, String> assignment : this.assignments.entrySet()) {
                if (this.airTagIds.contains(assignment.getKey())
                        && this.profiles.containsKey(assignment.getValue())) {
                    result.add(new AirTagProfileAssignment(
                            assignment.getKey(), assignment.getValue()));
                }
            }
            result.sort(Comparator.comparing((AirTagProfileAssignment item) -> item.profileId)
                    .thenComparing(item -> item.beaconId));
            return result;
        }

        @Override
        public List<String> getUnassignedAirTagIds() {
            final List<String> result = new ArrayList<>();
            for (final String airTagId : this.airTagIds) {
                final String profileId = this.assignments.get(airTagId);
                if (profileId == null || !this.profiles.containsKey(profileId)) {
                    result.add(airTagId);
                }
            }
            result.sort(String::compareTo);
            return result;
        }
    }
}
