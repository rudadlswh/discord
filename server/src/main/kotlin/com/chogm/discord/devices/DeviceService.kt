package com.chogm.discord.devices

import com.chogm.discord.ServiceException
import com.chogm.discord.db.DeviceTokens
import com.chogm.discord.models.DeviceTokenRequest
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

data class DeviceToken(
    val platform: String,
    val token: String,
    val voipToken: String?
)

class DeviceService {
    fun registerDevice(userId: String, request: DeviceTokenRequest) {
        val platform = request.platform.trim().lowercase()
        if (platform != "ios" && platform != "android") {
            throw ServiceException(HttpStatusCode.BadRequest, "Unsupported platform")
        }

        val token = request.token.trim()
        if (token.isEmpty()) {
            throw ServiceException(HttpStatusCode.BadRequest, "Missing token")
        }

        val voipToken = request.voipToken?.trim()?.ifEmpty { null }
        val now = Instant.now()

        transaction {
            val existing = DeviceTokens
                .select {
                    (DeviceTokens.userId eq userId) and
                        (DeviceTokens.platform eq platform) and
                        (DeviceTokens.token eq token)
                }
                .singleOrNull()

            if (existing == null) {
                DeviceTokens.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[DeviceTokens.userId] = userId
                    it[DeviceTokens.platform] = platform
                    it[DeviceTokens.token] = token
                    it[DeviceTokens.voipToken] = voipToken
                    it[DeviceTokens.updatedAt] = now
                }
            } else {
                DeviceTokens.update({
                    (DeviceTokens.userId eq userId) and
                        (DeviceTokens.platform eq platform) and
                        (DeviceTokens.token eq token)
                }) {
                    it[DeviceTokens.voipToken] = voipToken
                    it[DeviceTokens.updatedAt] = now
                }
            }
        }
    }

    fun listTokens(userId: String): List<DeviceToken> {
        return transaction {
            DeviceTokens
                .select { DeviceTokens.userId eq userId }
                .map { row ->
                    DeviceToken(
                        platform = row[DeviceTokens.platform],
                        token = row[DeviceTokens.token],
                        voipToken = row[DeviceTokens.voipToken]
                    )
                }
        }
    }
}
