package com.chogm.discord.realtime

import com.chogm.discord.JsonSupport
import com.chogm.discord.ServiceException
import com.chogm.discord.channels.ChannelService
import com.chogm.discord.models.ChatMessageRequest
import com.chogm.discord.models.SignalEnvelope
import com.chogm.discord.models.SignalError
import com.chogm.discord.users.requireUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send

fun Route.webSocketRoutes(chatHub: ChatHub, signalHub: SignalHub, channelService: ChannelService) {
    authenticate("auth-jwt") {
        webSocket("/ws/chat/{channelId}") {
            val userId = call.requireUserId()
            val channelId = call.parameters["channelId"]
                ?: throw ServiceException(HttpStatusCode.BadRequest, "Missing channelId")

            chatHub.join(channelId, userId, this)

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val request = JsonSupport.json.decodeFromString(
                            ChatMessageRequest.serializer(),
                            frame.readText()
                        )
                        val message = channelService.postMessage(userId, channelId, request.content)
                        chatHub.broadcast(channelId, message)
                    }
                }
            } catch (ex: ServiceException) {
                val error = SignalError(ex.message)
                send(Frame.Text(JsonSupport.json.encodeToString(SignalError.serializer(), error)))
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
                    if (frame is Frame.Text) {
                        val envelope = JsonSupport.json.decodeFromString(
                            SignalEnvelope.serializer(),
                            frame.readText()
                        )
                        signalHub.forward(channelId, userId, envelope)
                    }
                }
            } catch (ex: Exception) {
                val error = SignalError(ex.message ?: "Signaling error")
                send(Frame.Text(JsonSupport.json.encodeToString(SignalError.serializer(), error)))
            } finally {
                signalHub.leave(channelId, userId)
                close()
            }
        }
    }
}
