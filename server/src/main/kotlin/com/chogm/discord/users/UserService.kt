package com.chogm.discord.users

import com.chogm.discord.ServiceException
import com.chogm.discord.db.Users
import com.chogm.discord.models.UserLookupResponse
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class UserService {
    fun lookup(query: String): UserLookupResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            throw ServiceException(HttpStatusCode.BadRequest, "Missing query")
        }

        val row = transaction {
            Users.select {
                (Users.username eq trimmed) or
                    (Users.email eq trimmed) or
                    (Users.id eq trimmed)
            }.singleOrNull()
        } ?: throw ServiceException(HttpStatusCode.NotFound, "User not found")

        return UserLookupResponse(
            id = row[Users.id],
            username = row[Users.username],
            displayName = row[Users.displayName]
        )
    }
}
