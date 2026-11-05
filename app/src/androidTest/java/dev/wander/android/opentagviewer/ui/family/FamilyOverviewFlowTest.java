package dev.wander.android.opentagviewer.ui.family;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeDown;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
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

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import dev.wander.android.opentagviewer.AppleLoginActivity;
import dev.wander.android.opentagviewer.DeviceInfoActivity;
import dev.wander.android.opentagviewer.Eventually;
import dev.wander.android.opentagviewer.FamilyOverviewActivity;
import dev.wander.android.opentagviewer.MapsActivity;
import dev.wander.android.opentagviewer.MyDevicesListActivity;
import dev.wander.android.opentagviewer.R;
import dev.wander.android.opentagviewer.ReportHistoryActivity;
import dev.wander.android.opentagviewer.db.datastore.UserAuthDataStore;
import dev.wander.android.opentagviewer.db.repo.FamilyProfileRepository;
import dev.wander.android.opentagviewer.db.repo.UserAuthRepository;
import dev.wander.android.opentagviewer.db.room.OpenTagViewerDatabase;
import dev.wander.android.opentagviewer.db.room.entity.LocationReport;
import dev.wander.android.opentagviewer.db.room.entity.UserBeaconOptions;
import dev.wander.android.opentagviewer.python.AppDependencies;
import dev.wander.android.opentagviewer.python.icloud.ICloudException;
import dev.wander.android.opentagviewer.python.icloud.ICloudFailure;
import dev.wander.android.opentagviewer.ui.maps.AMapWithTagsOnIt;
import dev.wander.android.opentagviewer.util.android.AppCryptographyUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;

/** The signed-in home journey, using saved reports rather than a live Apple response. */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class FamilyOverviewFlowTest {

    private final AMapWithTagsOnIt savedFamily = new AMapWithTagsOnIt();
    private final List<String> profileIds = new ArrayList<>();

    private FamilyProfileRepository profiles;
    private OpenTagViewerDatabase database;
    private ActivityScenario<FamilyOverviewActivity> scenario;

    @Before
    public void seedSavedFamilyOverview() {
        this.savedFamily.seed("Backpack AirTag", "Bike AirTag", "Keys AirTag");
        this.database = OpenTagViewerDatabase.getInstance(
                getInstrumentation().getTargetContext());
        this.database.userBeaconOptionsDao().insertAll(UserBeaconOptions.builder()
                .beaconId(this.savedFamily.tagIds().get(0))
                .lastUpdate(System.currentTimeMillis())
                .uiEmoji("🎒")
                .build());
        this.savedFamily.arrangedAs(0, 1, 2);
        this.profiles = new FamilyProfileRepository(this.database);
        this.createProfile("Mina", 0);
        this.createProfile("Evan", 1);
        AppDependencies.replaceAppleServiceRestore(
                (context, settings, userAuth) -> Completable.complete());

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
                sentIntent(ReportHistoryActivity.class, null, null)));
    }

    @Test
    public void disconnectedNoticeOffersAButtonThatOpensLogin() {
        new UserAuthRepository(
                UserAuthDataStore.getInstance(getInstrumentation().getTargetContext()),
                new AppCryptographyUtil())
                .clearUser()
                .blockingAwait();
        this.scenario = ActivityScenario.launch(FamilyOverviewActivity.class);

        Eventually.check(() -> this.scenario.onActivity(activity -> {
            assertNotNull("saved AirTags disappeared with the disconnected account",
                    rowNamed(activity, "Backpack AirTag"));
            assertEquals(VISIBLE,
                    activity.findViewById(R.id.family_status_notice).getVisibility());
            assertEquals(activity.getString(R.string.family_account_disconnected_title),
                    ((TextView) activity.findViewById(R.id.family_status_title))
                            .getText().toString());
            assertEquals(VISIBLE,
                    activity.findViewById(R.id.family_status_login).getVisibility());
        }));

        onView(withId(R.id.family_status_login)).perform(click());

        Eventually.check(() -> assertTrue("the disconnected notice did not open Login",
                sentIntent(AppleLoginActivity.class, null, null)));
    }

    @Test
    public void pullToRefreshWaitsForTheStoredAccountWithoutSigningItOut() {
        final CompletableSubject accountRestore = CompletableSubject.create();
        final AtomicInteger refreshCalls = new AtomicInteger();
        AppDependencies.replaceAppleServiceRestore(
                (context, settings, userAuth) -> accountRestore);
        AppDependencies.replaceAirTagReportRefresh(airTag ->
                Completable.fromAction(refreshCalls::incrementAndGet));
        this.openHome();

        onView(withId(R.id.family_refresh)).perform(swipeDown());

        Eventually.check(() -> this.scenario.onActivity(activity ->
                assertTrue("the refresh indicator stopped while account restoration was pending",
                        ((SwipeRefreshLayout) activity.findViewById(R.id.family_refresh))
                                .isRefreshing())));
        assertEquals("a report request ran before the stored account was restored",
                0, refreshCalls.get());
        assertTrue("waiting for account restoration deleted the stored session",
                this.hasStoredAccount());

        accountRestore.onComplete();

        Eventually.check(() -> assertEquals(
                "the assigned AirTags were not refreshed after account restoration",
                2, refreshCalls.get()));
        assertTrue("refreshing deleted the stored session", this.hasStoredAccount());
    }

    @Test
    public void refusedCredentialsKeepTheSessionUntilLoginIsChosen() {
        AppDependencies.replaceAirTagReportRefresh(airTag -> Completable.error(
                new ICloudException(ICloudFailure.CREDENTIALS_REJECTED, "refused")));
        this.openHome();

        onView(withId(R.id.family_refresh)).perform(swipeDown());

        Eventually.check(() -> onView(withId(R.id.family_status_login))
                .check(matches(isDisplayed())));
        assertTrue("a refused refresh deleted the session before the user chose Login",
                this.hasStoredAccount());
        assertFalse("a refused refresh opened Login without the user choosing it",
                sentIntent(AppleLoginActivity.class, null, null));

        onView(withId(R.id.family_status_login)).perform(click());

        Eventually.check(() -> assertTrue("the Login button did not open Login",
                sentIntent(AppleLoginActivity.class, null, null)));
        Eventually.check(() -> assertFalse(
                "the explicitly replaced session was kept",
                this.hasStoredAccount()));
    }

    @Test
    public void pullToRefreshUpdatesOnlyAssignedAirTagsAndReloadsTheirEvidence() {
        final Set<String> refreshed = Collections.synchronizedSet(new HashSet<>());
        AppDependencies.replaceAirTagReportRefresh(airTag ->
                Completable.fromAction(() -> {
                    refreshed.add(airTag.getBeaconId());
                    this.storeFreshReport(airTag.getBeaconId());
                }).subscribeOn(Schedulers.io()));
        this.openHome();

        onView(withId(R.id.family_refresh)).perform(swipeDown());

        final Set<String> expected = new HashSet<>(
                this.savedFamily.tagIds().subList(0, 2));
        Eventually.check(() -> assertEquals(
                "pull-to-refresh did not ask about exactly the assigned AirTags",
                expected,
                refreshed));
        Eventually.check(() -> this.scenario.onActivity(activity -> {
            final SwipeRefreshLayout refresh = activity.findViewById(R.id.family_refresh);
            assertFalse("the refresh indicator did not stop", refresh.isRefreshing());
            final View backpack = rowNamed(activity, "Backpack AirTag");
            assertNotNull("the refreshed AirTag row disappeared", backpack);
            final TextView freshness = backpack.findViewById(R.id.family_row_freshness);
            assertEquals("the refreshed report did not reach the visible row",
                    MaterialColors.getColor(
                            backpack, com.google.android.material.R.attr.colorPrimary),
                    freshness.getCurrentTextColor());
        }));
    }

    @Test
    public void oneTransientFailureStillAttemptsTheOtherAssignedAirTags() {
        final Set<String> attempted = Collections.synchronizedSet(new HashSet<>());
        final String failing = this.savedFamily.tagIds().get(0);
        AppDependencies.replaceAirTagReportRefresh(airTag ->
                Completable.fromAction(() -> {
                    attempted.add(airTag.getBeaconId());
                    if (failing.equals(airTag.getBeaconId())) {
                        throw new IOException("test refresh failure");
                    }
                }).subscribeOn(Schedulers.io()));
        this.openHome();

        onView(withId(R.id.family_refresh)).perform(swipeDown());

        final Set<String> expected = new HashSet<>(
                this.savedFamily.tagIds().subList(0, 2));
        Eventually.check(() -> assertEquals(
                "one failed AirTag prevented another assigned AirTag from being attempted",
                expected,
                attempted));
        Eventually.check(() -> this.scenario.onActivity(activity -> {
            assertEquals(VISIBLE,
                    activity.findViewById(R.id.family_status_notice).getVisibility());
            assertEquals(activity.getString(R.string.family_refresh_assigned_failed_title),
                    ((TextView) activity.findViewById(R.id.family_status_title))
                            .getText().toString());
            assertFalse(((SwipeRefreshLayout) activity.findViewById(R.id.family_refresh))
                    .isRefreshing());
            assertNotNull("saved rows disappeared after a refresh failure",
                    rowNamed(activity, "Backpack AirTag"));
        }));
    }

    @Test
    public void pullToRefreshExplainsWhenNothingIsAssigned() {
        this.profiles.unassignAirTag(this.savedFamily.tagIds().get(0)).blockingAwait();
        this.profiles.unassignAirTag(this.savedFamily.tagIds().get(1)).blockingAwait();
        final Set<String> attempted = Collections.synchronizedSet(new HashSet<>());
        AppDependencies.replaceAirTagReportRefresh(airTag ->
                Completable.fromAction(() -> attempted.add(airTag.getBeaconId())));
        this.openHome();

        onView(withId(R.id.family_refresh)).perform(swipeDown());

        Eventually.check(() -> this.scenario.onActivity(activity -> {
            assertTrue("an unassigned AirTag was refreshed", attempted.isEmpty());
            assertEquals(VISIBLE,
                    activity.findViewById(R.id.family_status_notice).getVisibility());
            assertEquals(activity.getString(R.string.family_refresh_no_assigned_title),
                    ((TextView) activity.findViewById(R.id.family_status_title))
                            .getText().toString());
            assertFalse(((SwipeRefreshLayout) activity.findViewById(R.id.family_refresh))
                    .isRefreshing());
        }));
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
            final View backpack = rowNamed(activity, "Backpack AirTag");
            assertNotNull("the saved AirTag row never appeared", backpack);
            assertEquals("the saved AirTag emoji did not appear in its Family avatar",
                    "🎒",
                    ((MaterialButton) backpack.findViewById(R.id.family_row_edit_profile))
                            .getText().toString());
            assertEquals("a healthy Family Overview rendered a status notice", GONE,
                    activity.findViewById(R.id.family_status_notice).getVisibility());
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
            final Field field =
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

    private boolean hasStoredAccount() {
        return new UserAuthRepository(
                UserAuthDataStore.getInstance(getInstrumentation().getTargetContext()),
                new AppCryptographyUtil())
                .getUserAuth()
                .blockingFirst()
                .isPresent();
    }

    private void storeFreshReport(final String beaconId) {
        final long now = System.currentTimeMillis();
        this.database.locationReportDao().insertAll(new LocationReport[] {
                LocationReport.builder()
                        .hashId("family-pull-" + beaconId)
                        .beaconId(beaconId)
                        .publishedAt(now)
                        .description("test pull-to-refresh")
                        .timestamp(now)
                        .confidence(2)
                        .latitude(12.34567)
                        .longitude(76.54321)
                        .horizontalAccuracy(25)
                        .status(144)
                        .lastUpdate(now)
                        .provenance(LocationReport.PROVENANCE_APPLE)
                        .build()
        });
    }

    private void createProfile(final String name, final int tagIndex) {
        final String profileId = this.profiles.createProfile(name).blockingGet().id;
        this.profileIds.add(profileId);
        this.profiles.assignAirTag(this.savedFamily.tagIds().get(tagIndex), profileId)
                .blockingAwait();
    }

}
