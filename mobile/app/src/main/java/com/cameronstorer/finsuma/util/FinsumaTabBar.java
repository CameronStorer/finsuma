package com.cameronstorer.finsuma.util;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;

import com.cameronstorer.finsuma.R;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Finsuma: compact top bar for the bottom-bar tabs (Playlists, Tracks, Albums/Genres).
 * The big page header is hidden; the title and plain action icons (new playlist, download,
 * sort, shuffle, albums/genres switch) sit in the one toolbar row next to search and grid.
 */
public final class FinsumaTabBar {
    public static final String ARG_TAB = "finsuma_tab";

    public static final int NEW_PLAYLIST = 1;
    public static final int DOWNLOAD = 2;
    public static final int SORT = 4;
    public static final int SHUFFLE = 8;
    public static final int TO_GENRES = 16;
    public static final int TO_ALBUMS = 32;

    private FinsumaTabBar() {
    }

    public static boolean isTab(@Nullable Bundle args) {
        return args != null && args.getBoolean(ARG_TAB, false);
    }

    /** Hides the page header and shows {@code title} in the toolbar. */
    public static void compact(AppBarLayout header, MaterialToolbar toolbar, CharSequence title) {
        header.setVisibility(View.GONE);
        toolbar.setTitle(title);
    }

    /** Adds the requested actions; call before inflating the page's own menu so they come first. */
    public static void addActions(Menu menu, int actions) {
        if (!com.cameronstorer.finsuma.BuildConfig.FINSUMA_DOWNLOADS) actions &= ~DOWNLOAD; // Google Play build
        if ((actions & TO_GENRES) != 0) add(menu, R.id.finsuma_action_genres, R.string.genre_catalogue_title, R.drawable.ic_genres_tab);
        if ((actions & TO_ALBUMS) != 0) add(menu, R.id.finsuma_action_albums, R.string.album_catalogue_title, R.drawable.ic_albums_tab);
        if ((actions & NEW_PLAYLIST) != 0) add(menu, R.id.finsuma_action_new_playlist, R.string.finsuma_new_playlist, R.drawable.ic_add);
        if ((actions & DOWNLOAD) != 0) add(menu, R.id.finsuma_action_download, R.string.finsuma_nav_download, R.drawable.ic_download);
        if ((actions & SORT) != 0) add(menu, R.id.finsuma_action_sort, R.string.finsuma_sort, R.drawable.ic_sort_list);
        if ((actions & SHUFFLE) != 0) add(menu, R.id.finsuma_action_shuffle, R.string.finsuma_shuffle, R.drawable.ic_shuffle);
    }

    private static void add(Menu menu, int id, int title, int icon) {
        if (menu.findItem(id) != null) return;
        menu.add(Menu.NONE, id, Menu.NONE, title)
                .setIcon(icon)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    /** The view of a toolbar action (to anchor a popup), or the toolbar itself. */
    public static View anchor(MaterialToolbar toolbar, int itemId) {
        View view = toolbar.findViewById(itemId);
        return view != null ? view : toolbar;
    }

    /** Opens a bottom-bar library page (albums or genres) as a tab, replacing the current one. */
    public static void openTab(NavController navController, int destination) {
        com.cameronstorer.finsuma.App.getInstance().getPreferences().edit()
                .putBoolean("finsuma_library_genres", destination == R.id.genreCatalogueFragment).apply();
        Bundle args = new Bundle();
        args.putBoolean(ARG_TAB, true);
        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                .build();
        navController.navigate(destination, args, options);
    }

    /** The bottom-bar Library tab: albums or genres, whichever was used last. */
    public static void openLibrary(NavController navController) {
        boolean genres = com.cameronstorer.finsuma.App.getInstance().getPreferences().getBoolean("finsuma_library_genres", false);
        openTab(navController, genres ? R.id.genreCatalogueFragment : R.id.albumCatalogueFragment);
    }

    /** Shows a checkmark next to the current choice in a sort popup. */
    public static void checkSort(Menu menu, @Nullable Integer currentItemId) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            item.setCheckable(true);
            item.setChecked(currentItemId != null && item.getItemId() == currentItemId);
        }
    }
}
