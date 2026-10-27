package com.cameronstorer.finsuma.ui.dialog

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.media3.session.MediaBrowser
import com.cameronstorer.finsuma.App
import com.cameronstorer.finsuma.R
import com.cameronstorer.finsuma.repository.GenreRepository
import com.cameronstorer.finsuma.service.MediaManager
import com.cameronstorer.finsuma.subsonic.base.ApiResponse
import com.cameronstorer.finsuma.subsonic.models.Child
import com.cameronstorer.finsuma.subsonic.models.SubsonicResponse
import com.cameronstorer.finsuma.util.MusicUtil
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import retrofit2.Call
import java.util.concurrent.Executors

/**
 * Finsuma Play random, ported from the desktop shuffle-all modal: random songs or random albums,
 * optionally limited by year and genre, then Play / Play next / Add last. The choices are
 * remembered like on desktop.
 */
object PlayRandomDialog {
    private const val TAG = "PlayRandom"
    private const val KEY_ALBUMS = "play_random_albums"
    private const val KEY_LIMIT = "play_random_limit"
    private const val KEY_MIN_YEAR = "play_random_min_year"
    private const val KEY_MAX_YEAR = "play_random_max_year"
    private const val KEY_GENRE = "play_random_genre"
    private const val MAX_LIMIT = 500 // same cap as desktop

    private enum class Play { NOW, NEXT, LAST }

    private data class Query(
        val albums: Boolean,
        val limit: Int,
        val minYear: Int?,
        val maxYear: Int?,
        val genre: String?,
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @JvmStatic
    fun show(activity: Activity, browser: ListenableFuture<MediaBrowser>) {
        val prefs = App.getInstance().preferences
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_play_random, null)
        val kind = view.findViewById<MaterialButtonToggleGroup>(R.id.play_random_kind)
        val limit = view.findViewById<EditText>(R.id.play_random_limit)
        val minYear = view.findViewById<EditText>(R.id.play_random_min_year)
        val maxYear = view.findViewById<EditText>(R.id.play_random_max_year)
        val genre = view.findViewById<AutoCompleteTextView>(R.id.play_random_genre)

        kind.check(if (prefs.getBoolean(KEY_ALBUMS, false)) R.id.play_random_kind_albums else R.id.play_random_kind_songs)
        limit.setText(prefs.getInt(KEY_LIMIT, 100).toString())
        prefs.getInt(KEY_MIN_YEAR, 0).takeIf { it > 0 }?.let { minYear.setText(it.toString()) }
        prefs.getInt(KEY_MAX_YEAR, 0).takeIf { it > 0 }?.let { maxYear.setText(it.toString()) }
        genre.setText(prefs.getString(KEY_GENRE, "") ?: "", false)

        // Like desktop, the album list has no "to year" filter.
        fun syncKind() {
            val albums = kind.checkedButtonId == R.id.play_random_kind_albums
            view.findViewById<android.view.View>(R.id.play_random_max_year_layout).isEnabled = !albums
            maxYear.isEnabled = !albums
        }
        kind.addOnButtonCheckedListener { _, _, _ -> syncKind() }
        syncKind()

        val genres = GenreRepository().getGenres(false, -1)
        genres.observeForever(object : androidx.lifecycle.Observer<List<com.cameronstorer.finsuma.subsonic.models.Genre>> {
            override fun onChanged(value: List<com.cameronstorer.finsuma.subsonic.models.Genre>) {
                genres.removeObserver(this)
                genre.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, value.mapNotNull { it.genre }))
            }
        })

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.play_random)
            .setView(view)
            .create()

        fun go(play: Play) {
            val query = Query(
                albums = kind.checkedButtonId == R.id.play_random_kind_albums,
                limit = (limit.text.toString().toIntOrNull() ?: 100).coerceIn(1, MAX_LIMIT),
                minYear = minYear.text.toString().toIntOrNull()?.takeIf { it > 0 },
                maxYear = maxYear.text.toString().toIntOrNull()?.takeIf { it > 0 },
                genre = genre.text.toString().trim().takeIf { it.isNotEmpty() },
            )
            prefs.edit()
                .putBoolean(KEY_ALBUMS, query.albums)
                .putInt(KEY_LIMIT, query.limit)
                .putInt(KEY_MIN_YEAR, query.minYear ?: 0)
                .putInt(KEY_MAX_YEAR, query.maxYear ?: 0)
                .putString(KEY_GENRE, query.genre ?: "")
                .apply()
            dialog.dismiss()
            run(activity.applicationContext, browser, query, play)
        }
        view.findViewById<android.view.View>(R.id.play_random_now).setOnClickListener { go(Play.NOW) }
        view.findViewById<android.view.View>(R.id.play_random_next).setOnClickListener { go(Play.NEXT) }
        view.findViewById<android.view.View>(R.id.play_random_last).setOnClickListener { go(Play.LAST) }
        dialog.show()
    }

    private fun run(context: Context, browser: ListenableFuture<MediaBrowser>, q: Query, play: Play) {
        executor.execute {
            val songs = try {
                if (q.albums) albumSongs(q) else randomSongs(q)
            } catch (t: Throwable) {
                Log.e(TAG, "failed", t)
                emptyList()
            }.toMutableList()
            MusicUtil.ratingFilter(songs)
            main.post {
                if (songs.isEmpty()) {
                    Toast.makeText(context, R.string.play_random_empty, Toast.LENGTH_SHORT).show()
                    return@post
                }
                when (play) {
                    Play.NOW -> MediaManager.startQueue(browser, songs, 0)
                    Play.NEXT -> MediaManager.enqueue(browser, songs, true)
                    Play.LAST -> MediaManager.enqueue(browser, songs, false)
                }
            }
        }
    }

    private fun client() = App.getSubsonicClientInstance(false).albumSongListClient

    private fun randomSongs(q: Query): List<Child> {
        val call = if (q.genre == null) client().getRandomSongs(q.limit, q.minYear, q.maxYear)
        else client().getRandomSongs(q.limit, q.minYear, q.maxYear, q.genre)
        return body(call)?.randomSongs?.songs.orEmpty()
    }

    /** Random albums (their songs in track order, albums in random order), like desktop. */
    private fun albumSongs(q: Query): List<Child> {
        val albums = if (q.genre != null) {
            body(client().getAlbumList2ByGenre(q.genre, q.limit))?.albumList2?.albums.orEmpty().shuffled()
        } else {
            body(client().getAlbumList2("random", q.limit, 0, q.minYear, null))?.albumList2?.albums.orEmpty()
        }
        val browsing = App.getSubsonicClientInstance(false).browsingClient
        return albums.mapNotNull { it.id }.take(q.limit).flatMap { id ->
            body(browsing.getAlbum(id))?.album?.songs.orEmpty()
        }
    }

    private fun body(call: Call<ApiResponse>): SubsonicResponse? = try {
        val response = call.execute()
        if (response.isSuccessful) response.body()?.subsonicResponse else null
    } catch (t: Throwable) {
        Log.w(TAG, "request failed: $t")
        null
    }
}
