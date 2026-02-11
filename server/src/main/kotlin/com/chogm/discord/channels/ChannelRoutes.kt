package com.chogm.discord.channels

import com.chogm.discord.ServiceException
import com.chogm.discord.models.ChannelCreateRequest
import com.chogm.discord.models.ChatMessageRequest
import com.chogm.discord.models.ErrorResponse
import com.chogm.discord.users.requireUserId
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.channelRoutes(channelService: ChannelService) {
    post("/api/channels") {
        try {
            val userId = call.requireUserId()
            val request = call.receive<ChannelCreateRequest>()
            call.respond(channelService.createChannel(userId, request))
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }

    get("/api/channels") {
        try {
            val userId = call.requireUserId()
            call.respond(channelService.listChannels(userId))
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }

    post("/api/channels/{channelId}/join") {
        try {
            val userId = call.requireUserId()
            val channelId = call.parameters["channelId"] ?: throw ServiceException(
                io.ktor.http.HttpStatusCode.BadRequest,
                "Missing channelId"
            )
            channelService.joinChannel(userId, channelId)
            call.respond(mapOf("status" to "joined"))
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }

    get("/api/channels/{channelId}/messages") {
        try {
            val userId = call.requireUserId()
            val channelId = call.parameters["channelId"] ?: throw ServiceException(
                io.ktor.http.HttpStatusCode.BadRequest,
                "Missing channelId"
            )
            call.respond(channelService.listMessages(userId, channelId))
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }

    post("/api/channels/{channelId}/messages") {
        try {
            val userId = call.requireUserId()
            val channelId = call.parameters["channelId"] ?: throw ServiceException(
                io.ktor.http.HttpStatusCode.BadRequest,
                "Missing channelId"
            )
            val request = call.receive<ChatMessageRequest>()
            val message = channelService.postMessage(userId, channelId, request.content)
            call.respond(message)
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }
}
