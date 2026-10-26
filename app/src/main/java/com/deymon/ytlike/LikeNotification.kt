package com.deymon.ytlike

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import android.widget.RemoteViews

/** Silent ongoing notification with a big "like" button, visible on the lock screen. */
object LikeNotification {
    private const val CHANNEL_ID = "like_button"
    private const val NOTIFICATION_ID = 1

    private val INACTIVE_STATES = setOf(
        PlaybackState.STATE_NONE,
        PlaybackState.STATE_STOPPED,
        PlaybackState.STATE_ERROR,
    )

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager(context).createNotificationChannel(channel)
    }

    fun cancel(context: Context) {
        manager(context).cancel(NOTIFICATION_ID)
    }

    fun update(context: Context, controller: MediaController?) {
        val state = controller?.playbackState?.state
        val enabled = Like.prefs(context).getBoolean(Like.KEY_SHOW_NOTIFICATION, true)
        if (controller == null || state == null || state in INACTIVE_STATES || !enabled) {
            cancel(context)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val title = Like.title(controller).ifEmpty { "YouTube Music" }
        val artist = Like.artist(controller)
        // YT Music's own label for the button; it may reflect whether the track is already liked.
        val label = "👍 " + (Like.findCustomAction(context, controller)?.name?.toString()
            ?.takeIf { it.isNotBlank() } ?: "Нравится")
        val likeIntent = LikeReceiver.pendingIntent(context)

        val views = RemoteViews(context.packageName, R.layout.notification_like).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.artist, artist)
            setTextViewText(R.id.like_button, label)
            setOnClickPendingIntent(R.id.like_button, likeIntent)
        }

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_thumb_up)
            .setContentTitle(title)
            .setContentText(artist)
            .setCustomContentView(views)
            .setStyle(Notification.DecoratedCustomViewStyle())
            // Plain action too: shown when expanded and forwarded to watches that support actions.
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_thumb_up), label, likeIntent
                ).build()
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)

        context.packageManager.getLaunchIntentForPackage(controller.packageName)?.let { launch ->
            builder.setContentIntent(
                PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
            )
        }

        manager(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)!!
}
