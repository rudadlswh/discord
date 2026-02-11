package com.chogm.discord.db

data class DbConfig(
    val url: String = System.getenv("DB_URL") ?: "jdbc:oracle:thin:@localhost:1521/FREEPDB1",
    val user: String = System.getenv("DB_USER") ?: "",
    val password: String = System.getenv("DB_PASSWORD") ?: "",
    val driver: String = System.getenv("DB_DRIVER") ?: "oracle.jdbc.OracleDriver",
    val autoMigrate: Boolean = (System.getenv("DB_AUTO_MIGRATE") ?: "true").toBoolean()
)
