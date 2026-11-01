package dev.wander.android.opentagviewer.db.room.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A person named by the user. Profiles are local to this Tether installation. */
@Entity(tableName = "FamilyProfile")
public final class FamilyProfile {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public final String id;

    @NonNull
    @ColumnInfo(name = "display_name")
    public final String displayName;

    public FamilyProfile(@NonNull final String id, @NonNull final String displayName) {
        this.id = id;
        this.displayName = displayName;
    }
}
