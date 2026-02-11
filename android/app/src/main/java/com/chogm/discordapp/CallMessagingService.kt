package com.chogm.discordapp

import android.app.PendingIntent
import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CallMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "call_request") return

        val callId = data["callId"] ?: return
        val channelId = data["channelId"] ?: return
        val fromUserId = data["fromUserId"] ?: return
        val callerName = data["callerName"] ?: "Caller"

        CallManager.handleIncomingCall(
            applicationContext,
            callId,
            channelId,
            fromUserId,
            callerName,
            notify = false
        )

        val fullScreenIntent = Intent(this, CallActivity::class.java)
            .setAction(CallActivity.ACTION_INCOMING)
            .putExtra(CallActivity.EXTRA_CALL_ID, callId)
            .putExtra(CallActivity.EXTRA_CHANNEL_ID, channelId)
            .putExtra(CallActivity.EXTRA_FROM_USER_ID, fromUserId)
            .putExtra(CallActivity.EXTRA_CALLER_NAME, callerName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val acceptIntent = Intent(this, CallActivity::class.java)
            .setAction(CallActivity.ACTION_ACCEPT)
            .putExtra(CallActivity.EXTRA_CALL_ID, callId)
            .putExtra(CallActivity.EXTRA_CHANNEL_ID, channelId)
            .putExtra(CallActivity.EXTRA_FROM_USER_ID, fromUserId)
            .putExtra(CallActivity.EXTRA_CALLER_NAME, callerName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val declineIntent = Intent(this, CallActivity::class.java)
            .setAction(CallActivity.ACTION_DECLINE)
            .putExtra(CallActivity.EXTRA_CALL_ID, callId)
            .putExtra(CallActivity.EXTRA_CHANNEL_ID, channelId)
            .putExtra(CallActivity.EXTRA_FROM_USER_ID, fromUserId)
            .putExtra(CallActivity.EXTRA_CALLER_NAME, callerName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val fullScreenPending = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val acceptPending = PendingIntent.getActivity(
            this,
            (callId.hashCode() + 1),
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declinePending = PendingIntent.getActivity(
            this,
            (callId.hashCode() + 2),
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        MessageNotifications.notifyIncomingCall(
            context = this,
            callerName = callerName,
            callId = callId,
            fullScreenIntent = fullScreenPending,
            acceptIntent = acceptPending,
            declineIntent = declinePending
        )
    }

    override fun onNewToken(token: String) {
        AppPrefs.setFcmToken(applicationContext, token)
        CallManager.registerDeviceIfNeeded(applicationContext)
    }
}
