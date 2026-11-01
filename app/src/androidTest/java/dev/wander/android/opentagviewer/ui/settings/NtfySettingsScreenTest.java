package dev.wander.android.opentagviewer.ui.settings;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.wander.android.opentagviewer.Eventually;
import dev.wander.android.opentagviewer.NtfySettingsActivity;
import dev.wander.android.opentagviewer.R;
import dev.wander.android.opentagviewer.Shot;
import dev.wander.android.opentagviewer.source.NtfyClient;
import dev.wander.android.opentagviewer.source.NtfySettings;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class NtfySettingsScreenTest {
    private Context context;
    private ActivityScenario<NtfySettingsActivity> scenario;

    @Before
    public void openEmpty() {
        this.context = getInstrumentation().getTargetContext();
        this.clearSettings();
        NtfySettingsActivity.replacePublisherFactory(settings -> () -> { });
        this.scenario = ActivityScenario.launch(NtfySettingsActivity.class);
    }

    @After
    public void closeAndReset() {
        if (this.scenario != null) {
            this.scenario.close();
        }
        NtfySettingsActivity.resetPublisherFactory();
        this.clearSettings();
    }

    @Test
    public void deliveryIsDisabledByDefaultAndInvalidConfigurationCannotEnableIt() {
        onView(withId(R.id.ntfy_enabled)).perform(scrollTo()).check(matches(isNotChecked())).perform(click());
        onView(withText(R.string.ntfy_server_required)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withText(R.string.ntfy_enable_needs_configuration)).perform(scrollTo()).check(matches(isDisplayed()));

        onView(withId(R.id.ntfy_server_url)).perform(scrollTo(), replaceText("file:///private/topic"), closeSoftKeyboard());
        onView(withId(R.id.ntfy_topic)).perform(scrollTo(), replaceText("family"), closeSoftKeyboard());
        onView(withId(R.id.ntfy_enabled)).perform(scrollTo(), click());
        onView(withText(R.string.ntfy_server_invalid)).perform(scrollTo()).check(matches(isDisplayed()));

        onView(withId(R.id.ntfy_server_url)).perform(scrollTo(), replaceText("https://ntfy.example/proxy"), closeSoftKeyboard());
        onView(withId(R.id.ntfy_enabled)).perform(scrollTo(), click()).check(matches(isChecked()));
        assertTrue(new NtfySettings(this.context).isEnabled());

        onView(withId(R.id.ntfy_enabled)).perform(scrollTo(), click()).check(matches(isNotChecked()));
        assertFalse(new NtfySettings(this.context).isEnabled());
    }

    @Test
    public void httpAddressWarnsThatTokenAndMessageAreUnencrypted() {
        onView(withId(R.id.ntfy_server_url)).perform(scrollTo(), replaceText("http://192.168.1.4:8090"), closeSoftKeyboard());

        onView(withText(R.string.ntfy_http_warning)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    @Test
    public void savedTokenIsEncryptedButNeverPutBackIntoTheFieldAfterReload() {
        onView(withId(R.id.ntfy_server_url)).perform(scrollTo(), replaceText("https://ntfy.example"), closeSoftKeyboard());
        onView(withId(R.id.ntfy_topic)).perform(scrollTo(), replaceText("family"), closeSoftKeyboard());
        onView(withId(R.id.ntfy_access_token))
                .perform(scrollTo(), replaceText("secret-token"), closeSoftKeyboard());

        this.scenario.recreate();

        onView(withId(R.id.ntfy_access_token)).check(matches(withText("")));
        onView(withText(R.string.ntfy_token_saved_redacted)).perform(scrollTo()).check(matches(isDisplayed()));
        assertEquals("secret-token", new NtfySettings(this.context).getAccessToken());
    }

    @Test
    public void testActionShowsSendingThenSuccess() throws Exception {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch finish = new CountDownLatch(1);
        NtfySettingsActivity.replacePublisherFactory(settings -> () -> {
            started.countDown();
            try {
                if (!finish.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("test publisher was not released");
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("test publisher interrupted", e);
            }
        });
        this.enterCompleteConfiguration();

        onView(withId(R.id.ntfy_test)).perform(scrollTo(), click());
        assertTrue(started.await(5, TimeUnit.SECONDS));
        onView(withText(R.string.ntfy_test_sending)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.ntfy_test_progress)).perform(scrollTo()).check(matches(isDisplayed()));

        finish.countDown();
        Eventually.check(() -> onView(withText(R.string.ntfy_test_success))
                .perform(scrollTo()).check(matches(isDisplayed())));
    }

    @Test
    public void testFailureIsActionableAndDoesNotShowServerContent() {
        NtfySettingsActivity.replacePublisherFactory(settings -> () -> {
            throw new NtfyClient.HttpStatusException(401);
        });
        this.enterCompleteConfiguration();

        onView(withId(R.id.ntfy_test)).perform(scrollTo(), click());

        Eventually.check(() -> onView(withText(R.string.ntfy_test_failed_auth))
                .perform(scrollTo()).check(matches(isDisplayed())));
    }

    @Test
    public void interactiveControlsKeepAtLeastFortyEightDpTargets() {
        this.scenario.onActivity(activity -> {
            final int minimum = Math.round(48 * activity.getResources().getDisplayMetrics().density);
            assertTrue(activity.findViewById(R.id.ntfy_enabled).getHeight() >= minimum);
            assertTrue(activity.findViewById(R.id.ntfy_server_url).getHeight() >= minimum);
            assertTrue(activity.findViewById(R.id.ntfy_topic).getHeight() >= minimum);
            assertTrue(activity.findViewById(R.id.ntfy_access_token).getHeight() >= minimum);
            assertTrue(activity.findViewById(R.id.ntfy_test).getHeight() >= minimum);
        });
    }

    @Test
    public void controlsRemainReachableAtTwoHundredPercentText() throws Exception {
        final String originalScale = runShell("settings get system font_scale");
        try {
            runShell("settings put system font_scale 2.0");
            this.scenario.recreate();
            this.scenario.onActivity(activity -> assertTrue(
                    activity.getResources().getConfiguration().fontScale >= 1.9f));
            onView(withId(R.id.ntfy_enabled)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.ntfy_test)).perform(scrollTo()).check(matches(isDisplayed()));
        } finally {
            runShell("settings put system font_scale " + originalScale);
            this.scenario.recreate();
        }
    }

    @Test
    public void lightAndDarkSurfacesRenderForVisualReview() {
        final AtomicReference<NtfySettingsActivity> activity = new AtomicReference<>();
        this.scenario.close();
        this.scenario = null;
        try {
            this.setNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            try (ActivityScenario<NtfySettingsActivity> light =
                         ActivityScenario.launch(NtfySettingsActivity.class)) {
                light.onActivity(activity::set);
                Shot.of(activity.get(), "ntfy-settings-light");
            }

            this.setNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            try (ActivityScenario<NtfySettingsActivity> dark =
                         ActivityScenario.launch(NtfySettingsActivity.class)) {
                dark.onActivity(activity::set);
                Shot.of(activity.get(), "ntfy-settings-dark");
            }
        } finally {
            this.setNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    private void setNightMode(final int mode) {
        getInstrumentation().runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(mode));
        getInstrumentation().waitForIdleSync();
    }

    private void enterCompleteConfiguration() {
        onView(withId(R.id.ntfy_server_url)).perform(scrollTo(), replaceText("https://ntfy.example"), closeSoftKeyboard());
        onView(withId(R.id.ntfy_topic)).perform(scrollTo(), replaceText("family"), closeSoftKeyboard());
    }

    private static String runShell(final String command) throws IOException {
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                getInstrumentation().getUiAutomation().executeShellCommand(command));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[256];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name()).trim();
        }
    }

    private void clearSettings() {
        this.context.getSharedPreferences(NtfySettings.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }
}
