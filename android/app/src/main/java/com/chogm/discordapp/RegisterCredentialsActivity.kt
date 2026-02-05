package com.chogm.discordapp

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RegisterCredentialsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_credentials)

        val email = intent.getStringExtra(RegisterExtras.EMAIL)
        if (email.isNullOrBlank()) {
            finish()
            return
        }

        val backButton = findViewById<TextView>(R.id.registerCredentialsBackButton)
        val usernameInput = findViewById<EditText>(R.id.registerUsernameInput)
        val passwordInput = findViewById<EditText>(R.id.registerPasswordInput)
        val nextButton = findViewById<TextView>(R.id.registerCredentialsNextButton)

        backButton.setOnClickListener { finish() }

        nextButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (username.isBlank()) {
                usernameInput.error = getString(R.string.register_required_username)
                return@setOnClickListener
            }
            if (password.isBlank()) {
                passwordInput.error = getString(R.string.register_required_password)
                return@setOnClickListener
            }

            val intent = Intent(this, RegisterDisplayNameActivity::class.java)
                .putExtra(RegisterExtras.EMAIL, email)
                .putExtra(RegisterExtras.USERNAME, username)
                .putExtra(RegisterExtras.PASSWORD, password)
            startActivity(intent)
        }
    }
}
