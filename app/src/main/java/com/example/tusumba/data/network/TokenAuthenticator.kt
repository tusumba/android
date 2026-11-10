package com.example.tusumba.data.network

import com.example.tusumba.data.identity.api.DefaultApi
import com.example.tusumba.data.identity.model.RefreshRequest
import com.example.tusumba.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider

class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApiProvider: Provider<DefaultApi>
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val refreshToken = tokenManager.getRefreshToken() ?: return null

        synchronized(refreshLock) {
            val currentToken = tokenManager.getAccessToken()
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (currentToken != null && currentToken != failedToken) {
                return response.request.newBuilder().header("Authorization", "Bearer $currentToken").build()
            }
            return runBlocking {
                try {
                    val newTokensResponse = authApiProvider.get().refreshToken(RefreshRequest(refreshToken))
                    if (newTokensResponse.isSuccessful) {
                        val newTokens = newTokensResponse.body()
                        if (newTokens != null) {
                            tokenManager.saveTokens(newTokens.accessToken, newTokens.refreshToken)
                            return@runBlocking response.request.newBuilder().header("Authorization", "Bearer ${newTokens.accessToken}").build()
                        }
                    }
                    tokenManager.clearTokens()
                    null
                } catch (e: Exception) {
                    tokenManager.clearTokens()
                    null
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var priorResponse = response.priorResponse
        while (priorResponse != null) { count++; priorResponse = priorResponse.priorResponse }
        return count
    }
}