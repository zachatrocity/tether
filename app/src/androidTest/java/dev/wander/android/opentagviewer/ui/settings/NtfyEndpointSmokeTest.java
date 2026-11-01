package dev.wander.android.opentagviewer.ui.settings;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.wander.android.opentagviewer.Eventually;
import dev.wander.android.opentagviewer.NtfySettingsActivity;
import dev.wander.android.opentagviewer.R;
import dev.wander.android.opentagviewer.source.NtfyClient;
import dev.wander.android.opentagviewer.source.NtfySettings;

/** The installed screen and real HttpURLConnection client against a disposable ntfy endpoint. */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class NtfyEndpointSmokeTest {
    private Context context;
    private LocalEndpoint endpoint;
    private ActivityScenario<NtfySettingsActivity> scenario;

    @Before
    public void openScreenAndEndpoint() throws Exception {
        this.context = getInstrumentation().getTargetContext();
        this.context.getSharedPreferences(NtfySettings.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        NtfySettingsActivity.resetPublisherFactory();
        this.endpoint = new LocalEndpoint();
        this.scenario = ActivityScenario.launch(NtfySettingsActivity.class);
    }

    @After
    public void closeEverything() throws Exception {
        if (this.scenario != null) {
            this.scenario.close();
        }
        if (this.endpoint != null) {
            this.endpoint.close();
        }
        this.context.getSharedPreferences(NtfySettings.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        NtfySettingsActivity.resetPublisherFactory();
    }

    @Test
    public void realScreenSendsOneGenericTestToTheExactConfiguredTopic() throws Exception {
        onView(withId(R.id.ntfy_server_url))
                .perform(replaceText(this.endpoint.url() + "/reverse/proxy/"));
        onView(withId(R.id.ntfy_topic)).perform(replaceText("family / café"));
        onView(withId(R.id.ntfy_access_token))
                .perform(replaceText("secret-token"), closeSoftKeyboard());

        onView(withId(R.id.ntfy_test)).perform(scrollTo(), click());

        Eventually.check(() -> onView(withText(R.string.ntfy_test_success))
                .check(matches(isDisplayed())));
        assertTrue(this.endpoint.requestArrived.await(5, TimeUnit.SECONDS));
        assertNull(this.endpoint.failure);
        assertEquals(1, this.endpoint.requestCount);
        assertEquals("POST", this.endpoint.method);
        assertEquals("/reverse/proxy/family%20%2F%20caf%C3%A9", this.endpoint.target);
        assertEquals("Bearer secret-token", this.endpoint.headers.get("authorization"));
        assertEquals(NtfyClient.TEST_TITLE, this.endpoint.headers.get("title"));
        assertEquals(NtfyClient.TEST_PRIORITY, this.endpoint.headers.get("priority"));
        assertEquals(NtfyClient.TEST_MESSAGE, this.endpoint.body);
    }

    private static final class LocalEndpoint implements Closeable {
        private final ServerSocket socket;
        private final CountDownLatch requestArrived = new CountDownLatch(1);
        private volatile IOException failure;
        private volatile int requestCount;
        private volatile String method;
        private volatile String target;
        private volatile Map<String, String> headers;
        private volatile String body;

        LocalEndpoint() throws IOException {
            this.socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            final Thread server = new Thread(this::serve, "LocalNtfyEndpoint");
            server.setDaemon(true);
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + this.socket.getLocalPort();
        }

        private void serve() {
            try (Socket client = this.socket.accept()) {
                final InputStream input = client.getInputStream();
                final String[] head = readHead(input).split("\r\n");
                final String[] requestLine = head[0].split(" ");
                final Map<String, String> receivedHeaders = new HashMap<>();
                for (int i = 1; i < head.length; i++) {
                    final int colon = head[i].indexOf(':');
                    if (colon > 0) {
                        receivedHeaders.put(head[i].substring(0, colon).trim()
                                        .toLowerCase(Locale.ROOT),
                                head[i].substring(colon + 1).trim());
                    }
                }
                final int length = receivedHeaders.containsKey("content-length")
                        ? Integer.parseInt(receivedHeaders.get("content-length")) : 0;
                final byte[] payload = new byte[length];
                int offset = 0;
                while (offset < payload.length) {
                    final int read = input.read(payload, offset, payload.length - offset);
                    if (read < 0) {
                        throw new IOException("request body ended early");
                    }
                    offset += read;
                }
                this.requestCount++;
                this.method = requestLine[0];
                this.target = requestLine[1];
                this.headers = receivedHeaders;
                this.body = new String(payload, StandardCharsets.UTF_8);

                final OutputStream output = client.getOutputStream();
                output.write(("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n"
                        + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
            } catch (final IOException e) {
                if (!this.socket.isClosed()) {
                    this.failure = e;
                }
            } finally {
                this.requestArrived.countDown();
            }
        }

        private static String readHead(final InputStream input) throws IOException {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final byte[] end = {'\r', '\n', '\r', '\n'};
            int matched = 0;
            int value;
            while (matched < end.length && (value = input.read()) != -1) {
                buffer.write(value);
                matched = value == end[matched] ? matched + 1 : (value == '\r' ? 1 : 0);
            }
            final String head = buffer.toString(StandardCharsets.UTF_8.name());
            return head.substring(0, Math.max(0, head.length() - end.length));
        }

        @Override
        public void close() throws IOException {
            this.socket.close();
        }
    }
}
