package com.cameronstorer.finsuma.util;

import android.content.Context;

import com.cameronstorer.finsuma.R;
import com.cameronstorer.finsuma.model.HomeSector;

import java.util.ArrayList;
import java.util.List;

/**
 * Finsuma's home layout: the same structure as the desktop home page (playlists first, then
 * genres, random albums, recently added/released/played and most played). The other Tempus
 * sections stay available but start hidden; users can turn them on in "Rearrange home".
 */
public final class HomeSectorDefaults {
    private HomeSectorDefaults() {}

    public static List<HomeSector> build(Context context) {
        List<HomeSector> sectors = new ArrayList<>();
        int order = 0;

        // Shown
        sectors.add(new HomeSector(Constants.HOME_SECTOR_DISCOVERY, context.getString(R.string.home_title_discovery), true, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_PLAYLISTS, context.getString(R.string.home_title_playlists), true, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_GENRES, context.getString(R.string.home_title_genres), true, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_RANDOM_ALBUMS, context.getString(R.string.home_title_random_albums), true, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_RECENTLY_ADDED, context.getString(R.string.home_title_recently_added), true, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_NEW_RELEASES, context.getString(R.string.home_title_new_releases), true, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_LAST_PLAYED, context.getString(R.string.home_title_last_played), true, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_MOST_PLAYED, context.getString(R.string.home_title_most_played), true, ++order));

        // Hidden by default
        sectors.add(new HomeSector(Constants.HOME_SECTOR_PLAY_RANDOM, context.getString(R.string.play_random), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_CONTINUE_LISTENING, context.getString(R.string.home_title_continue_listening), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_MADE_FOR_YOU, context.getString(R.string.home_title_made_for_you), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_PINNED_PLAYLISTS, context.getString(R.string.home_title_pinned_playlists), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_BEST_OF, context.getString(R.string.home_title_best_of), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_RADIO_STATION, context.getString(R.string.home_title_radio_station), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_TOP_SONGS, context.getString(R.string.home_title_top_songs), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_TRACKS, context.getString(R.string.home_title_starred_tracks), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_ALBUMS, context.getString(R.string.home_title_starred_albums), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_ARTISTS, context.getString(R.string.home_title_starred_artists), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_FLASHBACK, context.getString(R.string.home_title_flashback), false, ++order));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_SHARED, context.getString(R.string.home_title_shares), false, ++order));

        return sectors;
    }

    /** Adds sections a saved layout doesn't know about yet (e.g. after an update), hidden. */
    public static boolean addMissing(Context context, List<HomeSector> saved) {
        boolean changed = false;
        for (HomeSector sector : build(context)) {
            if (saved.stream().noneMatch(s -> s.getId().equals(sector.getId()))) {
                saved.add(new HomeSector(sector.getId(), sector.getSectorTitle(), false, saved.size() + 1));
                changed = true;
            }
        }
        return changed;
    }
}
