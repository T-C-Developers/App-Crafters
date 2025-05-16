package com.example.quickconnect.core

import android.content.Context
import java.util.*

object UserPrefs {
    private const val PREFS_NAME    = "quick_connect_prefs"
    private const val KEY_USER_ID   = "user_id"
    private const val KEY_USER_NAME = "user_name"

    /**
     * Returns our stable userId, generating & saving one if needed.
     */
    fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_USER_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, id).apply()
        }
        return id
    }

    /**
     * Returns our stable displayName, generating & saving one if needed.
     * You can replace the default or prompt the user instead.
     */
    fun getUserName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var name = prefs.getString(KEY_USER_NAME, null)
        if (name.isNullOrBlank()) {
            name = "User-${UUID.randomUUID().toString().substring(0, 5)}"
            prefs.edit().putString(KEY_USER_NAME, name).apply()
        }
        return name
    }
}
