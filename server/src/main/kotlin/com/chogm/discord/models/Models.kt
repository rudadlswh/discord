package com.chogm.discord.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val displayName: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserResponse
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val username: String,
    val displayName: String,
    val createdAt: String
)

@Serializable
data class FriendRequestCreateRequest(
    val addresseeId: String
)

@Serializable
data class FriendRequestResponse(
    val id: String,
    val requesterId: String,
    val addresseeId: String,
    val status: FriendRequestStatus,
    val createdAt: String,
    val respondedAt: String? = null
)

@Serializable
data class FriendResponse(
    val id: String,
    val username: String,
    val displayName: String
)

@Serializable
data class ChannelCreateRequest(
    val name: String,
    val type: ChannelType
)

@Serializable
data class ChannelResponse(
    val id: String,
    val name: String,
    val type: ChannelType,
    val createdBy: String,
    val createdAt: String
)

@Serializable
data class ChatMessageRequest(
    val content: String
)

@Serializable
data class ChatMessageResponse(
    val id: String,
    val channelId: String,
    val senderId: String,
    val content: String,
    val createdAt: String
)

@Serializable
data class SignalEnvelope(
    val type: String,
    val targetUserId: String? = null,
    val fromUserId: String? = null,
    val payload: JsonElement? = null
)

@Serializable
data class SignalError(
    val error: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
enum class ChannelType {
    TEXT,
    VOICE
}

@Serializable
enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
