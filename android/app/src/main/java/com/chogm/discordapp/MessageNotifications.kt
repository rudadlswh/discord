package com.chogm.discordapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object MessageNotifications {
    private const val CHANNEL_ID = "direct_messages"
    private const val CHANNEL_NAME = "Direct Messages"
    private const val CALL_CHANNEL_ID = "incoming_calls"
    private const val CALL_CHANNEL_NAME = "Incoming Calls"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    fun ensureCallChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CALL_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CALL_CHANNEL_ID,
            CALL_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        channel.setSound(sound, audioAttributes)
        channel.enableVibration(true)
        manager.createNotificationChannel(channel)
    }

    fun notifyNewMessage(context: Context, senderName: String, preview: String, messageId: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_message)
            .setContentTitle(context.getString(R.string.message_notification_title, senderName))
            .setContentText(preview)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context)
            .notify(messageId.hashCode(), notification)
    }

    fun notifyIncomingCall(context: Context, callerName: String, callId: String) {
        notifyIncomingCall(context, callerName, callId, null, null, null)
    }

    fun notifyIncomingCall(
        context: Context,
        callerName: String,
        callId: String,
        fullScreenIntent: PendingIntent?,
        acceptIntent: PendingIntent?,
        declineIntent: PendingIntent?
    ) {
        ensureCallChannel(context)
        val builder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_message)
            .setContentTitle(context.getString(R.string.call_notification_title))
            .setContentText(context.getString(R.string.call_notification_body, callerName))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
        if (fullScreenIntent != null) {
            builder.setFullScreenIntent(fullScreenIntent, true)
        }
        if (acceptIntent != null) {
            builder.addAction(
                R.drawable.ic_nav_message,
                context.getString(R.string.call_notification_accept),
                acceptIntent
            )
        }
        if (declineIntent != null) {
            builder.addAction(
                R.drawable.ic_nav_message,
                context.getString(R.string.call_notification_decline),
                declineIntent
            )
        }
        val notification = builder.build()

        NotificationManagerCompat.from(context)
            .notify(callId.hashCode(), notification)
    }
}
