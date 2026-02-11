package com.chogm.discord.users

import com.chogm.discord.ServiceException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal

fun ApplicationCall.requireUserId(): String {
    val principal = principal<JWTPrincipal>()
        ?: throw ServiceException(HttpStatusCode.Unauthorized, "Missing authentication")
    val userId = principal.payload.getClaim("uid").asString()
        ?: throw ServiceException(HttpStatusCode.Unauthorized, "Invalid token")
    return userId
}
