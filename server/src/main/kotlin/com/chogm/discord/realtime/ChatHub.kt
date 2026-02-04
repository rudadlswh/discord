package com.chogm.discord.realtime

import com.chogm.discord.JsonSupport
import com.chogm.discord.models.ChatMessageResponse
import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class ChatHub {
    private data class LiveSession(val userId: String, val session: DefaultWebSocketServerSession)

    private val rooms = ConcurrentHashMap<String, MutableSet<LiveSession>>()

    fun join(channelId: String, userId: String, session: DefaultWebSocketServerSession) {
        val room = rooms.computeIfAbsent(channelId) { Collections.synchronizedSet(mutableSetOf()) }
        room.add(LiveSession(userId, session))
    }

    fun leave(channelId: String, userId: String, session: DefaultWebSocketServerSession) {
        rooms[channelId]?.removeIf { it.userId == userId && it.session == session }
    }

    suspend fun broadcast(channelId: String, message: ChatMessageResponse) {
        val payload = JsonSupport.json.encodeToString(ChatMessageResponse.serializer(), message)
        val room = rooms[channelId] ?: return
        room.toList().forEach { live ->
            live.session.send(Frame.Text(payload))
        }
    }
}
