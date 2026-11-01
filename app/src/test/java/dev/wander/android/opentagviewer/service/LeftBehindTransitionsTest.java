package dev.wander.android.opentagviewer.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public class LeftBehindTransitionsTest {
    private static final long QUIET_FOR_MS = 100L;

    @Test
    public void repeatedChecksClaimOneGoneTransitionAndHearingAgainRearmsIt() {
        final LeftBehindTransitions transitions = new LeftBehindTransitions();
        assertNotNull(transitions.noteHeard("backpack", 1_000L));
        assertNull(transitions.noteHeard("backpack", 1_050L));

        assertTrue(transitions.claimGoneTransitions(
                Set.of("backpack"), 1_149L, QUIET_FOR_MS).isEmpty());
        final List<LeftBehindTransitions.GoneTransition> first = transitions.claimGoneTransitions(
                Set.of("backpack"), 1_150L, QUIET_FOR_MS);
        assertEquals(1, first.size());
        assertEquals("backpack", first.get(0).getBeaconId());
        assertEquals(1_050L, first.get(0).getLastHeardMs());
        assertTrue(transitions.claimGoneTransitions(
                Set.of("backpack"), 2_000L, QUIET_FOR_MS).isEmpty());

        assertNotNull(transitions.noteHeard("backpack", 3_000L));
        final List<LeftBehindTransitions.GoneTransition> second = transitions.claimGoneTransitions(
                Set.of("backpack"), 3_100L, QUIET_FOR_MS);
        assertEquals(1, second.size());
        assertEquals(3_000L, second.get(0).getLastHeardMs());
    }

    @Test
    public void twoEnabledAirTagsFanOutWhileDisabledAirTagsStaySilent() {
        final LeftBehindTransitions transitions = new LeftBehindTransitions();
        transitions.noteHeard("backpack", 1_000L);
        transitions.noteHeard("jacket", 1_000L);
        transitions.noteHeard("keys", 1_000L);

        final List<LeftBehindTransitions.GoneTransition> gone = transitions.claimGoneTransitions(
                Set.of("backpack", "jacket"), 1_100L, QUIET_FOR_MS);

        assertEquals(2, gone.size());
        assertEquals(Set.of("backpack", "jacket"), Set.of(
                gone.get(0).getBeaconId(), gone.get(1).getBeaconId()));
        assertTrue(transitions.claimGoneTransitions(
                Set.of("backpack", "jacket", "keys"), 1_200L, QUIET_FOR_MS).isEmpty());
    }

    @Test
    public void onlyEnabledPendingAirTagsEscalateTheScanner() {
        final LeftBehindTransitions transitions = new LeftBehindTransitions();
        transitions.noteHeard("backpack", 1_000L);

        assertTrue(transitions.shouldListenHard(
                Set.of("backpack"), 1_050L, QUIET_FOR_MS, 0.5));
        assertFalse(transitions.shouldListenHard(
                Set.of(), 1_050L, QUIET_FOR_MS, 0.5));

        transitions.claimGoneTransitions(Set.of("backpack"), 1_100L, QUIET_FOR_MS);
        assertFalse(transitions.shouldListenHard(
                Set.of("backpack"), 1_101L, QUIET_FOR_MS, 0.5));
    }
}
