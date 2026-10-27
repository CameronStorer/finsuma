package com.cameronstorer.finsuma.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
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
import androidx.annotation.OptIn;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cameronstorer.finsuma.R;
import com.cameronstorer.finsuma.databinding.FragmentGenreCatalogueBinding;
import com.cameronstorer.finsuma.helper.recyclerview.GridItemDecoration;
import com.cameronstorer.finsuma.interfaces.ClickCallback;
import com.cameronstorer.finsuma.ui.activity.MainActivity;
import com.cameronstorer.finsuma.ui.adapter.GenreCatalogueAdapter;
import com.cameronstorer.finsuma.util.Constants;
import com.cameronstorer.finsuma.util.Preferences;
import com.cameronstorer.finsuma.util.TileSizeManager;
import com.cameronstorer.finsuma.viewmodel.GenreCatalogueViewModel;

@OptIn(markerClass = UnstableApi.class)
public class GenreCatalogueFragment extends Fragment implements ClickCallback {
    private FragmentGenreCatalogueBinding bind;
    private MainActivity activity;
    private GenreCatalogueViewModel genreCatalogueViewModel;

    private GenreCatalogueAdapter genreCatalogueAdapter;

    private int spanCount = 2;
    private int tileSpacing = 20;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentGenreCatalogueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        genreCatalogueViewModel = new ViewModelProvider(requireActivity()).get(GenreCatalogueViewModel.class);
        genreCatalogueViewModel.loadGenreList();

        TileSizeManager.getInstance().calculateGenreSize( requireContext() );
        spanCount = TileSizeManager.getInstance().getGenreSpanCount( requireContext() );
        tileSpacing = TileSizeManager.getInstance().getGenreSpacing( requireContext() );

        init();
        initAppBar();
        initGenreCatalogueView();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init() {
        bind.filterGenresTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_genreCatalogueFragment_to_filterFragment));
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        // Finsuma: as the bottom-bar Library tab: no back arrow, compact top bar.
        boolean isTab = com.cameronstorer.finsuma.util.FinsumaTabBar.isTab(getArguments());
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(!isTab);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(!isTab);
        }
        if (isTab) com.cameronstorer.finsuma.util.FinsumaTabBar.compact(bind.appBarLayout, bind.toolbar, getString(R.string.genre_catalogue_title));

        bind.toolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            activity.navController.navigateUp();
        });

        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (bind == null || com.cameronstorer.finsuma.util.FinsumaTabBar.isTab(getArguments())) return;

            if ((bind.genreInfoSector.getHeight() + verticalOffset) < bind.genreInfoSector.getHeight() / 3) {
                bind.toolbar.setTitle(R.string.genre_catalogue_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initGenreCatalogueView() {
        com.cameronstorer.finsuma.util.PinchGrid.setupCatalogue(bind.genreCatalogueRecyclerView, "genres", spanCount, tileSpacing);
        bind.genreCatalogueRecyclerView.setHasFixedSize(true);

        genreCatalogueAdapter = new GenreCatalogueAdapter(this);
        genreCatalogueAdapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        bind.genreCatalogueRecyclerView.setAdapter(genreCatalogueAdapter);

        genreCatalogueViewModel.getGenreList().observe(getViewLifecycleOwner(), genres -> genreCatalogueAdapter.setItems(genres) );

        bind.genreCatalogueRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.genreListSortImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.sort_genre_popup_menu));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (com.cameronstorer.finsuma.util.FinsumaTabBar.isTab(getArguments())) {
            com.cameronstorer.finsuma.util.FinsumaTabBar.addActions(menu, com.cameronstorer.finsuma.util.FinsumaTabBar.TO_ALBUMS | com.cameronstorer.finsuma.util.FinsumaTabBar.SORT);
        }
        inflater.inflate(R.menu.toolbar_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);

        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                genreCatalogueAdapter.getFilter().filter(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                genreCatalogueAdapter.getFilter().filter(newText);
                return true;
            }
        });

        searchView.setPadding(0, 0, 0, 0); // Finsuma: was -32, which cut off the icon
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.finsuma_action_albums) {
            com.cameronstorer.finsuma.util.FinsumaTabBar.openTab(activity.navController, R.id.albumCatalogueFragment);
            return true;
        }
        if (item.getItemId() == R.id.finsuma_action_sort) {
            showPopupMenu(com.cameronstorer.finsuma.util.FinsumaTabBar.anchor(bind.toolbar, item.getItemId()), R.menu.sort_genre_popup_menu);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_genre_sort_name) {
                genreCatalogueAdapter.sort(Constants.GENRE_ORDER_BY_NAME);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_genre_sort_random) {
                genreCatalogueAdapter.sort(Constants.GENRE_ORDER_BY_RANDOM);
                return true;
            }

            return false;
        });

        popup.show();
    }

    @Override
    public void onGenreClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songListPageFragment, bundle);
        hideKeyboard(requireView());
    }
}