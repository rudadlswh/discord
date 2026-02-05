package com.chogm.discordapp

import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.EditText
import android.widget.TextView

class RegisterEmailActivity : AppCompatActivity() {
    private var useEmail = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_email)

        val backButton = findViewById<TextView>(R.id.registerEmailBackButton)
        val phoneToggle = findViewById<TextView>(R.id.togglePhone)
        val emailToggle = findViewById<TextView>(R.id.toggleEmail)
        val emailGroup = findViewById<View>(R.id.registerEmailGroup)
        val phoneGroup = findViewById<View>(R.id.registerPhoneGroup)
        val emailInput = findViewById<EditText>(R.id.registerEmailInput)
        val phoneInput = findViewById<EditText>(R.id.registerPhoneInput)
        val nextButton = findViewById<TextView>(R.id.registerEmailNextButton)

        backButton.setOnClickListener { finish() }

        fun updateToggle() {
            if (useEmail) {
                emailToggle.setBackgroundResource(R.drawable.bg_segmented_selected)
                emailToggle.setTextColor(getColor(R.color.text_primary))
                emailToggle.setTypeface(null, Typeface.BOLD)

                phoneToggle.background = null
                phoneToggle.setTextColor(getColor(R.color.text_secondary))
                phoneToggle.setTypeface(null, Typeface.NORMAL)

                emailGroup.visibility = View.VISIBLE
                phoneGroup.visibility = View.GONE
            } else {
                phoneToggle.setBackgroundResource(R.drawable.bg_segmented_selected)
                phoneToggle.setTextColor(getColor(R.color.text_primary))
                phoneToggle.setTypeface(null, Typeface.BOLD)

                emailToggle.background = null
                emailToggle.setTextColor(getColor(R.color.text_secondary))
                emailToggle.setTypeface(null, Typeface.NORMAL)

                emailGroup.visibility = View.GONE
                phoneGroup.visibility = View.VISIBLE
            }
        }

        phoneToggle.setOnClickListener {
            useEmail = false
            updateToggle()
        }

        emailToggle.setOnClickListener {
            useEmail = true
            updateToggle()
        }

        nextButton.setOnClickListener {
            val identifier = if (useEmail) {
                val email = emailInput.text.toString().trim()
                if (email.isBlank()) {
                    emailInput.error = getString(R.string.register_required_email)
                    return@setOnClickListener
                }
                email
            } else {
                val phone = phoneInput.text.toString().trim()
                if (phone.isBlank()) {
                    phoneInput.error = getString(R.string.register_required_phone)
                    return@setOnClickListener
                }
                phone
            }

            val intent = Intent(this, RegisterCredentialsActivity::class.java)
                .putExtra(RegisterExtras.EMAIL, identifier)
            startActivity(intent)
        }

        updateToggle()
    }
}
