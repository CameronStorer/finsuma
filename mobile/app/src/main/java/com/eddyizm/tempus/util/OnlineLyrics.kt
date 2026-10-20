package com.eddyizm.tempus.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Line
import com.eddyizm.tempus.subsonic.models.LyricsList
import com.eddyizm.tempus.subsonic.models.StructuredLyrics
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Finsuma: lyrics from the internet when the music server has none, the way the desktop app
 * (Feishin) finds lyrics for nearly every song: LRCLIB (exact lookup, then search) and NetEase,
 * best match by title, artist and length, synced lyrics preferred. Results are cached.
 */
object OnlineLyrics {
    private const val TAG = "OnlineLyrics"
    private const val USER_AGENT = "Finsuma (https://github.com/CameronStorer/finsuma)"

    /** Worst acceptable match (0 = perfect, 1 = unrelated), like Feishin's 0.55 threshold. */
    private const val MAX_SCORE = 0.45

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val executor = Executors.newFixedThreadPool(2)
    private val searchExecutor = Executors.newCachedThreadPool() // separate, so lookups never wait on each other
    private val main = Handler(Looper.getMainLooper())

    /** Song id -> lyrics (or NONE when nothing was found), so each song is looked up once. */
    private val cache = LruCache<String, LyricsList>(100)
    private val NONE = LyricsList()

    private data class Candidate(
        val source: String,
        val id: String,
        val title: String,
        val artist: String,
        val durationSec: Double?,
        val synced: Boolean,
        val lyrics: String?, // LRCLIB search results already include the lyrics
        var score: Double = 1.0,
    )

    /** Looks up lyrics for [song]; [callback] gets a LyricsList or null, on the main thread. */
    @JvmStatic
    fun find(song: Child, callback: (LyricsList?) -> Unit) {
        val title = song.title
        if (title.isNullOrBlank()) {
            callback(null)
            return
        }
        cache.get(song.id)?.let {
            callback(if (it === NONE) null else it)
            return
        }
        executor.execute {
            val result = try {
                lookup(song)
            } catch (t: Throwable) {
                Log.w(TAG, "lookup failed: $t")
                null
            }
            cache.put(song.id, result ?: NONE)
            main.post { callback(result) }
        }
    }

    private fun lookup(song: Child): LyricsList? {
        val title = song.title ?: return null
        val artist = song.artist ?: ""
        val duration = song.duration?.toDouble()

        // 1. LRCLIB exact lookup (fast, and accurate when it knows the song).
        lrclibGet(title, artist, song.album, duration)?.let { return toLyricsList(it, title, artist) }

        // 2. Search LRCLIB and NetEase together and take the best match.
        val lrclib = searchExecutor.submitSafely { lrclibSearch(title, artist) }
        val netease = neteaseSearch(title, artist)
        val candidates = (lrclib.getSafely() + netease).onEach { it.score = score(it, title, artist, duration) }
            .filter { it.score <= MAX_SCORE }
            // Better match first; between close matches, prefer synced lyrics.
            .sortedWith(compareBy({ it.score - if (it.synced) 0.05 else 0.0 }))

        for (candidate in candidates.take(3)) {
            val text = candidate.lyrics ?: if (candidate.source == "netease") neteaseLyrics(candidate.id) else null
            if (!text.isNullOrBlank()) {
                Log.d(TAG, "lyrics for \"$title\" from ${candidate.source} (score ${"%.2f".format(candidate.score)})")
                return toLyricsList(text, title, artist)
            }
        }
        return null
    }

    // ------------------------------------------------------------------ LRCLIB

    private fun lrclibGet(title: String, artist: String, album: String?, duration: Double?): String? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply { if (!album.isNullOrBlank()) addQueryParameter("album_name", album) }
            .apply { if (duration != null && duration > 0) addQueryParameter("duration", duration.toInt().toString()) }
            .build()
        val json = getJson(url.toString()) as? JSONObject ?: return null
        if (json.optBoolean("instrumental")) return null
        return json.optString("syncedLyrics").nonEmpty() ?: json.optString("plainLyrics").nonEmpty()
    }

    private fun lrclibSearch(title: String, artist: String): List<Candidate> {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .apply { if (artist.isNotBlank()) addQueryParameter("artist_name", artist) }
            .build()
        val array = getJson(url.toString()) as? JSONArray ?: return emptyList()
        return (0 until minOf(array.length(), 15)).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val synced = o.optString("syncedLyrics").nonEmpty()
            val plain = o.optString("plainLyrics").nonEmpty()
            if (synced == null && plain == null) return@mapNotNull null
            Candidate(
                source = "lrclib",
                id = o.optString("id"),
                title = o.optString("trackName", o.optString("name")),
                artist = o.optString("artistName"),
                durationSec = o.optDouble("duration").takeIf { !it.isNaN() },
                synced = synced != null,
                lyrics = synced ?: plain,
            )
        }
    }

    // ------------------------------------------------------------------ NetEase

    private fun neteaseSearch(title: String, artist: String): List<Candidate> {
        val url = "https://music.163.com/api/search/get".toHttpUrl().newBuilder()
            .addQueryParameter("s", "$artist $title".trim())
            .addQueryParameter("type", "1")
            .addQueryParameter("limit", "8")
            .addQueryParameter("offset", "0")
            .build()
        val json = getJson(url.toString()) as? JSONObject ?: return emptyList()
        val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        return (0 until songs.length()).mapNotNull { i ->
            val o = songs.optJSONObject(i) ?: return@mapNotNull null
            val artists = o.optJSONArray("artists")
            val names = (0 until (artists?.length() ?: 0)).joinToString(", ") {
                artists!!.optJSONObject(it)?.optString("name") ?: ""
            }
            Candidate(
                source = "netease",
                id = o.optLong("id").toString(),
                title = o.optString("name"),
                artist = names,
                durationSec = o.optLong("duration", 0).takeIf { it > 0 }?.div(1000.0),
                synced = true,
                lyrics = null,
            )
        }
    }

    private fun neteaseLyrics(id: String): String? {
        val url = "https://music.163.com/api/song/lyric?id=$id&lv=-1&kv=-1&tv=-1"
        val json = getJson(url) as? JSONObject ?: return null
        val lrc = json.optJSONObject("lrc")?.optString("lyric").nonEmpty() ?: return null
        // NetEase marks songs without real lyrics with a single "pure music" line.
        return if (lrc.lines().count { it.isNotBlank() } <= 2 && lrc.contains("纯音乐")) null else lrc
    }

    // ------------------------------------------------------------------ matching

    /** 0 = perfect match, 1 = unrelated: title and artist similarity, with a length check. */
    private fun score(c: Candidate, title: String, artist: String, duration: Double?): Double {
        val titleScore = 1 - similarity(clean(c.title), clean(title))
        val artistScore = if (artist.isBlank()) 0.0 else 1 - artistSimilarity(c.artist, artist)
        var s = max(titleScore, artistScore)
        if (duration != null && duration > 0 && c.durationSec != null) {
            val off = abs(c.durationSec - duration)
            if (off > 10) s += 0.3 else if (off > 4) s += 0.1
        }
        return s
    }

    /** Best match between any listed artist on either side ("A & B", "A, B", "A feat. B"). */
    private fun artistSimilarity(a: String, b: String): Double {
        val split = Regex("\\s*(,|&|/|;| feat\\.? | ft\\.? | x | and )\\s*", RegexOption.IGNORE_CASE)
        val left = a.split(split).map(::clean).filter { it.isNotEmpty() } + clean(a)
        val right = b.split(split).map(::clean).filter { it.isNotEmpty() } + clean(b)
        return left.maxOf { l -> right.maxOf { r -> similarity(l, r) } }
    }

    /** Lowercase, no accents, no "(feat. ...)" / "- Remastered" / brackets, letters and digits only. */
    private fun clean(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        t = t.replace(Regex("\\((feat|ft|with|prod)[^)]*\\)|\\[[^]]*]"), " ")
        t = t.replace(Regex(" - (remaster|live|radio edit|single version|mono|stereo|explicit).*$"), "")
        return t.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }

    /** 1 - normalized edit distance. */
    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a.contains(b) || b.contains(a)) return 0.9
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return 1.0 - prev[b.length].toDouble() / max(a.length, b.length)
    }

    // ------------------------------------------------------------------ parsing

    private val TIME = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    /** LRC (synced) or plain text into the app's lyrics model. */
    private fun toLyricsList(text: String, title: String, artist: String): LyricsList? {
        val lines = ArrayList<Line>()
        var synced = false
        for (raw in text.lines()) {
            val stamps = TIME.findAll(raw).toList()
            if (stamps.isEmpty()) {
                // Plain lyrics; skip LRC tags like [ar:...].
                if (raw.isNotBlank() && !raw.trim().matches(Regex("^\\[[a-zA-Z]+:.*]$"))) {
                    lines.add(Line().apply { value = raw.trim() })
                }
                continue
            }
            synced = true
            val value = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val min = m.groupValues[1].toInt()
                val sec = m.groupValues[2].toInt()
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; 3 -> frac.toInt(); else -> 0 }
                lines.add(Line().apply { start = (min * 60 + sec) * 1000 + ms; this.value = value })
            }
        }
        if (synced) {
            lines.removeAll { it.start == null }
            lines.sortBy { it.start }
        }
        if (lines.none { it.value.isNotBlank() }) return null
        return LyricsList().apply {
            structuredLyrics = listOf(StructuredLyrics().apply {
                this.synced = synced
                displayTitle = title
                displayArtist = artist
                line = lines
            })
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun getJson(url: String): Any? = try {
        http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { res ->
            if (!res.isSuccessful) return@use null
            val body = res.body?.string() ?: return@use null
            if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
        }
    } catch (t: Throwable) {
        Log.d(TAG, "request failed: ${url.substringBefore('?')}: $t")
        null
    }

    private fun String?.nonEmpty(): String? = if (this.isNullOrBlank() || this == "null") null else this

    private fun <T> java.util.concurrent.ExecutorService.submitSafely(task: () -> List<T>) =
        submit<List<T>> { try { task() } catch (t: Throwable) { emptyList() } }

    private fun <T> java.util.concurrent.Future<List<T>>.getSafely(): List<T> =
        try { get(10, TimeUnit.SECONDS) } catch (t: Throwable) { emptyList() }
}
