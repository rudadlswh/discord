package com.chogm.discordapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class RegisterEmailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiscordTheme(darkTheme = false) {
                RegisterEmailScreen(
                    onBack = { finish() },
                    onNext = { identifier ->
                        val intent = Intent(this, RegisterCredentialsActivity::class.java)
                            .putExtra(RegisterExtras.EMAIL, identifier)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
