package com.chogm.discordapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class RegisterCredentialsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val email = intent.getStringExtra(RegisterExtras.EMAIL)
        if (email.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            DiscordTheme(darkTheme = false) {
                RegisterCredentialsScreen(
                    onBack = { finish() },
                    onNext = { username, password ->
                        val intent = Intent(this, RegisterDisplayNameActivity::class.java)
                            .putExtra(RegisterExtras.EMAIL, email)
                            .putExtra(RegisterExtras.USERNAME, username)
                            .putExtra(RegisterExtras.PASSWORD, password)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
