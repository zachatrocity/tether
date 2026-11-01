package dev.wander.android.opentagviewer.db.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import dev.wander.android.opentagviewer.db.room.entity.AirTagProfileAssignment;
import dev.wander.android.opentagviewer.db.room.entity.FamilyProfile;

@Dao
public interface FamilyProfileDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertProfile(FamilyProfile profile);

    @Query("UPDATE FamilyProfile SET display_name = :displayName WHERE id = :profileId")
    int renameProfile(String profileId, String displayName);

    /** Deleting the profile cascades only its assignments; AirTags and their history stay intact. */
    @Query("DELETE FROM FamilyProfile WHERE id = :profileId")
    int deleteProfile(String profileId);

    /**
     * The AirTag id is the primary key, so this one statement atomically creates or replaces its
     * sole assignment. REPLACE can delete only the old assignment row; it cannot delete either
     * parent because neither parent is a child of this table.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void assignAirTag(AirTagProfileAssignment assignment);

    @Query("DELETE FROM AirTagProfileAssignment WHERE beacon_id = :beaconId")
    int unassignAirTag(String beaconId);

    @Query("SELECT * FROM FamilyProfile ORDER BY display_name COLLATE NOCASE, id")
    List<FamilyProfile> getProfiles();

    /**
     * Inner joins deliberately discard a partially missing assignment. The corresponding AirTag
     * then appears in {@link #getUnassignedAirTagIds()} instead of inventing a profile for it.
     */
    @Query("SELECT assignment.* FROM AirTagProfileAssignment AS assignment"
            + " INNER JOIN FamilyProfile AS profile ON profile.id = assignment.profile_id"
            + " INNER JOIN OwnedBeacons AS beacon ON beacon.id = assignment.beacon_id"
            + " WHERE beacon.is_removed = 0"
            + " ORDER BY assignment.profile_id, assignment.beacon_id")
    List<AirTagProfileAssignment> getValidAssignments();

    @Query("SELECT beacon.id FROM OwnedBeacons AS beacon"
            + " WHERE beacon.is_removed = 0"
            + " AND NOT EXISTS ("
            + " SELECT 1 FROM AirTagProfileAssignment AS assignment"
            + " INNER JOIN FamilyProfile AS profile ON profile.id = assignment.profile_id"
            + " WHERE assignment.beacon_id = beacon.id"
            + " ) ORDER BY beacon.id")
    List<String> getUnassignedAirTagIds();
}
