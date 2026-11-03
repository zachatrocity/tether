package dev.wander.android.opentagviewer.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import dev.wander.android.opentagviewer.DeviceInfoActivity;
import dev.wander.android.opentagviewer.Eventually;
import dev.wander.android.opentagviewer.R;
import dev.wander.android.opentagviewer.ReportHistoryActivity;
import dev.wander.android.opentagviewer.db.repo.FamilyProfileRepository;
import dev.wander.android.opentagviewer.db.room.OpenTagViewerDatabase;
import dev.wander.android.opentagviewer.db.room.entity.LastBleSighting;
import dev.wander.android.opentagviewer.db.room.entity.LocationReport;
import dev.wander.android.opentagviewer.python.AppDependencies;
import dev.wander.android.opentagviewer.ui.maps.AMapWithTagsOnIt;
import dev.wander.android.opentagviewer.ui.maps.FakeMapProvider;
import dev.wander.android.opentagviewer.ui.maps.MapProviderFactory;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Real activity flows over separate local Bluetooth and Apple network evidence. */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AirTagDetailFlowTest {
    private static final String PROFILE_NAME = "Mina";
    private static final String SELECTED_NAME = "Backpack AirTag";
    private static final String OTHER_NAME = "Jacket AirTag";
    private static final double UPDATED_LATITUDE = 12.34567;
    private static final double UPDATED_LONGITUDE = 76.54321;

    private final Context context = getInstrumentation().getTargetContext();
    private final OpenTagViewerDatabase db = OpenTagViewerDatabase.getInstance(this.context);
    private final AMapWithTagsOnIt savedFamily = new AMapWithTagsOnIt();

    private FamilyProfileRepository profiles;
    private String profileId;
    private String selectedId;
    private ActivityScenario<?> scenario;
    private FakeMapProvider map;

    @Before
    public void saveTwoAssociatedAirTagsWithBothEvidenceSources() {
        this.savedFamily.seed(SELECTED_NAME, OTHER_NAME);
        this.selectedId = this.savedFamily.tagIds().get(0);
        this.profiles = new FamilyProfileRepository(this.db);
        this.profileId = this.profiles.createProfile(PROFILE_NAME).blockingGet().id;
        for (final String tagId : this.savedFamily.tagIds()) {
            this.profiles.assignAirTag(tagId, this.profileId).blockingAwait();
        }
        this.map = new FakeMapProvider();
        MapProviderFactory.replaceWith(() -> this.map);

        final long heardAt = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(10);
        this.db.lastBleSightingDao().insert(LastBleSighting.builder()
                .beaconId(this.selectedId)
                .heardAt(heardAt)
                .batteryLevel("FULL")
                .statusByte(0)
                .build());
        this.db.locationReportDao().insertAll(report(
                this.selectedId,
                heardAt,
                AMapWithTagsOnIt.LATITUDE,
                AMapWithTagsOnIt.LONGITUDE,
                LocationReport.PROVENANCE_LOCAL,
                "local-seed"));
    }

    @After
    public void restoreDeviceState() {
        if (this.scenario != null) {
            this.scenario.close();
        }
        AppDependencies.reset();
        if (this.profileId != null) {
            this.profiles.deleteProfile(this.profileId).blockingAwait();
        }
        this.savedFamily.putItBack();
    }

    @Test
    public void detailShowsProfileTagsAndBothEvidenceSourcesWithActions() {
        AppDependencies.replaceAirTagReportRefresh(airTag -> Completable.complete());
        this.openDetail();

        Eventually.check(() -> this.scenario.onActivity(activity -> {
            assertEquals(PROFILE_NAME, text(activity, R.id.detail_profile_name));
            assertEquals(SELECTED_NAME, text(activity, R.id.detail_airtag_name));
            assertEquals("Nearby now", text(activity, R.id.detail_local_title));
            assertTrue(text(activity, R.id.detail_local_body)
                    .contains(AMapWithTagsOnIt.WHERE_THAT_IS));
            assertTrue(text(activity, R.id.detail_network_age).contains("not current"));
            assertEquals(AMapWithTagsOnIt.WHERE_THAT_IS,
                    text(activity, R.id.detail_network_place));
            assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.detail_network_map_card).getVisibility());
            assertEquals("the latest Apple report did not get one map marker",
                    1, this.map.markerCount());
            assertEquals(AMapWithTagsOnIt.LATITUDE,
                    this.map.markers().get(0).marker.getLatitude(), 0.00001);
            assertEquals(AMapWithTagsOnIt.LONGITUDE,
                    this.map.markers().get(0).marker.getLongitude(), 0.00001);
            assertFalse("the mini map never centred on its marker",
                    this.map.cameraMoves().isEmpty());

            final RecyclerView associated = activity.findViewById(
                    R.id.detail_associated_airtags);
            assertNotNull(associated.getAdapter());
            assertEquals(2, associated.getAdapter().getItemCount());

            assertTrue(activity.findViewById(R.id.detail_check_again).isEnabled());
            assertTrue(activity.findViewById(R.id.detail_play_sound).isEnabled());
            assertFalse(activity.findViewById(R.id.detail_local_card)
                    .getContentDescription().toString().isBlank());
            assertFalse(activity.findViewById(R.id.detail_network_card)
                    .getContentDescription().toString().isBlank());
        }));

        onView(allOf(withId(R.id.detail_check_again), isDisplayed(), isEnabled()))
                .check(matches(withText(R.string.detail_check_again)));
        onView(allOf(withId(R.id.detail_play_sound), isDisplayed(), isEnabled()))
                .check(matches(withText(R.string.detail_play_sound)));
        onView(withId(R.id.device_settings_name)).perform(scrollTo())
                .check(matches(isDisplayed()));
    }

    @Test
    public void checkAgainSuccessReloadsTheNewAppleReport() {
        AppDependencies.replaceAirTagReportRefresh(airTag ->
                Completable.fromAction(() -> this.db.locationReportDao().insertAll(
                                new LocationReport[] {report(
                                        this.selectedId,
                                        System.currentTimeMillis(),
                                        UPDATED_LATITUDE,
                                        UPDATED_LONGITUDE,
                                        LocationReport.PROVENANCE_APPLE,
                                        "network-updated")}))
                        .subscribeOn(Schedulers.io()));
        this.openDetail();

        Eventually.check(() -> onView(withText(containsString("not current")))
                .check(matches(isDisplayed())));
        onView(withId(R.id.detail_check_again)).perform(click());

        Eventually.check(() -> this.scenario.onActivity(activity -> {
            assertEquals("12.34567, 76.54321", text(activity, R.id.detail_network_place));
            assertFalse(text(activity, R.id.detail_network_age).contains("not current"));
            assertTrue(activity.findViewById(R.id.detail_check_again).isEnabled());
            assertEquals(activity.getString(R.string.detail_check_again),
                    text(activity, R.id.detail_check_again));
            assertEquals("refreshing the report left the mini map at the old latitude",
                    UPDATED_LATITUDE,
                    this.map.markers().get(0).marker.getLatitude(),
                    0.00001);
            assertEquals("refreshing the report left the mini map at the old longitude",
                    UPDATED_LONGITUDE,
                    this.map.markers().get(0).marker.getLongitude(),
                    0.00001);
        }));
    }

    @Test
    public void checkAgainFailurePreservesSavedEvidenceAndRestoresTheAction() {
        AppDependencies.replaceAirTagReportRefresh(airTag ->
                Completable.fromAction(() -> {
                    throw new IOException("test refresh failure");
                }));
        this.openDetail();

        Eventually.check(() -> this.scenario.onActivity(activity ->
                assertEquals(AMapWithTagsOnIt.WHERE_THAT_IS,
                        text(activity, R.id.detail_network_place))));
        onView(withId(R.id.detail_check_again)).perform(click());

        Eventually.check(() -> this.scenario.onActivity(activity -> {
            assertEquals(AMapWithTagsOnIt.WHERE_THAT_IS,
                    text(activity, R.id.detail_network_place));
            assertTrue(text(activity, R.id.detail_network_age).contains("not current"));
            assertTrue(activity.findViewById(R.id.detail_check_again).isEnabled());
            assertEquals(activity.getString(R.string.detail_check_again),
                    text(activity, R.id.detail_check_again));
        }));
    }

    @Test
    public void globalReportHistoryShowsOnlyTheSavedFactualEvidence() {
        this.scenario = ActivityScenario.launch(
                new Intent(this.context, ReportHistoryActivity.class));

        Eventually.check(() -> this.scenario.onActivity(activity -> {
            final RecyclerView events = activity.findViewById(R.id.report_history_events);
            assertNotNull(events.getAdapter());
            assertEquals(3, events.getAdapter().getItemCount());
            assertEquals(View.VISIBLE, events.getVisibility());
            assertEquals(View.GONE,
                    activity.findViewById(R.id.report_history_empty).getVisibility());
            assertTrue(events.getChildCount() > 0);
            final TextView action = events.getChildAt(0)
                    .findViewById(R.id.report_event_full_history);
            assertEquals(activity.getString(R.string.detail_open_full_history),
                    action.getText().toString());
            assertTrue(action.isEnabled());
        }));
        onView(withText(SELECTED_NAME + " heard over Bluetooth"))
                .check(matches(isDisplayed()));
        onView(withText(SELECTED_NAME + " report is getting old"))
                .check(matches(isDisplayed()));
        onView(withText(containsString("inferred trips"))).check(matches(isDisplayed()));
    }

    private void openDetail() {
        this.scenario = ActivityScenario.launch(new Intent(this.context, DeviceInfoActivity.class)
                .putExtra("beaconId", this.selectedId));
        Eventually.check(() -> onView(withId(R.id.detail_airtag_name))
                .check(matches(isDisplayed())));
    }
    private static String text(final Activity activity, final int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    private static LocationReport report(
            final String beaconId,
            final long timestamp,
            final double latitude,
            final double longitude,
            final String provenance,
            final String suffix) {
        return LocationReport.builder()
                .hashId(beaconId + '-' + suffix)
                .beaconId(beaconId)
                .publishedAt(timestamp)
                .description("test evidence")
                .timestamp(timestamp)
                .confidence(1)
                .latitude(latitude)
                .longitude(longitude)
                .horizontalAccuracy(42)
                .status(0)
                .lastUpdate(timestamp)
                .provenance(provenance)
                .build();
    }
}
