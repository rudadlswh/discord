package com.chogm.discord.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : Table("users") {
    val id = varchar("id", 36)
    val email = varchar("email", 255).uniqueIndex()
    val username = varchar("username", 50).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object FriendRequests : Table("friend_requests") {
    val id = varchar("id", 36)
    val requesterId = varchar("requester_id", 36).index()
    val addresseeId = varchar("addressee_id", 36).index()
    val status = varchar("status", 16)
    val createdAt = timestamp("created_at")
    val respondedAt = timestamp("responded_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Friendships : Table("friendships") {
    val userId = varchar("user_id", 36)
    val friendId = varchar("friend_id", 36)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(userId, friendId)
}

object Channels : Table("channels") {
    val id = varchar("id", 36)
    val name = varchar("name", 100)
    val type = varchar("type", 16)
    val createdBy = varchar("created_by", 36)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ChannelMembers : Table("channel_members") {
    val channelId = varchar("channel_id", 36)
    val userId = varchar("user_id", 36)
    val joinedAt = timestamp("joined_at")

    override val primaryKey = PrimaryKey(channelId, userId)
}

object Messages : Table("messages") {
    val id = varchar("id", 36)
    val channelId = varchar("channel_id", 36).index()
    val senderId = varchar("sender_id", 36).index()
    val content = varchar("content", 4000)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object CallSessions : Table("call_sessions") {
    val id = varchar("id", 36)
    val channelId = varchar("channel_id", 36)
    val createdAt = timestamp("created_at")
    val endedAt = timestamp("ended_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object CallParticipants : Table("call_participants") {
    val id = varchar("id", 36)
    val callId = varchar("call_id", 36)
    val userId = varchar("user_id", 36)
    val joinedAt = timestamp("joined_at")
    val leftAt = timestamp("left_at").nullable()
    val audioEnabled = bool("audio_enabled")
    val videoEnabled = bool("video_enabled")
    val screenshareEnabled = bool("screenshare_enabled")

    override val primaryKey = PrimaryKey(id)
}
