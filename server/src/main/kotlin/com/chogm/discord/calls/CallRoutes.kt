package com.chogm.discord.calls

import com.chogm.discord.ServiceException
import com.chogm.discord.models.ErrorResponse
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.callRoutes(callConfigService: CallConfigService) {
    get("/api/calls/ice") {
        try {
            call.respond(callConfigService.getIceConfig())
        } catch (ex: ServiceException) {
            call.respond(ex.status, ErrorResponse(ex.message))
        }
    }
}
