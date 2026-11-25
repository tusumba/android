package com.example.tusumba.notifications

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class PushDeviceBinding(val userId: String, val token: String, val deviceId: UUID)

@Singleton
class PushDeviceStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "tusumba_push_devices",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    internal fun binding(): PushDeviceBinding? {
        val userId = preferences.getString("user_id", null) ?: return null
        val token = preferences.getString("token", null) ?: return null
        val deviceId = preferences.getString("device_id", null) ?: return null
        return PushDeviceBinding(userId, token, UUID.fromString(deviceId))
    }

    internal fun pendingRemovals(userId: String): Set<UUID> =
        preferences.getStringSet("pending_$userId", emptySet()).orEmpty().map(UUID::fromString).toSet()

    internal fun replaceBinding(binding: PushDeviceBinding) {
        retireBinding()
        preferences.edit()
            .putString("user_id", binding.userId)
            .putString("token", binding.token)
            .putString("device_id", binding.deviceId.toString())
            .apply()
        forgetRemoval(binding.userId, binding.deviceId)
    }

    internal fun retireBinding() {
        val previous = binding() ?: return
        val pending = pendingRemovals(previous.userId).map { it.toString() }.toSet() + previous.deviceId.toString()
        preferences.edit()
            .putStringSet("pending_${previous.userId}", pending)
            .remove("user_id").remove("token").remove("device_id")
            .apply()
    }

    internal fun forgetRemoval(userId: String, deviceId: UUID) {
        val pending = pendingRemovals(userId).filterNot { it == deviceId }.map { it.toString() }.toSet()
        preferences.edit().putStringSet("pending_$userId", pending).apply()
    }

    fun permissionWasRequested(): Boolean = preferences.getBoolean("permission_requested", false)

    fun markPermissionRequested() {
        preferences.edit().putBoolean("permission_requested", true).apply()
    }
}
