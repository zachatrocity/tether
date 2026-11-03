package dev.wander.android.opentagviewer.ui;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.color.MaterialColors;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import dev.wander.android.opentagviewer.R;

/** Inflates and measures the two evidence surfaces under both supported theme modes. */
@RunWith(AndroidJUnit4.class)
public class AirTagDetailAndReportHistoryLayoutTest {
    private static final int WIDTH_PX = 1080;
    private static final int HEIGHT_PX = 2400;

    @Test
    public void detailSummaryRendersInLightAndDarkModesWithOrderedActions() {
        for (final Context context : this.themedContexts()) {
            final View screen = this.inflateAndMeasure(context, R.layout.activity_device_info);
            getInstrumentation().runOnMainSync(() -> {
                ((TextView) screen.findViewById(R.id.detail_profile_name)).setText("Mina");
                ((TextView) screen.findViewById(R.id.detail_airtag_name))
                        .setText("Backpack AirTag");
                screen.findViewById(R.id.detail_network_map_card).setVisibility(View.VISIBLE);
                this.measure(screen);
            });
            for (final int id : new int[] {
                    R.id.detail_profile_name,
                    R.id.detail_airtag_name,
                    R.id.detail_local_card,
                    R.id.detail_check_again,
                    R.id.detail_play_sound,
                    R.id.detail_network_card,
                    R.id.detail_network_map_card,
                    R.id.detail_network_map,
                    R.id.detail_full_history,
            }) {
                assertRealSize(screen, id);
            }

            assertEquals(R.id.detail_local_card,
                    screen.findViewById(R.id.detail_check_again).getAccessibilityTraversalAfter());
            assertEquals(R.id.detail_check_again,
                    screen.findViewById(R.id.detail_play_sound).getAccessibilityTraversalAfter());
            assertEquals(R.id.detail_play_sound,
                    screen.findViewById(R.id.detail_network_card).getAccessibilityTraversalAfter());
            assertEquals(R.id.detail_network_card,
                    screen.findViewById(R.id.detail_full_history).getAccessibilityTraversalAfter());
        }
    }

    @Test
    public void reportFeedRendersInLightAndDarkModesWithReachableControls() {
        for (final Context context : this.themedContexts()) {
            final View screen = this.inflateAndMeasure(
                    context, R.layout.activity_report_history);
            assertRealSize(screen, R.id.report_history_app_icon);
            assertRealSize(screen, R.id.report_history_page_label);
            assertRealSize(screen, R.id.report_history_loading);
            assertRealSize(screen, R.id.report_history_navigation_menu);

            final View row = this.inflateAndMeasure(context, R.layout.report_history_item);
            getInstrumentation().runOnMainSync(() -> {
                ((TextView) row.findViewById(R.id.report_event_title))
                        .setText(R.string.report_history_apple_title);
                ((TextView) row.findViewById(R.id.report_event_body))
                        .setText(R.string.report_history_intro);
                this.measure(row);
            });
            assertRealSize(row, R.id.report_event_title);
            assertRealSize(row, R.id.report_event_body);
            assertRealSize(row, R.id.report_event_full_history);
            assertEquals(R.id.report_event_row,
                    row.findViewById(R.id.report_event_full_history)
                            .getAccessibilityTraversalAfter());
        }
    }

    @Test
    public void lightAndDarkSurfacesRenderForVisualReview() throws IOException {
        final Context[] contexts = this.themedContexts();
        for (int index = 0; index < contexts.length; index++) {
            final String mode = index == 0 ? "light" : "dark";
            final View detail = this.inflateAndMeasure(
                    contexts[index], R.layout.activity_device_info);
            ((TextView) detail.findViewById(R.id.detail_profile_name)).setText("Mina");
            ((TextView) detail.findViewById(R.id.detail_airtag_name))
                    .setText("Backpack AirTag");
            ((TextView) detail.findViewById(R.id.detail_local_title))
                    .setText(R.string.detail_nearby_now);
            ((TextView) detail.findViewById(R.id.detail_local_body))
                    .setText("This phone heard Backpack AirTag over Bluetooth 1 minute ago.");
            ((TextView) detail.findViewById(R.id.detail_network_age))
                    .setText("Reported 2 hours ago · not current");
            ((TextView) detail.findViewById(R.id.detail_network_place))
                    .setText("Pine Park entrance");
            detail.findViewById(R.id.detail_network_map_card).setVisibility(View.VISIBLE);
            this.measure(detail);
            this.write(this.draw(detail), "airtag-detail-" + mode + ".png");

            final View reports = this.inflateAndMeasure(
                    contexts[index], R.layout.activity_report_history);
            reports.findViewById(R.id.report_history_loading).setVisibility(View.GONE);
            reports.findViewById(R.id.report_history_empty).setVisibility(View.VISIBLE);
            this.measure(reports);
            this.write(this.draw(reports), "report-history-" + mode + ".png");
        }
    }

    private Context[] themedContexts() {
        final Context target = getInstrumentation().getTargetContext();
        final Context light = this.withMode(target, Configuration.UI_MODE_NIGHT_NO);
        final Context dark = this.withMode(target, Configuration.UI_MODE_NIGHT_YES);
        return new Context[] {light, dark};
    }

    private Context withMode(final Context base, final int mode) {
        final Configuration configuration = new Configuration(
                base.getResources().getConfiguration());
        configuration.uiMode = mode | Configuration.UI_MODE_TYPE_NORMAL;
        return new ContextThemeWrapper(
                base.createConfigurationContext(configuration), R.style.Theme_OpenTagViewer);
    }

    private View inflateAndMeasure(final Context context, final int layout) {
        final View[] root = new View[1];
        getInstrumentation().runOnMainSync(() -> {
            root[0] = LayoutInflater.from(context).inflate(layout, null);
            this.measure(root[0]);
        });
        assertNotNull(root[0]);
        return root[0];
    }

    private void measure(final View view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, WIDTH_PX, view.getMeasuredHeight());
    }

    private Bitmap draw(final View view) {
        final Bitmap bitmap = Bitmap.createBitmap(
                WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(MaterialColors.getColor(
                view, com.google.android.material.R.attr.colorSurface));
        view.draw(canvas);
        return bitmap;
    }

    private void write(final Bitmap bitmap, final String name) throws IOException {
        final String directory = InstrumentationRegistry.getArguments()
                .getString("additionalTestOutputDir");
        if (directory == null) {
            bitmap.recycle();
            return;
        }
        final File output = new File(directory, name);
        final File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        try (FileOutputStream stream = new FileOutputStream(output)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally {
            bitmap.recycle();
        }
    }

    private static void assertRealSize(final View root, final int id) {
        final View view = root.findViewById(id);
        assertNotNull("missing view " + id, view);
        assertTrue("view " + id + " measured " + view.getMeasuredWidth()
                        + "x" + view.getMeasuredHeight(),
                view.getMeasuredWidth() > 0 && view.getMeasuredHeight() > 0);
    }
}
