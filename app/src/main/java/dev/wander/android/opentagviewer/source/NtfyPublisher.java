package dev.wander.android.opentagviewer.source;

import java.io.IOException;

/** A publisher used by the ntfy Settings test action. */
@FunctionalInterface
public interface NtfyPublisher {
    void sendTestNotification() throws IOException;
}
