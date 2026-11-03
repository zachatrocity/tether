package dev.wander.android.opentagviewer.ui.family;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import dev.wander.android.opentagviewer.python.icloud.ICloudException;
import dev.wander.android.opentagviewer.python.icloud.ICloudFailure;
import io.reactivex.rxjava3.core.Completable;

public class FamilyOverviewReportRefreshTest {

    @Test
    public void atransientFailureDoesNotHideTheOtherAssignedAirTags() {
        final List<String> attempted = new ArrayList<>();
        final FamilyOverviewReportRefresh refresh = new FamilyOverviewReportRefresh(airTag ->
                Completable.defer(() -> {
                    attempted.add(airTag.getBeaconId());
                    if ("one".equals(airTag.getBeaconId())) {
                        return Completable.error(new IOException("temporarily unavailable"));
                    }
                    return Completable.complete();
                }));

        refresh.refresh(List.of(airTag("one"), airTag("two")))
                .test()
                .assertError(IOException.class);

        assertEquals(List.of("one", "two"), attempted);
    }

    @Test
    public void adeadSessionStopsRequestsThatWouldFailIdentically() {
        final List<String> attempted = new ArrayList<>();
        final FamilyOverviewReportRefresh refresh = new FamilyOverviewReportRefresh(airTag ->
                Completable.defer(() -> {
                    attempted.add(airTag.getBeaconId());
                    return Completable.error(new ICloudException(
                            ICloudFailure.CREDENTIALS_REJECTED, "refused"));
                }));

        refresh.refresh(List.of(airTag("one"), airTag("two")))
                .test()
                .assertError(ICloudException.class);

        assertEquals(List.of("one"), attempted);
    }

    private static BeaconInformation airTag(final String id) {
        return BeaconInformation.builder()
                .beaconId(id)
                .originalName(id)
                .build();
    }
}
