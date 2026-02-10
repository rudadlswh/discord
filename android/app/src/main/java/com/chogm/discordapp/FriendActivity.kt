package com.chogm.discordapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class FriendActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = AppPrefs.getToken(this)
        if (token.isNullOrBlank()) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        requestNotificationPermissionIfNeeded()
        FriendNotifications.ensureChannel(this)
        MessageNotifications.ensureChannel(this)

        setContent {
            DiscordTheme(darkTheme = true) {
                FriendHomeScreen(onLogout = { performLogout() })
            }
        }
    }

    private fun performLogout() {
        AppPrefs.clearAuth(this)
        val intent = Intent(this, WelcomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
