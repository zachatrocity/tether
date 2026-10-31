package dev.wander.android.opentagviewer.ui;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

import dev.wander.android.opentagviewer.R;

@RunWith(AndroidJUnit4.class)
public class RemoteServerButtonLayoutTest {
    @Test
    public void buttonIsVisibleAndReadableInBothThemes() throws Exception {
        for (boolean night : new boolean[] {false, true}) {
            Context base = getInstrumentation().getTargetContext();
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            Context themed = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_OpenTagViewer);
            Button button = (Button) LayoutInflater.from(themed).inflate(R.layout.login_remote_server_button, null);
            assertEquals(R.id.login_choose_remote_server, button.getId());
            assertEquals(View.VISIBLE, button.getVisibility());
            assertEquals(themed.getString(R.string.login_try_remote_anisette), button.getText().toString());
            button.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            assertTrue(button.getMeasuredWidth() > 0);
            assertTrue(button.getMeasuredHeight() > 0);
            button.layout(0, 0, button.getMeasuredWidth(), button.getMeasuredHeight());
            Bitmap bitmap = Bitmap.createBitmap(button.getWidth(), button.getHeight(), Bitmap.Config.ARGB_8888);
            button.draw(new Canvas(bitmap));
            String dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir");
            if (dir != null) {
                try (FileOutputStream out = new FileOutputStream(new File(dir,
                        "remote_server-" + (night ? "dark" : "light") + ".png"))) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            }
        }
    }
}
