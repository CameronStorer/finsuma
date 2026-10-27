package com.cameronstorer.finsuma.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
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
import androidx.annotation.OptIn;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cameronstorer.finsuma.R;
import com.cameronstorer.finsuma.databinding.FragmentAlbumCatalogueBinding;
import com.cameronstorer.finsuma.helper.recyclerview.GridItemDecoration;
import com.cameronstorer.finsuma.interfaces.ClickCallback;
import com.cameronstorer.finsuma.ui.activity.MainActivity;
import com.cameronstorer.finsuma.ui.adapter.AlbumCatalogueAdapter;
import com.cameronstorer.finsuma.util.Constants;
import com.cameronstorer.finsuma.util.Preferences;
import com.cameronstorer.finsuma.util.TileSizeManager;
import com.cameronstorer.finsuma.viewmodel.AlbumCatalogueViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class AlbumCatalogueFragment extends Fragment implements ClickCallback {
    private static final String TAG = "AlbumCatalogueFragment";

    private FragmentAlbumCatalogueBinding bind;
    private MainActivity activity;
    private AlbumCatalogueViewModel albumCatalogueViewModel;
    private int spanCount = 2;
    private int tileSpacing = 20;
    private AlbumCatalogueAdapter albumAdapter;
    private String currentSortOrder;
    private List<com.cameronstorer.finsuma.subsonic.models.AlbumID3> originalAlbums;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        currentSortOrder = Preferences.getAlbumSortOrder();

        initData();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Only re-sort when the sort changed elsewhere; re-sorting here moved the albums (and
        // reshuffled "random") every time the page came back.
        String latestSort = Preferences.getAlbumSortOrder();
        if (!latestSort.equals(currentSortOrder)) {
            currentSortOrder = latestSort;
            applySortToAlbums(currentSortOrder);
            updateSortIndicator();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        albumCatalogueViewModel.stopLoading();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentAlbumCatalogueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        TileSizeManager.getInstance().calculateTileSize( requireContext() );
        spanCount = TileSizeManager.getInstance().getTileSpanCount( requireContext() );
        tileSpacing = TileSizeManager.getInstance().getTileSpacing( requireContext() );

        initAppBar();
        initAlbumCatalogueView();
        initProgressLoader();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initData() {
        albumCatalogueViewModel = new ViewModelProvider(requireActivity()).get(AlbumCatalogueViewModel.class);
        albumCatalogueViewModel.loadAlbumsIfNeeded();
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        // Finsuma: as the bottom-bar Library tab: no back arrow, compact top bar.
        boolean isTab = com.cameronstorer.finsuma.util.FinsumaTabBar.isTab(getArguments());
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(!isTab);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(!isTab);
        }
        if (isTab) com.cameronstorer.finsuma.util.FinsumaTabBar.compact(bind.appBarLayout, bind.toolbar, getString(R.string.album_catalogue_title));

        bind.toolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            activity.navController.navigateUp();
        });


        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (bind == null || com.cameronstorer.finsuma.util.FinsumaTabBar.isTab(getArguments())) return;

            if ((bind.albumInfoSector.getHeight() + verticalOffset) < bind.albumInfoSector.getHeight() / 3) {
                bind.toolbar.setTitle(R.string.album_catalogue_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initAlbumCatalogueView() {
        com.cameronstorer.finsuma.util.PinchGrid.setupCatalogue(bind.albumCatalogueRecyclerView, "albums", spanCount, tileSpacing);
        bind.albumCatalogueRecyclerView.setHasFixedSize(true);

        albumAdapter = new AlbumCatalogueAdapter(this, true);
        albumAdapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        bind.albumCatalogueRecyclerView.setAdapter(albumAdapter);
        albumCatalogueViewModel.getAlbumList().observe(getViewLifecycleOwner(), albums -> {
            originalAlbums = albums;
            currentSortOrder = Preferences.getAlbumSortOrder();
            applySortToAlbums(currentSortOrder);
            updateSortIndicator();
        });

        bind.albumCatalogueRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.albumListSortImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.sort_album_popup_menu));
    }

    private void applySortToAlbums(String sortOrder) {
        if (originalAlbums == null) {
            return;
        }
        albumAdapter.setItemsWithoutFilter(originalAlbums);
        if (sortOrder != null) {
            albumAdapter.sort(sortOrder);
        }
    }

    private void initProgressLoader() {
        albumCatalogueViewModel.getLoadingStatus().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading) {
                bind.albumListSortImageView.setEnabled(false);
                bind.albumListProgressLoader.setVisibility(View.VISIBLE);
            } else {
                bind.albumListSortImageView.setEnabled(true);
                bind.albumListProgressLoader.setVisibility(View.GONE);
            }
        });
    }

    private void updateSortIndicator() {
        if (bind == null) return;

        String sortText = getSortDisplayText(currentSortOrder);
        bind.albumListSortTextView.setText(sortText);
        bind.albumListSortTextView.setVisibility(View.VISIBLE);
    }

    private String getSortDisplayText(String sortOrder) {
        if (sortOrder == null) return "";
        
        switch (sortOrder) {
            case Constants.ALBUM_ORDER_BY_NAME:
                return getString(R.string.menu_sort_name);
            case Constants.ALBUM_ORDER_BY_ARTIST:
                return getString(R.string.menu_group_by_artist);
            case Constants.ALBUM_ORDER_BY_YEAR:
                return getString(R.string.menu_sort_year);
            case Constants.ALBUM_ORDER_BY_RANDOM:
                return getString(R.string.menu_sort_random);
            case Constants.ALBUM_ORDER_BY_RECENTLY_ADDED:
                return getString(R.string.menu_sort_recently_added);
            case Constants.ALBUM_ORDER_BY_RECENTLY_PLAYED:
                return getString(R.string.menu_sort_recently_played);
            case Constants.ALBUM_ORDER_BY_MOST_PLAYED:
                return getString(R.string.menu_sort_most_played);
            case Constants.ALBUM_ORDER_BY_SONG_COUNT:
                return getString(R.string.menu_sort_song_count);
            case Constants.ALBUM_ORDER_BY_DURATION:
                return getString(R.string.finsuma_sort_duration);
            default:
                return "";
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (com.cameronstorer.finsuma.util.FinsumaTabBar.isTab(getArguments())) {
            com.cameronstorer.finsuma.util.FinsumaTabBar.addActions(menu, com.cameronstorer.finsuma.util.FinsumaTabBar.TO_GENRES | com.cameronstorer.finsuma.util.FinsumaTabBar.SORT);
        }
        inflater.inflate(R.menu.toolbar_menu, menu);

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
                albumAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(0, 0, 0, 0); // Finsuma: was -32, which cut off the icon
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.finsuma_action_genres) {
            com.cameronstorer.finsuma.util.FinsumaTabBar.openTab(activity.navController, R.id.genreCatalogueFragment);
            return true;
        }
        if (item.getItemId() == R.id.finsuma_action_sort) {
            showPopupMenu(com.cameronstorer.finsuma.util.FinsumaTabBar.anchor(bind.toolbar, item.getItemId()), R.menu.sort_album_popup_menu);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());
        MenuItem checked = popup.getMenu().findItem(sortMenuId(currentSortOrder));
        if (checked != null) checked.setChecked(true);

        popup.setOnMenuItemClickListener(menuItem -> {
            String newSortOrder = null;
            
            if (menuItem.getItemId() == R.id.menu_album_sort_name) {
                newSortOrder = Constants.ALBUM_ORDER_BY_NAME;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_artist) {
                newSortOrder = Constants.ALBUM_ORDER_BY_ARTIST;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_year) {
                newSortOrder = Constants.ALBUM_ORDER_BY_YEAR;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_random) {
                newSortOrder = Constants.ALBUM_ORDER_BY_RANDOM;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_recently_added) {
                newSortOrder = Constants.ALBUM_ORDER_BY_RECENTLY_ADDED;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_recently_played) {
                newSortOrder = Constants.ALBUM_ORDER_BY_RECENTLY_PLAYED;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_most_played) {
                newSortOrder = Constants.ALBUM_ORDER_BY_MOST_PLAYED;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_song_count) {
                newSortOrder = Constants.ALBUM_ORDER_BY_SONG_COUNT;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_duration) {
                newSortOrder = Constants.ALBUM_ORDER_BY_DURATION;
            }

            if (newSortOrder != null) {
                if (Constants.ALBUM_ORDER_BY_RANDOM.equals(newSortOrder)) AlbumCatalogueAdapter.reshuffle();
                currentSortOrder = newSortOrder;
                Preferences.setAlbumSortOrder(newSortOrder);
                applySortToAlbums(newSortOrder);
                updateSortIndicator();
                return true;
            }

            return false;
        });

        popup.show();
    }

    private static int sortMenuId(String order) {
        if (order == null) return 0;
        switch (order) {
            case Constants.ALBUM_ORDER_BY_NAME: return R.id.menu_album_sort_name;
            case Constants.ALBUM_ORDER_BY_ARTIST: return R.id.menu_album_sort_artist;
            case Constants.ALBUM_ORDER_BY_YEAR: return R.id.menu_album_sort_year;
            case Constants.ALBUM_ORDER_BY_RANDOM: return R.id.menu_album_sort_random;
            case Constants.ALBUM_ORDER_BY_RECENTLY_ADDED: return R.id.menu_album_sort_recently_added;
            case Constants.ALBUM_ORDER_BY_RECENTLY_PLAYED: return R.id.menu_album_sort_recently_played;
            case Constants.ALBUM_ORDER_BY_MOST_PLAYED: return R.id.menu_album_sort_most_played;
            case Constants.ALBUM_ORDER_BY_SONG_COUNT: return R.id.menu_album_sort_song_count;
            case Constants.ALBUM_ORDER_BY_DURATION: return R.id.menu_album_sort_duration;
            default: return 0;
        }
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
        hideKeyboard(requireView());
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }
}