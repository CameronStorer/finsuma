package com.cameronstorer.finsuma.util;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;

import com.cameronstorer.finsuma.R;

/** Finsuma: remembers list/grid choice per screen and adds the toggle to a toolbar menu. */
public final class FinsumaGrid {
    private FinsumaGrid() {}

    public static boolean isGrid(Context context, String screen) {
        return context.getSharedPreferences("finsuma_ui", Context.MODE_PRIVATE).getBoolean("grid_" + screen, false);
    }

    public static void setGrid(Context context, String screen, boolean grid) {
        context.getSharedPreferences("finsuma_ui", Context.MODE_PRIVATE).edit().putBoolean("grid_" + screen, grid).apply();
    }

    public static MenuItem addToggle(Menu menu, boolean grid) {
        MenuItem item = menu.add(Menu.NONE, R.id.finsuma_grid_toggle, Menu.NONE, grid ? R.string.finsuma_list_view : R.string.finsuma_grid_view);
        item.setIcon(grid ? R.drawable.ic_finsuma_list : R.drawable.ic_finsuma_grid);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return item;
    }

    public static void updateToggle(MenuItem item, boolean grid) {
        if (item == null) return;
        item.setTitle(grid ? R.string.finsuma_list_view : R.string.finsuma_grid_view);
        item.setIcon(grid ? R.drawable.ic_finsuma_list : R.drawable.ic_finsuma_grid);
    }
}
