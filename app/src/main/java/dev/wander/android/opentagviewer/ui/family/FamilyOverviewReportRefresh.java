package dev.wander.android.opentagviewer.ui.family;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import dev.wander.android.opentagviewer.python.icloud.ICloudFailures;
import dev.wander.android.opentagviewer.ui.detail.AirTagReportRefresh;
import dev.wander.android.opentagviewer.ui.detail.AirTagReportRefresher;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;

/** Refreshes a Family Overview snapshot without one unavailable AirTag hiding the rest. */
public final class FamilyOverviewReportRefresh {

    private final AirTagReportRefresh oneAirTag;

    public FamilyOverviewReportRefresh(final AirTagReportRefresh oneAirTag) {
        this.oneAirTag = oneAirTag;
    }

    /**
     * Refreshes each assigned AirTag in order.
     *
     * <p>Transient failures are remembered while the remaining AirTags are attempted, then
     * returned so the screen can keep saved reports visible and explain the partial failure.
     * A dead or absent session ends the batch immediately because every later request would fail
     * for the same reason and the remedy is user action, not another request.
     */
    public Completable refresh(final List<BeaconInformation> assignedAirTags) {
        final AtomicReference<Throwable> firstTransientFailure = new AtomicReference<>();
        return Observable.fromIterable(assignedAirTags)
                .concatMapCompletable(airTag -> this.oneAirTag.checkAgain(airTag)
                        .onErrorResumeNext(error -> {
                            if (error instanceof AirTagReportRefresher.SignedOutException
                                    || ICloudFailures.meansSignInAgain(error)) {
                                return Completable.error(error);
                            }
                            firstTransientFailure.compareAndSet(null, error);
                            return Completable.complete();
                        }))
                .andThen(Completable.defer(() -> {
                    final Throwable failure = firstTransientFailure.get();
                    return failure == null
                            ? Completable.complete()
                            : Completable.error(failure);
                }));
    }
}
