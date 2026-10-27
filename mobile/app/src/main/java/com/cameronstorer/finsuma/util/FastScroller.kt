package com.cameronstorer.finsuma.util

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

/**
 * Finsuma: a modern fast scroller for long lists. It is invisible until the list moves, then a
 * thin handle shows on the right edge for a moment. Drag the handle to jump through the list; a
 * bubble shows where you are (a letter when the list is sorted A-Z, otherwise a value such as the
 * year, or the position) and small markers line the edge as a guide.
 *
 * Draws as an item decoration and takes touches only on the handle itself, so it needs no layout
 * changes and doesn't steal taps from the rows.
 */
class FastScroller private constructor(
    private val list: RecyclerView,
    private val labeler: Labeler,
) : RecyclerView.ItemDecoration(), RecyclerView.OnItemTouchListener {

    /** Text for the bubble at [position] (null for none); [count] is the number of rows. */
    fun interface Labeler {
        fun label(position: Int, count: Int): String?
    }

    private val density = list.resources.displayMetrics.density
    private val thumbW = 6 * density
    private val thumbWDrag = 10 * density
    private val thumbH = 56 * density
    private val hitW = 36 * density
    private val margin = 4 * density

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28 * density
        isFakeBoldText = true
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var alpha = 0f
    private var dragging = false
    private var thumbTop = 0f
    private var label: String? = null
    private val fade = ValueAnimator().apply {
        duration = 250
        addUpdateListener { alpha = it.animatedValue as Float; list.invalidate() }
    }
    private val hide = Runnable { if (!dragging) animateTo(0f) }

    init {
        list.addItemDecoration(this)
        list.addOnItemTouchListener(this)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0 && rv.adapter?.itemCount.let { it != null && it > 40 }) show()
            }
        })
    }

    private fun show() {
        animateTo(1f)
        list.removeCallbacks(hide)
        list.postDelayed(hide, 1500)
    }

    private fun animateTo(target: Float) {
        if (fade.isRunning) fade.cancel()
        fade.setFloatValues(alpha, target)
        fade.start()
    }

    private fun range(): Float {
        val r = list.computeVerticalScrollRange() - list.computeVerticalScrollExtent()
        return if (r > 0) r.toFloat() else 1f
    }

    private fun trackLength() = list.height - thumbH

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        try {
            draw(c, parent)
        } catch (t: Throwable) {
            alpha = 0f // a decoration must never take the screen down with it
        }
    }

    private fun draw(c: Canvas, parent: RecyclerView) {
        if (alpha <= 0.01f) return
        val count = parent.adapter?.itemCount ?: 0
        if (count == 0) return
        if (!dragging) {
            thumbTop = (parent.computeVerticalScrollOffset() / range()).coerceIn(0f, 1f) * trackLength()
        }
        val primary = MaterialColors.getColor(parent, androidx.appcompat.R.attr.colorPrimary)
        thumbPaint.color = primary
        thumbPaint.alpha = (alpha * 200).toInt()

        val w = if (dragging) thumbWDrag else thumbW
        val right = parent.width - margin
        if (dragging) {
            // Guide markers: ten evenly spaced positions along the edge, with their labels.
            tickPaint.color = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnSurfaceVariant)
            tickPaint.alpha = (alpha * 140).toInt()
            textPaint.textSize = 11 * density
            textPaint.color = tickPaint.color
            textPaint.alpha = tickPaint.alpha
            for (i in 0..9) {
                val pos = (i / 9f * (count - 1)).toInt()
                val y = thumbH / 2 + i / 9f * trackLength()
                c.drawCircle(right - thumbWDrag - 4 * density, y, 1.5f * density, tickPaint)
                labeler.label(pos, count)?.let { c.drawText(it.take(4), right - thumbWDrag - 22 * density, y + 4 * density, textPaint) }
            }
        }
        rect.set(right - w, thumbTop, right, thumbTop + thumbH)
        c.drawRoundRect(rect, w / 2, w / 2, thumbPaint)

        if (dragging) label?.let { text ->
            val onContainer = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnPrimaryContainer)
            bubblePaint.color = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorPrimaryContainer)
            textPaint.textSize = 28 * density
            textPaint.color = onContainer
            textPaint.alpha = 255
            val bw = maxOf(64 * density, textPaint.measureText(text) + 28 * density)
            val bh = 64 * density
            val cy = (thumbTop + thumbH / 2).coerceIn(bh / 2, parent.height - bh / 2)
            val bRight = right - thumbWDrag - 56 * density
            rect.set(bRight - bw, cy - bh / 2, bRight, cy + bh / 2)
            c.drawRoundRect(rect, bh / 2, bh / 2, bubblePaint)
            c.drawText(text, rect.centerX(), cy + textPaint.textSize / 3, textPaint)
        }
    }

    private fun onHandle(e: MotionEvent) =
        alpha > 0.3f && e.x >= list.width - hitW && e.y >= thumbTop - 12 * density && e.y <= thumbTop + thumbH + 12 * density

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN && onHandle(e)) {
            dragging = true
            list.removeCallbacks(hide)
            rv.parent?.requestDisallowInterceptTouchEvent(true)
            rv.stopScroll()
            jumpTo(e.y)
            return true
        }
        return dragging
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging) jumpTo(e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                label = null
                list.invalidate()
                show()
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    private fun jumpTo(y: Float) {
        val count = list.adapter?.itemCount ?: return
        if (count == 0) return
        val fraction = ((y - thumbH / 2) / trackLength()).coerceIn(0f, 1f)
        thumbTop = fraction * trackLength()
        val position = (fraction * (count - 1)).toInt()
        (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        label = try { labeler.label(position, count) } catch (t: Throwable) { null }
        list.invalidate()
    }

    companion object {
        /** Adds a fast scroller to [list]. */
        @JvmStatic
        fun attach(list: RecyclerView, labeler: Labeler): FastScroller = FastScroller(list, labeler)

        /** First letter for A-Z lists ("#" for numbers and symbols). */
        @JvmStatic
        fun letter(text: String?): String {
            val c = text?.trim()?.firstOrNull() ?: return "#"
            return if (c.isLetter()) c.uppercaseChar().toString() else "#"
        }
    }
}
