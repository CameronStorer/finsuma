package com.cameronstorer.finsuma.ui.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cameronstorer.finsuma.databinding.FragmentFinsumaWebBinding;
import com.cameronstorer.finsuma.util.Constants;
import com.cameronstorer.finsuma.util.FinsumaService;

/** Finsuma: YouTube or SoundCloud in the app, with a button that saves what's open to the server. */
public class FinsumaWebFragment extends Fragment {
    private FragmentFinsumaWebBinding bind;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        bind = FragmentFinsumaWebBinding.inflate(inflater, container, false);
        return bind.getRoot();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        WebView web = bind.finsumaWebView;
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return false; // stay inside the app
            }

            @Override
            public void doUpdateVisitedHistory(WebView v, String url, boolean isReload) {
                updateFab(url);
            }
        });

        String url = getArguments() != null ? getArguments().getString(Constants.FINSUMA_SITE_URL) : null;
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(url != null ? url : "https://m.youtube.com/");

        bind.finsumaDownloadFab.setOnClickListener(v ->
                FinsumaService.showDownloadDialog(requireContext(), web.getUrl()));

        // Back goes back in the site first.
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (bind != null && bind.finsumaWebView.canGoBack()) {
                    bind.finsumaWebView.goBack();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void updateFab(String url) {
        if (bind != null) bind.finsumaDownloadFab.setAlpha(FinsumaService.targetFor(url).ok() ? 1f : 0.55f);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (bind != null) bind.finsumaWebView.saveState(outState);
    }

    @Override
    public void onDestroyView() {
        if (bind != null) bind.finsumaWebView.destroy();
        bind = null;
        super.onDestroyView();
    }
}
