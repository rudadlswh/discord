package com.chogm.discord

import com.chogm.discord.auth.AuthService
import com.chogm.discord.auth.JwtConfig
import com.chogm.discord.auth.authRoutes
import com.chogm.discord.channels.ChannelService
import com.chogm.discord.channels.channelRoutes
import com.chogm.discord.db.DatabaseFactory
import com.chogm.discord.db.DbConfig
import com.chogm.discord.friend.FriendService
import com.chogm.discord.friend.friendRoutes
import com.chogm.discord.models.ErrorResponse
import com.chogm.discord.realtime.ChatHub
import com.chogm.discord.realtime.SignalHub
import com.chogm.discord.realtime.webSocketRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init(DbConfig())

    install(ContentNegotiation) {
        json(JsonSupport.json)
    }

    install(WebSockets) {
        pingPeriodMillis = 20_000
        timeoutMillis = 30_000
    }

    install(CallLogging)

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowNonSimpleContentTypes = true
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtConfig.realm
            verifier(JwtConfig.verifier)
            validate { credential ->
                val userId = credential.payload.getClaim("uid").asString()
                if (userId.isNullOrBlank()) null else io.ktor.server.auth.jwt.JWTPrincipal(credential.payload)
            }
        }
    }

    install(StatusPages) {
        exception<ServiceException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
    }

    val authService = AuthService()
    val friendService = FriendService()
    val channelService = ChannelService()
    val chatHub = ChatHub()
    val signalHub = SignalHub()

    routing {
        get("/ping") {
            call.respondText("OK")
        }

        authRoutes(authService)

        authenticate("auth-jwt") {
            friendRoutes(friendService)
            channelRoutes(channelService)
        }

        webSocketRoutes(chatHub, signalHub, channelService)
    }
}
