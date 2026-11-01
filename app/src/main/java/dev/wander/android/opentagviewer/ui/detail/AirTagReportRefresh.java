package dev.wander.android.opentagviewer.ui.detail;

import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import io.reactivex.rxjava3.core.Completable;

/** Checks Apple for one AirTag and persists any returned network reports. */
public interface AirTagReportRefresh {
    Completable checkAgain(BeaconInformation airTag);
}
