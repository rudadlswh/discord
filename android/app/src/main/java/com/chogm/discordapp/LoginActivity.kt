package com.chogm.discordapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val existingToken = AppPrefs.getToken(this)
        if (!existingToken.isNullOrBlank()) {
            startActivity(Intent(this, FriendActivity::class.java))
            finish()
            return
        }

        setContent {
            DiscordTheme(darkTheme = false) {
                LoginScreen(
                    onBack = { finish() },
                    onLoginSuccess = {
                        startActivity(Intent(this, FriendActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}
