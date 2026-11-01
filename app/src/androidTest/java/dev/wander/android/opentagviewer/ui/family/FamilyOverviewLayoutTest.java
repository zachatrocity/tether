package dev.wander.android.opentagviewer.ui.family;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.color.MaterialColors;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import dev.wander.android.opentagviewer.R;

/** Fixed-size light and dark renders of the approved Home composition. */
@RunWith(AndroidJUnit4.class)
public class FamilyOverviewLayoutTest {
    /** Pixel 6 at 420 dpi: the approved approximately 412 by 915dp compact target. */
    private static final int WIDTH_PX = 1080;
    private static final int HEIGHT_PX = 2400;

    @Test
    public void lightAndDarkLayoutsRenderForVisualReview() throws IOException {
        final Rendered light = this.render(false, "family-overview-light");
        final Rendered dark = this.render(true, "family-overview-dark");

        assertNotEquals("the dark render kept the light surface colour",
                light.surfaceColor, dark.surfaceColor);
        assertEquals("the menu no longer measures to the approved 58dp target",
                light.expectedMenuSize, light.menuSize);
        assertEquals("the dark menu no longer measures to the approved 58dp target",
                dark.expectedMenuSize, dark.menuSize);
    }

    private Rendered render(final boolean night, final String name) throws IOException {
        final Context base = getInstrumentation().getTargetContext();
        final Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        final Context configured = base.createConfigurationContext(configuration);
        final Context themed = new ContextThemeWrapper(
                configured, R.style.Theme_OpenTagViewer_GenericGreyishActivity);

        final View root = LayoutInflater.from(themed)
                .inflate(R.layout.activity_family_overview, null, false);
        root.findViewById(R.id.family_loading_state).setVisibility(GONE);
        root.findViewById(R.id.family_empty_state).setVisibility(GONE);
        root.findViewById(R.id.family_rows_card).setVisibility(VISIBLE);

        final RecyclerView rows = root.findViewById(R.id.family_rows);
        rows.setLayoutManager(new LinearLayoutManager(themed));
        rows.setAdapter(new PreviewRows());

        root.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, WIDTH_PX, HEIGHT_PX);
        final View scrollingContent = root.findViewById(R.id.family_overview_scroll);
        final View mapControl = root.findViewById(R.id.family_open_map);
        assertTrue(name + " lets scrolling content run behind the floating map control",
                scrollingContent.getBottom() <= mapControl.getTop());

        final RecyclerView.ViewHolder finalHolder =
                rows.findViewHolderForAdapterPosition(3);
        assertNotNull(name + " did not lay out the final compact row", finalHolder);
        final View finalRow = finalHolder.itemView;
        final View evidence = finalRow.findViewById(R.id.family_row_evidence);
        final View divider = finalRow.findViewById(R.id.family_row_divider);
        assertTrue(name + " lets wrapped evidence collide with its divider",
                evidence.getBottom() <= divider.getTop());

        final Bitmap bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final int surface = MaterialColors.getColor(
                root, com.google.android.material.R.attr.colorSurface);
        canvas.drawColor(surface);
        root.draw(canvas);
        this.write(bitmap, name);
        bitmap.recycle();

        final View menu = root.findViewById(R.id.family_navigation_menu);
        final int expectedMenuSize = Math.round(
                58 * root.getResources().getDisplayMetrics().density);
        return new Rendered(surface, menu.getMeasuredWidth(), expectedMenuSize);
    }

    private void write(final Bitmap bitmap, final String name) throws IOException {
        final String directory = InstrumentationRegistry.getArguments()
                .getString("additionalTestOutputDir");
        if (directory == null) {
            return;
        }
        final File output = new File(directory, name + ".png");
        final File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        try (FileOutputStream stream = new FileOutputStream(output)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
    }

    private static final class Rendered {
        private final int surfaceColor;
        private final int menuSize;
        private final int expectedMenuSize;

        Rendered(final int surfaceColor, final int menuSize, final int expectedMenuSize) {
            this.surfaceColor = surfaceColor;
            this.menuSize = menuSize;
            this.expectedMenuSize = expectedMenuSize;
        }
    }

    private static final class PreviewRows extends RecyclerView.Adapter<PreviewHolder> {
        private static final String[][] ROWS = {
                {"Mina", "Backpack AirTag", "Nearby now", "This phone heard the AirTag"},
                {"Evan", "Bike AirTag", "Last report 14 minutes ago", "Apple network report · accuracy ~35 m"},
                {"", "", "", ""},
                {"Unassigned", "Keys AirTag", "Last report 3 hours ago · not current", "Near Cedar School · Apple network report · accuracy ~80 m"}
        };

        @Override
        public int getItemViewType(final int position) {
            return position == 2 ? 1 : 0;
        }

        @NonNull
        @Override
        public PreviewHolder onCreateViewHolder(
                @NonNull final ViewGroup parent, final int viewType) {
            final int layout = viewType == 1
                    ? R.layout.family_overview_section_header : R.layout.family_overview_row;
            return new PreviewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final PreviewHolder holder, final int position) {
            if (position == 2) {
                ((TextView) holder.itemView.findViewById(R.id.family_section_title))
                        .setText(R.string.family_unassigned_airtags);
                return;
            }
            final String[] row = ROWS[position];
            ((TextView) holder.itemView.findViewById(R.id.family_row_person)).setText(row[0]);
            ((TextView) holder.itemView.findViewById(R.id.family_row_airtag)).setText(row[1]);
            ((TextView) holder.itemView.findViewById(R.id.family_row_freshness)).setText(row[2]);
            ((TextView) holder.itemView.findViewById(R.id.family_row_evidence)).setText(row[3]);
        }

        @Override
        public int getItemCount() {
            return ROWS.length;
        }
    }

    private static final class PreviewHolder extends RecyclerView.ViewHolder {
        PreviewHolder(final View view) {
            super(view);
        }
    }
}
