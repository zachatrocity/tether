package dev.wander.android.opentagviewer;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.location.Address;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.datastore.UserAuthDataStore;
import dev.wander.android.opentagviewer.db.datastore.UserSettingsDataStore;
import dev.wander.android.opentagviewer.db.repo.BeaconRepository;
import dev.wander.android.opentagviewer.db.repo.FamilyProfileRepository;
import dev.wander.android.opentagviewer.db.repo.UserAuthRepository;
import dev.wander.android.opentagviewer.db.repo.UserSettingsRepository;
import dev.wander.android.opentagviewer.db.repo.model.FamilyProfileWithAirTags;
import dev.wander.android.opentagviewer.db.repo.model.FamilyProfilesSnapshot;
import dev.wander.android.opentagviewer.db.repo.model.LastSightingData;
import dev.wander.android.opentagviewer.db.repo.model.UserSettings;
import dev.wander.android.opentagviewer.db.room.OpenTagViewerDatabase;
import dev.wander.android.opentagviewer.db.room.entity.FamilyProfile;
import dev.wander.android.opentagviewer.python.AppDependencies;
import dev.wander.android.opentagviewer.python.icloud.ICloudFailures;
import dev.wander.android.opentagviewer.ui.compat.WindowPaddingUtil;
import dev.wander.android.opentagviewer.ui.detail.AirTagReportRefresher;
import dev.wander.android.opentagviewer.ui.family.FamilyOverviewAdapter;
import dev.wander.android.opentagviewer.ui.family.FamilyOverviewReportRefresh;
import dev.wander.android.opentagviewer.ui.family.FamilyOverviewRow;
import dev.wander.android.opentagviewer.ui.login.SignInAgain;
import dev.wander.android.opentagviewer.ui.navigation.FloatingNavigationMenu;
import dev.wander.android.opentagviewer.util.TagOrder;
import dev.wander.android.opentagviewer.util.TagVisibility;
import dev.wander.android.opentagviewer.util.android.AddressLookup;
import dev.wander.android.opentagviewer.util.android.AppCryptographyUtil;
import dev.wander.android.opentagviewer.util.parse.BeaconDataParser;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** The post-setup home: local family associations over honest, cached AirTag evidence. */
public final class FamilyOverviewActivity extends AppCompatActivity {
    private static final String TAG = FamilyOverviewActivity.class.getSimpleName();

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<BeaconInformation> assignedAirTags = new ArrayList<>();
    private FamilyOverviewAdapter adapter;
    private BeaconRepository beaconRepository;
    private FamilyProfileRepository familyRepository;
    private FamilyOverviewReportRefresh reportRefresh;
    private UserAuthRepository userAuthRepository;
    private AddressLookup geocoder;
    private FamilyProfilesSnapshot latestProfiles;
    private PopupMenu navigationPopup;
    private boolean navigationMenuOpen;

    private View loadingState;
    private TextView loadingText;
    private View rowsCard;
    private View emptyState;
    private View statusNotice;
    private TextView statusTitle;
    private TextView statusMessage;
    private View statusLoginButton;
    private SwipeRefreshLayout refreshLayout;
    private boolean assignedRefreshInFlight;
    private boolean refreshFailedWhileUpdating;

    private final ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::settingsReturned);

    private final ActivityResultLauncher<Intent> addAirTagLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                final Intent data = result.getData();
                if (data != null && data.getBooleanExtra("startImport", false)) {
                    this.startActivity(new Intent(this, MapsActivity.class)
                            .putExtra(MapsActivity.EXTRA_START_AIR_TAG_IMPORT, true));
                    return;
                }
                this.loadSavedOverview();
            });

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((OpenAirTagApplication) this.getApplication()).setupTheme();
        this.setContentView(R.layout.activity_family_overview);
        WindowPaddingUtil.insetForSystemBars(this.findViewById(R.id.family_overview_root));

        if (this.getSupportActionBar() != null) {
            this.getSupportActionBar().hide();
        }

        final OpenTagViewerDatabase database = OpenTagViewerDatabase.getInstance(
                this.getApplicationContext());
        this.beaconRepository = new BeaconRepository(database);
        this.familyRepository = new FamilyProfileRepository(database);
        this.reportRefresh = new FamilyOverviewReportRefresh(
                AppDependencies.airTagReportRefresh(this.beaconRepository));
        this.userAuthRepository = new UserAuthRepository(
                UserAuthDataStore.getInstance(this.getApplicationContext()),
                new AppCryptographyUtil());
        this.geocoder = AppDependencies.geocoder(
                this.getApplicationContext(), Locale.getDefault());

        this.loadingState = this.findViewById(R.id.family_loading_state);
        this.loadingText = this.findViewById(R.id.family_loading_text);
        this.rowsCard = this.findViewById(R.id.family_rows_card);
        this.emptyState = this.findViewById(R.id.family_empty_state);
        this.statusNotice = this.findViewById(R.id.family_status_notice);
        this.statusTitle = this.findViewById(R.id.family_status_title);
        this.statusMessage = this.findViewById(R.id.family_status_message);
        this.statusLoginButton = this.findViewById(R.id.family_status_login);
        this.refreshLayout = this.findViewById(R.id.family_refresh);
        this.refreshLayout.setOnRefreshListener(this::refreshAssignedAirTags);
        this.statusLoginButton.setOnClickListener(view -> this.openLoginFromNotice());

        this.adapter = new FamilyOverviewAdapter(this::openDetails, this::editProfileFor);
        final RecyclerView rows = this.findViewById(R.id.family_rows);
        rows.setLayoutManager(new LinearLayoutManager(this));
        rows.setAdapter(this.adapter);

        this.findViewById(R.id.family_navigation_menu)
                .setOnClickListener(this::showNavigationMenu);
        this.findViewById(R.id.family_open_map).setOnClickListener(view -> this.openMap());

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.loadSavedOverview();
    }

    @Override
    protected void onDestroy() {
        this.disposables.clear();
        super.onDestroy();
    }

    private void loadSavedOverview() {
        final boolean preservingRows = this.adapter.hasRows();
        this.statusNotice.setVisibility(GONE);
        this.loadingState.setVisibility(preservingRows ? GONE : VISIBLE);
        this.loadingText.setText(R.string.family_loading_saved_reports);

        final UserSettings settings = new UserSettingsRepository(
                UserSettingsDataStore.getInstance(this.getApplicationContext()))
                .getUserSettings();

        final Observable<List<BeaconInformation>> beacons = this.beaconRepository.getAllBeacons()
                .flatMap(BeaconDataParser::parseAsync)
                .map(all -> TagVisibility.visible(all, settings.shouldShowAppleDevices()))
                .map(TagOrder::sorted);

        this.disposables.add(Observable.zip(
                        this.familyRepository.listProfilesWithAirTags().toObservable(),
                        beacons,
                        this.beaconRepository.getLastLocationsForAll(),
                        this.beaconRepository.getLastSightingsForAll(),
                        this.userAuthRepository.getUserAuth().map(optional -> optional.isPresent()),
                        LoadedOverview::new)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showOverview, this::showLoadFailure));
    }

    private void showOverview(final LoadedOverview loaded) {
        this.latestProfiles = loaded.profiles;
        final List<FamilyOverviewRow> rows = this.buildRows(loaded);
        this.rememberAssignedAirTags(rows);
        this.adapter.replaceWith(rows);

        this.loadingState.setVisibility(GONE);
        this.rowsCard.setVisibility(rows.isEmpty() ? GONE : VISIBLE);
        this.emptyState.setVisibility(loaded.beacons.isEmpty() ? VISIBLE : GONE);

        if (this.refreshFailedWhileUpdating) {
            this.refreshFailedWhileUpdating = false;
            this.showStatusNotice(
                    R.string.family_refresh_assigned_failed_title,
                    R.string.family_refresh_assigned_failed_body);
        } else if (loaded.signedIn) {
            this.statusNotice.setVisibility(GONE);
            this.statusLoginButton.setVisibility(GONE);
        } else {
            this.showDisconnectedNotice();
        }
        this.resolvePlaces(rows);
    }

    private void showStatusNotice(final int title, final int message) {
        this.statusTitle.setText(title);
        this.statusMessage.setText(message);
        this.statusLoginButton.setVisibility(GONE);
        this.statusNotice.setVisibility(VISIBLE);
    }

    private void showDisconnectedNotice() {
        this.showStatusNotice(
                R.string.family_account_disconnected_title,
                R.string.family_account_disconnected_body);
        this.statusLoginButton.setVisibility(VISIBLE);
    }

    private void openLoginFromNotice() {
        this.statusLoginButton.setEnabled(false);
        this.disposables.add(this.userAuthRepository.clearUser()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            this.startActivity(new Intent(this, AppleLoginActivity.class));
                            this.finish();
                        },
                        error -> {
                            this.statusLoginButton.setEnabled(true);
                            Log.e(TAG, "Could not clear the unusable session before login", error);
                        }));
    }

    private void rememberAssignedAirTags(final List<FamilyOverviewRow> rows) {
        this.assignedAirTags.clear();
        for (final FamilyOverviewRow row : rows) {
            if (row.getKind() == FamilyOverviewRow.Kind.AIR_TAG
                    && row.getProfileId() != null
                    && row.getAirTag() != null) {
                this.assignedAirTags.add(row.getAirTag());
            }
        }
    }

    /**
     * A user-requested fetch for assigned AirTags only. Transient failures do not prevent later
     * AirTags from being attempted; a dead or absent session stops immediately because every
     * remaining request would fail for the same reason.
     */
    private void refreshAssignedAirTags() {
        if (this.assignedRefreshInFlight) {
            return;
        }
        if (this.assignedAirTags.isEmpty()) {
            this.refreshLayout.setRefreshing(false);
            this.showStatusNotice(
                    R.string.family_refresh_no_assigned_title,
                    R.string.family_refresh_no_assigned_body);
            return;
        }

        this.assignedRefreshInFlight = true;
        this.disposables.add(this.reportRefresh.refresh(this.assignedAirTags)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            this.assignedRefreshInFlight = false;
                            this.refreshLayout.setRefreshing(false);
                            Toast.makeText(
                                    this,
                                    R.string.family_refresh_assigned_success,
                                    Toast.LENGTH_SHORT).show();
                            this.loadSavedOverview();
                        },
                        error -> {
                            this.assignedRefreshInFlight = false;
                            this.refreshLayout.setRefreshing(false);
                            Log.w(TAG, "Could not refresh every assigned AirTag", error);
                            if (ICloudFailures.meansSignInAgain(error)) {
                                this.disposables.add(SignInAgain.from(this));
                                return;
                            }
                            if (error instanceof AirTagReportRefresher.SignedOutException) {
                                this.showDisconnectedNotice();
                                return;
                            }
                            this.refreshFailedWhileUpdating = true;
                            this.loadSavedOverview();
                        }));
    }

    private List<FamilyOverviewRow> buildRows(final LoadedOverview loaded) {
        final Map<String, FamilyProfile> profileByAirTag = new HashMap<>();
        final Set<String> profilesWithAirTags = new HashSet<>();
        for (final FamilyProfileWithAirTags profile : loaded.profiles.getProfiles()) {
            for (final String airTagId : profile.getAirTagIds()) {
                profileByAirTag.put(airTagId, profile.getProfile());
                profilesWithAirTags.add(profile.getProfile().id);
            }
        }

        final List<FamilyOverviewRow> assigned = new ArrayList<>();
        final List<FamilyOverviewRow> unassigned = new ArrayList<>();
        for (final BeaconInformation airTag : loaded.beacons) {
            final FamilyProfile profile = profileByAirTag.get(airTag.getBeaconId());
            final FamilyOverviewRow row = FamilyOverviewRow.airTag(
                    profile == null ? null : profile.id,
                    profile == null ? this.getString(R.string.family_unassigned) : profile.displayName,
                    airTag,
                    loaded.reports.get(airTag.getBeaconId()),
                    loaded.sightings.get(airTag.getBeaconId()));
            (profile == null ? unassigned : assigned).add(row);
        }

        for (final FamilyProfileWithAirTags profile : loaded.profiles.getProfiles()) {
            if (!profilesWithAirTags.contains(profile.getProfile().id)) {
                assigned.add(FamilyOverviewRow.emptyProfile(
                        profile.getProfile().id, profile.getProfile().displayName));
            }
        }

        if (!unassigned.isEmpty()) {
            assigned.add(FamilyOverviewRow.sectionHeader(
                    this.getString(R.string.family_unassigned_airtags)));
            assigned.addAll(unassigned);
        }
        return assigned;
    }

    private void resolvePlaces(final List<FamilyOverviewRow> rows) {
        for (final FamilyOverviewRow row : rows) {
            final BeaconLocationReport report = row.getReport();
            final BeaconInformation airTag = row.getAirTag();
            if (report == null || airTag == null) {
                continue;
            }
            this.disposables.add(Observable.fromCallable(() -> this.geocoder.getFromLocation(
                            report.getLatitude(), report.getLongitude(), 1))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(addresses -> {
                        if (!addresses.isEmpty()) {
                            final Address address = addresses.get(0);
                            final String line = address.getAddressLine(0);
                            if (line != null && !line.isBlank()) {
                                this.adapter.setPlaceName(airTag.getBeaconId(), line);
                            }
                        }
                    }, error -> {
                        Log.w(TAG, "Could not resolve a saved AirTag place", error);
                        this.showStatusNotice(
                                R.string.family_saved_reports_title,
                                R.string.family_place_refresh_failed);
                    }));
        }
    }

    private void showLoadFailure(final Throwable error) {
        Log.e(TAG, "Could not load Family Overview", error);
        this.loadingState.setVisibility(GONE);
        this.refreshFailedWhileUpdating = false;
        this.showStatusNotice(
                R.string.family_saved_reports_title,
                R.string.family_refresh_failed);
        if (!this.adapter.hasRows()) {
            this.rowsCard.setVisibility(GONE);
            this.emptyState.setVisibility(VISIBLE);
        }
    }

    private void openDetails(final String beaconId) {
        this.startActivity(new Intent(this, DeviceInfoActivity.class)
                .putExtra("beaconId", beaconId));
    }

    private void editProfileFor(final FamilyOverviewRow row) {
        if (row.getProfileId() == null) {
            this.assignToProfile(row);
            return;
        }
        this.promptForProfileName(row.getProfileId(), row.getPersonName(), null);
    }

    private void assignToProfile(final FamilyOverviewRow row) {
        final BeaconInformation airTag = row.getAirTag();
        if (airTag == null) {
            return;
        }
        final List<FamilyProfileWithAirTags> profiles = this.latestProfiles == null
                ? List.of() : this.latestProfiles.getProfiles();
        final String[] names = new String[profiles.size() + 1];
        for (int index = 0; index < profiles.size(); index++) {
            names[index] = profiles.get(index).getProfile().displayName;
        }
        names[names.length - 1] = this.getString(R.string.family_create_profile);

        new MaterialAlertDialogBuilder(this)
                .setTitle(this.getString(R.string.family_assign_airtag_title, airTag.getName()))
                .setItems(names, (dialog, which) -> {
                    if (which == profiles.size()) {
                        this.promptForProfileName(null, "", airTag.getBeaconId());
                        return;
                    }
                    this.assignAirTag(
                            airTag.getBeaconId(), profiles.get(which).getProfile().id);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptForProfileName(
            final String profileId,
            final String currentName,
            final String airTagToAssign) {
        final EditText input = new EditText(this);
        input.setHint(R.string.family_profile_name_hint);
        input.setSingleLine(true);
        input.setText(currentName);
        input.setSelectAllOnFocus(true);
        final int horizontalPadding = Math.round(24 * this.getResources().getDisplayMetrics().density);
        input.setPadding(horizontalPadding, input.getPaddingTop(),
                horizontalPadding, input.getPaddingBottom());

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(profileId == null
                        ? R.string.family_new_profile_title : R.string.family_rename_profile_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    final String name = input.getText() == null
                            ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        input.setError(this.getString(R.string.family_profile_name_required));
                        return;
                    }
                    dialog.dismiss();
                    this.saveProfileName(profileId, name, airTagToAssign);
                }));
        dialog.show();
    }

    private void saveProfileName(
            final String profileId,
            final String name,
            final String airTagToAssign) {
        if (profileId != null) {
            this.disposables.add(this.familyRepository.renameProfile(profileId, name)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::loadSavedOverview, this::profileWriteFailed));
            return;
        }
        this.disposables.add(this.familyRepository.createProfile(name)
                .flatMapCompletable(created -> airTagToAssign == null
                        ? Completable.complete()
                        : this.familyRepository.assignAirTag(airTagToAssign, created.id))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::loadSavedOverview, this::profileWriteFailed));
    }

    private void assignAirTag(final String airTagId, final String profileId) {
        this.disposables.add(this.familyRepository.assignAirTag(airTagId, profileId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::loadSavedOverview, this::profileWriteFailed));
    }

    private void profileWriteFailed(final Throwable error) {
        Log.e(TAG, "Could not update local family profile", error);
        Toast.makeText(this, R.string.family_profile_save_failed, Toast.LENGTH_LONG).show();
    }

    private void showNavigationMenu(final View anchor) {
        this.navigationPopup = FloatingNavigationMenu.show(
                anchor, FloatingNavigationMenu.Destination.FAMILY, this::openDestination);
        this.navigationMenuOpen = true;
        this.navigationPopup.setOnDismissListener(menu -> this.navigationMenuOpen = false);
    }

    private void openDestination(final FloatingNavigationMenu.Destination destination) {
        switch (destination) {
            case MAP:
                this.openMap();
                break;
            case REPORTS:
                this.openReports();
                break;
            case ADD_AIR_TAG:
                this.openAddAirTag();
                break;
            case SETTINGS:
                this.settingsLauncher.launch(new Intent(this, SettingsActivity.class));
                break;
            case FAMILY:
            default:
                break;
        }
    }

    private void openMap() {
        this.startActivity(new Intent(this, MapsActivity.class));
    }

    private void openReports() {
        this.startActivity(new Intent(this, ReportHistoryActivity.class));
    }

    private void openAddAirTag() {
        this.addAirTagLauncher.launch(new Intent(this, MyDevicesListActivity.class)
                .putExtra(MyDevicesListActivity.EXTRA_DESTINATION,
                        MyDevicesListActivity.DESTINATION_ADD_AIR_TAG));
    }

    private void settingsReturned(final ActivityResult result) {
        final Intent data = result.getData();
        if (data != null && data.getBooleanExtra("requestSendToLogin", false)) {
            this.finish();
            this.startActivity(new Intent(this, AppleLoginActivity.class));
            return;
        }
        this.loadSavedOverview();
    }

    private static final class LoadedOverview {
        private final FamilyProfilesSnapshot profiles;
        private final List<BeaconInformation> beacons;
        private final Map<String, BeaconLocationReport> reports;
        private final Map<String, LastSightingData> sightings;
        private final boolean signedIn;

        LoadedOverview(
                final FamilyProfilesSnapshot profiles,
                final List<BeaconInformation> beacons,
                final Map<String, BeaconLocationReport> reports,
                final Map<String, LastSightingData> sightings,
                final boolean signedIn) {
            this.profiles = profiles;
            this.beacons = beacons;
            this.reports = reports;
            this.sightings = sightings;
            this.signedIn = signedIn;
        }
    }
}
