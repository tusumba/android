package com.example.tusumba.data.auth

import com.example.tusumba.data.identity.api.DefaultApi
import com.example.tusumba.data.identity.model.RefreshRequest
import com.example.tusumba.data.local.TokenManager
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

class SessionLogout @Inject constructor(
    @Named("identityLogout") private val api: DefaultApi,
    private val tokenManager: TokenManager,
) {
    suspend operator fun invoke() {
        try {
            val refreshToken = tokenManager.getRefreshToken()?.takeIf { it.isNotBlank() } ?: return
            api.logout(RefreshRequest(refreshToken)).errorBody()?.close()
        } catch (_: IOException) {
            // The local session must also end when Identity cannot be reached.
        } finally {
            tokenManager.clearTokens()
        }
    }
}
