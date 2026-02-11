package com.chogm.discord.dm

import com.chogm.discord.ServiceException
import com.chogm.discord.models.DirectMessageSendRequest
import com.chogm.discord.models.ErrorResponse
import com.chogm.discord.users.requireUserId
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.directMessageRoutes(directMessageService: DirectMessageService) {
    get("/api/dm/threads") {
        try {
            val userId = call.requireUserId()
            call.respond(directMessageService.listThreads(userId))
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }

    post("/api/dm/send") {
        try {
            val userId = call.requireUserId()
            val request = call.receive<DirectMessageSendRequest>()
            call.respond(directMessageService.sendMessage(userId, request))
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }
}
