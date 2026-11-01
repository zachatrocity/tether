package dev.wander.android.opentagviewer.ui.family;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dev.wander.android.opentagviewer.data.model.BeaconInformation;

public final class FamilyOverviewAdapterTest {

    @Test
    public void dividersAppearOnlyBetweenAdjacentAirTags() {
        final FamilyOverviewRow first = airTag("first");
        final FamilyOverviewRow second = airTag("second");
        final FamilyOverviewRow section = FamilyOverviewRow.sectionHeader("Unassigned AirTags");
        final FamilyOverviewRow empty = FamilyOverviewRow.emptyProfile("mina", "Mina");

        assertTrue(FamilyOverviewAdapter.hasDividerBetween(first, second));
        assertFalse(FamilyOverviewAdapter.hasDividerBetween(first, section));
        assertFalse(FamilyOverviewAdapter.hasDividerBetween(first, null));
        assertFalse(FamilyOverviewAdapter.hasDividerBetween(empty, second));
    }

    private static FamilyOverviewRow airTag(final String name) {
        return FamilyOverviewRow.airTag(
                "profile",
                "Mina",
                BeaconInformation.builder()
                        .beaconId(name)
                        .originalName(name)
                        .build(),
                null,
                null);
    }
}
