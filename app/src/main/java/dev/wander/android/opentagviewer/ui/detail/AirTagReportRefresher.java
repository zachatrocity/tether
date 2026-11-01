package dev.wander.android.opentagviewer.ui.detail;

import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import dev.wander.android.opentagviewer.db.repo.BeaconRepository;
import dev.wander.android.opentagviewer.python.PythonAppleService;
import io.reactivex.rxjava3.core.Completable;

/** Production one-AirTag refresh using the same Apple report bridge and cache as Map. */
public final class AirTagReportRefresher implements AirTagReportRefresh {
    private static final int HOURS_TO_CHECK = 1;

    private final BeaconRepository beacons;

    public AirTagReportRefresher(final BeaconRepository beacons) {
        this.beacons = beacons;
    }

    @Override
    public Completable checkAgain(final BeaconInformation airTag) {
        final PythonAppleService apple = PythonAppleService.getInstance();
        if (apple == null) {
            return Completable.error(new SignedOutException());
        }
        return this.beacons.toAccessoryRequests(BeaconRepository.plistFallback(
                        airTag.getBeaconId(), airTag.getOwnedBeaconPlistRaw()))
                .flatMap(requests -> apple.getLastReports(requests, HOURS_TO_CHECK))
                .flatMap(this.beacons::storeFetchResult)
                .ignoreElements();
    }

    /** Distinguishes a missing signed-in service from a network or Apple response failure. */
    public static final class SignedOutException extends IllegalStateException {
        public SignedOutException() {
            super("No signed-in Apple report service");
        }
    }
}
