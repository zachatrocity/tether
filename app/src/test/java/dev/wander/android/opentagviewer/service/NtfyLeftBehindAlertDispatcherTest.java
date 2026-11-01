package dev.wander.android.opentagviewer.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class NtfyLeftBehindAlertDispatcherTest {
    @Test
    public void dispatchReturnsBeforeIoAndPublishesOnePrivacyBoundedMessage() {
        final List<Runnable> queued = new ArrayList<>();
        final AtomicInteger requests = new AtomicInteger();
        final AtomicReference<String> title = new AtomicReference<>();
        final AtomicReference<String> priority = new AtomicReference<>();
        final AtomicReference<String> body = new AtomicReference<>();
        final NtfyLeftBehindAlertDispatcher dispatcher = new NtfyLeftBehindAlertDispatcher(
                queued::add, (sentTitle, sentPriority, sentBody) -> {
                    requests.incrementAndGet();
                    title.set(sentTitle);
                    priority.set(sentPriority);
                    body.set(sentBody);
                    return true;
                });

        dispatcher.dispatch("Backpack AirTag", "5 minutes ago");

        assertEquals(0, requests.get());
        assertEquals(1, queued.size());
        queued.get(0).run();
        assertEquals(1, requests.get());
        assertEquals("AirTag may have been left behind", title.get());
        assertEquals("high", priority.get());
        assertEquals("Backpack AirTag may have been left behind. Last heard: 5 minutes ago.",
                body.get());
        assertFalse(body.get().contains("47.6205"));
        assertFalse(body.get().contains("School"));
        assertFalse(body.get().contains("profile"));
        assertFalse(body.get().contains("person"));
        assertFalse(body.get().contains("account"));
        assertFalse(body.get().contains("live location"));
    }

    @Test
    public void disabledPublishingIsANoOpAndDoesNotQueueRetries() {
        final AtomicInteger attempts = new AtomicInteger();
        final NtfyLeftBehindAlertDispatcher dispatcher = new NtfyLeftBehindAlertDispatcher(
                Runnable::run, (title, priority, body) -> {
                    attempts.incrementAndGet();
                    return false;
                });

        dispatcher.dispatch("Backpack AirTag", "5 minutes ago");

        assertEquals(1, attempts.get());
    }

    @Test
    public void publisherFailureCannotEscapeOrPreventASeparateAirTagAlert() {
        final AtomicInteger attempts = new AtomicInteger();
        final NtfyLeftBehindAlertDispatcher dispatcher = new NtfyLeftBehindAlertDispatcher(
                Runnable::run, (title, priority, body) -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new SocketTimeoutException("private endpoint detail");
                    }
                    return true;
                });

        dispatcher.dispatch("Backpack AirTag", "5 minutes ago");
        dispatcher.dispatch("Jacket AirTag", "6 minutes ago");

        assertEquals(2, attempts.get());
    }
}
