package dev.wander.android.opentagviewer;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import javax.net.ssl.SSLException;

import dev.wander.android.opentagviewer.databinding.ActivityNtfySettingsBinding;
import dev.wander.android.opentagviewer.source.NtfyClient;
import dev.wander.android.opentagviewer.source.NtfyPublisher;
import dev.wander.android.opentagviewer.source.NtfySettings;
import dev.wander.android.opentagviewer.ui.compat.WindowPaddingUtil;

/** Optional ntfy delivery for future AirTag left-behind alerts. */
public class NtfySettingsActivity extends AppCompatActivity {
    private static final Function<NtfySettings, NtfyPublisher> REAL_PUBLISHER = NtfyClient::new;
    private static Function<NtfySettings, NtfyPublisher> publisherFactory = REAL_PUBLISHER;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ActivityNtfySettingsBinding binding;
    private NtfySettings settings;
    private boolean changingEnabled;

    @VisibleForTesting
    public static void replacePublisherFactory(
            final Function<NtfySettings, NtfyPublisher> replacement) {
        publisherFactory = Objects.requireNonNull(replacement);
    }

    @VisibleForTesting
    public static void resetPublisherFactory() {
        publisherFactory = REAL_PUBLISHER;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.settings = new NtfySettings(this);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_ntfy_settings);
        WindowPaddingUtil.insetForSystemBarsAndKeyboard(this.binding.getRoot());
        this.binding.setHandleClickBack(this::finish);
        if (this.getSupportActionBar() != null) {
            this.getSupportActionBar().hide();
        }

        this.binding.ntfyServerUrl.setText(this.settings.getServerUrl());
        this.binding.ntfyTopic.setText(this.settings.getTopic());
        this.setEnabledChecked(this.settings.isEnabled());
        this.updateTokenState();
        this.updateCleartextWarning();

        final TextWatcher configurationWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count,
                                          final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before,
                                      final int count) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                NtfySettingsActivity.this.configurationChanged();
            }
        };
        this.binding.ntfyServerUrl.addTextChangedListener(configurationWatcher);
        this.binding.ntfyTopic.addTextChangedListener(configurationWatcher);
        this.binding.ntfyEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (!this.changingEnabled) {
                this.enabledChanged(checked);
            }
        });
        this.binding.ntfyClearToken.setOnClickListener(v -> this.clearToken());
        this.binding.ntfyTest.setOnClickListener(v -> this.testNotification());
    }

    private void configurationChanged() {
        this.saveNonSecretFields();
        this.updateCleartextWarning();
        if (this.binding.ntfyEnabled.isChecked()
                && this.settings.validationError() != NtfySettings.ValidationError.NONE) {
            this.settings.setEnabled(false);
            this.setEnabledChecked(false);
            this.showValidationErrors();
        }
    }

    private void enabledChanged(final boolean enabled) {
        this.saveEnteredToken();
        this.saveNonSecretFields();
        final NtfySettings.ValidationError validation = this.showValidationErrors();
        if (enabled && validation != NtfySettings.ValidationError.NONE) {
            this.settings.setEnabled(false);
            this.setEnabledChecked(false);
            this.binding.ntfyTestResult.setText(R.string.ntfy_enable_needs_configuration);
            return;
        }
        this.settings.setEnabled(enabled);
        this.binding.ntfyTestResult.setText("");
    }

    private void setEnabledChecked(final boolean checked) {
        this.changingEnabled = true;
        this.binding.ntfyEnabled.setChecked(checked);
        this.changingEnabled = false;
    }

    private void saveNonSecretFields() {
        this.settings.setServerUrl(text(this.binding.ntfyServerUrl));
        this.settings.setTopic(text(this.binding.ntfyTopic));
    }

    /** A blank token field means keep the encrypted token; clearing is an explicit action. */
    private void saveEnteredToken() {
        final String entered = text(this.binding.ntfyAccessToken).trim();
        if (!entered.isEmpty()) {
            this.settings.setAccessToken(entered);
            this.binding.ntfyAccessToken.setText("");
            this.updateTokenState();
        }
    }

    private void clearToken() {
        this.binding.ntfyAccessToken.setText("");
        this.settings.clearAccessToken();
        this.updateTokenState();
        this.binding.ntfyTestResult.setText(R.string.ntfy_token_cleared);
    }

    private void updateTokenState() {
        final boolean saved = this.settings.hasAccessToken();
        this.binding.ntfyAccessTokenLayout.setHelperText(saved
                ? this.getString(R.string.ntfy_token_saved_redacted) : null);
        this.binding.ntfyClearToken.setVisibility(saved ? View.VISIBLE : View.GONE);
    }

    private void updateCleartextWarning() {
        final String server = text(this.binding.ntfyServerUrl).trim();
        this.binding.ntfyServerUrlLayout.setHelperText(
                server.regionMatches(true, 0, "http://", 0, 7)
                        ? this.getString(R.string.ntfy_http_warning) : null);
    }

    private NtfySettings.ValidationError showValidationErrors() {
        final NtfySettings.ValidationError validation = NtfySettings.validate(
                text(this.binding.ntfyServerUrl), text(this.binding.ntfyTopic));
        this.binding.ntfyServerUrlLayout.setError(null);
        this.binding.ntfyTopicLayout.setError(null);
        if (validation == NtfySettings.ValidationError.SERVER_REQUIRED) {
            this.binding.ntfyServerUrlLayout.setError(this.getString(R.string.ntfy_server_required));
        } else if (validation == NtfySettings.ValidationError.SERVER_INVALID) {
            this.binding.ntfyServerUrlLayout.setError(this.getString(R.string.ntfy_server_invalid));
        } else if (validation == NtfySettings.ValidationError.TOPIC_REQUIRED) {
            this.binding.ntfyTopicLayout.setError(this.getString(R.string.ntfy_topic_required));
        }
        this.updateCleartextWarning();
        return validation;
    }

    private void testNotification() {
        this.saveEnteredToken();
        this.saveNonSecretFields();
        if (this.showValidationErrors() != NtfySettings.ValidationError.NONE) {
            this.binding.ntfyTestResult.setText(R.string.ntfy_test_needs_configuration);
            return;
        }

        this.binding.ntfyTest.setEnabled(false);
        this.binding.ntfyTestProgress.setVisibility(View.VISIBLE);
        this.binding.ntfyTestResult.setText(R.string.ntfy_test_sending);
        final NtfyPublisher publisher = publisherFactory.apply(this.settings);
        this.io.execute(() -> {
            final int result = this.sendTest(publisher);
            this.runOnUiThread(() -> {
                if (this.isFinishing() || this.isDestroyed()) {
                    return;
                }
                this.binding.ntfyTest.setEnabled(true);
                this.binding.ntfyTestProgress.setVisibility(View.GONE);
                this.binding.ntfyTestResult.setText(result);
            });
        });
    }

    private int sendTest(final NtfyPublisher publisher) {
        try {
            publisher.sendTestNotification();
            return R.string.ntfy_test_success;
        } catch (final Exception failure) {
            return this.failureMessage(failure);
        }
    }

    private int failureMessage(final Exception failure) {
        if (failure instanceof NtfyClient.HttpStatusException) {
            final int status = ((NtfyClient.HttpStatusException) failure).getStatus();
            if (status == 401 || status == 403) {
                return R.string.ntfy_test_failed_auth;
            }
            if (status == 404) {
                return R.string.ntfy_test_failed_not_found;
            }
            if (status == 429) {
                return R.string.ntfy_test_failed_rate_limited;
            }
            if (status >= 500 && status <= 599) {
                return R.string.ntfy_test_failed_server;
            }
            return R.string.ntfy_test_failed_http;
        }
        if (failure instanceof SocketTimeoutException) {
            return R.string.ntfy_test_failed_timeout;
        }
        if (failure instanceof UnknownHostException) {
            return R.string.ntfy_test_failed_dns;
        }
        if (failure instanceof SSLException) {
            return R.string.ntfy_test_failed_tls;
        }
        if (failure instanceof NtfyClient.ConfigurationException) {
            return R.string.ntfy_test_needs_configuration;
        }
        return R.string.ntfy_test_failed_network;
    }

    @Override
    protected void onPause() {
        this.saveEnteredToken();
        this.saveNonSecretFields();
        if (this.settings.validationError() != NtfySettings.ValidationError.NONE) {
            this.settings.setEnabled(false);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        this.io.shutdownNow();
        super.onDestroy();
    }

    private static String text(final EditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }
}
