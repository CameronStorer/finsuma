package com.eddyizm.tempus.ui.fragment;

import android.annotation.SuppressLint;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.ArrayList;
import com.eddyizm.tempus.util.TileSizeManager;
import com.eddyizm.tempus.util.FinsumaGrid;
import com.eddyizm.tempus.ui.adapter.PlaylistCardAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentPlaylistCatalogueBinding;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.PlaylistHorizontalAdapter;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.viewmodel.PlaylistCatalogueViewModel;

@UnstableApi
public class PlaylistCatalogueFragment extends Fragment implements ClickCallback {
    private FragmentPlaylistCatalogueBinding bind;
    private MainActivity activity;
    private PlaylistCatalogueViewModel playlistCatalogueViewModel;

    private PlaylistHorizontalAdapter playlistHorizontalAdapter;
    private PlaylistCardAdapter playlistCardAdapter;
    private MenuItem gridToggle;

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.finsuma_grid_toggle) {
            boolean grid = !FinsumaGrid.isGrid(requireContext(), "playlists");
            FinsumaGrid.setGrid(requireContext(), "playlists", grid);
            applyGridMode(grid);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyGridMode(boolean grid) {
        if (bind == null) return;
        if (grid) {
            int span = TileSizeManager.getInstance().getTileSpanCount(requireContext());
            bind.playlistCatalogueRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), span));
            if (playlistHorizontalAdapter.getCurrentItems() != null)
                playlistCardAdapter.setItems(new ArrayList<>(playlistHorizontalAdapter.getCurrentItems()));
            bind.playlistCatalogueRecyclerView.setAdapter(playlistCardAdapter);
        } else {
            bind.playlistCatalogueRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            bind.playlistCatalogueRecyclerView.setAdapter(playlistHorizontalAdapter);
        }
        FinsumaGrid.updateToggle(gridToggle, grid);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentPlaylistCatalogueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        playlistCatalogueViewModel = new ViewModelProvider(requireActivity()).get(PlaylistCatalogueViewModel.class);

        Bundle args = getArguments();
        if (args == null) {
            if (activity != null && activity.navController != null) activity.navController.navigateUp();
            return view;
        }

        init(args);
        initAppBar();
        initPlaylistCatalogueView();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init(Bundle args) {
        if (args.getString(Constants.PLAYLIST_ALL) != null) {
            playlistCatalogueViewModel.setType(Constants.PLAYLIST_ALL);
        } else if (args.getString(Constants.PLAYLIST_DOWNLOADED) != null) {
            playlistCatalogueViewModel.setType(Constants.PLAYLIST_DOWNLOADED);
        }
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            // Finsuma: no back arrow when this is the bottom-bar Playlists tab.
            boolean isTab = getArguments() != null && getArguments().getBoolean("finsuma_tab", false);
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(!isTab);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(!isTab);
        }

        bind.toolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            activity.navController.navigateUp();
        });


        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (bind == null) return;

            if ((bind.albumInfoSector.getHeight() + verticalOffset) < bind.albumInfoSector.getHeight() / 3) {
                bind.toolbar.setTitle(R.string.playlist_catalogue_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playlistCatalogueViewModel.getSortedPlaylistList().observe(getViewLifecycleOwner(), playlists -> {
            if (playlists != null) {
                android.util.Log.d("TempusLog", "UI Update: Received " + playlists.size() + " items");
                playlistHorizontalAdapter.setItems(playlists);
                playlistHorizontalAdapter.notifyDataSetChanged(); 
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initPlaylistCatalogueView() {
        bind.playlistCatalogueRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playlistCatalogueRecyclerView.setHasFixedSize(true);

        playlistHorizontalAdapter = new PlaylistHorizontalAdapter(this);
        bind.playlistCatalogueRecyclerView.setAdapter(playlistHorizontalAdapter);

        // Finsuma: optional grid of cover tiles that mirrors the list (search included).
        playlistCardAdapter = new PlaylistCardAdapter(this);
        playlistHorizontalAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                if (playlistHorizontalAdapter.getCurrentItems() != null)
                    playlistCardAdapter.setItems(new ArrayList<>(playlistHorizontalAdapter.getCurrentItems()));
            }
        });
        applyGridMode(FinsumaGrid.isGrid(requireContext(), "playlists"));

        playlistCatalogueViewModel.getPlaylistList(getViewLifecycleOwner());

        bind.playlistCatalogueRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.playlistListSortImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.sort_playlist_popup_menu));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_menu, menu);
        gridToggle = FinsumaGrid.addToggle(menu, FinsumaGrid.isGrid(requireContext(), "playlists"));

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
                playlistHorizontalAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(-32, 0, 0, 0);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.menu_playlist_sort_name) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_NAME);
            } else if (id == R.id.menu_playlist_sort_pinned) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_PINNED);
            } else if (id == R.id.menu_playlist_sort_random) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_RANDOM);
            } else if (id == R.id.menu_playlist_sort_date) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_DATE);
            } else if (id == R.id.menu_playlist_sort_songs) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_SONGS);
            } else if (id == R.id.menu_playlist_sort_last_played) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_LAST_PLAYED);
            } else if (id == R.id.menu_playlist_sort_last_updated) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_LAST_UPDATED);
            } else if (id == R.id.menu_playlist_sort_both) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_BOTH);
            }
            return true;
        });
        popup.show();
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        bundle.putBoolean("is_offline", false);
        Navigation.findNavController(requireView()).navigate(R.id.playlistPageFragment, bundle);
        hideKeyboard(requireView());
    }

    @Override
    public void onPlaylistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistRowBottomSheetDialog, bundle);
    }
}