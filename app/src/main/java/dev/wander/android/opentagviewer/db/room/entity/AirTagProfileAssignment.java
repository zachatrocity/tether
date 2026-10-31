package dev.wander.android.opentagviewer.db.room.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** The one current family profile, if any, associated with an existing AirTag. */
@Entity(
        tableName = "AirTagProfileAssignment",
        primaryKeys = {"beacon_id"},
        foreignKeys = {
                @ForeignKey(
                        entity = OwnedBeacon.class,
                        parentColumns = {"id"},
                        childColumns = {"beacon_id"},
                        onUpdate = ForeignKey.CASCADE,
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = FamilyProfile.class,
                        parentColumns = {"id"},
                        childColumns = {"profile_id"},
                        onUpdate = ForeignKey.CASCADE,
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = @Index(value = {"profile_id"})
)
public final class AirTagProfileAssignment {
    @NonNull
    @ColumnInfo(name = "beacon_id")
    public final String beaconId;

    @NonNull
    @ColumnInfo(name = "profile_id")
    public final String profileId;

    public AirTagProfileAssignment(
            @NonNull final String beaconId,
            @NonNull final String profileId) {
        this.beaconId = beaconId;
        this.profileId = profileId;
    }
}
