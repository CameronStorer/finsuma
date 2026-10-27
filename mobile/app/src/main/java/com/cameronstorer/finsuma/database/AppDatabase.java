package com.cameronstorer.finsuma.database;

import androidx.media3.common.util.UnstableApi;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.cameronstorer.finsuma.App;
import com.cameronstorer.finsuma.database.converter.DateConverters;
import com.cameronstorer.finsuma.database.converter.StringListConverter;
import com.cameronstorer.finsuma.database.dao.ChronologyDao;
import com.cameronstorer.finsuma.database.dao.DownloadDao;
import com.cameronstorer.finsuma.database.dao.FavoriteDao;
import com.cameronstorer.finsuma.database.dao.InternetRadioStationDao;
import com.cameronstorer.finsuma.database.dao.LyricsDao;
import com.cameronstorer.finsuma.database.dao.PinnedPlaylistDao;
import com.cameronstorer.finsuma.database.dao.SyncedPlaylistDao;
import com.cameronstorer.finsuma.database.dao.PlaylistDao;
import com.cameronstorer.finsuma.database.dao.PlaylistSongDao;
import com.cameronstorer.finsuma.database.dao.QueueDao;
import com.cameronstorer.finsuma.database.dao.RecentSearchDao;
import com.cameronstorer.finsuma.database.dao.ServerDao;
import com.cameronstorer.finsuma.database.dao.SessionMediaItemDao;
import com.cameronstorer.finsuma.model.Chronology;
import com.cameronstorer.finsuma.model.Download;
import com.cameronstorer.finsuma.model.Favorite;
import com.cameronstorer.finsuma.model.InternetRadioStationCache;
import com.cameronstorer.finsuma.model.LyricsCache;
import com.cameronstorer.finsuma.model.PinnedPlaylist;
import com.cameronstorer.finsuma.model.PlaylistSong;
import com.cameronstorer.finsuma.model.Queue;
import com.cameronstorer.finsuma.model.RecentSearch;
import com.cameronstorer.finsuma.model.Server;
import com.cameronstorer.finsuma.model.SessionMediaItem;
import com.cameronstorer.finsuma.model.SyncedPlaylist;
import com.cameronstorer.finsuma.subsonic.models.Playlist;

@UnstableApi
@Database(
        version = 24,
        entities = {
            Queue.class,
            Server.class,
            RecentSearch.class,
            Download.class,
            Chronology.class,
            Favorite.class,
            SessionMediaItem.class,
            Playlist.class,
            PinnedPlaylist.class,
            LyricsCache.class,
            InternetRadioStationCache.class,
            PlaylistSong.class,
            SyncedPlaylist.class,
        },
        autoMigrations = {
                @AutoMigration(from = 10, to = 11),
                @AutoMigration(from = 11, to = 12),
                @AutoMigration(from = 12, to = 13),
                @AutoMigration(from = 13, to = 14),
                @AutoMigration(from = 14, to = 15),
                @AutoMigration(from = 15, to = 16),
                @AutoMigration(from = 16, to = 17),
                @AutoMigration(from = 17, to = 18),
                @AutoMigration(from = 18, to = 19),
                @AutoMigration(from = 19, to = 20),
                @AutoMigration(from = 20, to = 21),
                @AutoMigration(from = 21, to = 22),
                @AutoMigration(from = 22, to = 23),
                @AutoMigration(from = 23, to = 24),
        }
)
@TypeConverters({DateConverters.class, StringListConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private final static String DB_NAME = "tempo_db";
    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance() {
        if (instance == null) {
            instance = Room.databaseBuilder(App.getContext(), AppDatabase.class, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
        }

        return instance;
    }

    public abstract QueueDao queueDao();

    public abstract ServerDao serverDao();

    public abstract RecentSearchDao recentSearchDao();

    public abstract DownloadDao downloadDao();

    public abstract ChronologyDao chronologyDao();

    public abstract FavoriteDao favoriteDao();

    public abstract SessionMediaItemDao sessionMediaItemDao();

    public abstract PlaylistDao playlistDao();

    public abstract PinnedPlaylistDao pinnedPlaylistDao();

    public abstract SyncedPlaylistDao syncedPlaylistDao();

    public abstract PlaylistSongDao playlistSongDao();

    public abstract LyricsDao lyricsDao();

    public abstract InternetRadioStationDao internetRadioStationDao();
}
