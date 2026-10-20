package com.eddyizm.tempus.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.Player
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.SubsonicResponse
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.Preferences
import retrofit2.Call
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finsuma Auto DJ, ported from the desktop app (features/player/auto-dj).
 *
 * When the songs left after the current one drop below the "timing" setting, it appends
 * "item count" songs: similar songs first, then the same genre, the same artist and finally
 * random songs ("similar" strategy), or just random songs ("library random" strategy).
 */
object AutoDj {
    private const val TAG = "AutoDj"
    const val STRATEGY_SIMILAR = "similar"
    const val STRATEGY_LIBRARY_RANDOM = "library_random"

    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private data class Seed(
        val songId: String,
        val artistId: String?,
        val genre: String?,
        val queueIds: Set<String>,
    )

    private data class Settings(
        val strategy: String,
        val itemCount: Int,
        val onlySimilar: Boolean,
        val allowDuplicates: Boolean,
    )

    /** Songs that will play after the current one (follows shuffle order, ignores repeat). */
    @JvmStatic
    fun remainingAfterCurrent(player: Player): Int {
        val timeline = player.currentTimeline
        var index = player.currentMediaItemIndex
        if (timeline.isEmpty || index == C.INDEX_UNSET) return 0
        var count = 0
        while (count < 1000) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) break
            count++
        }
        return count
    }

    /** Call on the main thread whenever the current song or the queue changes. */
    @JvmStatic
    fun check(context: Context, queueTarget: MediaManager.QueueTarget) {
        if (!Preferences.isAutoDjEnabled()) return
        val player = queueTarget.livePlayer() ?: return
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return
        val current = player.currentMediaItem ?: return
        val extras = current.mediaMetadata.extras
        if (extras?.getString("type") != Constants.MEDIA_TYPE_MUSIC) return
        if (remainingAfterCurrent(player) >= Preferences.getAutoDjTiming()) return
        if (!running.compareAndSet(false, true)) return

        val queueIds = HashSet<String>()
        for (i in 0 until player.mediaItemCount) queueIds.add(player.getMediaItemAt(i).mediaId)
        val seed = Seed(
            songId = current.mediaId,
            artistId = extras.getString("artistId"),
            genre = extras.getString("genre"),
            queueIds = queueIds,
        )
        val settings = Settings(
            strategy = Preferences.getAutoDjStrategy(),
            itemCount = Preferences.getAutoDjItemCount(),
            onlySimilar = Preferences.isAutoDjOnlySimilar(),
            allowDuplicates = Preferences.isAutoDjAllowDuplicates(),
        )
        val appContext = context.applicationContext

        Log.d(TAG, "triggered for ${seed.songId}")
        executor.execute {
            val songs = try {
                collect(seed, settings)
            } catch (t: Throwable) {
                Log.e(TAG, "failed", t)
                emptyList()
            }
            main.post {
                try {
                    if (songs.isNotEmpty() && Preferences.isAutoDjEnabled() && queueTarget.livePlayer() != null) {
                        MediaManager.enqueue(queueTarget, songs, false)
                        Toast.makeText(
                            appContext,
                            appContext.resources.getQuantityString(R.plurals.auto_dj_added, songs.size, songs.size),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } finally {
                    running.set(false)
                }
            }
        }
    }

    private fun collect(seed: Seed, s: Settings): List<Child> {
        val selected = LinkedHashMap<String, Child>()
        fun available(song: Child) =
            s.allowDuplicates || (song.id !in seed.queueIds && song.id !in selected)
        fun remaining() = s.itemCount - selected.size
        fun take(pool: List<Child>) {
            pool.filter(::available).shuffled().take(remaining().coerceAtLeast(0))
                .forEach { selected[it.id] = it }
        }
        fun done() = remaining() <= 0 || (s.onlySimilar && selected.isNotEmpty())

        if (s.strategy == STRATEGY_LIBRARY_RANDOM) {
            take(random(maxOf(s.itemCount * 3, 50), null))
            return selected.values.toList()
        }

        // 1. Similar songs (Navidrome needs an agent such as Last.fm for these)
        take(songs(client().browsingClient.getSimilarSongs(seed.songId, maxOf(s.itemCount * 2, 10))) {
            it.similarSongs?.songs
        })
        if (done()) return selected.values.toList()

        // 2. Same genre
        if (!seed.genre.isNullOrBlank()) {
            take(random(50, seed.genre))
            if (done()) return selected.values.toList()
        }

        // 3. Same artist: songs from a few of their albums
        if (!seed.artistId.isNullOrBlank()) {
            take(artistSongs(seed.artistId))
            if (done()) return selected.values.toList()
        }

        // 4. Anything
        take(random(50, null))
        return selected.values.toList()
    }

    private fun client() = App.getSubsonicClientInstance(false)

    private fun random(size: Int, genre: String?): List<Child> {
        val c = client().albumSongListClient
        val call = if (genre == null) c.getRandomSongs(size, null, null) else c.getRandomSongs(size, null, null, genre)
        return songs(call) { it.randomSongs?.songs }
    }

    private fun artistSongs(artistId: String): List<Child> {
        val albums = body(client().browsingClient.getArtist(artistId))?.artist?.albums.orEmpty()
        return albums.mapNotNull { it.id }.shuffled().take(3).flatMap { albumId ->
            songs(client().browsingClient.getAlbum(albumId)) { it.album?.songs }
        }
    }

    private fun songs(call: Call<ApiResponse>, pick: (SubsonicResponse) -> List<Child>?): List<Child> =
        body(call)?.let(pick).orEmpty()

    private fun body(call: Call<ApiResponse>): SubsonicResponse? = try {
        val response = call.execute()
        if (response.isSuccessful) response.body()?.subsonicResponse else null
    } catch (t: Throwable) {
        Log.w(TAG, "request failed: $t")
        null
    }
}
