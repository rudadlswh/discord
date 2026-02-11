package com.chogm.discordapp

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging

object PushTokenManager {
    fun refreshToken(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isNullOrBlank()) return@addOnSuccessListener
                AppPrefs.setFcmToken(context, token)
                CallManager.registerDeviceIfNeeded(context)
            }
            .addOnFailureListener {
                // Ignore token fetch failures; we'll retry next launch.
            }
    }
}
