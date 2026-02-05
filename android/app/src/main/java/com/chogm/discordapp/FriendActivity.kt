package com.chogm.discordapp

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class FriendActivity : AppCompatActivity() {
    private enum class HomeTab {
        HOME,
        NOTIFICATIONS,
        PROFILE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = AppPrefs.getToken(this)
        if (token.isNullOrBlank()) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_friends)

        val messagesScreen = findViewById<View>(R.id.messagesScreen)
        val notificationsScreen = findViewById<View>(R.id.notificationsScreen)
        val profileScreen = findViewById<View>(R.id.profileScreen)

        val navHome = findViewById<View>(R.id.navHome)
        val navNotifications = findViewById<View>(R.id.navNotifications)
        val navProfile = findViewById<View>(R.id.navProfile)

        val navHomeIcon = findViewById<ImageView>(R.id.navHomeIcon)
        val navNotificationsIcon = findViewById<ImageView>(R.id.navNotificationsIcon)
        val navProfileIcon = findViewById<ImageView>(R.id.navProfileIcon)

        val navHomeLabel = findViewById<TextView>(R.id.navHomeLabel)
        val navNotificationsLabel = findViewById<TextView>(R.id.navNotificationsLabel)
        val navProfileLabel = findViewById<TextView>(R.id.navProfileLabel)

        val navHomeIndicator = findViewById<View>(R.id.navHomeIndicator)
        val navNotificationsIndicator = findViewById<View>(R.id.navNotificationsIndicator)
        val navProfileIndicator = findViewById<View>(R.id.navProfileIndicator)

        val notificationsMenuButton = findViewById<ImageButton>(R.id.notificationsMenuButton)

        val displayName = AppPrefs.getDisplayName(this) ?: getString(R.string.profile_default_name)
        val username = AppPrefs.getUsername(this) ?: getString(R.string.profile_default_username)
        val avatarInitial = displayName.trim().take(1).ifEmpty { "D" }

        findViewById<TextView>(R.id.profileDisplayName).text = displayName
        findViewById<TextView>(R.id.profileUsername).text = "@$username"
        findViewById<TextView>(R.id.profileAvatar).text = avatarInitial

        fun setNavSelected(
            selected: Boolean,
            icon: ImageView,
            label: TextView,
            indicator: View
        ) {
            val color = if (selected) getColor(R.color.nav_active) else getColor(R.color.nav_inactive)
            icon.imageTintList = ColorStateList.valueOf(color)
            label.setTextColor(color)
            indicator.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }

        fun selectTab(tab: HomeTab) {
            messagesScreen.visibility = if (tab == HomeTab.HOME) View.VISIBLE else View.GONE
            notificationsScreen.visibility = if (tab == HomeTab.NOTIFICATIONS) View.VISIBLE else View.GONE
            profileScreen.visibility = if (tab == HomeTab.PROFILE) View.VISIBLE else View.GONE

            setNavSelected(tab == HomeTab.HOME, navHomeIcon, navHomeLabel, navHomeIndicator)
            setNavSelected(
                tab == HomeTab.NOTIFICATIONS,
                navNotificationsIcon,
                navNotificationsLabel,
                navNotificationsIndicator
            )
            setNavSelected(tab == HomeTab.PROFILE, navProfileIcon, navProfileLabel, navProfileIndicator)
        }

        navHome.setOnClickListener { selectTab(HomeTab.HOME) }
        navNotifications.setOnClickListener { selectTab(HomeTab.NOTIFICATIONS) }
        navProfile.setOnClickListener { selectTab(HomeTab.PROFILE) }

        notificationsMenuButton.setOnClickListener { showNotificationsSheet() }

        selectTab(HomeTab.HOME)
    }

    private fun showNotificationsSheet() {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.sheet_notifications)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundResource(android.R.color.transparent)
        }
        dialog.show()
    }
}
