package com.chogm.discord.friend

import com.chogm.discord.ServiceException
import com.chogm.discord.db.FriendRequests
import com.chogm.discord.db.Friendships
import com.chogm.discord.db.Users
import com.chogm.discord.models.FriendRequestResponse
import com.chogm.discord.models.FriendRequestStatus
import com.chogm.discord.models.FriendResponse
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class FriendService {
    fun createRequest(requesterId: String, addresseeId: String): FriendRequestResponse {
        if (requesterId == addresseeId) {
            throw ServiceException(HttpStatusCode.BadRequest, "Cannot friend yourself")
        }

        return transaction {
            val addresseeExists = Users.select { Users.id eq addresseeId }.count() > 0
            if (!addresseeExists) {
                throw ServiceException(HttpStatusCode.NotFound, "Addressee not found")
            }

            val alreadyFriends = Friendships
                .select { (Friendships.userId eq requesterId) and (Friendships.friendId eq addresseeId) }
                .count() > 0
            if (alreadyFriends) {
                throw ServiceException(HttpStatusCode.Conflict, "Already friends")
            }

            val pendingBetween = FriendRequests
                .select {
                    ((FriendRequests.requesterId eq requesterId) and (FriendRequests.addresseeId eq addresseeId)) or
                        ((FriendRequests.requesterId eq addresseeId) and (FriendRequests.addresseeId eq requesterId))
                }
                .andWhere { FriendRequests.status eq FriendRequestStatus.PENDING.name }
                .count() > 0

            if (pendingBetween) {
                throw ServiceException(HttpStatusCode.Conflict, "Friend request already pending")
            }

            val id = UUID.randomUUID().toString()
            val now = Instant.now()

            FriendRequests.insert {
                it[FriendRequests.id] = id
                it[FriendRequests.requesterId] = requesterId
                it[FriendRequests.addresseeId] = addresseeId
                it[FriendRequests.status] = FriendRequestStatus.PENDING.name
                it[FriendRequests.createdAt] = now
                it[FriendRequests.respondedAt] = null
            }

            FriendRequestResponse(
                id = id,
                requesterId = requesterId,
                addresseeId = addresseeId,
                status = FriendRequestStatus.PENDING,
                createdAt = now.toString(),
                respondedAt = null
            )
        }
    }

    fun listPending(userId: String): List<FriendRequestResponse> {
        return transaction {
            FriendRequests
                .select { (FriendRequests.addresseeId eq userId) and (FriendRequests.status eq FriendRequestStatus.PENDING.name) }
                .map { row ->
                    FriendRequestResponse(
                        id = row[FriendRequests.id],
                        requesterId = row[FriendRequests.requesterId],
                        addresseeId = row[FriendRequests.addresseeId],
                        status = FriendRequestStatus.valueOf(row[FriendRequests.status]),
                        createdAt = row[FriendRequests.createdAt].toString(),
                        respondedAt = row[FriendRequests.respondedAt]?.toString()
                    )
                }
        }
    }

    fun acceptRequest(requestId: String, userId: String): FriendRequestResponse {
        return respondToRequest(requestId, userId, FriendRequestStatus.ACCEPTED)
    }

    fun rejectRequest(requestId: String, userId: String): FriendRequestResponse {
        return respondToRequest(requestId, userId, FriendRequestStatus.REJECTED)
    }

    fun listFriends(userId: String): List<FriendResponse> {
        return transaction {
            val friendIds = Friendships
                .slice(Friendships.friendId)
                .select { Friendships.userId eq userId }
                .map { it[Friendships.friendId] }

            if (friendIds.isEmpty()) {
                emptyList()
            } else {
                Users.select { Users.id inList friendIds }
                    .map {
                        FriendResponse(
                            id = it[Users.id],
                            username = it[Users.username],
                            displayName = it[Users.displayName]
                        )
                    }
            }
        }
    }

    private fun respondToRequest(
        requestId: String,
        userId: String,
        newStatus: FriendRequestStatus
    ): FriendRequestResponse {
        return transaction {
            val row = FriendRequests.select { FriendRequests.id eq requestId }.singleOrNull()
                ?: throw ServiceException(HttpStatusCode.NotFound, "Friend request not found")

            val addresseeId = row[FriendRequests.addresseeId]
            if (addresseeId != userId) {
                throw ServiceException(HttpStatusCode.Forbidden, "Not allowed to respond to this request")
            }

            val status = FriendRequestStatus.valueOf(row[FriendRequests.status])
            if (status != FriendRequestStatus.PENDING) {
                throw ServiceException(HttpStatusCode.Conflict, "Friend request already resolved")
            }

            val now = Instant.now()
            FriendRequests.update({ FriendRequests.id eq requestId }) {
                it[FriendRequests.status] = newStatus.name
                it[FriendRequests.respondedAt] = now
            }

            if (newStatus == FriendRequestStatus.ACCEPTED) {
                val requesterId = row[FriendRequests.requesterId]
                val existed = Friendships.select {
                    (Friendships.userId eq requesterId) and (Friendships.friendId eq addresseeId)
                }.count() > 0

                if (!existed) {
                    Friendships.insert {
                        it[Friendships.userId] = requesterId
                        it[Friendships.friendId] = addresseeId
                        it[Friendships.createdAt] = now
                    }
                    Friendships.insert {
                        it[Friendships.userId] = addresseeId
                        it[Friendships.friendId] = requesterId
                        it[Friendships.createdAt] = now
                    }
                }
            }

            FriendRequestResponse(
                id = row[FriendRequests.id],
                requesterId = row[FriendRequests.requesterId],
                addresseeId = row[FriendRequests.addresseeId],
                status = newStatus,
                createdAt = row[FriendRequests.createdAt].toString(),
                respondedAt = now.toString()
            )
        }
    }
}
