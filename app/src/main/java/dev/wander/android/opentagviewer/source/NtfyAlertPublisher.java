package dev.wander.android.opentagviewer.source;

import java.io.IOException;

/** Blocking publisher for one optional ntfy AirTag alert. */
@FunctionalInterface
public interface NtfyAlertPublisher {
    /**
     * Publishes one alert when ntfy is enabled and completely configured.
     *
     * @return true when a request was sent; false when delivery is disabled or incomplete
     */
    boolean publishIfEnabled(String title, String priority, String message) throws IOException;
}
