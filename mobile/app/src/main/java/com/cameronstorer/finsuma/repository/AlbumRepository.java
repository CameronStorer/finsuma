package com.cameronstorer.finsuma.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import android.util.Log;

import com.cameronstorer.finsuma.App;
import com.cameronstorer.finsuma.interfaces.DecadesCallback;
import com.cameronstorer.finsuma.subsonic.base.ApiResponse;
import com.cameronstorer.finsuma.subsonic.models.AlbumID3;
import com.cameronstorer.finsuma.subsonic.models.AlbumInfo;
import com.cameronstorer.finsuma.subsonic.models.AlbumWithSongsID3;
import com.cameronstorer.finsuma.subsonic.models.Child;
import com.cameronstorer.finsuma.util.Constants.SeedType;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlbumRepository {
    public MutableLiveData<List<AlbumID3>> getAlbums(String type, int size, Integer fromYear, Integer toYear) {
        MutableLiveData<List<AlbumID3>> listLiveAlbums = new MutableLiveData<>(new ArrayList<>());

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2(type, size, 0, fromYear, toYear)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() 
                            && response.body() != null 
                            && response.body().getSubsonicResponse().getAlbumList2() != null 
                            && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            
                            listLiveAlbums.setValue(response.body().getSubsonicResponse().getAlbumList2().getAlbums());
                        } else {
                            Log.e("AlbumRepository", "API Error on getAlbums. HTTP Code: " + response.code());
                            listLiveAlbums.setValue(new ArrayList<>());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e("AlbumRepository", "Network Failure on getAlbums: " + t.getMessage());
                        listLiveAlbums.setValue(new ArrayList<>());
                    }
                });

        return listLiveAlbums;
    }

    public MutableLiveData<List<AlbumID3>> getStarredAlbums(boolean random, int size) {
        MutableLiveData<List<AlbumID3>> starredAlbums = new MutableLiveData<>(new ArrayList<>());

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getStarred2().getAlbums();

                            if (albums != null) {
                                if (random) {
                                    Collections.shuffle(albums);
                                    starredAlbums.setValue(albums.subList(0, Math.min(size, albums.size())));
                                } else {
                                    starredAlbums.setValue(albums);
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return starredAlbums;
    }

    public void setRating(String id, int rating) {
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .setRating(id, rating)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public MutableLiveData<List<Child>> getAlbumTracks(String id) {
        MutableLiveData<List<Child>> albumTracks = new MutableLiveData<>();
        getAlbumTracks(id, albumTracks::setValue);
        return albumTracks;
    }

    /** Callback-based album fetch that always reports (empty list on failure). */
    public void getAlbumTracks(String id, Consumer<List<Child>> onResult) {
        fetchAlbum(id, album -> {
            List<Child> tracks = new ArrayList<>();
            if (album != null && album.getSongs() != null) tracks.addAll(album.getSongs());
            onResult.accept(tracks);
        });
    }

    // Finsuma: albums opened recently are shown from memory; the server is asked again only when
    // the entry is older than ALBUM_CACHE_TTL_MS.
    private static final long ALBUM_CACHE_TTL_MS = 2 * 60 * 1000;
    private static final android.util.LruCache<String, AlbumWithSongsID3> ALBUM_CACHE = new android.util.LruCache<>(30);
    private static final java.util.Map<String, Long> ALBUM_CACHE_TIME = new java.util.concurrent.ConcurrentHashMap<>();

    /** Reports the album now if cached (and fresh enough, only that), else after fetching it. */
    private void fetchAlbum(String id, Consumer<AlbumWithSongsID3> onResult) {
        AlbumWithSongsID3 cached = ALBUM_CACHE.get(id);
        Long time = ALBUM_CACHE_TIME.get(id);
        boolean fresh = cached != null && time != null && System.currentTimeMillis() - time < ALBUM_CACHE_TTL_MS;
        if (cached != null) onResult.accept(cached);
        if (fresh) return;

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbum(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        AlbumWithSongsID3 album = null;
                        if (response.isSuccessful() && response.body() != null) {
                            album = response.body().getSubsonicResponse().getAlbum();
                        }
                        if (album != null) {
                            ALBUM_CACHE.put(id, album);
                            ALBUM_CACHE_TIME.put(id, System.currentTimeMillis());
                            onResult.accept(album);
                        } else if (cached == null) {
                            onResult.accept(null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        if (cached == null) onResult.accept(null);
                    }
                });
    }

    public MutableLiveData<List<AlbumID3>> getArtistAlbums(String id) {
        MutableLiveData<List<AlbumID3>> artistsAlbum = new MutableLiveData<>(new ArrayList<>());

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null && response.body().getSubsonicResponse().getArtist().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getArtist().getAlbums();
                            albums.sort(Comparator.comparing(AlbumID3::getYear));
                            Collections.reverse(albums);
                            artistsAlbum.setValue(albums);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return artistsAlbum;
    }

    public MutableLiveData<AlbumID3> getAlbum(String id) {
        MutableLiveData<AlbumID3> album = new MutableLiveData<>();
        fetchAlbum(id, result -> {
            if (result != null) album.setValue(result);
        });
        return album;
    }

    public MutableLiveData<AlbumInfo> getAlbumInfo(String id) {
        MutableLiveData<AlbumInfo> albumInfo = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbumInfo2(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumInfo() != null) {
                            albumInfo.setValue(response.body().getSubsonicResponse().getAlbumInfo());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return albumInfo;
    }

    public MutableLiveData<List<Child>> getInstantMix(AlbumID3 album, int count) {
        // Delegate to the centralized SongRepository
        return new SongRepository().getInstantMix(album.getId(), SeedType.ALBUM, count);
    }


    public MutableLiveData<List<Integer>> getDecades() {
        MutableLiveData<List<Integer>> decades = new MutableLiveData<>();

        getFirstAlbum(new DecadesCallback() {
            @Override
            public void onLoadYear(int first) {
                getLastAlbum(new DecadesCallback() {
                    @Override
                    public void onLoadYear(int last) {
                        if (first != -1 && last != -1) {
                            List<Integer> decadeList = new ArrayList<>();

                            int startDecade = first - (first % 10);
                            int lastDecade = last - (last % 10);

                            while (startDecade <= lastDecade) {
                                decadeList.add(startDecade);
                                startDecade = startDecade + 10;
                            }

                            decades.setValue(decadeList);
                        }
                    }
                });
            }
        });

        return decades;
    }

    private void getFirstAlbum(DecadesCallback callback) {
        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2("byYear", 1, 0, 1900, Calendar.getInstance().get(Calendar.YEAR))
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumList2() != null && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null && !response.body().getSubsonicResponse().getAlbumList2().getAlbums().isEmpty()) {
                            callback.onLoadYear(response.body().getSubsonicResponse().getAlbumList2().getAlbums().get(0).getYear());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        callback.onLoadYear(-1);
                    }
                });
    }

    private void getLastAlbum(DecadesCallback callback) {
        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2("byYear", 1, 0, Calendar.getInstance().get(Calendar.YEAR), 1900)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumList2() != null && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            if (!response.body().getSubsonicResponse().getAlbumList2().getAlbums().isEmpty() && !response.body().getSubsonicResponse().getAlbumList2().getAlbums().isEmpty()) {
                                callback.onLoadYear(response.body().getSubsonicResponse().getAlbumList2().getAlbums().get(0).getYear());
                            } else {
                                callback.onLoadYear(-1);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        callback.onLoadYear(-1);
                    }
                });
    }
}