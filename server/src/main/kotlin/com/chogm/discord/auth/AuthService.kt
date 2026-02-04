package com.chogm.discord.auth

import com.chogm.discord.ServiceException
import com.chogm.discord.db.Users
import com.chogm.discord.models.AuthResponse
import com.chogm.discord.models.LoginRequest
import com.chogm.discord.models.RegisterRequest
import com.chogm.discord.models.UserResponse
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.UUID

class AuthService {
    fun register(request: RegisterRequest): AuthResponse {
        if (request.password.length < 8) {
            throw ServiceException(HttpStatusCode.BadRequest, "Password must be at least 8 characters")
        }

        val user = transaction {
            val duplicate = Users
                .select { (Users.email eq request.email) or (Users.username eq request.username) }
                .count() > 0
            if (duplicate) {
                throw ServiceException(HttpStatusCode.Conflict, "Email or username already exists")
            }

            val id = UUID.randomUUID().toString()
            val createdAt = Instant.now()
            val hash = BCrypt.hashpw(request.password, BCrypt.gensalt())

            Users.insert {
                it[Users.id] = id
                it[Users.email] = request.email
                it[Users.username] = request.username
                it[Users.displayName] = request.displayName
                it[Users.passwordHash] = hash
                it[Users.createdAt] = createdAt
            }

            UserResponse(
                id = id,
                email = request.email,
                username = request.username,
                displayName = request.displayName,
                createdAt = createdAt.toString()
            )
        }

        return AuthResponse(
            token = JwtConfig.makeToken(user.id),
            user = user
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val row = transaction {
            Users.select { Users.email eq request.email }.singleOrNull()
        } ?: throw ServiceException(HttpStatusCode.Unauthorized, "Invalid credentials")

        if (!BCrypt.checkpw(request.password, row[Users.passwordHash])) {
            throw ServiceException(HttpStatusCode.Unauthorized, "Invalid credentials")
        }

        val user = row.toUserResponse()

        return AuthResponse(
            token = JwtConfig.makeToken(user.id),
            user = user
        )
    }

    private fun ResultRow.toUserResponse(): UserResponse {
        return UserResponse(
            id = this[Users.id],
            email = this[Users.email],
            username = this[Users.username],
            displayName = this[Users.displayName],
            createdAt = this[Users.createdAt].toString()
        )
    }
}
