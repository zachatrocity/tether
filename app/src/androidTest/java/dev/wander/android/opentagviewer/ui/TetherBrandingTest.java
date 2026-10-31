package dev.wander.android.opentagviewer.ui;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static androidx.test.platform.app.InstrumentationRegistry.getArguments;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import dev.wander.android.opentagviewer.R;

/** Verify the installed identity and the actual Android-rendered brand resources. */
@RunWith(AndroidJUnit4.class)
public class TetherBrandingTest {
    @Test
    public void packageLabelAndOrbitArtworkAreInstalled() throws Exception {
        Context base = getInstrumentation().getTargetContext();
        assertEquals("io.zachs.tether", base.getPackageName());
        assertEquals("Tether", base.getApplicationInfo().loadLabel(base.getPackageManager()).toString());
        for (boolean night : new boolean[] {false, true}) {
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            Context themed = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_OpenTagViewer);
            Bitmap bitmap = Bitmap.createBitmap(432, 216, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(night ? Color.BLACK : Color.WHITE);
            Drawable launcher = AppCompatResources.getDrawable(themed, R.mipmap.ic_launcher);
            launcher.setBounds(0, 0, 216, 216);
            launcher.draw(canvas);
            // Empty center and the upper companion dot are deliberately different brand colors.
            assertEquals(Color.rgb(7, 61, 57), bitmap.getPixel(108, 108));
            int mintPixels = 0;
            for (int y = 0; y < 216; y++) {
                for (int x = 0; x < 216; x++) {
                    if (bitmap.getPixel(x, y) == Color.rgb(165, 243, 199)) mintPixels++;
                }
            }
            assertTrue("Orbit foreground must be visible inside the launcher mask", mintPixels > 500);
            Drawable logo = AppCompatResources.getDrawable(themed, R.drawable.opentagviewer_icon_themed);
            logo.setBounds(216, 0, 432, 216);
            logo.draw(canvas);
            assertTrue(bitmap.getPixel(356, 76) != (night ? Color.BLACK : Color.WHITE));
            String dir = getArguments().getString("additionalTestOutputDir");
            if (dir != null) {
                try (FileOutputStream out = new FileOutputStream(new File(dir, "tether-" + (night ? "dark" : "light") + ".png"))) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            }
        }
    }
}
