package com.cameronstorer.finsuma.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;
import androidx.viewpager2.widget.ViewPager2;

import com.cameronstorer.finsuma.R;
import com.cameronstorer.finsuma.databinding.FragmentHomeBinding;
import com.cameronstorer.finsuma.navigation.NavigationController;
import com.cameronstorer.finsuma.ui.activity.MainActivity;
import com.cameronstorer.finsuma.ui.fragment.pager.HomePager;
import com.cameronstorer.finsuma.util.Preferences;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Objects;

@UnstableApi
public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding bind;
    private MainActivity activity;
    private NavigationController navigationController;

    private MaterialToolbar materialToolbar;
    private AppBarLayout appBarLayout;
    private TabLayout tabLayout;
    private int radioPage = -1;
    private int podcastPage = -1;

    /** Toolbar button (next to search) that switches the home page between Music, Radio and Podcasts. */
    private void addBrowseMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                MenuItem item = menu.add(Menu.NONE, R.id.finsuma_browse_menu, Menu.NONE, R.string.finsuma_browse);
                item.setIcon(R.drawable.ic_finsuma_radio);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() != R.id.finsuma_browse_menu) return false;
                View anchor = requireActivity().findViewById(R.id.finsuma_browse_menu);
                PopupMenu popup = new PopupMenu(requireContext(), anchor != null ? anchor : bind.getRoot());
                popup.getMenu().add(Menu.NONE, 0, 0, R.string.finsuma_browse_music);
                popup.getMenu().add(Menu.NONE, 1, 1, R.string.finsuma_browse_radio);
                if (podcastPage >= 0) popup.getMenu().add(Menu.NONE, 2, 2, R.string.finsuma_browse_podcasts);
                popup.setOnMenuItemClickListener(choice -> {
                    int page = choice.getItemId() == 1 ? radioPage : choice.getItemId() == 2 ? podcastPage : 0;
                    if (bind != null && page >= 0) bind.homeViewPager.setCurrentItem(page, false);
                    return true;
                });
                popup.show();
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        navigationController = activity.getNavigationController();
        bind = FragmentHomeBinding.inflate(inflater, container, false);
        return bind.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAppBar();
        initHomePager();
    }

    @Override
    public void onStart() {
        super.onStart();

        activity.toggleBottomNavigationBarVisibilityOnOrientationChange();
        activity.setBottomSheetVisibility(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initAppBar() {
        appBarLayout = bind.getRoot().findViewById(R.id.toolbar_fragment);
        materialToolbar = bind.getRoot().findViewById(R.id.toolbar);

        activity.setSupportActionBar(materialToolbar);
        navigationController.setHamburgerMenuForLandscape(activity, materialToolbar);

        Objects.requireNonNull(materialToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));

        tabLayout = new TabLayout(requireContext());
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        tabLayout.setTabMode(TabLayout.MODE_FIXED);

        appBarLayout.addView(tabLayout);
    }

    private void initHomePager() {
        HomePager pager = new HomePager(this);

        // Finsuma: Music is the home page; Radio (and Podcasts, if enabled) open from the toolbar
        // button instead of a tab bar.
        pager.addFragment(new HomeTabMusicFragment(), getString(R.string.home_section_music), R.drawable.ic_home);
        radioPage = pager.getItemCount();
        pager.addFragment(new HomeTabRadioFragment(), getString(R.string.home_section_radio), R.drawable.ic_play_for_work);
        podcastPage = -1;
        if (Preferences.isPodcastSectionVisible()) {
            podcastPage = pager.getItemCount();
            pager.addFragment(new HomeTabPodcastFragment(), getString(R.string.home_section_podcast), R.drawable.ic_graphic_eq);
        }

        bind.homeViewPager.setAdapter(pager);
        bind.homeViewPager.setOffscreenPageLimit(3);
        bind.homeViewPager.setUserInputEnabled(false);

        new TabLayoutMediator(tabLayout, bind.homeViewPager,
                (tab, position) -> {
                    tab.setText(pager.getPageTitle(position));
                    // tab.setIcon(pager.getPageIcon(position));
                }
        ).attach();

        tabLayout.setVisibility(View.GONE);
        addBrowseMenu();

        bind.homeViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                refreshToolbarLibraryScope();
            }
        });
    }

    /**
     * The music library line in the toolbar belongs to the Music tab. Podcast and Radio share the
     * toolbar and are not filtered by library, so the line would name something with nothing to do
     * with what is on screen. Music is always the first page; the other two are optional.
     *
     * The toolbar reads this on every update instead of being pushed the page once, so a page the
     * pager put back on its own cannot leave the line naming the wrong tab.
     */
    public boolean isLibraryScopedTab() {
        return bind != null && bind.homeViewPager.getCurrentItem() == 0;
    }

    private void refreshToolbarLibraryScope() {
        ToolbarFragment toolbarFragment = (ToolbarFragment) getChildFragmentManager().findFragmentById(R.id.toolbar_fragment);
        if (toolbarFragment != null) toolbarFragment.refreshMusicLibraryIndicator();
    }
}
