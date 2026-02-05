package com.chogm.discordapp

import android.content.Context

object AppPrefs {
    private const val NAME = "discord_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_BASE_URL = "base_url"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun getUserId(context: Context): String? = prefs(context).getString(KEY_USER_ID, null)

    fun getUsername(context: Context): String? = prefs(context).getString(KEY_USERNAME, null)

    fun getDisplayName(context: Context): String? =
        prefs(context).getString(KEY_DISPLAY_NAME, null)

    fun saveAuth(context: Context, token: String, userId: String, username: String, displayName: String) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()
    }

    fun clearAuth(context: Context) {
        prefs(context).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_DISPLAY_NAME)
            .apply()
    }

    fun getBaseUrl(context: Context, defaultValue: String): String {
        return prefs(context).getString(KEY_BASE_URL, defaultValue) ?: defaultValue
    }

    fun setBaseUrl(context: Context, value: String) {
        prefs(context).edit().putString(KEY_BASE_URL, value).apply()
    }
}
