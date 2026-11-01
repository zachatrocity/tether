package dev.wander.android.opentagviewer.source;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Blocking ntfy publisher. Every call performs at most one POST and must run on an I/O thread.
 * Redirects are deliberately not followed: a configured topic must receive exactly one request,
 * and credentials must not be forwarded to another origin.
 */
public final class NtfyClient implements NtfyPublisher, NtfyAlertPublisher {
    public static final String TEST_TITLE = "Tether test notification";
    public static final String TEST_PRIORITY = "default";
    public static final String TEST_MESSAGE = "Tether notifications are configured.";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    interface Configuration {
        boolean isEnabled();
        String serverUrl();
        String topic();
        String accessToken();
    }

    @FunctionalInterface
    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    public static final class ConfigurationException extends IOException {
        private final NtfySettings.ValidationError error;

        ConfigurationException(final NtfySettings.ValidationError error) {
            super("The ntfy settings are incomplete or invalid.");
            this.error = error;
        }

        public NtfySettings.ValidationError getError() {
            return this.error;
        }
    }

    /** A non-success response. It intentionally retains only the status, never the response body. */
    public static final class HttpStatusException extends IOException {
        private final int status;

        public HttpStatusException(final int status) {
            super("The notification server returned HTTP " + status + ".");
            this.status = status;
        }

        public int getStatus() {
            return this.status;
        }
    }

    private final Configuration configuration;
    private final ConnectionFactory connections;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public NtfyClient(final NtfySettings settings) {
        this(configuration(settings), url -> (HttpURLConnection) url.openConnection(),
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    /** Test seam for settings, connection behavior, and bounded timeout coverage on the JVM. */
    NtfyClient(final Configuration configuration, final ConnectionFactory connections,
               final int connectTimeoutMs, final int readTimeoutMs) {
        this.configuration = configuration;
        this.connections = connections;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    private static Configuration configuration(final NtfySettings settings) {
        return new Configuration() {
            @Override
            public boolean isEnabled() {
                return settings.isEnabled();
            }

            @Override
            public String serverUrl() {
                return settings.getServerUrl();
            }

            @Override
            public String topic() {
                return settings.getTopic();
            }

            @Override
            public String accessToken() {
                return settings.getAccessToken();
            }
        };
    }

    @Override
    public void sendTestNotification() throws IOException {
        this.publishConfigured(TEST_TITLE, TEST_PRIORITY, TEST_MESSAGE);
    }

    /**
     * Publishes an AirTag alert only when delivery is enabled and complete.
     *
     * @return true when a request was sent; false when disabled or incomplete
     */
    @Override
    public boolean publishIfEnabled(final String title, final String priority, final String message)
            throws IOException {
        if (!this.configuration.isEnabled()
                || NtfySettings.validate(this.configuration.serverUrl(), this.configuration.topic())
                != NtfySettings.ValidationError.NONE) {
            return false;
        }
        this.publishConfigured(title, priority, message);
        return true;
    }

    private void publishConfigured(final String title, final String priority, final String message)
            throws IOException {
        final NtfySettings.ValidationError validation = NtfySettings.validate(
                this.configuration.serverUrl(), this.configuration.topic());
        if (validation != NtfySettings.ValidationError.NONE) {
            throw new ConfigurationException(validation);
        }

        String base = this.configuration.serverUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        final URL target = new URL(base + "/" + encodePathSegment(this.configuration.topic().trim()));
        final byte[] body = message.getBytes(StandardCharsets.UTF_8);
        final HttpURLConnection connection = this.connections.open(target);
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(this.connectTimeoutMs);
            connection.setReadTimeout(this.readTimeoutMs);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            connection.setRequestProperty("Title", title);
            connection.setRequestProperty("Priority", priority);
            final String token = this.configuration.accessToken();
            if (token != null && !token.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token.trim());
            }
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            final int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new HttpStatusException(status);
            }
        } finally {
            connection.disconnect();
        }
    }

    /** Percent-encodes the topic as one UTF-8 path segment, including slash and percent. */
    static String encodePathSegment(final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        final char[] hex = "0123456789ABCDEF".toCharArray();
        final StringBuilder encoded = new StringBuilder(bytes.length);
        for (final byte raw : bytes) {
            final int b = raw & 0xff;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z')
                    || (b >= '0' && b <= '9') || b == '-' || b == '.' || b == '_' || b == '~') {
                encoded.append((char) b);
            } else {
                encoded.append('%').append(hex[b >>> 4]).append(hex[b & 0x0f]);
            }
        }
        return encoded.toString();
    }
}
