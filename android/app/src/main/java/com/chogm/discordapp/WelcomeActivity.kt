package com.chogm.discordapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val existingToken = AppPrefs.getToken(this)
        if (!existingToken.isNullOrBlank()) {
            startActivity(Intent(this, FriendActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_welcome)

        val signupButton = findViewById<Button>(R.id.welcomeSignupButton)
        val loginButton = findViewById<Button>(R.id.welcomeLoginButton)

        signupButton.setOnClickListener {
            startActivity(Intent(this, RegisterEmailActivity::class.java))
        }

        loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}
