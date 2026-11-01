package dev.wander.android.opentagviewer.db.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import dev.wander.android.opentagviewer.db.repo.model.FamilyProfileWithAirTags;
import dev.wander.android.opentagviewer.db.repo.model.FamilyProfilesSnapshot;
import dev.wander.android.opentagviewer.db.room.OpenTagViewerDatabase;
import dev.wander.android.opentagviewer.db.room.entity.FamilyProfile;
import dev.wander.android.opentagviewer.db.room.entity.LocationReport;
import dev.wander.android.opentagviewer.db.room.entity.OwnedBeacon;

/** Real Room coverage for profile persistence, reassignment and cascade boundaries. */
@RunWith(AndroidJUnit4.class)
public class FamilyProfilePersistenceTest {
    private static final String TEST_DB = "family-profile-persistence-test";

    private Context context;
    private OpenTagViewerDatabase db;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.context.deleteDatabase(TEST_DB);
        this.openDatabase();
    }

    @After
    public void tearDown() {
        if (this.db != null && this.db.isOpen()) {
            this.db.close();
        }
        this.context.deleteDatabase(TEST_DB);
    }

    @Test
    public void assignmentsPersistAndProfileDeletionCannotDeleteAirTagData() {
        this.db.ownedBeaconDao().insertAll(airTag("tag-a"), airTag("tag-b"));
        this.db.locationReportDao().insertAll(report("report-a", "tag-a"));
        FamilyProfileRepository repository = new FamilyProfileRepository(this.db);
        final FamilyProfile avery = repository.createProfile("Avery").blockingGet();
        final FamilyProfile jordan = repository.createProfile("Jordan").blockingGet();
        repository.assignAirTag("tag-a", avery.id).blockingAwait();
        repository.assignAirTag("tag-b", avery.id).blockingAwait();
        repository.assignAirTag("tag-b", jordan.id).blockingAwait();

        this.db.close();
        this.openDatabase();
        repository = new FamilyProfileRepository(this.db);

        FamilyProfilesSnapshot snapshot = repository.listProfilesWithAirTags().blockingGet();
        assertEquals(List.of("tag-a"), airTagsFor(snapshot, avery.id));
        assertEquals(List.of("tag-b"), airTagsFor(snapshot, jordan.id));

        repository.deleteProfile(avery.id).blockingAwait();
        snapshot = repository.listProfilesWithAirTags().blockingGet();
        assertEquals(List.of("tag-a"), snapshot.getUnassignedAirTagIds());
        assertEquals(List.of("tag-b"), airTagsFor(snapshot, jordan.id));
        assertNotNull(this.db.ownedBeaconDao().getById("tag-a"));
        assertNotNull(this.db.locationReportDao().getLastFor("tag-a"));
    }

    @Test
    public void danglingProfileAssignmentFailsToTheUnassignedState() {
        this.db.ownedBeaconDao().insertAll(airTag("tag-a"));
        final SupportSQLiteDatabase sqlite = this.db.getOpenHelper().getWritableDatabase();
        sqlite.setForeignKeyConstraintsEnabled(false);
        sqlite.execSQL("INSERT INTO AirTagProfileAssignment(beacon_id, profile_id)"
                + " VALUES('tag-a', 'missing-profile')");
        sqlite.setForeignKeyConstraintsEnabled(true);

        final FamilyProfilesSnapshot snapshot = new FamilyProfileRepository(this.db)
                .listProfilesWithAirTags().blockingGet();

        assertEquals(List.of(), snapshot.getProfiles());
        assertEquals(List.of("tag-a"), snapshot.getUnassignedAirTagIds());
        assertNotNull(this.db.ownedBeaconDao().getById("tag-a"));
    }

    private void openDatabase() {
        this.db = Room.databaseBuilder(this.context, OpenTagViewerDatabase.class, TEST_DB)
                .allowMainThreadQueries()
                .build();
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

    private static OwnedBeacon airTag(final String id) {
        return OwnedBeacon.builder()
                .id(id)
                .version("1.0")
                .isRemoved(false)
                .fromAccount(true)
                .build();
    }

    private static LocationReport report(final String id, final String beaconId) {
        return LocationReport.builder()
                .hashId(id)
                .beaconId(beaconId)
                .publishedAt(1L)
                .timestamp(1L)
                .confidence(1L)
                .latitude(1.0)
                .longitude(1.0)
                .horizontalAccuracy(10L)
                .status(0L)
                .lastUpdate(1L)
                .provenance(LocationReport.PROVENANCE_APPLE)
                .build();
    }
}
