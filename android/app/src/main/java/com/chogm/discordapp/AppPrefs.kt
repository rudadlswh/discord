package com.chogm.discordapp

import android.content.Context

object AppPrefs {
    private const val NAME = "discord_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_SEEN_FRIEND_REQUESTS = "seen_friend_requests"
    private const val KEY_SEEN_MESSAGE_IDS = "seen_message_ids"
    private const val KEY_ACTIVE_CHANNEL_ID = "active_channel_id"
    private const val KEY_FCM_TOKEN = "fcm_token"

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

    fun getActiveChannelId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_CHANNEL_ID, null)

    fun setActiveChannelId(context: Context, value: String?) {
        val trimmed = value?.trim().orEmpty()
        val editor = prefs(context).edit()
        if (trimmed.isBlank()) {
            editor.remove(KEY_ACTIVE_CHANNEL_ID)
        } else {
            editor.putString(KEY_ACTIVE_CHANNEL_ID, trimmed)
        }
        editor.apply()
    }

    fun getFcmToken(context: Context): String? =
        prefs(context).getString(KEY_FCM_TOKEN, null)

    fun setFcmToken(context: Context, value: String?) {
        val trimmed = value?.trim().orEmpty()
        val editor = prefs(context).edit()
        if (trimmed.isBlank()) {
            editor.remove(KEY_FCM_TOKEN)
        } else {
            editor.putString(KEY_FCM_TOKEN, trimmed)
        }
        editor.apply()
    }

    private fun seenFriendRequestsKey(userId: String?): String {
        return if (userId.isNullOrBlank()) {
            KEY_SEEN_FRIEND_REQUESTS
        } else {
            "${KEY_SEEN_FRIEND_REQUESTS}_$userId"
        }
    }

    fun getSeenFriendRequestIds(context: Context, userId: String?): Set<String> {
        val key = seenFriendRequestsKey(userId)
        return prefs(context).getStringSet(key, emptySet()) ?: emptySet()
    }

    fun addSeenFriendRequestIds(context: Context, userId: String?, ids: Set<String>) {
        if (ids.isEmpty()) return
        val key = seenFriendRequestsKey(userId)
        val current = prefs(context).getStringSet(key, emptySet()) ?: emptySet()
        prefs(context).edit().putStringSet(key, current + ids).apply()
    }

    private fun seenMessageIdsKey(userId: String?): String {
        return if (userId.isNullOrBlank()) {
            KEY_SEEN_MESSAGE_IDS
        } else {
            "${KEY_SEEN_MESSAGE_IDS}_$userId"
        }
    }

    fun getSeenMessageIds(context: Context, userId: String?): Set<String> {
        val key = seenMessageIdsKey(userId)
        return prefs(context).getStringSet(key, emptySet()) ?: emptySet()
    }

    fun addSeenMessageIds(context: Context, userId: String?, ids: Set<String>) {
        if (ids.isEmpty()) return
        val key = seenMessageIdsKey(userId)
        val current = prefs(context).getStringSet(key, emptySet()) ?: emptySet()
        prefs(context).edit().putStringSet(key, current + ids).apply()
    }
}
