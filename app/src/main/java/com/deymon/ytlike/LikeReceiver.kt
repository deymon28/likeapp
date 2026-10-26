package com.deymon.ytlike

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LikeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Feedback.show(context, Like.like(context))
        MediaListenerService.refresh()
    }

    companion object {
        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, LikeReceiver::class.java).setAction("com.deymon.ytlike.LIKE"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
