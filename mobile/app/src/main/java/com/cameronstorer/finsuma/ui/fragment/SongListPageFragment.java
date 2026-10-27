package com.cameronstorer.finsuma.ui.fragment;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.cameronstorer.finsuma.util.TileSizeManager;
import com.cameronstorer.finsuma.util.FinsumaGrid;
import com.cameronstorer.finsuma.ui.adapter.SongCardAdapter;
import androidx.recyclerview.widget.GridLayoutManager;

import com.cameronstorer.finsuma.R;
import com.cameronstorer.finsuma.databinding.FragmentSongListPageBinding;
import com.cameronstorer.finsuma.helper.recyclerview.PaginationScrollListener;
import com.cameronstorer.finsuma.interfaces.ClickCallback;
import com.cameronstorer.finsuma.service.MediaManager;
import com.cameronstorer.finsuma.service.MediaService;
import com.cameronstorer.finsuma.subsonic.models.Child;
import com.cameronstorer.finsuma.ui.activity.MainActivity;
import com.cameronstorer.finsuma.ui.adapter.SongHorizontalAdapter;
import com.cameronstorer.finsuma.util.Constants;
import com.cameronstorer.finsuma.viewmodel.PlaybackViewModel;
import com.cameronstorer.finsuma.viewmodel.SongListPageViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.List;

@UnstableApi
public class SongListPageFragment extends Fragment implements ClickCallback {
    private static final String TAG = "SongListPageFragment";

    private FragmentSongListPageBinding bind;
    private MainActivity activity;
    private SongListPageViewModel songListPageViewModel;
    private PlaybackViewModel playbackViewModel;

    private SongHorizontalAdapter songHorizontalAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private boolean isLoading = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentSongListPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        songListPageViewModel = new ViewModelProvider(requireActivity()).get(SongListPageViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        Bundle args = getArguments();
        if (args == null) {
            if (activity != null && activity.navController != null) activity.navController.navigateUp();
            return view;
        }

        init(args);
        initAppBar();
        initButtons();
        initSongListView();
        initSwipeToRefresh();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeMediaBrowser();

        MediaManager.registerPlaybackObserver(mediaBrowserListenableFuture, playbackViewModel);
        observePlayback();
    }

    @Override
    public void onResume() {
        super.onResume();
        setMediaBrowserListenableFuture();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init(Bundle args) {
        if (args.getString(Constants.MEDIA_ALL_SONGS) != null) {
            songListPageViewModel.title = Constants.MEDIA_ALL_SONGS;
            songListPageViewModel.toolbarTitle = getString(R.string.finsuma_tracks_title);
            bind.pageTitleLabel.setText(R.string.finsuma_tracks_title);
        } else if (args.getString(Constants.MEDIA_RECENTLY_PLAYED) != null) {
            songListPageViewModel.title = Constants.MEDIA_RECENTLY_PLAYED;
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_recently_played);
            bind.pageTitleLabel.setText(R.string.song_list_page_recently_played);
        } else if (args.getString(Constants.MEDIA_MOST_PLAYED) != null) {
            songListPageViewModel.title = Constants.MEDIA_MOST_PLAYED;
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_most_played);
            bind.pageTitleLabel.setText(R.string.song_list_page_most_played);
        } else if (args.getString(Constants.MEDIA_RECENTLY_ADDED) != null) {
            songListPageViewModel.title = Constants.MEDIA_RECENTLY_ADDED;
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_recently_added);
            bind.pageTitleLabel.setText(R.string.song_list_page_recently_added);
        } else if (args.getString(Constants.MEDIA_BY_GENRE) != null) {
            songListPageViewModel.title = Constants.MEDIA_BY_GENRE;
            songListPageViewModel.genre = args.getParcelable(Constants.GENRE_OBJECT);
            songListPageViewModel.toolbarTitle = songListPageViewModel.genre.getGenre();
            bind.pageTitleLabel.setText(songListPageViewModel.genre.getGenre());
        } else if (args.getString(Constants.MEDIA_BY_ARTIST) != null) {
            songListPageViewModel.title = Constants.MEDIA_BY_ARTIST;
            songListPageViewModel.artist = args.getParcelable(Constants.ARTIST_OBJECT);
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_top, songListPageViewModel.artist.getName());
            bind.pageTitleLabel.setText(getString(R.string.song_list_page_top, songListPageViewModel.artist.getName()));
        } else if (args.getString(Constants.MEDIA_BY_GENRES) != null) {
            songListPageViewModel.title = Constants.MEDIA_BY_GENRES;
            songListPageViewModel.filters = args.getStringArrayList("filters_list");
            songListPageViewModel.filterNames = args.getStringArrayList("filter_name_list");
            songListPageViewModel.toolbarTitle = songListPageViewModel.getFiltersTitle();
            bind.pageTitleLabel.setText(songListPageViewModel.getFiltersTitle());
        } else if (args.getString(Constants.MEDIA_BY_YEAR) != null) {
            songListPageViewModel.title = Constants.MEDIA_BY_YEAR;
            songListPageViewModel.year = args.getInt("year_object");
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_year, songListPageViewModel.year);
            bind.pageTitleLabel.setText(getString(R.string.song_list_page_year, songListPageViewModel.year));
        } else if (args.getString(Constants.MEDIA_STARRED) != null) {
            songListPageViewModel.title = Constants.MEDIA_STARRED;
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_starred);
            bind.pageTitleLabel.setText(R.string.song_list_page_starred);
        } else if (args.getString(Constants.MEDIA_DOWNLOADED) != null) {
            songListPageViewModel.title = Constants.MEDIA_DOWNLOADED;
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_downloaded);
            bind.pageTitleLabel.setText(getString(R.string.song_list_page_downloaded));
        } else if (args.getParcelable(Constants.ALBUM_OBJECT) != null) {
            songListPageViewModel.album = args.getParcelable(Constants.ALBUM_OBJECT);
            songListPageViewModel.title = Constants.MEDIA_FROM_ALBUM;
            songListPageViewModel.toolbarTitle = songListPageViewModel.album.getName();
            bind.pageTitleLabel.setText(songListPageViewModel.album.getName());
        }
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            // Tracks is a bottom-bar tab, so it has no back arrow.
            boolean isTab = Constants.MEDIA_ALL_SONGS.equals(songListPageViewModel.title);
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(!isTab);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(!isTab);
            if (isTab) {
                com.cameronstorer.finsuma.util.FinsumaTabBar.compact(bind.appBarLayout, bind.toolbar, getString(R.string.finsuma_nav_tracks));
            }
        }

        bind.toolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            activity.navController.navigateUp();
        });

        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (bind == null || Constants.MEDIA_ALL_SONGS.equals(songListPageViewModel.title)) return;

            if ((bind.albumInfoSector.getHeight() + verticalOffset) < bind.albumInfoSector.getHeight() / 3) {
                bind.toolbar.setTitle(songListPageViewModel.toolbarTitle);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    private void initButtons() {
        songListPageViewModel.getSongList().observe(getViewLifecycleOwner(), songs -> {
            if (bind != null) {
                setSongListPageSorter();

                bind.songListShuffleImageView.setOnClickListener(v -> {
                    // Shuffle a copy: the list is shared with the Tracks screen (and its cache).
                    List<Child> shuffled = new java.util.ArrayList<>(songs);
                    Collections.shuffle(shuffled);
                    MediaManager.startQueue(mediaBrowserListenableFuture, shuffled.subList(0, Math.min(500, shuffled.size())), 0);
                    activity.setBottomSheetInPeek(true);
                });
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initSongListView() {
        bind.songListRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.songListRecyclerView.setHasFixedSize(true);

        songHorizontalAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        bind.songListRecyclerView.setAdapter(songHorizontalAdapter);

        // Finsuma: optional grid of cover tiles that mirrors the list (sort/search included).
        songCardAdapter = new SongCardAdapter(this);
        songHorizontalAdapter.addListListener((previous, current) -> songCardAdapter.setItems(current));
        applyGridMode(FinsumaGrid.isGrid(requireContext(), "songs"));
        setMediaBrowserListenableFuture();
        reapplyPlayback();
        songListPageViewModel.getSongList().observe(getViewLifecycleOwner(), songs -> {
            isLoading = false;
            songHorizontalAdapter.setItems(songs);
            String saved = savedSort();
            if (saved != null) songHorizontalAdapter.sort(saved);
            reapplyPlayback();
            setSongListPageSubtitle(songs);
        });

        bind.songListRecyclerView.addOnScrollListener(new PaginationScrollListener((LinearLayoutManager) bind.songListRecyclerView.getLayoutManager()) {
            @Override
            protected void loadMoreItems() {
                isLoading = true;
                songListPageViewModel.getSongsByPage(getViewLifecycleOwner());
            }

            @Override
            public boolean isLoading() {
                return isLoading;
            }
        });

        bind.songListRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.songListSortImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.sort_song_popup_menu));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (Constants.MEDIA_ALL_SONGS.equals(songListPageViewModel.title)) {
            com.cameronstorer.finsuma.util.FinsumaTabBar.addActions(menu, com.cameronstorer.finsuma.util.FinsumaTabBar.DOWNLOAD
                    | com.cameronstorer.finsuma.util.FinsumaTabBar.SORT | com.cameronstorer.finsuma.util.FinsumaTabBar.SHUFFLE);
        }
        inflater.inflate(R.menu.toolbar_menu, menu);
        gridToggle = FinsumaGrid.addToggle(menu, FinsumaGrid.isGrid(requireContext(), "songs"));

        MenuItem searchItem = menu.findItem(R.id.action_search);

        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                songHorizontalAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(0, 0, 0, 0); // Finsuma: was -32, which cut off the icon
    }

    private SongCardAdapter songCardAdapter;
    private MenuItem gridToggle;

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.finsuma_action_download) {
            com.cameronstorer.finsuma.util.FinsumaService.showSiteChooser(requireContext(), activity.navController);
            return true;
        }
        if (item.getItemId() == R.id.finsuma_action_sort) {
            showPopupMenu(com.cameronstorer.finsuma.util.FinsumaTabBar.anchor(bind.toolbar, item.getItemId()), R.menu.sort_song_popup_menu);
            return true;
        }
        if (item.getItemId() == R.id.finsuma_action_shuffle) {
            bind.songListShuffleImageView.performClick();
            return true;
        }
        if (item.getItemId() == R.id.finsuma_grid_toggle) {
            boolean grid = !FinsumaGrid.isGrid(requireContext(), "songs");
            FinsumaGrid.setGrid(requireContext(), "songs", grid);
            applyGridMode(grid);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyGridMode(boolean grid) {
        if (bind == null) return;
        if (grid) {
            int span = TileSizeManager.getInstance().getTileSpanCount(requireContext());
            com.cameronstorer.finsuma.util.PinchGrid.setupCards(bind.songListRecyclerView, "songs", span, px -> {
                songCardAdapter.setSizePx(px);
                songCardAdapter.notifyDataSetChanged();
            });
            songCardAdapter.setItems(songHorizontalAdapter.getCurrentItems());
            bind.songListRecyclerView.setAdapter(songCardAdapter);
        } else {
            bind.songListRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            bind.songListRecyclerView.setAdapter(songHorizontalAdapter);
        }
        FinsumaGrid.updateToggle(gridToggle, grid);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // Finsuma: remember each list's sort, show it checked, and jump to the top after sorting.
    private static final java.util.Map<String, Integer> SORT_ITEMS = new java.util.HashMap<>();
    static {
        SORT_ITEMS.put(Constants.MEDIA_BY_TITLE, R.id.menu_song_sort_name);
        SORT_ITEMS.put(Constants.MEDIA_MOST_RECENTLY_STARRED, R.id.menu_song_sort_most_recently_starred);
        SORT_ITEMS.put(Constants.MEDIA_LEAST_RECENTLY_STARRED, R.id.menu_song_sort_least_recently_starred);
        SORT_ITEMS.put(Constants.SORT_SONG_ARTIST, R.id.menu_song_sort_artist);
        SORT_ITEMS.put(Constants.SORT_SONG_ALBUM, R.id.menu_song_sort_album);
        SORT_ITEMS.put(Constants.SORT_SONG_YEAR, R.id.menu_song_sort_year);
        SORT_ITEMS.put(Constants.SORT_SONG_RECENTLY_ADDED, R.id.menu_song_sort_recently_added);
        SORT_ITEMS.put(Constants.SORT_SONG_MOST_PLAYED, R.id.menu_song_sort_most_played);
        SORT_ITEMS.put(Constants.SORT_SONG_RATING, R.id.menu_song_sort_rating);
    }

    private String sortKey() {
        return "finsuma_song_sort_" + songListPageViewModel.title;
    }

    private String savedSort() {
        return com.cameronstorer.finsuma.App.getInstance().getPreferences().getString(sortKey(), null);
    }

    private void applySort(String order) {
        com.cameronstorer.finsuma.App.getInstance().getPreferences().edit().putString(sortKey(), order).apply();
        songHorizontalAdapter.sort(order, () -> {
            if (bind != null) bind.songListRecyclerView.scrollToPosition(0);
        });
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());
        Integer current = SORT_ITEMS.get(savedSort());
        com.cameronstorer.finsuma.util.FinsumaTabBar.checkSort(popup.getMenu(), current);

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_song_sort_name) {
                applySort(Constants.MEDIA_BY_TITLE);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_song_sort_most_recently_starred) {
                applySort(Constants.MEDIA_MOST_RECENTLY_STARRED);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_song_sort_least_recently_starred) {
                applySort(Constants.MEDIA_LEAST_RECENTLY_STARRED);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_song_sort_artist) {
                applySort(Constants.SORT_SONG_ARTIST);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_song_sort_album) {
                applySort(Constants.SORT_SONG_ALBUM);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_song_sort_year) {
                applySort(Constants.SORT_SONG_YEAR);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_song_sort_recently_added) {
                applySort(Constants.SORT_SONG_RECENTLY_ADDED);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_song_sort_most_played) {
                applySort(Constants.SORT_SONG_MOST_PLAYED);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_song_sort_rating) {
                applySort(Constants.SORT_SONG_RATING);
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void setSongListPageSubtitle(List<Child> children) {
        if (children == null) return;
        switch (songListPageViewModel.title) {
            case Constants.MEDIA_ALL_SONGS:
                bind.pageSubtitleLabel.setText(getString(R.string.generic_list_page_count, children.size()));
                bind.toolbar.setSubtitle(getResources().getQuantityString(R.plurals.finsuma_song_count, children.size(), children.size()));
                break;
            case Constants.MEDIA_BY_GENRE:
                bind.pageSubtitleLabel.setText(children.size() < songListPageViewModel.maxNumberByGenre ?
                        getString(R.string.generic_list_page_count, children.size()) :
                        getString(R.string.generic_list_page_count_unknown, songListPageViewModel.maxNumberByGenre)
                );
                break;
            case Constants.MEDIA_BY_YEAR:
                bind.pageSubtitleLabel.setText(children.size() < songListPageViewModel.maxNumberByYear ?
                        getString(R.string.generic_list_page_count, children.size()) :
                        getString(R.string.generic_list_page_count_unknown, songListPageViewModel.maxNumberByYear)
                );
                break;
            case Constants.MEDIA_BY_ARTIST:
            case Constants.MEDIA_BY_GENRES:
            case Constants.MEDIA_STARRED:
                bind.pageSubtitleLabel.setText(getString(R.string.generic_list_page_count, children.size()));
                break;
        }
    }

    private void setSongListPageSorter() {
        switch (songListPageViewModel.title) {
            case Constants.MEDIA_BY_GENRE:
            case Constants.MEDIA_BY_YEAR:
                bind.songListSortImageView.setVisibility(View.GONE);
                break;
            case Constants.MEDIA_BY_ARTIST:
            case Constants.MEDIA_BY_GENRES:
            case Constants.MEDIA_STARRED:
            case Constants.MEDIA_ALL_SONGS:
                bind.songListSortImageView.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        hideKeyboard(requireView());
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    private void observePlayback() {
        playbackViewModel.getCurrentSongId().observe(getViewLifecycleOwner(), id -> {
            if (songHorizontalAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (songHorizontalAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void reapplyPlayback() {
        if (songHorizontalAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            Boolean playing = playbackViewModel.getIsPlaying().getValue();
            songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
        }
    }

    private void setMediaBrowserListenableFuture() {
        songHorizontalAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }

    public void initSwipeToRefresh() {
        bind.swipeSongListToRefresh.setOnRefreshListener(() -> {
            pullToRefresh();
            bind.swipeSongListToRefresh.setRefreshing(false);
        });
    }

    private void pullToRefresh() {
        com.cameronstorer.finsuma.repository.SongRepository.invalidateAllSongs();
        LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
        songListPageViewModel.getSongList().observe(lifecycleOwner, songs -> {
            isLoading = false;
            songHorizontalAdapter.setItems(songs);
            reapplyPlayback();
            setSongListPageSubtitle(songs);
        });

    }
}