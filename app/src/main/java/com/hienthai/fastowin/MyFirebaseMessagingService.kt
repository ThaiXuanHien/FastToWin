package com.hienthai.fastowin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal const val PUSH_DESTINATION = "destination"
internal const val PUSH_INBOX_PATH = "/notifications"

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences(FCM_PREFERENCES, Context.MODE_PRIVATE).edit().putString(FCM_TOKEN, token).apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val notification = remoteMessage.notification ?: return
        
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val channelId = "fast_to_win_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Fast To Win Notifications", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)

        val opensInbox = remoteMessage.data[PUSH_DESTINATION] == PUSH_INBOX_PATH
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (opensInbox) putExtra(PUSH_DESTINATION, PUSH_INBOX_PATH)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, if (opensInbox) 1 else 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
