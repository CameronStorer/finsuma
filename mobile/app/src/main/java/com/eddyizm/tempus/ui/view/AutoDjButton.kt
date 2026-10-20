package com.eddyizm.tempus.ui.view

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.widget.ImageViewCompat
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.util.Preferences
import com.google.android.material.color.MaterialColors

/**
 * Finsuma: an Auto DJ on/off button (mini player and full player). Tapping toggles Auto DJ;
 * the icon is accent-colored when on and dimmed when off, and follows changes made elsewhere
 * (the other button, Settings) while it is on screen.
 */
object AutoDjButton {
    @JvmStatic
    fun bind(button: ImageButton?) {
        if (button == null) return
        refresh(button)
        button.setOnClickListener {
            val enabled = !Preferences.isAutoDjEnabled()
            Preferences.setAutoDjEnabled(enabled)
            Toast.makeText(
                button.context,
                if (enabled) R.string.auto_dj_on else R.string.auto_dj_off,
                Toast.LENGTH_SHORT
            ).show()
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Preferences.AUTO_DJ_ENABLED) refresh(button)
        }
        val prefs = App.getInstance().preferences
        if (button.isAttachedToWindow) prefs.registerOnSharedPreferenceChangeListener(listener)
        button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                prefs.registerOnSharedPreferenceChangeListener(listener)
                refresh(button)
            }

            override fun onViewDetachedFromWindow(v: View) {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        })
        // Keep a strong reference: SharedPreferences only holds listeners weakly.
        button.setTag(R.id.player_header_auto_dj_button, listener)
    }

    @JvmStatic
    fun refresh(button: ImageButton) {
        val enabled = Preferences.isAutoDjEnabled()
        val color = MaterialColors.getColor(
            button,
            if (enabled) com.google.android.material.R.attr.colorPrimary
            else com.google.android.material.R.attr.colorOnSurface
        )
        ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(color))
        button.alpha = if (enabled) 1f else 0.55f
    }
}
