package dev.wander.android.opentagviewer.ui.family;

import static android.view.View.GONE;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation.ActivityResult;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import dev.wander.android.opentagviewer.DeviceInfoActivity;
import dev.wander.android.opentagviewer.Eventually;
import dev.wander.android.opentagviewer.FamilyOverviewActivity;
import dev.wander.android.opentagviewer.MapsActivity;
import dev.wander.android.opentagviewer.MyDevicesListActivity;
import dev.wander.android.opentagviewer.R;
import dev.wander.android.opentagviewer.db.repo.FamilyProfileRepository;
import dev.wander.android.opentagviewer.db.room.OpenTagViewerDatabase;
import dev.wander.android.opentagviewer.ui.maps.AMapWithTagsOnIt;

/** The signed-in home journey, using saved reports rather than a live Apple response. */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class FamilyOverviewFlowTest {

    private final AMapWithTagsOnIt savedFamily = new AMapWithTagsOnIt();
    private final List<String> profileIds = new ArrayList<>();

    private FamilyProfileRepository profiles;
    private ActivityScenario<FamilyOverviewActivity> scenario;

    @Before
    public void seedSavedFamilyOverview() {
        this.savedFamily.seed("Backpack AirTag", "Bike AirTag", "Keys AirTag")
                .arrangedAs(0, 1, 2);
        this.profiles = new FamilyProfileRepository(OpenTagViewerDatabase.getInstance(
                getInstrumentation().getTargetContext()));
        this.createProfile("Mina", 0);
        this.createProfile("Evan", 1);

        Intents.init();
        intending(not(hasComponent(FamilyOverviewActivity.class.getName())))
                .respondWith(new ActivityResult(Activity.RESULT_OK, null));
    }

    @After
    public void restoreStoredState() {
        if (this.scenario != null) {
            this.scenario.close();
        }
        Intents.release();
        for (final String profileId : this.profileIds) {
            this.profiles.deleteProfile(profileId).blockingAwait();
        }
        this.savedFamily.putItBack();
    }

    @Test
    public void aSavedRowOpensDetailsAndTheMenuOpensMapWithoutBackLeavingHome() {
        this.openHome();

        this.scenario.onActivity(activity -> {
            final View row = rowNamed(activity, "Backpack AirTag");
            assertNotNull("the saved AirTag row never appeared", row);
            assertTrue("the saved AirTag row did not handle its click", row.performClick());
        });
        Eventually.check(() -> assertTrue("the row did not open its AirTag details",
                sentIntent(DeviceInfoActivity.class, "beaconId",
                        this.savedFamily.tagIds().get(0))));

        this.scenario.onActivity(activity -> {
            assertTrue(activity.findViewById(R.id.family_navigation_menu).performClick());
            activity.getOnBackPressedDispatcher().onBackPressed();
            assertFalse("Back left Home instead of closing its open menu", activity.isFinishing());

            assertTrue(activity.findViewById(R.id.family_navigation_menu).performClick());
            assertTrue("the Map menu item did not handle its click",
                    navigationMenu(activity).getMenu()
                            .performIdentifierAction(R.id.navigation_map, 0));
        });
        Eventually.check(() -> assertTrue("the global menu did not open Map",
                sentIntent(MapsActivity.class, null, null)));
    }

    @Test
    public void addAirTagAndReportsRemainReachableFromTheNavigationMenu() {
        this.openHome();

        this.scenario.onActivity(activity -> {
            assertTrue(activity.findViewById(R.id.family_navigation_menu).performClick());
            assertTrue("the Add AirTag menu item did not handle its click",
                    navigationMenu(activity).getMenu()
                            .performIdentifierAction(R.id.navigation_add_airtag, 0));
        });
        Eventually.check(() -> assertTrue("the navigation menu did not open Add AirTag",
                sentIntent(MyDevicesListActivity.class, MyDevicesListActivity.EXTRA_DESTINATION,
                        MyDevicesListActivity.DESTINATION_ADD_AIR_TAG)));

        this.scenario.onActivity(activity -> {
            assertTrue(activity.findViewById(R.id.family_navigation_menu).performClick());
            assertTrue("the Reports menu item did not handle its click",
                    navigationMenu(activity).getMenu()
                            .performIdentifierAction(R.id.navigation_reports, 0));
        });
        Eventually.check(() -> assertTrue("the navigation menu did not open Reports",
                sentIntent(MyDevicesListActivity.class, MyDevicesListActivity.EXTRA_DESTINATION,
                        MyDevicesListActivity.DESTINATION_REPORT_HISTORY)));
    }

    @Test
    public void compactHomeKeepsTheFinalNamedAirTagAboveTheFloatingMapControl() {
        this.openHome();

        Eventually.check(() -> this.scenario.onActivity(activity -> {
            final View unassigned = rowNamed(activity, "Keys AirTag");
            assertNotNull("the final unassigned AirTag row never appeared", unassigned);
            assertEquals("the final AirTag row rendered a trailing divider",
                    GONE, unassigned.findViewById(R.id.family_row_divider).getVisibility());
            assertEquals("the AirTag name must lead an unassigned row",
                    "Keys AirTag",
                    ((TextView) unassigned.findViewById(R.id.family_row_person))
                            .getText().toString());
            assertEquals("assignment state must remain visible without dominating the row",
                    activity.getString(R.string.family_unassigned),
                    ((TextView) unassigned.findViewById(R.id.family_row_airtag))
                            .getText().toString());

            final NestedScrollView scroll =
                    activity.findViewById(R.id.family_overview_scroll);
            final View mapControl = activity.findViewById(R.id.family_open_map);
            assertTrue("the floating Map control still owns a full-width bottom region",
                    scroll.getBottom() > mapControl.getTop());

            scroll.fullScroll(View.FOCUS_DOWN);
            final View finalRow = laidOutRowNamed(activity, "Keys AirTag");
            assertNotNull("the final row was not laid out on the compact screen", finalRow);
            final int[] finalRowLocation = new int[2];
            final int[] mapControlLocation = new int[2];
            finalRow.getLocationOnScreen(finalRowLocation);
            mapControl.getLocationOnScreen(mapControlLocation);
            assertTrue("the final row cannot scroll completely above the floating map control",
                    finalRowLocation[1] + finalRow.getHeight() <= mapControlLocation[1]);
        }));
    }

    private void openHome() {
        this.scenario = ActivityScenario.launch(FamilyOverviewActivity.class);
        this.waitForHomeRows();
    }

    private void waitForHomeRows() {
        Eventually.check(() -> this.scenario.onActivity(activity -> {
            assertNotNull("the saved AirTag row never appeared",
                    rowNamed(activity, "Backpack AirTag"));
            final RecyclerView rows = activity.findViewById(R.id.family_rows);
            assertTrue("unassigned AirTags were not kept explicit",
                    adapterContainsText(rows,
                            activity.getString(R.string.family_unassigned_airtags)));
        }));
    }

    private static View laidOutRowNamed(
            final FamilyOverviewActivity activity, final String airTagName) {
        final RecyclerView rows = activity.findViewById(R.id.family_rows);
        for (int index = 0; index < rows.getChildCount(); index++) {
            final View row = rows.getChildAt(index);
            if (containsText(row, airTagName)) {
                return row;
            }
        }
        return null;
    }

    private static View rowNamed(
            final FamilyOverviewActivity activity, final String airTagName) {
        final RecyclerView rows = activity.findViewById(R.id.family_rows);
        final RecyclerView.Adapter<?> adapter = rows.getAdapter();
        if (adapter == null) {
            return null;
        }
        for (int index = 0; index < adapter.getItemCount(); index++) {
            final View row = boundRow(rows, adapter, index);
            if (containsText(row, airTagName)) {
                return row;
            }
        }
        return null;
    }

    private static boolean adapterContainsText(
            final RecyclerView rows, final String expected) {
        final RecyclerView.Adapter<?> adapter = rows.getAdapter();
        if (adapter == null) {
            return false;
        }
        for (int index = 0; index < adapter.getItemCount(); index++) {
            if (containsText(boundRow(rows, adapter, index), expected)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static View boundRow(
            final RecyclerView rows,
            final RecyclerView.Adapter<?> adapter,
            final int position) {
        final RecyclerView.Adapter rawAdapter = adapter;
        final RecyclerView.ViewHolder holder = rawAdapter.createViewHolder(
                rows, adapter.getItemViewType(position));
        rawAdapter.bindViewHolder(holder, position);
        return holder.itemView;
    }

    private static boolean containsText(final View view, final String expected) {
        if (view instanceof TextView
                && expected.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        final ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (containsText(group.getChildAt(index), expected)) {
                return true;
            }
        }
        return false;
    }

    private static PopupMenu navigationMenu(final FamilyOverviewActivity activity) {
        try {
            final java.lang.reflect.Field field =
                    FamilyOverviewActivity.class.getDeclaredField("navigationPopup");
            field.setAccessible(true);
            final PopupMenu popup = (PopupMenu) field.get(activity);
            assertNotNull("the navigation button did not open its menu", popup);
            return popup;
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError("could not inspect the open navigation menu", error);
        }
    }

    private static boolean sentIntent(
            final Class<?> destination,
            final String extraName,
            final Object extraValue) {
        for (final Intent intent : Intents.getIntents()) {
            if (intent.getComponent() == null
                    || !destination.getName().equals(intent.getComponent().getClassName())) {
                continue;
            }
            if (extraName == null
                    || (intent.hasExtra(extraName)
                        && extraValue.equals(intent.getExtras().get(extraName)))) {
                return true;
            }
        }
        return false;
    }

    private void createProfile(final String name, final int tagIndex) {
        final String profileId = this.profiles.createProfile(name).blockingGet().id;
        this.profileIds.add(profileId);
        this.profiles.assignAirTag(this.savedFamily.tagIds().get(tagIndex), profileId)
                .blockingAwait();
    }

}
