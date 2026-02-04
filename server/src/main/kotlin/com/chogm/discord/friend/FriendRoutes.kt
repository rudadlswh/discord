package com.chogm.discord.friend

import com.chogm.discord.ServiceException
import com.chogm.discord.models.ErrorResponse
import com.chogm.discord.models.FriendRequestCreateRequest
import com.chogm.discord.users.requireUserId
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.friendRoutes(friendService: FriendService) {
    post("/api/friends/requests") {
        try {
            val userId = call.requireUserId()
            val request = call.receive<FriendRequestCreateRequest>()
            val response = friendService.createRequest(userId, request.addresseeId)
            call.respond(response)
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }

    get("/api/friends/requests") {
        try {
            val userId = call.requireUserId()
            call.respond(friendService.listPending(userId))
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }

    post("/api/friends/requests/{requestId}/accept") {
        try {
            val userId = call.requireUserId()
            val requestId = call.parameters["requestId"] ?: throw ServiceException(
                io.ktor.http.HttpStatusCode.BadRequest,
                "Missing requestId"
            )
            val response = friendService.acceptRequest(requestId, userId)
            call.respond(response)
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }

    post("/api/friends/requests/{requestId}/reject") {
        try {
            val userId = call.requireUserId()
            val requestId = call.parameters["requestId"] ?: throw ServiceException(
                io.ktor.http.HttpStatusCode.BadRequest,
                "Missing requestId"
            )
            val response = friendService.rejectRequest(requestId, userId)
            call.respond(response)
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }

    get("/api/friends") {
        try {
            val userId = call.requireUserId()
            call.respond(friendService.listFriends(userId))
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }
}
