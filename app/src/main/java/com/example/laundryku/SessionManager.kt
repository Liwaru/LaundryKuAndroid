package com.example.laundryku

import android.content.Context
import com.example.laundryku.model.UserData

class SessionManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun saveLoginSession(user: UserData): Boolean {
        if (!isValidSessionData(user.token, user.idUser, user.nama, user.noHp, user.username, user.level)) {
            clearSession()
            return false
        }
        return preferences.edit()
            .putInt(KEY_USER_ID, user.idUser)
            .putString(KEY_NAME, user.nama)
            .putString(KEY_PHONE, user.noHp)
            .putString(KEY_USERNAME, user.username)
            .putInt(KEY_LEVEL, user.level)
            // Bearer token is a credential: keep it only in MODE_PRIVATE storage and never log it.
            .putString(KEY_AUTH_TOKEN, user.token)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .commit()
    }

    fun isLoggedIn(): Boolean {
        if (!preferences.getBoolean(KEY_IS_LOGGED_IN, false)) return false
        val valid = isValidSessionData(
            getAuthToken(),
            getUserId(),
            getNama(),
            getNoHp(),
            getUsername(),
            getLevel()
        )
        if (!valid) clearSession()
        return valid
    }

    fun getUserId(): Int = preferences.getInt(KEY_USER_ID, INVALID_USER_ID)

    fun getNama(): String = preferences.getString(KEY_NAME, "").orEmpty()

    fun getNoHp(): String = preferences.getString(KEY_PHONE, "").orEmpty()

    fun getUsername(): String = preferences.getString(KEY_USERNAME, "").orEmpty()

    fun getLevel(): Int = preferences.getInt(KEY_LEVEL, INVALID_LEVEL)

    fun getAuthToken(): String = preferences.getString(KEY_AUTH_TOKEN, "").orEmpty()

    fun updateProfile(nama: String, noHp: String, username: String): Boolean {
        if (!isLoggedIn() || nama.isBlank() || noHp.isBlank() || username.isBlank()) return false
        return preferences.edit()
            .putString(KEY_NAME, nama)
            .putString(KEY_PHONE, noHp)
            .putString(KEY_USERNAME, username)
            .commit()
    }

    fun clearSession() {
        preferences.edit().clear().commit()
    }

    internal companion object {
        private const val PREFERENCES_NAME = "laundryku_session"
        private const val KEY_USER_ID = "id_user"
        private const val KEY_NAME = "nama"
        private const val KEY_PHONE = "no_hp"
        private const val KEY_USERNAME = "username"
        private const val KEY_LEVEL = "level"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val INVALID_USER_ID = -1
        private const val INVALID_LEVEL = -1

        fun isValidSessionData(
            token: String,
            userId: Int,
            name: String,
            phone: String,
            username: String,
            level: Int
        ): Boolean = token.matches(Regex("^[a-fA-F0-9]{64}$")) &&
            userId > 0 &&
            name.isNotBlank() &&
            phone.isNotBlank() &&
            username.isNotBlank() &&
            level in 1..4
    }
}
