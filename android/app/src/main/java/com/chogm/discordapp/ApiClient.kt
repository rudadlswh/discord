package com.chogm.discordapp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    data class HttpResult(val code: Int, val body: String)

    fun buildUrl(baseUrl: String, path: String): String {
        return baseUrl.trimEnd('/') + path
    }

    fun buildWebSocketUrl(baseUrl: String, path: String): String? {
        val trimmed = baseUrl.trim().trimEnd { it == '/' }
        return when {
            trimmed.startsWith("https://") -> {
                "wss://" + trimmed.removePrefix("https://") + path
            }
            trimmed.startsWith("http://") -> {
                "ws://" + trimmed.removePrefix("http://") + path
            }
            else -> null
        }
    }

    fun executeRequest(
        method: String,
        url: String,
        jsonBody: JSONObject? = null,
        token: String? = null
    ): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = method
        connection.setRequestProperty("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        if (jsonBody != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(jsonBody.toString()) }
        }

        val code = connection.responseCode
        val body = readResponseBody(connection, code)
        connection.disconnect()
        return HttpResult(code, body)
    }

    fun extractErrorMessage(body: String): String {
        if (body.isBlank()) {
            return "No response body"
        }
        return try {
            val error = JSONObject(body).optString("error").trim()
            if (error.isBlank()) body else error
        } catch (_: Exception) {
            body
        }
    }

    private fun readResponseBody(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }
}
