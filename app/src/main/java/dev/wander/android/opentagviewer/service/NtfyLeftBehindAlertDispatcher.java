package dev.wander.android.opentagviewer.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;

import dev.wander.android.opentagviewer.R;
import dev.wander.android.opentagviewer.source.NtfyAlertPublisher;
import dev.wander.android.opentagviewer.source.NtfyClient;
import dev.wander.android.opentagviewer.source.NtfySettings;

/**
 * Schedules optional ntfy delivery without making the foreground scan or local alert wait.
 *
 * <p>Failures stay at this boundary. Logs deliberately contain only a result category and, for
 * HTTP failures, a status code; publisher exceptions can include private endpoint details.</p>
 */
public final class NtfyLeftBehindAlertDispatcher implements AutoCloseable {
    static final String TITLE = "AirTag may have been left behind";
    static final String PRIORITY = "high";

    private static final String TAG = NtfyLeftBehindAlertDispatcher.class.getSimpleName();

    private final Executor executor;
    private final NtfyAlertPublisher publisher;
    private final ExecutorService ownedExecutor;
    private final String title;
    private final BiFunction<String, String, String> messages;

    public NtfyLeftBehindAlertDispatcher(final Context context) {
        final Context appContext = context.getApplicationContext();
        this.ownedExecutor = Executors.newSingleThreadExecutor();
        this.executor = this.ownedExecutor;
        this.publisher = new NtfyClient(new NtfySettings(appContext));
        this.title = appContext.getString(R.string.ntfy_left_behind_title);
        this.messages = (label, age) ->
                appContext.getString(R.string.ntfy_left_behind_message, label, age);
    }

    @VisibleForTesting
    NtfyLeftBehindAlertDispatcher(final Executor executor,
                                  final NtfyAlertPublisher publisher) {
        this.executor = Objects.requireNonNull(executor);
        this.publisher = Objects.requireNonNull(publisher);
        this.ownedExecutor = null;
        this.title = TITLE;
        this.messages = NtfyLeftBehindAlertDispatcher::message;
    }

    /** Queues at most one publish attempt for the supplied left-behind transition. */
    public void dispatch(final String airTagLabel, final String relativeLastHeardAge) {
        try {
            this.executor.execute(() -> this.publish(airTagLabel, relativeLastHeardAge));
        } catch (final RejectedExecutionException rejected) {
            Log.w(TAG, "ntfy left-behind delivery failed: scheduling");
        } catch (final RuntimeException failure) {
            Log.w(TAG, "ntfy left-behind delivery failed: dispatcher");
        }
    }

    private void publish(final String airTagLabel, final String relativeLastHeardAge) {
        try {
            final String message = this.messages.apply(airTagLabel, relativeLastHeardAge);
            if (this.publisher.publishIfEnabled(this.title, PRIORITY, message)) {
                Log.i(TAG, "ntfy left-behind delivery succeeded");
            }
        } catch (final NtfyClient.HttpStatusException failure) {
            Log.w(TAG, "ntfy left-behind delivery failed: HTTP " + failure.getStatus());
        } catch (final SocketTimeoutException failure) {
            Log.w(TAG, "ntfy left-behind delivery failed: timeout");
        } catch (final UnknownHostException | ConnectException failure) {
            Log.w(TAG, "ntfy left-behind delivery failed: connection");
        } catch (final IOException failure) {
            Log.w(TAG, "ntfy left-behind delivery failed: I/O");
        } catch (final RuntimeException failure) {
            Log.w(TAG, "ntfy left-behind delivery failed: publisher");
        }
    }

    @VisibleForTesting
    static String message(final String airTagLabel, final String relativeLastHeardAge) {
        return airTagLabel + " may have been left behind. Last heard: "
                + relativeLastHeardAge + ".";
    }

    /** Finishes already accepted requests but rejects new ones after service teardown. */
    @Override
    public void close() {
        if (this.ownedExecutor != null) {
            this.ownedExecutor.shutdown();
        }
    }
}
