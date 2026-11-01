package dev.wander.android.opentagviewer.source;

import static dev.wander.android.opentagviewer.AppKeyStoreConstants.KEYSTORE_ALIAS_NTFY;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import dev.wander.android.opentagviewer.util.android.AppCryptographyUtil;

/**
 * Synchronous ntfy configuration for the background alert path.
 *
 * <p>The access token has its own keystore alias because it has a lifecycle independent of the
 * Apple session and optional tracker sources. The non-secret fields use SharedPreferences because
 * background publishing reads them synchronously on an I/O thread.
 */
public final class NtfySettings {
    private static final String TAG = NtfySettings.class.getSimpleName();
    public static final String PREFERENCES_NAME = "ntfy";
    private static final String ENABLED = "enabled";
    private static final String SERVER_URL = "server_url";
    private static final String TOPIC = "topic";
    private static final String ACCESS_TOKEN_ENCRYPTED = "access_token_encrypted";

    private final SharedPreferences prefs;
    private final AppCryptographyUtil crypto = new AppCryptographyUtil();

    public enum ValidationError {
        NONE,
        SERVER_REQUIRED,
        SERVER_INVALID,
        TOPIC_REQUIRED
    }

    public NtfySettings(final Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return this.prefs.getBoolean(ENABLED, false);
    }

    /** Enabling invalid settings is refused so background callers never inherit a partial setup. */
    public void setEnabled(final boolean enabled) {
        if (enabled && this.validationError() != ValidationError.NONE) {
            throw new IllegalStateException("ntfy cannot be enabled with incomplete settings");
        }
        this.prefs.edit().putBoolean(ENABLED, enabled).apply();
    }

    public String getServerUrl() {
        return this.prefs.getString(SERVER_URL, "");
    }

    public void setServerUrl(final String serverUrl) {
        this.prefs.edit().putString(SERVER_URL, clean(serverUrl)).apply();
    }

    public String getTopic() {
        return this.prefs.getString(TOPIC, "");
    }

    public void setTopic(final String topic) {
        this.prefs.edit().putString(TOPIC, clean(topic)).apply();
    }

    public boolean hasAccessToken() {
        return this.prefs.contains(ACCESS_TOKEN_ENCRYPTED);
    }

    /** Returns the token only to publishing code. Settings screens must use {@link #hasAccessToken()}. */
    public String getAccessToken() {
        final String stored = this.prefs.getString(ACCESS_TOKEN_ENCRYPTED, null);
        if (stored == null) {
            return "";
        }
        try {
            final byte[] decrypted = this.crypto.decrypt(
                    AppCryptographyUtil.AppEncryptedData.fromFlattened(
                            Base64.decode(stored, Base64.NO_WRAP)),
                    KEYSTORE_ALIAS_NTFY);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (final RuntimeException e) {
            Log.w(TAG, "Could not decrypt the saved ntfy token; it must be entered again", e);
            return "";
        }
    }

    public void setAccessToken(final String accessToken) {
        final String cleaned = clean(accessToken);
        if (cleaned.isEmpty()) {
            this.clearAccessToken();
            return;
        }
        final byte[] flattened = this.crypto.encrypt(
                cleaned.getBytes(StandardCharsets.UTF_8), KEYSTORE_ALIAS_NTFY).flatten();
        this.prefs.edit().putString(ACCESS_TOKEN_ENCRYPTED,
                Base64.encodeToString(flattened, Base64.NO_WRAP)).apply();
    }

    public void clearAccessToken() {
        this.prefs.edit().remove(ACCESS_TOKEN_ENCRYPTED).apply();
    }

    public ValidationError validationError() {
        return validate(this.getServerUrl(), this.getTopic());
    }

    public static ValidationError validate(final String serverUrl, final String topic) {
        final String server = clean(serverUrl);
        if (server.isEmpty()) {
            return ValidationError.SERVER_REQUIRED;
        }
        try {
            final URI uri = new URI(server);
            final String scheme = uri.getScheme() == null
                    ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || uri.getRawAuthority() == null || uri.getRawAuthority().isEmpty()
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getRawUserInfo() != null) {
                return ValidationError.SERVER_INVALID;
            }
            uri.toURL();
        } catch (final IllegalArgumentException | URISyntaxException | MalformedURLException e) {
            return ValidationError.SERVER_INVALID;
        }
        return clean(topic).isEmpty() ? ValidationError.TOPIC_REQUIRED : ValidationError.NONE;
    }

    private static String clean(final String value) {
        return value == null ? "" : value.trim();
    }
}
