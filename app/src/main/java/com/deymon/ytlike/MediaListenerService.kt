package com.deymon.ytlike

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.quicksettings.TileService

/**
 * Notification access is what lets us read other apps' media sessions.
 * The system keeps this service bound, so it also hosts the "like" notification.
 */
class MediaListenerService : NotificationListenerService(),
    MediaSessionManager.OnActiveSessionsChangedListener {

    companion object {
        @Volatile
        var instance: MediaListenerService? = null
            private set

        fun refresh() {
            instance?.scheduleRefresh()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleRefresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleRefresh()
        override fun onSessionDestroyed() = reloadSessions()
    }

    private val refreshRunnable = Runnable {
        LikeNotification.update(this, controller)
        TileService.requestListeningState(this, ComponentName(this, LikeTileService::class.java))
    }

    override fun onListenerConnected() {
        instance = this
        LikeNotification.ensureChannel(this)
        sessionManager().addOnActiveSessionsChangedListener(this, Like.listenerComponent(this), handler)
        reloadSessions()
    }

    override fun onListenerDisconnected() {
        instance = null
        sessionManager().removeOnActiveSessionsChangedListener(this)
        controller?.unregisterCallback(callback)
        controller = null
        handler.removeCallbacks(refreshRunnable)
        LikeNotification.cancel(this)
    }

    override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        val next = Like.pickTarget(this, controllers.orEmpty())
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback, handler)
        }
        scheduleRefresh()
    }

    // YT Music's own notification buttons may change (e.g. after a like), keep ours in sync.
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == controller?.packageName) scheduleRefresh()
    }

    fun scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, 200)
    }

    private fun reloadSessions() {
        onActiveSessionsChanged(Like.controllers(this))
    }

    private fun sessionManager() = getSystemService(MediaSessionManager::class.java)!!
}
