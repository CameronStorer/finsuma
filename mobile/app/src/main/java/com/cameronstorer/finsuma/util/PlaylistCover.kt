package com.cameronstorer.finsuma.util

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import com.cameronstorer.finsuma.glide.CustomGlideRequest
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Finsuma: generated playlist covers, ported from the desktop (gradient-cover.ts): a
 * three-colour gradient with the playlist's initials on top. The colours come from the playlist
 * id, so a playlist always looks the same. Used when a playlist has no picture of its own.
 */
object PlaylistCover {
    @JvmStatic
    fun drawable(id: String?, name: String?): Drawable {
        val random = Random((id ?: name ?: "").hashCode().toLong())
        fun between(min: Double, max: Double) = min + random.nextDouble() * (max - min)

        // Hues that sit near each other (analogous) or spread out (triad); saturated enough to
        // stand out in a grid, dark enough for white letters.
        val base = random.nextDouble() * 360
        val spread = if (random.nextDouble() < 0.6) between(28.0, 60.0) else between(90.0, 130.0)
        val colors = IntArray(3) { i ->
            val hue = ((base + spread * i) % 360).toFloat()
            val saturation = between(0.62, 0.86).toFloat()
            val lightness = (between(0.40, 0.56) - i * 0.03).toFloat()
            ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
        }
        return GradientCover(colors, between(0.0, 360.0), initials(name.orEmpty()))
    }

    /** "Programming Jams" -> "PJ", "chill" -> "C": the first letter of up to two words. */
    private fun initials(name: String): String =
        name.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.substring(0, it.offsetByCodePoints(0, 1)) }
            .uppercase()

    private class GradientCover(
        private val colors: IntArray,
        private val angleDegrees: Double,
        private val letters: String,
    ) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val size = b.width().toFloat()
            val rad = Math.toRadians(angleDegrees)
            val half = size / 2f * 1.4142f
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val dx = (cos(rad) * half).toFloat()
            val dy = (sin(rad) * half).toFloat()
            fill.shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, colors, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            // Soft light from the top-left so it doesn't look flat.
            val gx = b.left + size * 0.25f
            val gy = b.top + size * 0.2f
            glow.shader = RadialGradient(gx, gy, max(size, 1f), intArrayOf(0x38FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.6f), Shader.TileMode.CLAMP)

            val radius = CustomGlideRequest.CORNER_RADIUS.toFloat()
            val rect = RectF(b)
            canvas.drawRoundRect(rect, radius, radius, fill)
            canvas.drawRoundRect(rect, radius, radius, glow)

            if (letters.isNotEmpty()) {
                text.textSize = size * if (letters.length == 1) 0.52f else 0.42f
                text.setShadowLayer(size * 0.03f, 0f, size * 0.01f, 0x47000000)
                canvas.drawText(letters, cx, cy - (text.descent() + text.ascent()) / 2f, text)
            }
        }

        override fun setAlpha(alpha: Int) {
            fill.alpha = alpha
            glow.alpha = alpha
            text.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fill.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
