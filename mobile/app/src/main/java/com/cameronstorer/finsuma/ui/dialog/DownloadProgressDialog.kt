package com.cameronstorer.finsuma.ui.dialog

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.cameronstorer.finsuma.util.DownloadUtil
import com.cameronstorer.finsuma.util.FinsumaService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Finsuma download manager: live progress for YouTube / SoundCloud downloads on the Finsuma
 * server and for songs being saved to this phone (playlists, albums). Refreshes every 2 seconds
 * while open and stops when it closes, so it costs nothing in the background.
 */
object DownloadProgressDialog {
    private const val REFRESH_MS = 2000L

    private val io = Executors.newSingleThreadExecutor()

    @JvmStatic
    @OptIn(UnstableApi::class)
    fun show(context: Context) {
        val main = Handler(Looper.getMainLooper())
        val dp = context.resources.displayMetrics.density
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
        }
        val scroll = ScrollView(context).apply { addView(list) }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Downloads")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create()

        fun heading(text: String) = TextView(context).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
        }

        fun row(label: String, detail: String, percent: Int?, active: Boolean): View {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            }
            box.addView(TextView(context).apply {
                text = label
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (active) {
                box.addView(LinearProgressIndicator(context).apply {
                    if (percent == null) {
                        isIndeterminate = true
                    } else {
                        isIndeterminate = false
                        max = 100
                        setProgressCompat(percent, false)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = (4 * dp).toInt() }
                })
            }
            box.addView(TextView(context).apply {
                text = detail
                alpha = 0.7f
                textSize = 12f
            })
            return box
        }

        fun render(phone: List<Download>, jobs: List<JSONObject>?, problem: String?) {
            list.removeAllViews()
            if (phone.isNotEmpty()) {
                list.addView(heading("Saving to this phone"))
                val avg = phone.map { it.percentDownloaded.coerceAtLeast(0f) }.average().toInt()
                list.addView(row("${phone.size} song${if (phone.size == 1) "" else "s"} downloading", "$avg%", avg, true))
            }
            list.addView(heading("Finsuma server (YouTube, SoundCloud)"))
            when {
                problem != null -> list.addView(row(problem, "", null, false))
                jobs.isNullOrEmpty() -> list.addView(row("No recent downloads", "Use YouTube or SoundCloud to add music.", null, false))
                else -> jobs.forEach { job ->
                    var title = job.optString("title", "")
                    if (title.isEmpty() || title == "null") title = job.optString("set_title", "")
                    if (title.isEmpty() || title == "null") title = job.optString("url", "")
                    val playlist = if (job.optString("mode") == "playlist") " (playlist)" else ""
                    val files = job.optJSONArray("files")?.length() ?: 0
                    when (job.optString("status")) {
                        "running" -> {
                            val pct = Math.round(job.optDouble("progress", 0.0)).toInt()
                            list.addView(row(title + playlist, "$pct%" + if (files > 0) " · $files saved" else "", pct, true))
                        }
                        "done" -> list.addView(row("✓ $title$playlist", if (files > 1) "$files songs" else "Done", null, false))
                        "failed" -> list.addView(row("✗ $title$playlist", job.optString("error", "Failed").lineSequence().firstOrNull() ?: "Failed", null, false))
                        else -> list.addView(row(title + playlist, "Waiting", null, true))
                    }
                }
            }
        }

        var open = true
        lateinit var tick: Runnable
        tick = Runnable {
            if (!open) return@Runnable
            io.execute {
                val phone = try {
                    DownloadUtil.getDownloadManager(context).currentDownloads.filter {
                        it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED || it.state == Download.STATE_RESTARTING
                    }
                } catch (_: Throwable) {
                    emptyList()
                }
                var problem: String? = null
                val jobs = try {
                    val array = FinsumaService.fetchJobs(context)
                    (0 until array.length()).map { array.getJSONObject(it) }
                } catch (e: Exception) {
                    problem = e.message ?: "Couldn't load downloads"
                    null
                }
                main.post {
                    if (!open) return@post
                    render(phone, jobs, problem)
                    // Keep polling while something is moving; otherwise one more look is enough.
                    val busy = phone.isNotEmpty() || jobs?.any { it.optString("status") in setOf("running", "queued") } == true
                    if (busy) main.postDelayed(tick, REFRESH_MS)
                }
            }
        }
        dialog.setOnDismissListener {
            open = false
            main.removeCallbacks(tick)
        }
        dialog.show()
        render(emptyList(), null, "Loading…")
        main.post(tick)
    }
}
