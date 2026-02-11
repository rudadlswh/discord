package com.chogm.discord.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(config: DbConfig) {
        require(config.user.isNotBlank()) { "DB_USER is required" }
        require(config.password.isNotBlank()) { "DB_PASSWORD is required" }

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            driverClassName = config.driver
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }

        Database.connect(HikariDataSource(hikariConfig))

        if (config.autoMigrate) {
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    Users,
                    FriendRequests,
                    Friendships,
                    Channels,
                    ChannelMembers,
                    Messages,
                    DeviceTokens,
                    CallSessions,
                    CallParticipants
                )
            }
        }
    }
}
