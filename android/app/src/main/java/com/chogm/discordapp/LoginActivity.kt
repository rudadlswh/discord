package com.chogm.discordapp

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val existingToken = AppPrefs.getToken(this)
        if (!existingToken.isNullOrBlank()) {
            startActivity(Intent(this, FriendActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val backButton = findViewById<TextView>(R.id.loginBackButton)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val passwordToggle = findViewById<ImageView>(R.id.passwordToggle)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val defaultBaseUrl = getString(R.string.base_url_default)
        val baseUrl = AppPrefs.getBaseUrl(this, defaultBaseUrl)

        var isPasswordVisible = false

        backButton.setOnClickListener {
            finish()
        }

        passwordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordInput.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                passwordToggle.setImageResource(R.drawable.ic_visibility)
            } else {
                passwordInput.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                passwordToggle.setImageResource(R.drawable.ic_visibility_off)
            }
            passwordInput.setSelection(passwordInput.text.length)
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (email.isBlank() || password.isBlank()) {
                if (email.isBlank()) {
                    emailInput.error = getString(R.string.login_email_or_phone)
                }
                if (password.isBlank()) {
                    passwordInput.error = getString(R.string.auth_password_hint)
                }
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            Toast.makeText(this, getString(R.string.auth_login_in_progress), Toast.LENGTH_SHORT).show()

            Thread {
                val result = try {
                    val body = JSONObject()
                        .put("email", email)
                        .put("password", password)
                    val response = ApiClient.executeRequest(
                        method = "POST",
                        url = ApiClient.buildUrl(baseUrl, "/api/auth/login"),
                        jsonBody = body
                    )
                    handleAuthResponse(response, baseUrl)
                } catch (ex: Exception) {
                    "ERROR: ${ex.message}"
                }

                runOnUiThread {
                    if (!result.startsWith("OK")) {
                        Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                    }
                    loginButton.isEnabled = true
                }
            }.start()
        }
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
