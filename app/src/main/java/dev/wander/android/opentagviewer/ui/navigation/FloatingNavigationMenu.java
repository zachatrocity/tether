package dev.wander.android.opentagviewer.ui.navigation;

import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import java.util.function.Consumer;

import dev.wander.android.opentagviewer.R;

/** The one floating navigation contract shared by Family Overview and Map. */
public final class FloatingNavigationMenu {
    public enum Destination {
        FAMILY,
        MAP,
        REPORTS,
        ADD_AIR_TAG,
        SETTINGS
    }

    private FloatingNavigationMenu() {}

    public static PopupMenu show(
            final View anchor,
            final Destination current,
            final Consumer<Destination> onDestination) {
        return show(anchor, current, onDestination, null);
    }

    public static PopupMenu show(
            final View anchor,
            final Destination current,
            final Consumer<Destination> onDestination,
            final Runnable onExportLogs) {
        final PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.global_navigation_menu, popup.getMenu());
        popup.getMenu().findItem(R.id.navigation_export_logs).setVisible(onExportLogs != null);

        final MenuItem currentItem = popup.getMenu().findItem(itemId(current));
        currentItem.setCheckable(true);
        currentItem.setChecked(true);
        currentItem.setEnabled(false);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.navigation_export_logs && onExportLogs != null) {
                onExportLogs.run();
                return true;
            }
            final Destination selected = destination(item.getItemId());
            if (selected == null) {
                return false;
            }
            onDestination.accept(selected);
            return true;
        });
        popup.show();
        return popup;
    }

    private static int itemId(final Destination destination) {
        switch (destination) {
            case FAMILY:
                return R.id.navigation_family;
            case MAP:
                return R.id.navigation_map;
            case REPORTS:
                return R.id.navigation_reports;
            case ADD_AIR_TAG:
                return R.id.navigation_add_airtag;
            case SETTINGS:
            default:
                return R.id.navigation_settings;
        }
    }

    private static Destination destination(final int itemId) {
        if (itemId == R.id.navigation_family) {
            return Destination.FAMILY;
        }
        if (itemId == R.id.navigation_map) {
            return Destination.MAP;
        }
        if (itemId == R.id.navigation_reports) {
            return Destination.REPORTS;
        }
        if (itemId == R.id.navigation_add_airtag) {
            return Destination.ADD_AIR_TAG;
        }
        if (itemId == R.id.navigation_settings) {
            return Destination.SETTINGS;
        }
        return null;
    }
}
