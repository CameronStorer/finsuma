package com.cameronstorer.finsuma.service

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import com.cameronstorer.finsuma.util.Preferences
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Play when a car or headphones connect" (like Spotify): when a Bluetooth device connects and
 * becomes the audio output, send Finsuma a Play press. It works with the app closed: the media
 * button receiver starts the playback service, which reloads the saved queue
 * (BaseSessionCallback.onPlaybackResumption). Asking a moment after the device connects means
 * Finsuma takes audio focus after any other app that started on connect.
 */
class BluetoothAutoPlayReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BluetoothAutoPlay"
        private const val CHECK_EVERY_MS = 1000L
        private const val MAX_CHECKS = 8 // a broadcast may run for about 10 seconds

        /** One wait at a time: a car often reports several connections at once. */
        private val waiting = AtomicBoolean(false)
    }

    /** Watches, keyboards and other non-audio devices never become the audio output. */
    private fun maybeAudioDevice(intent: Intent): Boolean {
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return true
        return try {
            val major = device.bluetoothClass?.majorDeviceClass ?: return true
            major == BluetoothClass.Device.Major.AUDIO_VIDEO || major == BluetoothClass.Device.Major.UNCATEGORIZED
        } catch (_: SecurityException) {
            true // no Bluetooth permission: fall back to checking the audio output below
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        if (!Preferences.isBluetoothAutoPlay()) return

        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        // Nothing to do (and no wake-ups) if the device isn't audio or music already plays.
        if (!maybeAudioDevice(intent) || audio.isMusicActive) return
        if (!waiting.compareAndSet(false, true)) return

        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        var checks = 0

        // Wait until audio is actually routed to a Bluetooth output (skips watches, keyboards...).
        val check = object : Runnable {
            override fun run() {
                checks++
                val bluetoothOutput = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                }
                when {
                    bluetoothOutput -> {
                        // Give the car a moment to settle, so Finsuma gets audio focus last.
                        handler.postDelayed({
                            if (!audio.isMusicActive) sendPlay(app)
                            waiting.set(false)
                            pending.finish()
                        }, 1500)
                    }
                    checks < MAX_CHECKS -> handler.postDelayed(this, CHECK_EVERY_MS)
                    else -> {
                        Log.d(TAG, "no Bluetooth audio output appeared")
                        waiting.set(false)
                        pending.finish()
                    }
                }
            }
        }
        handler.postDelayed(check, CHECK_EVERY_MS)
    }

    @OptIn(UnstableApi::class)
    private fun sendPlay(context: Context) {
        Log.d(TAG, "Bluetooth audio connected: starting playback")
        val press = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setClass(context, MediaButtonReceiver::class.java)
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        context.sendBroadcast(press)
    }
}
