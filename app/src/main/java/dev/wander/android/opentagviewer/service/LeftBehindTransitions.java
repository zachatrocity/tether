package dev.wander.android.opentagviewer.service;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory heard/gone transitions for the current foreground-service lifetime.
 *
 * <p>A restart intentionally starts empty: old radio state must not create a new departure.
 * Claiming a gone transition is atomic per AirTag, so repeated check ticks cannot duplicate an
 * alert. Hearing that AirTag again replaces the gone state and re-arms a later transition.</p>
 */
final class LeftBehindTransitions {
    static final class Arrival {
        private volatile long lastHeardMs;
        private volatile Double appearedLatitude;
        private volatile Double appearedLongitude;
        private volatile boolean gone;

        private Arrival(final long lastHeardMs) {
            this.lastHeardMs = lastHeardMs;
        }

        void setAppearedAt(final double latitude, final double longitude) {
            this.appearedLatitude = latitude;
            this.appearedLongitude = longitude;
        }
    }

    static final class GoneTransition {
        private final String beaconId;
        private final long lastHeardMs;
        private final Double appearedLatitude;
        private final Double appearedLongitude;

        private GoneTransition(final String beaconId, final Arrival state) {
            this.beaconId = beaconId;
            this.lastHeardMs = state.lastHeardMs;
            this.appearedLatitude = state.appearedLatitude;
            this.appearedLongitude = state.appearedLongitude;
        }

        String getBeaconId() {
            return this.beaconId;
        }

        long getLastHeardMs() {
            return this.lastHeardMs;
        }

        @Nullable
        Double getAppearedLatitude() {
            return this.appearedLatitude;
        }

        @Nullable
        Double getAppearedLongitude() {
            return this.appearedLongitude;
        }
    }

    private final Map<String, Arrival> presence = new ConcurrentHashMap<>();

    /**
     * Records a radio sighting.
     *
     * @return the newly arrived state when this is a first sighting or return; null otherwise
     */
    @Nullable
    Arrival noteHeard(final String beaconId, final long nowMs) {
        final Arrival active = this.presence.get(beaconId);
        if (active != null) {
            synchronized (active) {
                if (!active.gone) {
                    active.lastHeardMs = nowMs;
                    return null;
                }
            }
        }

        final Arrival arrived = new Arrival(nowMs);
        final Arrival result = this.presence.compute(beaconId, (id, existing) -> {
            if (existing != null) {
                synchronized (existing) {
                    if (!existing.gone) {
                        existing.lastHeardMs = nowMs;
                        return existing;
                    }
                }
            }
            return arrived;
        });
        return result == arrived ? arrived : null;
    }

    /** Atomically marks and returns each newly gone AirTag whose alert is enabled. */
    List<GoneTransition> claimGoneTransitions(final Set<String> alertsOn, final long nowMs,
                                               final long quietForMs) {
        final List<GoneTransition> transitions = new ArrayList<>();
        for (final String beaconId : this.presence.keySet()) {
            this.presence.computeIfPresent(beaconId, (id, state) -> {
                synchronized (state) {
                    if (state.gone || nowMs - state.lastHeardMs < quietForMs) {
                        return state;
                    }
                    state.gone = true;
                    if (alertsOn.contains(id)) {
                        transitions.add(new GoneTransition(id, state));
                    }
                    return state;
                }
            });
        }
        return transitions;
    }

    /** Whether any enabled AirTag is quiet enough to justify high-rate scanning. */
    boolean shouldListenHard(final Set<String> alertsOn, final long nowMs,
                             final long quietForMs, final double threshold) {
        final long escalateAfter = (long) (quietForMs * threshold);
        for (final Map.Entry<String, Arrival> entry : this.presence.entrySet()) {
            final Arrival state = entry.getValue();
            if (alertsOn.contains(entry.getKey()) && !state.gone
                    && nowMs - state.lastHeardMs >= escalateAfter) {
                return true;
            }
        }
        return false;
    }
}
