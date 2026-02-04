package com.chogm.discord.channels

import com.chogm.discord.ServiceException
import com.chogm.discord.db.*
import com.chogm.discord.models.*
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ChannelService {
    fun createChannel(userId: String, request: ChannelCreateRequest): ChannelResponse {
        return transaction {
            val exists = Users.select { Users.id eq userId }.count() > 0
            if (!exists) {
                throw ServiceException(HttpStatusCode.NotFound, "User not found")
            }

            val channelId = UUID.randomUUID().toString()
            val now = Instant.now()

            Channels.insert {
                it[Channels.id] = channelId
                it[Channels.name] = request.name
                it[Channels.type] = request.type.name
                it[Channels.createdBy] = userId
                it[Channels.createdAt] = now
            }

            ChannelMembers.insert {
                it[ChannelMembers.channelId] = channelId
                it[ChannelMembers.userId] = userId
                it[ChannelMembers.joinedAt] = now
            }

            ChannelResponse(
                id = channelId,
                name = request.name,
                type = request.type,
                createdBy = userId,
                createdAt = now.toString()
            )
        }
    }

    fun listChannels(userId: String): List<ChannelResponse> {
        return transaction {
            (Channels innerJoin ChannelMembers)
                .select { ChannelMembers.userId eq userId }
                .map {
                    ChannelResponse(
                        id = it[Channels.id],
                        name = it[Channels.name],
                        type = ChannelType.valueOf(it[Channels.type]),
                        createdBy = it[Channels.createdBy],
                        createdAt = it[Channels.createdAt].toString()
                    )
                }
        }
    }

    fun joinChannel(userId: String, channelId: String) {
        transaction {
            val channelExists = Channels.select { Channels.id eq channelId }.count() > 0
            if (!channelExists) {
                throw ServiceException(HttpStatusCode.NotFound, "Channel not found")
            }

            val alreadyMember = ChannelMembers.select {
                (ChannelMembers.channelId eq channelId) and (ChannelMembers.userId eq userId)
            }.count() > 0

            if (!alreadyMember) {
                ChannelMembers.insert {
                    it[ChannelMembers.channelId] = channelId
                    it[ChannelMembers.userId] = userId
                    it[ChannelMembers.joinedAt] = Instant.now()
                }
            }
        }
    }

    fun listMessages(userId: String, channelId: String, limit: Int = 100): List<ChatMessageResponse> {
        return transaction {
            ensureMember(userId, channelId)

            Messages.select { Messages.channelId eq channelId }
                .orderBy(Messages.createdAt, SortOrder.DESC)
                .limit(limit)
                .map {
                    ChatMessageResponse(
                        id = it[Messages.id],
                        channelId = it[Messages.channelId],
                        senderId = it[Messages.senderId],
                        content = it[Messages.content],
                        createdAt = it[Messages.createdAt].toString()
                    )
                }
                .reversed()
        }
    }

    fun postMessage(userId: String, channelId: String, content: String): ChatMessageResponse {
        if (content.isBlank()) {
            throw ServiceException(HttpStatusCode.BadRequest, "Message cannot be empty")
        }

        return transaction {
            ensureMember(userId, channelId)
            val id = UUID.randomUUID().toString()
            val now = Instant.now()
            Messages.insert {
                it[Messages.id] = id
                it[Messages.channelId] = channelId
                it[Messages.senderId] = userId
                it[Messages.content] = content
                it[Messages.createdAt] = now
            }
            ChatMessageResponse(
                id = id,
                channelId = channelId,
                senderId = userId,
                content = content,
                createdAt = now.toString()
            )
        }
    }

    private fun ensureMember(userId: String, channelId: String) {
        val isMember = ChannelMembers.select {
            (ChannelMembers.channelId eq channelId) and (ChannelMembers.userId eq userId)
        }.count() > 0

        if (!isMember) {
            throw ServiceException(HttpStatusCode.Forbidden, "Not a member of this channel")
        }
    }
}
