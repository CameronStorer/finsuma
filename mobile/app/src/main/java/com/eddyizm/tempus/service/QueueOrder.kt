package com.eddyizm.tempus.service

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.eddyizm.tempus.App
import com.eddyizm.tempus.repository.QueueRepository
import kotlin.random.Random

/**
 * Play order rules from the Finsuma desktop player (renderer/store/player.store.ts):
 *
 * - "Play next" songs form an Up Next block right after the current song, in the order they
 *   were added. The block keeps playing next even after jumping to another song.
 * - With shuffle on, songs added to the end of the queue (Add to queue, Auto DJ) go to the end
 *   of the play order instead of random spots, so they never land behind the current song.
 * - Turning shuffle on puts the current song first, then Up Next, then every other song
 *   shuffled. Turning it off goes back to the queue order with Up Next still next.
 *
 * Runs on the main thread, called from [BaseMediaService]'s player listener.
 */
@OptIn(UnstableApi::class)
object QueueOrder {
    private const val TAG = "QueueOrder"

    /** Media ids of the timeline as last seen, to tell which songs were just added. */
    private var lastIds: List<String> = emptyList()

    /** Up Next block (media ids), in play order. Saved so it survives an app restart. */
    private const val SAVED_UP_NEXT = "finsuma_up_next"
    private val upNext = ArrayList<String>(loadSaved())

    /** Set after carrying Up Next into a new queue: place the block once the songs show up. */
    private var placeCarriedBlock = false

    /** The Up Next songs themselves, to carry them over when a new queue starts. */
    private val upNextItems = HashMap<String, MediaItem>()

    private fun loadSaved(): List<String> = try {
        App.getInstance().preferences.getString(SAVED_UP_NEXT, null)
            ?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
    } catch (_: Throwable) {
        emptyList()
    }

    private fun save() {
        App.getInstance().preferences.edit().putString(SAVED_UP_NEXT, upNext.joinToString("\n")).apply()
    }

    /** Songs in the Up Next block; MediaManager inserts "play next" songs after them. */
    @JvmStatic
    fun upNextCount(): Int = upNext.size

    private fun ids(p: Player) = List(p.mediaItemCount) { p.getMediaItemAt(it).mediaId }

    private fun counts(ids: List<String>): HashMap<String, Int> {
        val out = HashMap<String, Int>()
        ids.forEach { out[it] = (out[it] ?: 0) + 1 }
        return out
    }

    /** Window indices in the order they will play (repeat ignored). */
    private fun playOrder(p: Player): MutableList<Int> {
        val t = p.currentTimeline
        val out = ArrayList<Int>(t.windowCount)
        if (t.isEmpty) return out
        var w = t.getFirstWindowIndex(p.shuffleModeEnabled)
        while (w != C.INDEX_UNSET && out.size < t.windowCount) {
            out.add(w)
            w = t.getNextWindowIndex(w, Player.REPEAT_MODE_OFF, p.shuffleModeEnabled)
        }
        return out
    }

    private fun setShuffleOrder(p: ExoPlayer, order: List<Int>) {
        if (order.size != p.mediaItemCount || order.toHashSet().size != order.size) return
        p.shuffleOrder = DefaultShuffleOrder(order.toIntArray(), Random.nextLong())
    }

    /** Window indices of the Up Next songs, in block order (never the current song). */
    private fun blockWindows(p: Player, order: List<Int>, exclude: Set<Int> = emptySet()): List<Int> {
        val current = p.currentMediaItemIndex
        val currentPos = order.indexOf(current)
        val used = HashSet<Int>()
        val out = ArrayList<Int>(upNext.size)
        for (id in upNext) {
            // Prefer the copy after the current song in play order, then any other copy.
            val candidates = order.withIndex().filter { (_, w) ->
                w != current && w !in used && w !in exclude && p.getMediaItemAt(w).mediaId == id
            }
            val pick = candidates.firstOrNull { it.index > currentPos } ?: candidates.firstOrNull()
            if (pick != null) {
                used.add(pick.value)
                out.add(pick.value)
            }
        }
        return out
    }

    /** Positions in [new] holding songs that were not in [old] (by id, counting copies). */
    private fun addedIndices(old: List<String>, new: List<String>): List<Int> {
        val oldCounts = counts(old)
        val extra = HashMap<String, Int>()
        for ((id, n) in counts(new)) {
            val more = n - (oldCounts[id] ?: 0)
            if (more > 0) extra[id] = more
        }
        if (extra.isEmpty()) return emptyList()

        // Walk both lists: songs that line up with the old timeline are old, the rest are new.
        val out = ArrayList<Int>()
        var i = 0
        for ((j, id) in new.withIndex()) {
            if (i < old.size && old[i] == id) {
                i++
                continue
            }
            val left = extra[id] ?: 0
            if (left > 0) {
                out.add(j)
                extra[id] = left - 1
                continue
            }
            // An old song after some removed ones: catch the old pointer up to it.
            val k = old.subList(i, old.size).indexOf(id)
            if (k >= 0) i += k + 1
        }
        return out
    }

    /** Debug log: the next songs in play order, with Up Next marked. */
    @JvmStatic
    fun logPlayOrder(p: Player, why: String) {
        if (!Log.isLoggable(TAG, Log.DEBUG) && !com.eddyizm.tempus.BuildConfig.DEBUG) return
        val order = playOrder(p)
        val from = order.indexOf(p.currentMediaItemIndex).coerceAtLeast(0)
        fun title(w: Int) = p.getMediaItemAt(w).mediaMetadata.title.toString()
        val ahead = order.drop(from)
        val next = ahead.take(6).joinToString(" | ") { title(it) }
        val last = if (ahead.size > 9) " ... " + ahead.takeLast(3).joinToString(" | ") { title(it) } else ""
        Log.d(TAG, "$why (shuffle=${p.shuffleModeEnabled}, upNext=${upNext.size}, ${ahead.size} left): $next$last")
    }

    /** Call from onTimelineChanged, before the play-next fixup. */
    @JvmStatic
    fun onTimelineChanged(p: Player) {
        val ids = ids(p)
        val old = lastIds
        lastIds = ids
        if (ids == old) return

        if (ids.isEmpty()) {
            upNext.clear()
            save()
            return
        }

        val added = if (p is ExoPlayer) addedIndices(old, ids) else emptyList()
        val newQueue = old.isEmpty() || added.size == ids.size || MediaManager.justStarted.get()

        // Like desktop, Up Next carries over when a new queue starts (Play now, shuffle play):
        // put any Up Next songs the new queue lacks right after the started song.
        if (newQueue && old.isNotEmpty() && upNext.isNotEmpty() && p.currentMediaItemIndex != C.INDEX_UNSET) {
            val have = counts(ids)
            val missing = upNext.mapNotNull { id ->
                val n = have[id] ?: 0
                if (n > 0) {
                    have[id] = n - 1
                    null
                } else {
                    upNextItems[id]
                }
            }
            if (missing.isNotEmpty()) {
                Log.d(TAG, "Up Next: carrying ${missing.size} song(s) into the new queue")
                // The next timeline change places them (shuffle order included).
                placeCarriedBlock = true
                p.addMediaItems(minOf(p.currentMediaItemIndex + 1, p.mediaItemCount), missing)
                return
            }
        }

        // Drop Up Next songs that are no longer in the queue.
        if (upNext.isNotEmpty()) {
            val left = counts(ids)
            p.currentMediaItem?.mediaId?.let { left[it] = (left[it] ?: 1) - 1 }
            val kept = upNext.filter { id ->
                val n = left[id] ?: 0
                if (n > 0) left[id] = n - 1
                n > 0
            }
            if (kept.size != upNext.size) {
                upNext.clear()
                upNext.addAll(kept)
                upNextItems.keys.retainAll(kept.toSet())
                save()
            }
        }

        if (p !is ExoPlayer) return

        if (placeCarriedBlock) {
            placeCarriedBlock = false
            placeBlockAfterCurrent(p)
            return
        }

        if (newQueue) {
            // A new queue (Play now, shuffle play, restore): the started song first.
            if (p.shuffleModeEnabled) shuffleFromCurrent(p)
            return
        }
        if (added.isEmpty() || !p.shuffleModeEnabled) return

        // Shuffle on: added songs go to the end of the play order, shuffled among themselves.
        // "Play next" songs are then moved up to the Up Next block by applyPlayNext.
        val addedSet = added.toHashSet()
        val order = playOrder(p).filter { it !in addedSet } + added.shuffled()
        setShuffleOrder(p, order)
        Log.d(TAG, "${added.size} added song(s) placed at the end of the shuffle order")
    }

    /**
     * [count] "play next" songs were inserted at timeline [insertPos]. Adds them to the end of
     * the Up Next block and, with shuffle on, puts them there in the play order. Without
     * shuffle, MediaManager already inserted them right after the block.
     */
    @JvmStatic
    fun applyPlayNext(p: Player, insertPos: Int, count: Int) {
        if (insertPos < 0 || count <= 0 || insertPos + count > p.mediaItemCount) return
        val newWindows = (insertPos until insertPos + count).toList()
        val newIds = newWindows.map { p.getMediaItemAt(it).mediaId }
        newWindows.forEach { upNextItems[p.getMediaItemAt(it).mediaId] = p.getMediaItemAt(it) }

        if (p is ExoPlayer && p.shuffleModeEnabled) {
            val newSet = newWindows.toHashSet()
            val base = playOrder(p).filter { it !in newSet }.toMutableList()
            val currentPos = base.indexOf(p.currentMediaItemIndex)
            if (currentPos >= 0) {
                val lastBlockPos = blockWindows(p, base, newSet).maxOfOrNull { base.indexOf(it) } ?: -1
                base.addAll(maxOf(currentPos, lastBlockPos) + 1, newWindows)
                setShuffleOrder(p, base)
            }
        }
        upNext.addAll(newIds)
        save()
        // Already handled: don't treat these songs as "added to the end" when the timeline
        // change for this insert is reported afterwards.
        lastIds = ids(p)
        Log.d(TAG, "Up Next: ${upNext.size} song(s)")
    }

    /** Call from onMediaItemTransition. */
    @JvmStatic
    fun onMediaItemTransition(p: Player) {
        if (upNext.isEmpty()) return
        val current = p.currentMediaItem?.mediaId ?: return
        val i = upNext.indexOf(current)
        if (i >= 0) {
            // Playing a song from the block: it and anything before it in the block are done.
            repeat(i + 1) { upNextItems.remove(upNext.removeAt(0)) }
            save()
            return
        }
        // Jumped to another song. If the block is still ahead of it, leave everything as is;
        // otherwise move the block right after this song so Up Next keeps playing next.
        val order = playOrder(p)
        val currentPos = order.indexOf(p.currentMediaItemIndex)
        val block = blockWindows(p, order)
        if (block.isEmpty() || block.all { order.indexOf(it) > currentPos }) return
        placeBlockAfterCurrent(p)
    }

    /** Call from onShuffleModeEnabledChanged. */
    @JvmStatic
    fun onShuffleModeChanged(p: Player, enabled: Boolean) {
        if (p.mediaItemCount == 0 || p.currentMediaItemIndex == C.INDEX_UNSET) return
        if (!enabled) placeBlockAfterCurrent(p) else if (p is ExoPlayer) shuffleFromCurrent(p)
    }

    /** Current song first, then Up Next, then every other song shuffled. */
    private fun shuffleFromCurrent(p: ExoPlayer) {
        val current = p.currentMediaItemIndex
        if (current == C.INDEX_UNSET) return
        val block = blockWindows(p, playOrder(p))
        val skip = block.toHashSet().apply { add(current) }
        val rest = (0 until p.mediaItemCount).filter { it !in skip }.shuffled()
        setShuffleOrder(p, listOf(current) + block + rest)
    }

    private fun placeBlockAfterCurrent(p: Player) {
        if (upNext.isEmpty() || p !is ExoPlayer || p.currentMediaItemIndex == C.INDEX_UNSET) return

        if (p.shuffleModeEnabled) {
            val order = playOrder(p)
            val block = blockWindows(p, order)
            val blockSet = block.toHashSet()
            val base = order.filter { it !in blockSet }.toMutableList()
            base.addAll(base.indexOf(p.currentMediaItemIndex) + 1, block)
            setShuffleOrder(p, base)
            return
        }

        // Queue order: move the block songs, one by one, to right after the current song.
        var placed = 0
        for (id in upNext.toList()) {
            val current = p.currentMediaItemIndex
            val insertAt = current + 1 + placed
            val from = (insertAt until p.mediaItemCount).firstOrNull { p.getMediaItemAt(it).mediaId == id }
                ?: (0 until current).firstOrNull { p.getMediaItemAt(it).mediaId == id }
                ?: continue
            // Taking a song from above the current one shifts everything after it up by one.
            val to = if (from < current) insertAt - 1 else insertAt
            if (from != to) p.moveMediaItem(from, to)
            placed++
        }
        lastIds = ids(p)
        QueueRepository().reorder(lastIds)
        Log.d(TAG, "Up Next moved after the current song")
    }
}
