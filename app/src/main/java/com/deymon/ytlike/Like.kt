package com.deymon.ytlike

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log

/**
 * Finds the YouTube Music media session and presses its "like" button.
 *
 * Three ways to like, tried in order:
 *  1. PlaybackState custom action (what the stock Android media player draws as the thumb button).
 *  2. Action button of YT Music's own notification (we are a notification listener, so we can press it).
 *  3. MediaSession rating (thumb up), if the session supports it.
 */
object Like {
    const val YT_MUSIC = "com.google.android.apps.youtube.music"
    private const val TAG = "YtLike"

    const val KEY_CUSTOM_ACTION = "custom_action"
    const val KEY_NOTIFICATION_ACTION = "notification_action"
    const val KEY_SHOW_NOTIFICATION = "show_notification"

    // "Не нравится" contains "нрав", so negatives are checked first.
    private val NEGATIVE = listOf("dislike", "thumbs_down", "thumb_down", "thumbsdown", "не нрав", "дизлайк")
    private val POSITIVE = listOf("like", "thumbs_up", "thumb_up", "thumbsup", "нрав", "лайк")

    data class Result(val ok: Boolean, val message: String)

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("ytlike", Context.MODE_PRIVATE)

    fun listenerComponent(context: Context) = ComponentName(context, MediaListenerService::class.java)

    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)!!
            .isNotificationListenerAccessGranted(listenerComponent(context))

    fun looksLikeLike(vararg texts: CharSequence?): Boolean {
        val text = texts.filterNotNull().joinToString(" ").lowercase()
        if (NEGATIVE.any { it in text }) return false
        return POSITIVE.any { it in text }
    }

    fun controllers(context: Context): List<MediaController> = try {
        context.getSystemService(MediaSessionManager::class.java)!!
            .getActiveSessions(listenerComponent(context))
    } catch (e: SecurityException) {
        emptyList()
    }

    fun pickTarget(context: Context, controllers: List<MediaController>): MediaController? =
        controllers.firstOrNull { it.packageName == YT_MUSIC }
            ?: controllers.firstOrNull { findCustomAction(context, it) != null }

    fun target(context: Context): MediaController? = pickTarget(context, controllers(context))

    fun title(controller: MediaController): String =
        controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()

    fun artist(controller: MediaController): String {
        val metadata = controller.metadata ?: return ""
        return metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
    }

    fun customActions(controller: MediaController): List<PlaybackState.CustomAction> =
        controller.playbackState?.customActions.orEmpty()

    fun findCustomAction(context: Context, controller: MediaController): PlaybackState.CustomAction? {
        val actions = customActions(controller)
        val prefs = prefs(context)
        val saved = prefs.getString(KEY_CUSTOM_ACTION, null)
        if (saved != null) {
            actions.firstOrNull { it.action == saved }?.let { return it }
        } else if (prefs.contains(KEY_NOTIFICATION_ACTION)) {
            // The user explicitly picked a notification button instead.
            return null
        }
        return actions.firstOrNull { looksLikeLike(it.action, it.name) }
    }

    fun notificationActions(packageName: String): List<Notification.Action> {
        val service = MediaListenerService.instance ?: return emptyList()
        val notifications = try {
            service.activeNotifications
        } catch (e: RuntimeException) {
            null
        } ?: return emptyList()
        return notifications
            .filter { it.packageName == packageName }
            .flatMap { it.notification.actions?.toList().orEmpty() }
    }

    fun findNotificationAction(context: Context, packageName: String): Notification.Action? {
        val actions = notificationActions(packageName)
        val saved = prefs(context).getString(KEY_NOTIFICATION_ACTION, null)
        if (saved != null) actions.firstOrNull { it.title?.toString() == saved }?.let { return it }
        return actions.firstOrNull { looksLikeLike(it.title) }
    }

    fun like(context: Context): Result {
        if (!hasAccess(context)) return Result(false, "Нет доступа к уведомлениям — открой YT Like")
        val controller = target(context) ?: return Result(false, "YouTube Music сейчас не играет")
        val track = title(controller)

        findCustomAction(context, controller)?.let { action ->
            controller.transportControls.sendCustomAction(action, action.extras)
            Log.i(TAG, "custom action ${action.action} (${action.name}) -> $track")
            return Result(true, "👍 ${action.name}: $track")
        }

        findNotificationAction(context, controller.packageName)?.let { action ->
            try {
                action.actionIntent.send()
                Log.i(TAG, "notification action ${action.title} -> $track")
                return Result(true, "👍 ${action.title}: $track")
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "notification action cancelled", e)
            }
        }

        if (controller.ratingType == Rating.RATING_THUMB_UP_DOWN) {
            controller.transportControls.setRating(Rating.newThumbRating(true))
            Log.i(TAG, "thumb rating -> $track")
            return Result(true, "👍 $track")
        }

        Log.w(TAG, "like action not found:\n" + describe(context))
        return Result(false, "Кнопка лайка не найдена — открой YT Like и выбери её вручную")
    }

    /** Text dump of every session and its actions, for logcat and the main screen. */
    fun describe(context: Context): String = buildString {
        val all = controllers(context)
        if (all.isEmpty()) append("Нет активных медиасессий\n")
        for (controller in all) {
            append("${controller.packageName}: ${title(controller)} — ${artist(controller)}\n")
            append("  ratingType=${controller.ratingType}\n")
            for (action in customActions(controller)) {
                append("  custom: ${action.action} | ${action.name}\n")
            }
            for (action in notificationActions(controller.packageName)) {
                append("  notification: ${action.title}\n")
            }
        }
    }
}
