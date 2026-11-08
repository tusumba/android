package com.example.tusumba.data.network

import com.example.tusumba.data.local.TokenManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // TODO: В будущем здесь будет логика обновления токена (refresh).
        // Пока бэкенда нет, мы очищаем токены и разлогиниваем пользователя.
        tokenManager.clearTokens()
        return null
    }
}