package com.example.tusumba.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.tusumba.data.auth.AuthUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context, "tusumba_secure_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString("access_token", accessToken)
            putString("refresh_token", refreshToken)
            apply()
        }
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    // --- ПОЛЬЗОВАТЕЛЬ ---
    fun saveUser(user: AuthUser) {
        prefs.edit().putString("user_data", json.encodeToString(user)).apply()
    }

    fun getUser(): AuthUser? {
        val userJson = prefs.getString("user_data", null) ?: return null
        return try {
            json.decodeFromString<AuthUser>(userJson)
        } catch (e: Exception) {
            null
        }
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
    }
}