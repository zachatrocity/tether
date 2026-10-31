package dev.wander.android.opentagviewer.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;

public class NtfyClientTest {
    private FakeHttpServer server;
    private final Config config = new Config();

    @Before
    public void startServer() throws IOException {
        this.server = new FakeHttpServer();
        this.config.serverUrl = this.server.url();
        this.config.topic = "family-alerts";
    }

    @After
    public void stopServer() throws IOException {
        this.server.close();
    }

    @Test
    public void testNotificationPostsExpectedHeadersBodyAndBearerTokenOnce() throws Exception {
        this.server.answer("/family-alerts", 204, "response content must be ignored");
        this.config.token = "secret-token";

        this.client().sendTestNotification();

        assertEquals(1, this.server.requests().size());
        final FakeHttpServer.Request request = this.server.lastRequest();
        assertEquals("POST", request.method);
        assertEquals("/family-alerts", request.target);
        assertEquals("text/plain; charset=utf-8", request.headers.get("content-type"));
        assertEquals(NtfyClient.TEST_TITLE, request.headers.get("title"));
        assertEquals(NtfyClient.TEST_PRIORITY, request.headers.get("priority"));
        assertEquals("Bearer secret-token", request.headers.get("authorization"));
        assertEquals(NtfyClient.TEST_MESSAGE, request.body);
    }

    @Test
    public void topicIsOneEncodedSegmentAndReverseProxyPathSurvivesTrailingSlashes() throws Exception {
        this.config.serverUrl = this.server.url() + "/reverse/proxy///";
        this.config.topic = "family / café";
        this.server.answer("/reverse/proxy/family%20%2F%20caf%C3%A9", 200, "ok");

        assertTrue(this.client().publishIfEnabled("Alert", "high", "Café — ready"));

        final FakeHttpServer.Request request = this.server.lastRequest();
        assertEquals("/reverse/proxy/family%20%2F%20caf%C3%A9", request.target);
        assertEquals("Café — ready", request.body);
        assertEquals("high", request.headers.get("priority"));
    }

    @Test
    public void emptyTokenSendsNoAuthorizationHeader() throws Exception {
        this.server.answer("/family-alerts", 200, "ok");

        this.client().sendTestNotification();

        assertNull(this.server.lastRequest().headers.get("authorization"));
    }

    @Test
    public void nonSuccessContainsStatusOnlyAndDoesNotFollowRedirects() {
        for (final int status : new int[]{302, 401, 403, 404, 429, 500, 503}) {
            this.server.answer("/family-alerts", status, "private server response " + status);

            final NtfyClient.HttpStatusException error = assertThrows(
                    NtfyClient.HttpStatusException.class, () -> this.client().sendTestNotification());

            assertEquals(status, error.getStatus());
            assertEquals("The notification server returned HTTP " + status + ".", error.getMessage());
            assertFalse(error.getMessage().contains("private server response"));
        }
        assertEquals(7, this.server.requests().size());
    }

    @Test
    public void readTimeoutIsBounded() {
        this.server.answerAfter("/family-alerts", 200, "late", 500);

        assertThrows(SocketTimeoutException.class, () -> this.client(100, 100).sendTestNotification());
    }

    @Test
    public void disabledOrIncompleteBackgroundPublishingMakesNoRequest() throws Exception {
        this.config.enabled = false;
        assertFalse(this.client().publishIfEnabled("Alert", "default", "body"));

        this.config.enabled = true;
        this.config.topic = "";
        assertFalse(this.client().publishIfEnabled("Alert", "default", "body"));

        assertTrue(this.server.requests().isEmpty());
    }

    @Test
    public void malformedOrNonHttpServerIsRejectedBeforeOpeningAConnection() {
        this.config.serverUrl = "file:///private/topic";

        final NtfyClient.ConfigurationException error = assertThrows(
                NtfyClient.ConfigurationException.class, () -> this.client().sendTestNotification());

        assertEquals(NtfySettings.ValidationError.SERVER_INVALID, error.getError());
        assertTrue(this.server.requests().isEmpty());
    }

    private NtfyClient client() {
        return this.client(10_000, 10_000);
    }

    private NtfyClient client(final int connectTimeoutMs, final int readTimeoutMs) {
        return new NtfyClient(this.config,
                url -> (HttpURLConnection) url.openConnection(), connectTimeoutMs, readTimeoutMs);
    }

    private static final class Config implements NtfyClient.Configuration {
        boolean enabled = true;
        String serverUrl;
        String topic;
        String token = "";

        @Override
        public boolean isEnabled() {
            return this.enabled;
        }

        @Override
        public String serverUrl() {
            return this.serverUrl;
        }

        @Override
        public String topic() {
            return this.topic;
        }

        @Override
        public String accessToken() {
            return this.token;
        }
    }
}
