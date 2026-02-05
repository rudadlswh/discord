package com.chogm.discordapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class RegisterDisplayNameActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_display_name)

        val email = intent.getStringExtra(RegisterExtras.EMAIL)
        val username = intent.getStringExtra(RegisterExtras.USERNAME)
        val password = intent.getStringExtra(RegisterExtras.PASSWORD)

        if (email.isNullOrBlank() || username.isNullOrBlank() || password.isNullOrBlank()) {
            finish()
            return
        }

        val backButton = findViewById<TextView>(R.id.registerDisplayBackButton)
        val skipButton = findViewById<TextView>(R.id.registerDisplaySkip)
        val displayNameInput = findViewById<EditText>(R.id.registerDisplayInput)
        val nextButton = findViewById<Button>(R.id.registerDisplayNextButton)

        val baseUrl = AppPrefs.getBaseUrl(this, getString(R.string.base_url_default))

        backButton.setOnClickListener { finish() }
        skipButton.setOnClickListener {
            submitRegister(baseUrl, email, username, password, username, nextButton)
        }

        nextButton.setOnClickListener {
            val displayName = displayNameInput.text.toString().trim()
            if (displayName.isBlank()) {
                displayNameInput.error = getString(R.string.register_required_display_name)
                return@setOnClickListener
            }
            submitRegister(baseUrl, email, username, password, displayName, nextButton)
        }
    }

    private fun submitRegister(
        baseUrl: String,
        email: String,
        username: String,
        password: String,
        displayName: String,
        nextButton: Button
    ) {
        nextButton.isEnabled = false
        Toast.makeText(this, getString(R.string.auth_register_in_progress), Toast.LENGTH_SHORT).show()

        Thread {
            val result = try {
                val body = JSONObject()
                    .put("email", email)
                    .put("username", username)
                    .put("displayName", displayName)
                    .put("password", password)
                val response = ApiClient.executeRequest(
                    method = "POST",
                    url = ApiClient.buildUrl(baseUrl, "/api/auth/register"),
                    jsonBody = body
                )
                handleAuthResponse(response, baseUrl)
            } catch (ex: Exception) {
                "ERROR: ${ex.message}"
            }

            runOnUiThread {
                nextButton.isEnabled = true
                if (!result.startsWith("OK")) {
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun handleAuthResponse(response: ApiClient.HttpResult, baseUrl: String): String {
        return if (response.code in 200..299) {
            val json = JSONObject(response.body)
            val token = json.getString("token")
            val user = json.getJSONObject("user")
            val userId = user.getString("id")
            val username = user.getString("username")
            val displayName = user.getString("displayName")

            AppPrefs.saveAuth(this, token, userId, username, displayName)
            AppPrefs.setBaseUrl(this, baseUrl)

            startActivity(Intent(this, FriendActivity::class.java))
            finish()

            "OK"
        } else {
            "ERROR ${response.code}: ${ApiClient.extractErrorMessage(response.body)}"
        }
    }
}
