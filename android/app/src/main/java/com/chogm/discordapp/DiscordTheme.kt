package com.chogm.discordapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object DiscordColors {
    val Blurple = Color(0xFF5865F2)
    val Green = Color(0xFF57F287)
    val WelcomeStart = Color(0xFF2A2D88)
    val WelcomeEnd = Color(0xFF0B0D33)
    val WelcomeOrbSoft = Color(0x33FFFFFF)
    val WelcomeOrbBright = Color(0x4D8E8BFF)
    val WelcomeTextPrimary = Color(0xFFFFFFFF)
    val WelcomeTextSecondary = Color(0xFFC9D0FF)
    val LightBackground = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF1F2430)
    val TextSecondary = Color(0xFF6B7280)
    val TextHint = Color(0xFFA0A5B1)
    val InputBackground = Color(0xFFF1F2F6)
    val ButtonPrimary = Color(0xFF7B7FF2)
    val ButtonPrimaryText = Color(0xFFFFFFFF)
    val ButtonSecondary = Color(0xFFFFFFFF)
    val ButtonSecondaryBorder = Color(0xFFE2E4EF)
    val ButtonSecondaryText = Color(0xFF1F2430)
    val DarkBackground = Color(0xFF1E1F24)
    val DarkSurface = Color(0xFF25262D)
    val DarkSurfaceAlt = Color(0xFF2B2D36)
    val DarkCard = Color(0xFF2F313A)
    val DarkRail = Color(0xFF181A1F)
    val DarkBorder = Color(0xFF343741)
    val TextPrimaryDark = Color(0xFFF2F4F8)
    val TextSecondaryDark = Color(0xFFA7ACB8)
    val TextMutedDark = Color(0xFF7B8190)
    val AccentBlue = Color(0xFF5865F2)
    val NavBackground = Color(0xFF20222A)
    val NavInactive = Color(0xFF8A8F9B)
    val NavActive = Color(0xFF7B7FF2)
    val ProfileHeaderStart = Color(0xFFF3A12B)
    val ProfileHeaderEnd = Color(0xFFE56E1E)
    val SheetBackground = Color(0xFF2C2E36)
}

@Composable
fun DiscordTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = DiscordColors.AccentBlue,
            secondary = DiscordColors.Green,
            background = DiscordColors.DarkBackground,
            surface = DiscordColors.DarkSurface,
            onPrimary = DiscordColors.TextPrimaryDark,
            onSecondary = DiscordColors.TextPrimaryDark,
            onBackground = DiscordColors.TextPrimaryDark,
            onSurface = DiscordColors.TextPrimaryDark
        )
    } else {
        lightColorScheme(
            primary = DiscordColors.Blurple,
            secondary = DiscordColors.Green,
            background = DiscordColors.LightBackground,
            surface = DiscordColors.InputBackground,
            onPrimary = DiscordColors.ButtonPrimaryText,
            onSecondary = DiscordColors.TextPrimary,
            onBackground = DiscordColors.TextPrimary,
            onSurface = DiscordColors.TextPrimary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
