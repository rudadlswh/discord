package com.chogm.discordapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class RegisterDisplayNameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val email = intent.getStringExtra(RegisterExtras.EMAIL)
        val username = intent.getStringExtra(RegisterExtras.USERNAME)
        val password = intent.getStringExtra(RegisterExtras.PASSWORD)

        if (email.isNullOrBlank() || username.isNullOrBlank() || password.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            DiscordTheme(darkTheme = false) {
                RegisterDisplayNameScreen(
                    email = email,
                    username = username,
                    password = password,
                    onBack = { finish() },
                    onSuccess = {
                        startActivity(Intent(this, FriendActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}
