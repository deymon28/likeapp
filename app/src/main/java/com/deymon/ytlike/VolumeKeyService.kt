package com.deymon.ytlike

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Long press of volume down likes (or dislikes) the current track without changing the volume.
 *
 * While YouTube Music has a session, every volume-down press is swallowed: a short press is
 * replayed as a normal volume step, a press held for [LONG_PRESS_MS] triggers the action instead.
 * Accessibility only sees keys while the screen is on, so with the screen off this does nothing.
 */
class VolumeKeyService : AccessibilityService() {
    companion object {
        private const val LONG_PRESS_MS = 500L

        const val KEY_VOLUME_ACTION = "volume_action"
        const val ACTION_LIKE = "like"
        const val ACTION_DISLIKE = "dislike"
        const val ACTION_OFF = "off"

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(context, VolumeKeyService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var fired = false

    private val longPress = Runnable {
        fired = true
        val result = when (action()) {
            ACTION_DISLIKE -> Like.dislike(this)
            else -> Like.like(this)
        }
        Feedback.show(this, result)
        MediaListenerService.refresh()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return controller != null
                controller = interceptFor()
                if (controller == null) return false
                fired = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
                true
            }
            KeyEvent.ACTION_UP -> {
                val pressed = controller ?: return false
                controller = null
                handler.removeCallbacks(longPress)
                if (!fired) lowerVolume(pressed)
                true
            }
            else -> controller != null
        }
    }

    /** The session to act on, or null to leave the key to the system. */
    private fun interceptFor(): MediaController? {
        if (action() == ACTION_OFF) return null
        // Calls and ringing keep the stock volume key behaviour.
        if (getSystemService(AudioManager::class.java)!!.mode != AudioManager.MODE_NORMAL) return null
        val target = Like.target(this) ?: return null
        val state = target.playbackState?.state ?: return null
        if (state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED) return null
        return target
    }

    private fun lowerVolume(controller: MediaController) {
        if (controller.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            controller.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        } else {
            getSystemService(AudioManager::class.java)!!.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_LOWER, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI
            )
        }
    }

    private fun action(): String =
        Like.prefs(this).getString(KEY_VOLUME_ACTION, ACTION_LIKE) ?: ACTION_LIKE

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        handler.removeCallbacks(longPress)
        controller = null
    }
}
