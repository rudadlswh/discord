package com.chogm.discord.users

import com.chogm.discord.ServiceException
import com.chogm.discord.models.ErrorResponse
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.userRoutes(userService: UserService) {
    get("/api/users/lookup") {
        try {
            val query = call.request.queryParameters["query"] ?: throw ServiceException(
                io.ktor.http.HttpStatusCode.BadRequest,
                "Missing query"
            )
            val response = userService.lookup(query)
            call.respond(response)
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }
}
