package com.chogm.discord.realtime

import com.chogm.discord.JsonSupport
import com.chogm.discord.models.SignalEnvelope
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.send
import java.util.concurrent.ConcurrentHashMap

class SignalHub {
    private val rooms = ConcurrentHashMap<String, ConcurrentHashMap<String, DefaultWebSocketServerSession>>()

    fun join(channelId: String, userId: String, session: DefaultWebSocketServerSession) {
        val room = rooms.computeIfAbsent(channelId) { ConcurrentHashMap() }
        room[userId] = session
    }

    fun leave(channelId: String, userId: String) {
        rooms[channelId]?.remove(userId)
    }

    suspend fun forward(channelId: String, fromUserId: String, envelope: SignalEnvelope) {
        val room = rooms[channelId] ?: return
        val outbound = envelope.copy(fromUserId = fromUserId)
        val payload = JsonSupport.json.encodeToString(SignalEnvelope.serializer(), outbound)

        if (envelope.targetUserId != null) {
            room[envelope.targetUserId]?.send(payload)
        } else {
            room.forEach { (userId, session) ->
                if (userId != fromUserId) {
                    session.send(payload)
                }
            }
        }
    }
}
