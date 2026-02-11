package com.chogm.discord.devices

import com.chogm.discord.ServiceException
import com.chogm.discord.models.DeviceTokenRequest
import com.chogm.discord.models.ErrorResponse
import com.chogm.discord.users.requireUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.deviceRoutes(deviceService: DeviceService) {
    post("/api/devices") {
        try {
            val userId = call.requireUserId()
            val request = call.receive<DeviceTokenRequest>()
            deviceService.registerDevice(userId, request)
            call.respond(HttpStatusCode.NoContent)
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }
}
