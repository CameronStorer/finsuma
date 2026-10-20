package com.eddyizm.tempus.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.navigation.NavController;

import com.eddyizm.tempus.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Finsuma: talks to the Finsuma server (server/yt-download) that saves YouTube/SoundCloud audio
 * as MP3s into the music library. Same API as the desktop app.
 */
public final class FinsumaService {
    private static final String PREFS = "finsuma_service";
    private static final String KEY_URL = "url";
    private static final String KEY_TOKEN = "token";
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private static final Set<String> YOUTUBE_HOSTS = new HashSet<>(Arrays.asList(
            "m.youtube.com", "music.youtube.com", "www.youtube.com", "youtu.be", "youtube.com"));
    private static final Set<String> SOUNDCLOUD_HOSTS = new HashSet<>(Arrays.asList(
            "m.soundcloud.com", "on.soundcloud.com", "soundcloud.com", "www.soundcloud.com"));
    private static final Set<String> SC_NOT_USERS = new HashSet<>(Arrays.asList(
            "charts", "discover", "feed", "jobs", "messages", "mobile", "notifications", "pages",
            "people", "popular", "search", "settings", "signin", "stations", "stream", "tags", "upload", "you"));
    private static final Set<String> SC_NOT_TRACKS = new HashSet<>(Arrays.asList(
            "albums", "comments", "followers", "following", "likes", "popular-tracks", "reposts", "sets",
            "spotlight", "tracks"));

    private FinsumaService() {}

    // ------------------------------------------------------------------ what can be downloaded

    /** single = a song/video, playlist = a playlist/set; both false = nothing to download here. */
    public static final class Target {
        public final boolean single;
        public final boolean playlist;
        public final boolean soundcloud;

        Target(boolean single, boolean playlist, boolean soundcloud) {
            this.single = single;
            this.playlist = playlist;
            this.soundcloud = soundcloud;
        }

        public boolean ok() {
            return single || playlist;
        }
    }

    public static Target targetFor(String url) {
        if (url == null) return new Target(false, false, false);
        Uri uri = Uri.parse(url);
        String host = uri.getHost() == null ? "" : uri.getHost();
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!"https".equals(uri.getScheme())) return new Target(false, false, false);
        if (SOUNDCLOUD_HOSTS.contains(host)) {
            List<String> parts = uri.getPathSegments();
            if ("on.soundcloud.com".equals(host)) return new Target(!parts.isEmpty(), false, true);
            if (parts.isEmpty() || SC_NOT_USERS.contains(parts.get(0))) return new Target(false, false, true);
            boolean set = parts.size() >= 3 && "sets".equals(parts.get(1));
            boolean track = parts.size() == 2 && !SC_NOT_TRACKS.contains(parts.get(1));
            return new Target(track, set, true);
        }
        if (YOUTUBE_HOSTS.contains(host)) {
            boolean video = ("/watch".equals(path) && uri.getQueryParameter("v") != null)
                    || path.startsWith("/shorts/")
                    || ("youtu.be".equals(host) && path.length() > 1);
            boolean list = uri.getQueryParameter("list") != null;
            return new Target(video, list, false);
        }
        return new Target(false, false, false);
    }

    // ------------------------------------------------------------------ settings

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Addresses to try, in order: the saved one, the music server's own URL + /finsuma (when the
     * server is set up behind the same URL), then port 3200 on the music server's machine.
     */
    public static List<String> candidateUrls(Context context) {
        List<String> urls = new java.util.ArrayList<>();
        String saved = prefs(context).getString(KEY_URL, null);
        if (saved != null && !saved.isEmpty()) urls.add(saved);
        String server = Preferences.getServer();
        if (server != null && !server.isEmpty()) urls.add(server.replaceAll("/+$", "") + "/finsuma");
        String local = Preferences.getLocalAddress();
        if (local != null && !local.isEmpty()) urls.add(local.replaceAll("/+$", "") + "/finsuma");
        String byHost = defaultUrl();
        if (!urls.contains(byHost)) urls.add(byHost);
        return urls;
    }

    /** Default: port 3200 on the machine the music server runs on (local address first). */
    public static String defaultUrl() {
        String server = Preferences.getLocalAddress();
        if (server == null || server.isEmpty()) server = Preferences.getServer();
        if (server == null) return "http://";
        Uri uri = Uri.parse(server);
        return "http://" + (uri.getHost() == null ? "" : uri.getHost()) + ":3200";
    }

    public static String url(Context context) {
        return prefs(context).getString(KEY_URL, defaultUrl());
    }

    public static String token(Context context) {
        return prefs(context).getString(KEY_TOKEN, "");
    }

    /** Asks for the service address and token, then runs {@code then}. */
    public static void showSettings(Context context, Runnable then) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        EditText url = new EditText(context);
        url.setHint("Service address, e.g. http://192.168.1.10:3200");
        url.setText(url(context));
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        EditText token = new EditText(context);
        token.setHint("Download token (YT_TOKEN on the server)");
        token.setText(token(context));
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(url);
        box.addView(token);
        new MaterialAlertDialogBuilder(context)
                .setTitle("Finsuma server")
                .setMessage("Downloads are saved by the Finsuma server next to your music server.")
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    prefs(context).edit()
                            .putString(KEY_URL, url.getText().toString().trim().replaceAll("/+$", ""))
                            .putString(KEY_TOKEN, token.getText().toString().trim())
                            .apply();
                    if (then != null) then.run();
                })
                .show();
    }

    // ------------------------------------------------------------------ actions

    public static void showSiteChooser(Context context, NavController navController) {
        String[] sites = {"YouTube", "SoundCloud"};
        String[] urls = {"https://m.youtube.com/", "https://m.soundcloud.com/discover"};
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.finsuma_download_from)
                .setItems(sites, (dialog, which) -> {
                    Bundle args = new Bundle();
                    args.putString(Constants.FINSUMA_SITE_URL, urls[which]);
                    navController.navigate(R.id.finsumaWebFragment, args);
                })
                .show();
    }

    /** The download popup: Video/Track or Playlist, then sends the URL to the server. */
    public static void showDownloadDialog(Context context, String url) {
        Target target = targetFor(url);
        if (!target.ok()) {
            Toast.makeText(context, "Open a song or playlist first, then press download.", Toast.LENGTH_LONG).show();
            return;
        }
        if (token(context).isEmpty()) {
            showSettings(context, () -> showDownloadDialog(context, url));
            return;
        }
        String single = target.soundcloud ? "Track" : "Video";
        String[] options = target.single && target.playlist
                ? new String[]{single, "Whole playlist"}
                : target.playlist ? new String[]{"Whole playlist"} : new String[]{single};
        final int[] chosen = {0};
        new MaterialAlertDialogBuilder(context)
                .setTitle("Save to your music server")
                .setSingleChoiceItems(options, 0, (d, which) -> chosen[0] = which)
                .setNeutralButton("Server…", (d, w) -> showSettings(context, null))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Download", (d, w) -> {
                    boolean playlist = options[chosen[0]].equals("Whole playlist");
                    send(context, url, playlist ? "playlist" : "single");
                })
                .show();
    }

    private static void send(Context context, String url, String mode) {
        Handler main = new Handler(Looper.getMainLooper());
        String body;
        try {
            body = new JSONObject().put("url", url).put("mode", mode).toString();
        } catch (Exception e) {
            return;
        }
        List<String> candidates = candidateUrls(context);
        new Thread(() -> {
            for (String base : candidates) {
                try (Response health = HTTP.newCall(new Request.Builder().url(base + "/health").build()).execute()) {
                    if (!health.isSuccessful()) continue;
                } catch (Exception e) {
                    continue;
                }
                // Remember the address that answers.
                prefs(context).edit().putString(KEY_URL, base).apply();
                Request request = new Request.Builder()
                        .url(base + "/download")
                        .header("X-Token", token(context))
                        .post(RequestBody.create(body, MediaType.get("application/json")))
                        .build();
                try (Response response = HTTP.newCall(request).execute()) {
                    int code = response.code();
                    main.post(() -> {
                        if (code == 202) {
                            Toast.makeText(context, "Downloading to your music server", Toast.LENGTH_SHORT).show();
                        } else if (code == 401) {
                            Toast.makeText(context, "Wrong download token", Toast.LENGTH_LONG).show();
                            showSettings(context, null);
                        } else {
                            Toast.makeText(context, "Download failed (" + code + ")", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (IOException e) {
                    main.post(() -> Toast.makeText(context, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
                return;
            }
            main.post(() -> Toast.makeText(context,
                    "Can't reach the Finsuma server (tried " + candidates.size() + " addresses).",
                    Toast.LENGTH_LONG).show());
        }).start();
    }
}
