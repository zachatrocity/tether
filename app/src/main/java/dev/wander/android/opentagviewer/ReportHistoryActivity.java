package dev.wander.android.opentagviewer;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.location.Address;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.PopupMenu;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.datastore.UserSettingsDataStore;
import dev.wander.android.opentagviewer.db.repo.BeaconRepository;
import dev.wander.android.opentagviewer.db.repo.UserSettingsRepository;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;
import dev.wander.android.opentagviewer.db.repo.model.TaggedLocationReport;
import dev.wander.android.opentagviewer.db.repo.model.UserSettings;
import dev.wander.android.opentagviewer.db.room.OpenTagViewerDatabase;
import dev.wander.android.opentagviewer.python.AppDependencies;
import dev.wander.android.opentagviewer.ui.compat.WindowPaddingUtil;
import dev.wander.android.opentagviewer.ui.history.ReportHistoryAdapter;
import dev.wander.android.opentagviewer.ui.history.ReportHistoryEvent;
import dev.wander.android.opentagviewer.ui.navigation.FloatingNavigationMenu;
import dev.wander.android.opentagviewer.util.TagOrder;
import dev.wander.android.opentagviewer.util.TagVisibility;
import dev.wander.android.opentagviewer.util.android.AddressLookup;
import dev.wander.android.opentagviewer.util.parse.BeaconDataParser;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Global chronological feed over saved local and Apple evidence for every visible AirTag. */
public final class ReportHistoryActivity extends AppCompatActivity {
    private static final String TAG = ReportHistoryActivity.class.getSimpleName();

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ReportHistoryAdapter adapter;
    private BeaconRepository beaconRepository;
    private AddressLookup geocoder;
    private RecyclerView eventsView;
    private View loadingView;
    private View emptyView;
    private PopupMenu navigationPopup;
    private boolean navigationMenuOpen;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((OpenAirTagApplication) this.getApplication()).setupTheme();
        this.setContentView(R.layout.activity_report_history);
        WindowPaddingUtil.insetForSystemBars(this.findViewById(R.id.report_history_root));

        if (this.getSupportActionBar() != null) {
            this.getSupportActionBar().hide();
        }

        this.beaconRepository = new BeaconRepository(OpenTagViewerDatabase.getInstance(
                this.getApplicationContext()));
        this.geocoder = AppDependencies.geocoder(
                this.getApplicationContext(), Locale.getDefault());
        this.loadingView = this.findViewById(R.id.report_history_loading);
        this.emptyView = this.findViewById(R.id.report_history_empty);
        this.eventsView = this.findViewById(R.id.report_history_events);
        this.adapter = new ReportHistoryAdapter(this::openFullHistory);
        this.eventsView.setLayoutManager(new LinearLayoutManager(this));
        this.eventsView.setAdapter(this.adapter);
        this.eventsView.setItemAnimator(null);

        this.findViewById(R.id.report_history_navigation_menu)
                .setOnClickListener(this::showNavigationMenu);
        this.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navigationMenuOpen && navigationPopup != null) {
                    navigationPopup.dismiss();
                    return;
                }
                finish();
            }
        });

        this.loadSavedEvents();
    }

    private void loadSavedEvents() {
        final UserSettings settings = new UserSettingsRepository(
                UserSettingsDataStore.getInstance(this.getApplicationContext()))
                .getUserSettings();
        final Observable<List<BeaconInformation>> airTags = this.beaconRepository.getAllBeacons()
                .flatMap(BeaconDataParser::parseAsync)
                .map(all -> TagVisibility.visible(all, settings.shouldShowAppleDevices()))
                .map(TagOrder::sorted);

        this.disposables.add(Observable.zip(
                        airTags,
                        this.beaconRepository.getAllAppleLocations(),
                        this.beaconRepository.getAllLocalLocations(),
                        this.beaconRepository.getLastSightingsForAll(),
                        LoadedEvents::new)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showEvents, this::showLoadFailure));
    }

    private void showEvents(final LoadedEvents loaded) {
        final List<ReportHistoryEvent> events = ReportHistoryEvent.fromSavedData(
                loaded.airTags,
                loaded.appleReports,
                loaded.localReports,
                loaded.localSightings,
                System.currentTimeMillis());
        this.adapter.replaceWith(events);
        this.loadingView.setVisibility(GONE);
        this.emptyView.setVisibility(events.isEmpty() ? VISIBLE : GONE);
        this.eventsView.setVisibility(events.isEmpty() ? GONE : VISIBLE);
        this.resolvePlaces(events);
    }

    private void showLoadFailure(final Throwable error) {
        Log.e(TAG, "Could not load saved report history", error);
        final ReportHistoryEvent failure = ReportHistoryEvent.refreshFailure(
                System.currentTimeMillis(),
                this.getString(R.string.report_history_failure_body));
        this.adapter.replaceWith(List.of(failure));
        this.loadingView.setVisibility(GONE);
        this.emptyView.setVisibility(GONE);
        this.eventsView.setVisibility(VISIBLE);
    }

    private void resolvePlaces(final List<ReportHistoryEvent> events) {
        for (final ReportHistoryEvent event : events) {
            final BeaconLocationReport report = event.getReport();
            if (report == null) {
                continue;
            }
            this.disposables.add(Observable.fromCallable(() -> this.geocoder.getFromLocation(
                            report.getLatitude(), report.getLongitude(), 1))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(addresses -> {
                        if (addresses.isEmpty()) {
                            return;
                        }
                        final Address address = addresses.get(0);
                        final String line = address.getAddressLine(0);
                        if (line != null && !line.isBlank()) {
                            this.adapter.setPlaceName(
                                    event.getBeaconId(), event.getTimestampMs(), line);
                        }
                    }, error -> Log.w(TAG,
                            "Could not resolve a saved report place; keeping coordinates", error)));
        }
    }

    private void openFullHistory(final String beaconId) {
        this.startActivity(new Intent(this, HistoryViewActivity.class)
                .putExtra("beaconId", beaconId));
    }

    private void showNavigationMenu(final View anchor) {
        this.navigationPopup = FloatingNavigationMenu.show(
                anchor, FloatingNavigationMenu.Destination.REPORTS, this::openDestination);
        this.navigationMenuOpen = true;
        this.navigationPopup.setOnDismissListener(menu -> this.navigationMenuOpen = false);
    }

    private void openDestination(final FloatingNavigationMenu.Destination destination) {
        switch (destination) {
            case FAMILY:
                this.startActivity(new Intent(this, FamilyOverviewActivity.class));
                break;
            case MAP:
                this.startActivity(new Intent(this, MapsActivity.class));
                break;
            case ADD_AIR_TAG:
                this.startActivity(new Intent(this, MyDevicesListActivity.class)
                        .putExtra(MyDevicesListActivity.EXTRA_DESTINATION,
                                MyDevicesListActivity.DESTINATION_ADD_AIR_TAG));
                break;
            case SETTINGS:
                this.startActivity(new Intent(this, SettingsActivity.class));
                break;
            case REPORTS:
            default:
                break;
        }
    }

    @Override
    protected void onDestroy() {
        this.disposables.clear();
        super.onDestroy();
    }

    private static final class LoadedEvents {
        private final List<BeaconInformation> airTags;
        private final List<TaggedLocationReport> appleReports;
        private final List<TaggedLocationReport> localReports;
        private final Map<String, LastSightingData> localSightings;

        LoadedEvents(
                final List<BeaconInformation> airTags,
                final List<TaggedLocationReport> appleReports,
                final List<TaggedLocationReport> localReports,
                final Map<String, LastSightingData> localSightings) {
            this.airTags = airTags;
            this.appleReports = appleReports;
            this.localReports = localReports;
            this.localSightings = localSightings;
        }
    }
}
