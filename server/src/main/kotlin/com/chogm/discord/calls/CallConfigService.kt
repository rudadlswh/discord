package com.chogm.discord.calls

import com.chogm.discord.models.IceConfigResponse
import com.chogm.discord.models.IceServerConfig

class CallConfigService {
    fun getIceConfig(): IceConfigResponse {
        val iceServers = mutableListOf<IceServerConfig>()

        val turnUrls = readUrls("TURN_URLS")
        if (turnUrls.isNotEmpty()) {
            val username = System.getenv("TURN_USERNAME") ?: ""
            val credential = System.getenv("TURN_PASSWORD") ?: ""
            if (username.isNotBlank() && credential.isNotBlank()) {
                iceServers.add(
                    IceServerConfig(
                        urls = turnUrls,
                        username = username,
                        credential = credential
                    )
                )
            }
        }

        val stunUrls = readUrls("STUN_URLS")
        if (stunUrls.isNotEmpty()) {
            iceServers.add(IceServerConfig(urls = stunUrls))
        }

        if (iceServers.isEmpty()) {
            iceServers.add(IceServerConfig(urls = listOf("stun:stun.l.google.com:19302")))
        }

        return IceConfigResponse(iceServers = iceServers)
    }

    private fun readUrls(name: String): List<String> {
        val raw = System.getenv(name) ?: return emptyList()
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
