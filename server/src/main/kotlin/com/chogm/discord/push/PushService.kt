package com.chogm.discord.push

import com.chogm.discord.devices.DeviceService
import com.eatthepath.pushy.apns.ApnsClient
import com.eatthepath.pushy.apns.ApnsClientBuilder
import com.eatthepath.pushy.apns.auth.ApnsSigningKey
import com.eatthepath.pushy.apns.DeliveryPriority
import com.eatthepath.pushy.apns.PushType
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.time.Instant

data class CallPushPayload(
    val callId: String,
    val channelId: String,
    val fromUserId: String,
    val callerName: String
)

class PushService(
    private val deviceService: DeviceService
) {
    private val apnsClient: ApnsClient? = buildApnsClient()
    private val apnsTopic: String? = buildApnsTopic()
    private val firebaseMessaging: FirebaseMessaging? = buildFirebaseMessaging()

    suspend fun sendIncomingCall(targetUserId: String, payload: CallPushPayload) {
        val tokens = deviceService.listTokens(targetUserId)
        if (tokens.isEmpty()) return

        val iosTokens = tokens.filter { it.platform == "ios" }
        val androidTokens = tokens.filter { it.platform == "android" }

        if (iosTokens.isNotEmpty()) {
            sendApns(iosTokens.mapNotNull { it.voipToken ?: it.token }, payload)
        }

        if (androidTokens.isNotEmpty()) {
            sendFcm(androidTokens.map { it.token }, payload)
        }
    }

    private suspend fun sendApns(tokens: List<String>, payload: CallPushPayload) {
        val client = apnsClient ?: return
        val topic = apnsTopic ?: return
        if (tokens.isEmpty()) return

        val apnsPayload = SimpleApnsPayloadBuilder()
            .setContentAvailable(true)
            .addCustomProperty("type", "call_request")
            .addCustomProperty("callId", payload.callId)
            .addCustomProperty("channelId", payload.channelId)
            .addCustomProperty("fromUserId", payload.fromUserId)
            .addCustomProperty("callerName", payload.callerName)
            .build()

        withContext(Dispatchers.IO) {
            tokens.forEach { token ->
                val expiration = Instant.now().plusSeconds(60 * 5)
                val notification = SimpleApnsPushNotification(
                    token,
                    topic,
                    apnsPayload,
                    expiration,
                    DeliveryPriority.IMMEDIATE,
                    PushType.VOIP
                )
                try {
                    client.sendNotification(notification).get()
                } catch (_: Exception) {
                    // ignore push errors
                }
            }
        }
    }

    private suspend fun sendFcm(tokens: List<String>, payload: CallPushPayload) {
        val messaging = firebaseMessaging ?: return
        if (tokens.isEmpty()) return

        withContext(Dispatchers.IO) {
            tokens.forEach { token ->
                val message = Message.builder()
                    .setToken(token)
                    .putData("type", "call_request")
                    .putData("callId", payload.callId)
                    .putData("channelId", payload.channelId)
                    .putData("fromUserId", payload.fromUserId)
                    .putData("callerName", payload.callerName)
                    .setAndroidConfig(
                        AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build()
                    )
                    .build()
                try {
                    messaging.send(message)
                } catch (_: Exception) {
                    // ignore push errors
                }
            }
        }
    }

    private fun buildApnsClient(): ApnsClient? {
        val keyId = System.getenv("APNS_KEY_ID") ?: return null
        val teamId = System.getenv("APNS_TEAM_ID") ?: return null
        val keyPath = System.getenv("APNS_PRIVATE_KEY_PATH") ?: return null

        val keyFile = File(keyPath)
        if (!keyFile.exists()) return null

        val signingKey = ApnsSigningKey.loadFromPkcs8File(keyFile, teamId, keyId)
        val builder = ApnsClientBuilder().setSigningKey(signingKey)
        val env = (System.getenv("APNS_ENV") ?: "development").lowercase()
        if (env == "production") {
            builder.setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
        } else {
            builder.setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
        }
        return builder.build()
    }

    private fun buildApnsTopic(): String? {
        val bundleId = System.getenv("APNS_BUNDLE_ID") ?: return null
        return "$bundleId.voip"
    }

    private fun buildFirebaseMessaging(): FirebaseMessaging? {
        val path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS") ?: return null
        val file = File(path)
        if (!file.exists()) return null

        return try {
            if (FirebaseApp.getApps().isEmpty()) {
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(FileInputStream(file)))
                    .build()
                FirebaseApp.initializeApp(options)
            }
            FirebaseMessaging.getInstance()
        } catch (_: Exception) {
            null
        }
    }
}
