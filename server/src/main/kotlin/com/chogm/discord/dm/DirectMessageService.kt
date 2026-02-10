package com.chogm.discord.dm

import com.chogm.discord.ServiceException
import com.chogm.discord.channels.ChannelService
import com.chogm.discord.db.ChannelMembers
import com.chogm.discord.db.Channels
import com.chogm.discord.db.Friendships
import com.chogm.discord.db.Messages
import com.chogm.discord.db.Users
import com.chogm.discord.models.ChatMessageResponse
import com.chogm.discord.models.ChannelType
import com.chogm.discord.models.DirectMessageResponse
import com.chogm.discord.models.DirectMessageSendRequest
import com.chogm.discord.models.DirectThreadResponse
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class DirectMessageService(private val channelService: ChannelService) {
    fun listThreads(userId: String): List<DirectThreadResponse> {
        return transaction {
            val channelIds = ChannelMembers
                .join(
                    Channels,
                    JoinType.INNER,
                    additionalConstraint = { ChannelMembers.channelId eq Channels.id }
                )
                .slice(ChannelMembers.channelId)
                .select {
                    (ChannelMembers.userId eq userId) and (Channels.type eq ChannelType.DIRECT.name)
                }
                .map { it[ChannelMembers.channelId] }
                .distinct()

            if (channelIds.isEmpty()) {
                return@transaction emptyList()
            }

            val memberRows = ChannelMembers
                .select { ChannelMembers.channelId inList channelIds }
                .toList()

            val friendByChannel = memberRows
                .groupBy { it[ChannelMembers.channelId] }
                .mapValues { entry ->
                    entry.value.map { it[ChannelMembers.userId] }.firstOrNull { it != userId }
                }

            val friendIds = friendByChannel.values.filterNotNull().distinct()
            val usersById = Users
                .select { Users.id inList friendIds }
                .associateBy { it[Users.id] }

            val lastMessages = mutableMapOf<String, ChatMessageResponse>()
            Messages
                .select { Messages.channelId inList channelIds }
                .orderBy(Messages.createdAt, SortOrder.DESC)
                .forEach { row ->
                    val channelId = row[Messages.channelId]
                    if (!lastMessages.containsKey(channelId)) {
                        lastMessages[channelId] = ChatMessageResponse(
                            id = row[Messages.id],
                            channelId = channelId,
                            senderId = row[Messages.senderId],
                            content = row[Messages.content],
                            createdAt = row[Messages.createdAt].toString()
                        )
                    }
                }

            channelIds.mapNotNull { channelId ->
                val friendId = friendByChannel[channelId] ?: return@mapNotNull null
                val friendRow = usersById[friendId] ?: return@mapNotNull null
                DirectThreadResponse(
                    channelId = channelId,
                    friendId = friendId,
                    friendUsername = friendRow[Users.username],
                    friendDisplayName = friendRow[Users.displayName],
                    lastMessage = lastMessages[channelId]
                )
            }.sortedByDescending { it.lastMessage?.createdAt ?: "" }
        }
    }

    fun sendMessage(userId: String, request: DirectMessageSendRequest): DirectMessageResponse {
        val friendId = request.toUserId.trim()
        if (friendId.isEmpty()) {
            throw ServiceException(HttpStatusCode.BadRequest, "Missing friendId")
        }
        if (userId == friendId) {
            throw ServiceException(HttpStatusCode.BadRequest, "Cannot message yourself")
        }

        val channelId = transaction {
            ensureFriends(userId, friendId)
            findOrCreateDirectChannel(userId, friendId)
        }

        val message = channelService.postMessage(userId, channelId, request.content)
        return DirectMessageResponse(channelId = channelId, message = message)
    }

    private fun ensureFriends(userId: String, friendId: String) {
        val isFriend = Friendships.select {
            (Friendships.userId eq userId) and (Friendships.friendId eq friendId)
        }.count() > 0

        if (!isFriend) {
            throw ServiceException(HttpStatusCode.Forbidden, "Not friends")
        }
    }

    private fun findOrCreateDirectChannel(userId: String, friendId: String): String {
        val pair = listOf(userId, friendId).sorted()
        val channelName = "dm:${pair[0]}:${pair[1]}"

        val existing = Channels.select {
            (Channels.type eq ChannelType.DIRECT.name) and (Channels.name eq channelName)
        }.singleOrNull()

        if (existing != null) {
            val channelId = existing[Channels.id]
            ensureMember(channelId, userId)
            ensureMember(channelId, friendId)
            return channelId
        }

        val channelId = UUID.randomUUID().toString()
        val now = Instant.now()

        Channels.insert {
            it[Channels.id] = channelId
            it[Channels.name] = channelName
            it[Channels.type] = ChannelType.DIRECT.name
            it[Channels.createdBy] = userId
            it[Channels.createdAt] = now
        }

        ChannelMembers.insert {
            it[ChannelMembers.channelId] = channelId
            it[ChannelMembers.userId] = userId
            it[ChannelMembers.joinedAt] = now
        }

        ChannelMembers.insert {
            it[ChannelMembers.channelId] = channelId
            it[ChannelMembers.userId] = friendId
            it[ChannelMembers.joinedAt] = now
        }

        return channelId
    }

    private fun ensureMember(channelId: String, userId: String) {
        val isMember = ChannelMembers.select {
            (ChannelMembers.channelId eq channelId) and (ChannelMembers.userId eq userId)
        }.count() > 0

        if (!isMember) {
            ChannelMembers.insert {
                it[ChannelMembers.channelId] = channelId
                it[ChannelMembers.userId] = userId
                it[ChannelMembers.joinedAt] = Instant.now()
            }
        }
    }
}
