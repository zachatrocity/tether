package dev.wander.android.opentagviewer.ui.family;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.button.MaterialButton;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import dev.wander.android.opentagviewer.R;
import dev.wander.android.opentagviewer.data.model.BeaconInformation;
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport;
import dev.wander.android.opentagviewer.db.room.entity.LocationReport;

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

    @Test
    public void oneAirTagHasNoTrailingDivider() {
        final RecyclerView rows = new RecyclerView(this.themedContext(false));
        rows.setLayoutManager(new LinearLayoutManager(rows.getContext()));
        final FamilyOverviewAdapter adapter = new FamilyOverviewAdapter(value -> {}, row -> {});
        adapter.replaceWith(Arrays.asList(this.airTag("only", "Mina", "Backpack AirTag")));

        final RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(
                rows, adapter.getItemViewType(0));
        adapter.onBindViewHolder(holder, 0);

        assertEquals("a one-item list rendered a trailing divider", GONE,
                holder.itemView.findViewById(R.id.family_row_divider).getVisibility());
    }

    @Test
    public void anAirTagAvatarShowsItsEmojiAndClearsItForTheFallbackIcon() {
        final RecyclerView rows = new RecyclerView(this.themedContext(false));
        rows.setLayoutManager(new LinearLayoutManager(rows.getContext()));
        final FamilyOverviewAdapter adapter = new FamilyOverviewAdapter(value -> {}, row -> {});
        adapter.replaceWith(Arrays.asList(
                this.airTag("mina", "Mina", "Backpack AirTag", "🎒"),
                this.airTag("mina", "Mina", "Keys AirTag")));

        final RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(
                rows, adapter.getItemViewType(0));
        final MaterialButton avatar =
                holder.itemView.findViewById(R.id.family_row_edit_profile);

        adapter.onBindViewHolder(holder, 0);
        assertEquals("the AirTag emoji did not reach its Family avatar",
                "🎒", avatar.getText().toString());
        assertNull("an icon was drawn over the AirTag emoji", avatar.getIcon());

        adapter.onBindViewHolder(holder, 1);
        assertEquals("a recycled Family row kept the previous AirTag's emoji",
                "", avatar.getText().toString());
        assertNotNull("an AirTag without an emoji rendered an empty avatar", avatar.getIcon());
    }

    @Test
    public void freshnessTextAndDotFollowAgeAcrossRecycledRows() {
        for (final boolean night : new boolean[] {false, true}) {
            final Context context = this.themedContext(night);
            final RecyclerView rows = new RecyclerView(context);
            rows.setLayoutManager(new LinearLayoutManager(context));
            final FamilyOverviewAdapter adapter =
                    new FamilyOverviewAdapter(value -> {}, row -> {});
            final long nowMs = System.currentTimeMillis();
            adapter.replaceWith(Arrays.asList(
                    this.airTagAt("Fresh", nowMs - TimeUnit.MINUTES.toMillis(10)),
                    this.airTagAt("Warning", nowMs - TimeUnit.MINUTES.toMillis(45)),
                    this.airTagAt("Old", nowMs - TimeUnit.HOURS.toMillis(3))));

            final RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(
                    rows, adapter.getItemViewType(0));
            final TextView freshness =
                    holder.itemView.findViewById(R.id.family_row_freshness);
            final ImageView dot =
                    holder.itemView.findViewById(R.id.family_row_status_dot);
            final int[] attributes = {
                    com.google.android.material.R.attr.colorPrimary,
                    com.google.android.material.R.attr.colorTertiary,
                    com.google.android.material.R.attr.colorError,
            };

            for (int index = 0; index < attributes.length; index++) {
                adapter.onBindViewHolder(holder, index);
                final int expected = MaterialColors.getColor(
                        holder.itemView, attributes[index]);
                assertEquals("wrong freshness text colour at row " + index,
                        expected, freshness.getCurrentTextColor());
                assertNotNull("status dot lost its tint at row " + index,
                        dot.getImageTintList());
                assertEquals("status dot disagrees with freshness text at row " + index,
                        expected, dot.getImageTintList().getDefaultColor());
            }
        }
    }

    private Rendered render(final boolean night, final String name) throws IOException {
        final Context themed = this.themedContext(night);

        final View root = LayoutInflater.from(themed)
                .inflate(R.layout.activity_family_overview, null, false);
        final View refresh = root.findViewById(R.id.family_refresh);
        assertNotNull(name + " did not inflate pull-to-refresh", refresh);
        assertEquals(name + " did not wrap the scrolling content in pull-to-refresh",
                refresh, root.findViewById(R.id.family_overview_scroll).getParent());
        assertEquals(name + " rendered the status notice in a healthy preview", GONE,
                root.findViewById(R.id.family_status_notice).getVisibility());
        assertEquals(name + " rendered the login action without a disconnected account",
                GONE, root.findViewById(R.id.family_status_login).getVisibility());
        root.findViewById(R.id.family_loading_state).setVisibility(GONE);
        root.findViewById(R.id.family_empty_state).setVisibility(GONE);
        root.findViewById(R.id.family_rows_card).setVisibility(VISIBLE);

        final RecyclerView rows = root.findViewById(R.id.family_rows);
        rows.setLayoutManager(new LinearLayoutManager(themed));
        rows.setAdapter(this.previewRows());

        root.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, WIDTH_PX, HEIGHT_PX);
        final View scrollingContent = root.findViewById(R.id.family_overview_scroll);
        final View mapControl = root.findViewById(R.id.family_open_map);
        assertTrue(name + " reserves a full-width region below the scrolling surface",
                scrollingContent.getBottom() > mapControl.getTop());
        assertTrue(name + " cannot scroll its final row clear of the floating Map control",
                scrollingContent.getPaddingBottom()
                        >= scrollingContent.getBottom() - mapControl.getTop());

        final RecyclerView.ViewHolder firstHolder =
                rows.findViewHolderForAdapterPosition(0);
        final RecyclerView.ViewHolder secondHolder =
                rows.findViewHolderForAdapterPosition(1);
        final RecyclerView.ViewHolder finalHolder =
                rows.findViewHolderForAdapterPosition(3);
        assertNotNull(name + " did not lay out the first compact row", firstHolder);
        assertNotNull(name + " did not lay out the second compact row", secondHolder);
        assertNotNull(name + " did not lay out the final compact row", finalHolder);
        assertEquals(name + " lost the divider between adjacent AirTag rows", VISIBLE,
                firstHolder.itemView.findViewById(R.id.family_row_divider).getVisibility());
        assertEquals(name + " rendered a divider before a section header", GONE,
                secondHolder.itemView.findViewById(R.id.family_row_divider).getVisibility());

        final View divider =
                finalHolder.itemView.findViewById(R.id.family_row_divider);
        assertEquals(name + " rendered a divider after the final AirTag row",
                GONE, divider.getVisibility());

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

    private Context themedContext(final boolean night) {
        final Context base = getInstrumentation().getTargetContext();
        final Configuration configuration =
                new Configuration(base.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return new ContextThemeWrapper(base.createConfigurationContext(configuration),
                R.style.Theme_OpenTagViewer_GenericGreyishActivity);
    }

    private FamilyOverviewAdapter previewRows() {
        final FamilyOverviewAdapter adapter = new FamilyOverviewAdapter(value -> {}, row -> {});
        final long nowMs = System.currentTimeMillis();
        adapter.replaceWith(Arrays.asList(
                this.airTagAt("Backpack AirTag", "🎒",
                        nowMs - TimeUnit.MINUTES.toMillis(10)),
                this.airTagAt("Bike AirTag", null,
                        nowMs - TimeUnit.MINUTES.toMillis(45)),
                FamilyOverviewRow.sectionHeader("Unassigned AirTags"),
                this.airTagAt("Keys AirTag", null,
                        nowMs - TimeUnit.HOURS.toMillis(3))));
        return adapter;
    }

    private FamilyOverviewRow airTag(
            final String profileId, final String personName, final String airTagName) {
        return FamilyOverviewRow.airTag(
                profileId,
                personName,
                BeaconInformation.builder()
                        .beaconId(airTagName)
                        .originalName(airTagName)
                        .build(),
                null,
                null);
    }

    private FamilyOverviewRow airTag(
            final String profileId,
            final String personName,
            final String airTagName,
            final String emoji) {
        return FamilyOverviewRow.airTag(
                profileId,
                personName,
                BeaconInformation.builder()
                        .beaconId(airTagName)
                        .originalName(airTagName)
                        .originalEmoji(emoji)
                        .build(),
                null,
                null);
    }

    private FamilyOverviewRow airTagAt(final String airTagName, final long timestamp) {
        return this.airTagAt(airTagName, null, timestamp);
    }

    private FamilyOverviewRow airTagAt(
            final String airTagName, final String emoji, final long timestamp) {
        return FamilyOverviewRow.airTag(
                "profile",
                "Mina",
                BeaconInformation.builder()
                        .beaconId(airTagName)
                        .originalName(airTagName)
                        .originalEmoji(emoji)
                        .build(),
                BeaconLocationReport.builder()
                        .timestamp(timestamp)
                        .provenance(LocationReport.PROVENANCE_LOCAL)
                        .build(),
                null);
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

}
