package com.chogm.discord

import kotlinx.serialization.json.Json

object JsonSupport {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}
