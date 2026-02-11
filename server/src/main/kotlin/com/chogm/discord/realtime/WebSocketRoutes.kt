package com.chogm.discord.realtime

import com.chogm.discord.JsonSupport
import com.chogm.discord.ServiceException
import com.chogm.discord.channels.ChannelService
import com.chogm.discord.models.ChatMessageRequest
import com.chogm.discord.models.SignalEnvelope
import com.chogm.discord.models.SignalError
import com.chogm.discord.push.CallPushPayload
import com.chogm.discord.push.PushService
import com.chogm.discord.users.UserService
import com.chogm.discord.users.requireUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Route.webSocketRoutes(
    chatHub: ChatHub,
    signalHub: SignalHub,
    channelService: ChannelService,
    pushService: PushService,
    userService: UserService
) {
    authenticate("auth-jwt") {
        webSocket("/ws/chat/{channelId}") {
            val userId = call.requireUserId()
            val channelId = call.parameters["channelId"]
                ?: throw ServiceException(HttpStatusCode.BadRequest, "Missing channelId")

            chatHub.join(channelId, userId, this)

            try {
                for (frame in incoming) {
                    if (frame is io.ktor.websocket.Frame.Text) {
                        try {
                            val request = JsonSupport.json.decodeFromString(
                                ChatMessageRequest.serializer(),
                                frame.readText()
                            )
                            val message = channelService.postMessage(userId, channelId, request.content)
                            chatHub.broadcast(channelId, message)
                        } catch (ex: ServiceException) {
                            val error = SignalError(ex.message)
                            send(JsonSupport.json.encodeToString(SignalError.serializer(), error))
                        } catch (ex: Exception) {
                            val error = SignalError(ex.message ?: "Message error")
                            send(JsonSupport.json.encodeToString(SignalError.serializer(), error))
                        }
                    }
                }
            } finally {
                chatHub.leave(channelId, userId, this)
            }
        }

        webSocket("/ws/signaling/{channelId}") {
            val userId = call.requireUserId()
            val channelId = call.parameters["channelId"]
                ?: throw ServiceException(HttpStatusCode.BadRequest, "Missing channelId")

            signalHub.join(channelId, userId, this)

            try {
                for (frame in incoming) {
                    if (frame is io.ktor.websocket.Frame.Text) {
                        val envelope = JsonSupport.json.decodeFromString(
                            SignalEnvelope.serializer(),
                            frame.readText()
                        )
                        val delivered = signalHub.forward(channelId, userId, envelope)
                        if (!delivered && envelope.type == "call_request" && envelope.targetUserId != null) {
                            val callId = envelope.payload
                                ?.jsonObject
                                ?.get("callId")
                                ?.jsonPrimitive
                                ?.content
                            if (!callId.isNullOrBlank()) {
                                val callerName = userService.getDisplayName(userId) ?: "Unknown"
                                pushService.sendIncomingCall(
                                    envelope.targetUserId,
                                    CallPushPayload(
                                        callId = callId,
                                        channelId = channelId,
                                        fromUserId = userId,
                                        callerName = callerName
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                val error = SignalError(ex.message ?: "Signaling error")
                send(JsonSupport.json.encodeToString(SignalError.serializer(), error))
            } finally {
                signalHub.leave(channelId, userId)
                close()
            }
        }
    }
}
