package com.chogm.discord.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtConfig {
    private val secret = System.getenv("JWT_SECRET") ?: "change-me"
    private val issuer = System.getenv("JWT_ISSUER") ?: "discord-clone"
    private val audience = System.getenv("JWT_AUDIENCE") ?: "discord-clone-users"
    val realm: String = System.getenv("JWT_REALM") ?: "discord"

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun makeToken(userId: String): String {
        val expiresAt = Date(System.currentTimeMillis() + 1000L * 60 * 60 * 12)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("uid", userId)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }
}
