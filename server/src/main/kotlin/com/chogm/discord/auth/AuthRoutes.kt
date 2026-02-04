package com.chogm.discord.auth

import com.chogm.discord.ServiceException
import com.chogm.discord.models.ErrorResponse
import com.chogm.discord.models.LoginRequest
import com.chogm.discord.models.RegisterRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(authService: AuthService) {
    post("/api/auth/register") {
        try {
            val request = call.receive<RegisterRequest>()
            val response = authService.register(request)
            call.respond(response)
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }

    post("/api/auth/login") {
        try {
            val request = call.receive<LoginRequest>()
            val response = authService.login(request)
            call.respond(response)
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }
}
