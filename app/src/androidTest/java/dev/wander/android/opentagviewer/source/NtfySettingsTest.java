package dev.wander.android.opentagviewer.source;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static dev.wander.android.opentagviewer.AppKeyStoreConstants.KEYSTORE_ALIAS_NTFY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyStore;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class NtfySettingsTest {
    private Context context;
    private SharedPreferences raw;

    @Before
    public void startEmpty() throws Exception {
        this.context = getInstrumentation().getTargetContext();
        this.raw = this.context.getSharedPreferences(NtfySettings.PREFERENCES_NAME, Context.MODE_PRIVATE);
        this.raw.edit().clear().commit();
        this.deleteNtfyKey();
    }

    @After
    public void leaveNothingBehind() throws Exception {
        this.raw.edit().clear().commit();
        this.deleteNtfyKey();
    }

    @Test
    public void freshAndUpgradedInstallsStartDisabled() {
        final NtfySettings settings = new NtfySettings(this.context);
        assertFalse(settings.isEnabled());

        settings.setServerUrl("https://ntfy.example/proxy");
        settings.setTopic("family");

        assertFalse(new NtfySettings(this.context).isEnabled());
    }

    @Test
    public void tokenReadsBackButUsesItsOwnKeystoreAliasAndNeverPlaintextPreferences()
            throws Exception {
        final NtfySettings settings = new NtfySettings(this.context);
        settings.setAccessToken("top-secret-token");

        assertEquals("top-secret-token", new NtfySettings(this.context).getAccessToken());
        assertTrue(settings.hasAccessToken());
        for (final Object stored : this.raw.getAll().values()) {
            assertFalse(String.valueOf(stored).contains("top-secret-token"));
        }

        final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        assertTrue(Collections.list(keyStore.aliases()).contains(KEYSTORE_ALIAS_NTFY));
    }

    @Test
    public void clearingTokenRemovesAllEncryptedMaterial() {
        final NtfySettings settings = new NtfySettings(this.context);
        settings.setAccessToken("top-secret-token");
        assertFalse(this.raw.getAll().isEmpty());

        settings.clearAccessToken();

        assertFalse(settings.hasAccessToken());
        assertEquals("", settings.getAccessToken());
        assertTrue(this.raw.getAll().isEmpty());
    }

    @Test
    public void onlyCompleteHttpOrHttpsConfigurationCanBeEnabled() {
        final NtfySettings settings = new NtfySettings(this.context);
        assertThrows(IllegalStateException.class, () -> settings.setEnabled(true));

        settings.setServerUrl("file:///private/topic");
        settings.setTopic("family");
        assertEquals(NtfySettings.ValidationError.SERVER_INVALID, settings.validationError());
        assertThrows(IllegalStateException.class, () -> settings.setEnabled(true));

        settings.setServerUrl("http://192.168.1.4:8090/reverse-proxy");
        settings.setEnabled(true);

        assertTrue(new NtfySettings(this.context).isEnabled());
    }

    private void deleteNtfyKey() throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEYSTORE_ALIAS_NTFY)) {
            keyStore.deleteEntry(KEYSTORE_ALIAS_NTFY);
        }
    }
}
