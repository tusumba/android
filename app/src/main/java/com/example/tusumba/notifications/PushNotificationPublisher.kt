package com.example.tusumba.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.tusumba.MainActivity
import com.example.tusumba.R
import com.example.tusumba.data.local.TokenManager
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class PushNotificationPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environment: FirebasePushEnvironment,
    private val session: TokenManager,
) {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun publish(message: RemoteMessage) {
        if (session.getUser() == null || !environment.notificationsEnabled()) return
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        createChannel(context)

        val id = message.messageId?.hashCode() ?: nextId.incrementAndGet()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        for (key in listOf("event_id", "registration_id", "ticket_id")) {
            message.data[key]?.let { intent.putExtra(key, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission may have been revoked between checking it and posting the notification.
        }
    }

    companion object {
        const val ChannelId = "tusumba_notifications"
        private val nextId = AtomicInteger()

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(ChannelId, context.getString(R.string.notifications_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
