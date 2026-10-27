package com.cameronstorer.finsuma.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cameronstorer.finsuma.App;
import com.cameronstorer.finsuma.util.Preferences;
import com.cameronstorer.finsuma.subsonic.base.ApiResponse;
import com.cameronstorer.finsuma.subsonic.models.Child;
import com.cameronstorer.finsuma.subsonic.models.SubsonicResponse;
import com.cameronstorer.finsuma.util.Constants.SeedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongRepository {

    private static final String TAG = "SongRepository";

    public interface MediaCallbackInternal {
        void onSongsAvailable(List<Child> songs);
    }

    // Finsuma: every song in the library, a page at a time (search3 with an empty query).
    private static final int ALL_SONGS_PAGE = 500;

    // Finsuma: the Tracks tab keeps the whole library in memory, so reopening it is instant.
    // The first load fills in page by page; later refreshes (after ALL_SONGS_FRESH_MS, or pull to
    // refresh) load quietly and swap the list in once complete.
    private static MutableLiveData<List<Child>> allSongsCache;
    private static String allSongsServer;
    private static long allSongsLoadedAt = 0;
    private static boolean allSongsLoading = false;
    private static final long ALL_SONGS_FRESH_MS = 10 * 60 * 1000;

    public static void invalidateAllSongs() {
        allSongsLoadedAt = 0;
    }

    public MutableLiveData<List<Child>> getAllSongs() {
        String server = Preferences.getInUseServerAddress();
        if (allSongsCache == null || !java.util.Objects.equals(server, allSongsServer)) {
            allSongsCache = new MutableLiveData<>();
            allSongsServer = server;
            allSongsLoading = true;
            MutableLiveData<List<Child>> target = allSongsCache;
            // Finsuma: after an app restart show the list saved last time right away, and only
            // re-read the whole library from the server when that copy is old.
            new Thread(() -> {
                List<Child> saved = readAllSongsFromDisk(server);
                if (saved == null) {
                    loadAllSongsPage(target, new ArrayList<>(), 0, true);
                    return;
                }
                allSongsLoadedAt = allSongsFile(server).lastModified();
                target.postValue(saved);
                if (System.currentTimeMillis() - allSongsLoadedAt < ALL_SONGS_DISK_FRESH_MS) {
                    allSongsLoading = false;
                } else {
                    loadAllSongsPage(target, new ArrayList<>(), 0, false);
                }
            }).start();
        } else if (!allSongsLoading && System.currentTimeMillis() - allSongsLoadedAt > ALL_SONGS_FRESH_MS) {
            allSongsLoading = true;
            loadAllSongsPage(allSongsCache, new ArrayList<>(), 0, false);
        }
        return allSongsCache;
    }

    private static final long ALL_SONGS_DISK_FRESH_MS = 60 * 60 * 1000;

    private static java.io.File allSongsFile(String server) {
        return new java.io.File(App.getInstance().getFilesDir(), "all_songs_" + Integer.toHexString(Objects.hashCode(server)) + ".json");
    }

    private static List<Child> readAllSongsFromDisk(String server) {
        java.io.File file = allSongsFile(server);
        if (!file.isFile()) return null;
        try (java.io.Reader reader = new java.io.InputStreamReader(new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
            List<Child> songs = new com.google.gson.Gson().fromJson(reader,
                    new com.google.gson.reflect.TypeToken<ArrayList<Child>>() {}.getType());
            return songs == null || songs.isEmpty() ? null : songs;
        } catch (Exception e) {
            Log.w(TAG, "could not read the saved song list: " + e);
            return null;
        }
    }

    private static void writeAllSongsToDisk(String server, List<Child> songs) {
        List<Child> copy = new ArrayList<>(songs);
        new Thread(() -> {
            java.io.File file = allSongsFile(server);
            java.io.File tmp = new java.io.File(file.getPath() + ".tmp");
            try (java.io.Writer writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(tmp), java.nio.charset.StandardCharsets.UTF_8)) {
                new com.google.gson.Gson().toJson(copy, writer);
            } catch (Exception e) {
                Log.w(TAG, "could not save the song list: " + e);
                tmp.delete();
                return;
            }
            if (!tmp.renameTo(file)) tmp.delete();
        }).start();
    }

    private void loadAllSongsPage(MutableLiveData<List<Child>> target, List<Child> acc, int offset, boolean progressive) {
        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3("", ALL_SONGS_PAGE, offset, 0, 0, 0, 0)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<Child> page = null;
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getSubsonicResponse().getSearchResult3() != null) {
                            page = response.body().getSubsonicResponse().getSearchResult3().getSongs();
                        }
                        if (page != null) acc.addAll(page);
                        boolean more = page != null && page.size() == ALL_SONGS_PAGE;
                        // A failed page on a quiet refresh keeps the list that is already showing.
                        if (progressive || (!more && page != null)) target.setValue(new ArrayList<>(acc));
                        if (more) {
                            loadAllSongsPage(target, acc, offset + ALL_SONGS_PAGE, progressive);
                        } else {
                            if (page != null) writeAllSongsToDisk(allSongsServer, acc);
                            allSongsLoadedAt = System.currentTimeMillis();
                            allSongsLoading = false;
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        if (progressive) target.setValue(new ArrayList<>(acc));
                        allSongsLoading = false;
                    }
                });
    }

    public MutableLiveData<List<Child>> getStarredSongs(boolean random, int size) {
        MutableLiveData<List<Child>> starredSongs = new MutableLiveData<>(Collections.emptyList());

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getStarred2().getSongs();

                            if (songs != null) {
                                if (!random) {
                                    starredSongs.setValue(songs);
                                } else {
                                    Collections.shuffle(songs);
                                    starredSongs.setValue(songs.subList(0, Math.min(size, songs.size())));
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
                });

        return starredSongs;
    }

    /**
     * Used by ViewModels. Updates the LiveData list incrementally as songs are found.
     */
    public MutableLiveData<List<Child>> getInstantMix(String id, SeedType type, int count) {
        MutableLiveData<List<Child>> instantMix = new MutableLiveData<>(new ArrayList<>());
        Set<String> trackIds = new HashSet<>();

        performSmartMix(id, type, count, songs -> {
            List<Child> current = instantMix.getValue();
            if (current != null) {
                for (Child s : songs) {
                    if (!trackIds.contains(s.getId())) {
                        current.add(s);
                        trackIds.add(s.getId());
                    }
                }

                if (current.size() < count / 2) {
                    fetchSimilarOnly(id, count, remainder -> {
                        for (Child r : remainder) {
                            if (!trackIds.contains(r.getId())) {
                                current.add(r);
                                trackIds.add(r.getId());
                            }
                        }
                        instantMix.postValue(current);
                    });
                } else {
                    instantMix.postValue(current);
                }
            }
        });

        return instantMix;
    }

    /**
     * Overloaded method used by other Repositories
     */
    public void getInstantMix(String id, SeedType type, int count, MediaCallbackInternal callback) {
        new MediaCallbackAccumulator(callback, count).start(id, type);
    }

    private class MediaCallbackAccumulator {
        private final MediaCallbackInternal originalCallback;
        private final int targetCount;
        private final List<Child> accumulatedSongs = new ArrayList<>();
        private final Set<String> trackIds = new HashSet<>();
        private boolean isComplete = false;
        
        MediaCallbackAccumulator(MediaCallbackInternal callback, int count) {
            this.originalCallback = callback;
            this.targetCount = count;
        }
        
        void start(String id, SeedType type) {
            performSmartMix(id, type, targetCount, this::onBatchReceived);
        }
        
        private void onBatchReceived(List<Child> batch) {
            if (isComplete || batch == null || batch.isEmpty()) {
                return;
            }

            int added = 0;
            for (Child song : batch) {
                if (!trackIds.contains(song.getId()) && accumulatedSongs.size() < targetCount) {
                    trackIds.add(song.getId());
                    accumulatedSongs.add(song);
                    added++;
                }
            }
            
            if (accumulatedSongs.size() >= targetCount) {
                originalCallback.onSongsAvailable(new ArrayList<>(accumulatedSongs));
                isComplete = true;
            }
        }

    }

    private void performSmartMix(final String id, final SeedType type, final int count, final MediaCallbackInternal callback) {
        switch (type) {
            case ARTIST:
                fetchSimilarByArtist(id, count, callback);
                break;
            case ALBUM:
                fetchAlbumSongs(id, count, callback);
                break;
            case TRACK:
                fetchSingleTrackThenSimilar(id, count, callback);
                break;
        }
    }

    private void fetchAlbumSongs(String albumId, int count, MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false).getBrowsingClient().getAlbum(albumId).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && 
                    response.body().getSubsonicResponse().getAlbum() != null) {
                    List<Child> albumSongs = response.body().getSubsonicResponse().getAlbum().getSongs();
                    if (albumSongs != null && !albumSongs.isEmpty()) {
                        int fromAlbum = Math.min(count, albumSongs.size());
                        List<Child> limitedAlbumSongs = albumSongs.subList(0, fromAlbum);
                        callback.onSongsAvailable(new ArrayList<>(limitedAlbumSongs));

                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchAlbumSongsThenSimilar.onFailure()", t);
            }
        });
    }

    private void fetchSimilarByArtist(String artistId, final int count, final MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(artistId, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<Child> similar = extractSongs(response, "similarSongs2");
                        Log.d(TAG, "fetchSimilarByArtist.onResponse() - similar songs: " + similar.size());

                        if (!similar.isEmpty()) {
                            List<Child> limitedSimilar = similar.subList(0, Math.min(count, similar.size()));
                            callback.onSongsAvailable(limitedSimilar);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "fetchSimilarByArtist.onFailure()", t);
                    }
                });
    }

    private void fetchSingleTrackThenSimilar(String trackId, int count, MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false).getBrowsingClient().getSong(trackId).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Child song = response.body().getSubsonicResponse().getSong();
                    if (song != null) {
                        callback.onSongsAvailable(Collections.singletonList(song));
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchSingleTrackThenSimilar.onFailure()", t);
            }
        });
    }

    private void fetchSimilarOnly(String id, int count, MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false).getBrowsingClient().getSimilarSongs(id, count).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                List<Child> songs = extractSongs(response, "similarSongs");
                if (!songs.isEmpty()) {
                    int limit = Math.min(count, songs.size());
                    callback.onSongsAvailable(songs.subList(0, limit));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchSimilarOnly.onFailure()", t);
            }
        });
    }


    public MutableLiveData<List<Child>> getContinuousMix(String trackId, String artistId, int count) {
        MutableLiveData<List<Child>> continuousMix = new MutableLiveData<>();

        if (artistId != null && !artistId.isEmpty()) {
            App.getSubsonicClientInstance(false)
                    .getBrowsingClient()
                    .getSimilarSongs2(artistId, count)
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            List<Child> songs = extractSongs(response, "similarSongs2");
                            if (!songs.isEmpty()) {
                                continuousMix.postValue(songs);
                            } else {
                                fetchContinuousFallback(trackId, count, continuousMix);
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            fetchContinuousFallback(trackId, count, continuousMix);
                        }
                    });
        } else {
            fetchContinuousFallback(trackId, count, continuousMix);
        }

        return continuousMix;
    }

    private void fetchContinuousFallback(String trackId, int count, MutableLiveData<List<Child>> target) {
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs(trackId, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        target.postValue(extractSongs(response, "similarSongs"));
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        target.postValue(new ArrayList<>());
                    }
                });
    }

    private List<Child> extractSongs(Response<ApiResponse> response, String type) {
        if (response.isSuccessful() && response.body() != null) {
            SubsonicResponse res = response.body().getSubsonicResponse();
            List<Child> list = null;
            if (type.equals("similarSongs") && res.getSimilarSongs() != null) {
                list = res.getSimilarSongs().getSongs();
            } else if (type.equals("similarSongs2") && res.getSimilarSongs2() != null) {
                list = res.getSimilarSongs2().getSongs();
            }
            return (list != null) ? list : new ArrayList<>();
        }

        return new ArrayList<>();
    }

    public MutableLiveData<List<Child>> getRandomSample(int number, Integer fromYear, Integer toYear) {
        MutableLiveData<List<Child>> randomSongsSample = new MutableLiveData<>();
        App.getSubsonicClientInstance(false).getAlbumSongListClient().getRandomSongs(number, fromYear, toYear).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                List<Child> songs = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null) {
                    List<Child> returned = response.body().getSubsonicResponse().getRandomSongs().getSongs();
                    if (returned != null) {
                        songs.addAll(returned);
                    }
                }
                randomSongsSample.setValue(songs);
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return randomSongsSample;
    }

    public MutableLiveData<List<Child>> getRandomSampleWithGenre(int number, Integer fromYear, Integer toYear, String genre) {
        MutableLiveData<List<Child>> randomSongsSample = new MutableLiveData<>();

        App.getSubsonicClientInstance(false).getAlbumSongListClient().getRandomSongs(number, fromYear, toYear, genre).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                List<Child> songs = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null) {
                    List<Child> returned = response.body().getSubsonicResponse().getRandomSongs().getSongs();
                    if (returned != null) {
                        songs.addAll(returned);
                    }
                }
                randomSongsSample.setValue(songs);
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return randomSongsSample;
    }

    public void scrobble(String id, boolean submission) {
        App.getSubsonicClientInstance(false).getMediaAnnotationClient().scrobble(id, submission).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {}
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
    }

    public void setRating(String id, int rating) {
        App.getSubsonicClientInstance(false).getMediaAnnotationClient().setRating(id, rating).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {}
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
    }

    public MutableLiveData<List<Child>> getSongsByGenre(String id, int page) {
        MutableLiveData<List<Child>> songsByGenre = new MutableLiveData<>();
        App.getSubsonicClientInstance(false).getAlbumSongListClient().getSongsByGenre(id, 100, 100 * page).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSongsByGenre() != null) {
                    songsByGenre.setValue(response.body().getSubsonicResponse().getSongsByGenre().getSongs());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return songsByGenre;
    }

    public MutableLiveData<List<Child>> getSongsByGenres(ArrayList<String> genresId) {
        MutableLiveData<List<Child>> songsByGenre = new MutableLiveData<>();
        for (String id : genresId) {
            App.getSubsonicClientInstance(false).getAlbumSongListClient().getSongsByGenre(id, 500, 0).enqueue(new Callback<ApiResponse>() {
                @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                    List<Child> songs = new ArrayList<>();
                    if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSongsByGenre() != null) {
                        List<Child> returned = response.body().getSubsonicResponse().getSongsByGenre().getSongs();
                        if (returned != null) {
                            songs.addAll(returned);
                        }
                    }
                    songsByGenre.setValue(songs);
                }
                @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
            });
        }
        return songsByGenre;
    }

    public MutableLiveData<Child> getSong(String id) {
        MutableLiveData<Child> song = new MutableLiveData<>();
        App.getSubsonicClientInstance(false).getBrowsingClient().getSong(id).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    song.setValue(response.body().getSubsonicResponse().getSong());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return song;
    }

    public MutableLiveData<String> getSongLyrics(Child song) {
        MutableLiveData<String> lyrics = new MutableLiveData<>(null);
        App.getSubsonicClientInstance(false).getMediaRetrievalClient().getLyrics(song.getArtist(), song.getTitle()).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getLyrics() != null) {
                    lyrics.setValue(response.body().getSubsonicResponse().getLyrics().getValue());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return lyrics;
    }
}