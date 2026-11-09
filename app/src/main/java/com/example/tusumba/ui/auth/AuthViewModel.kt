package com.example.tusumba.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.tusumba.data.auth.AuthRepository
import com.example.tusumba.data.auth.AuthResult
import com.example.tusumba.data.auth.AuthUser
import com.example.tusumba.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val tokenManager: TokenManager // <-- Внедрили хранилище
) : ViewModel() {
    var user by mutableStateOf<AuthUser?>(null)
        private set

    suspend fun signIn(login: String, password: String): AuthResult =
        recordSession(repository.signIn(login, password))

    suspend fun register(email: String, password: String, nickname: String): AuthResult =
        recordSession(repository.register(email, password, nickname))

    fun signOut() {
        user = null
        tokenManager.clearTokens() // <-- Очищаем токены при выходе
    }

    private fun recordSession(result: AuthResult): AuthResult {
        if (result is AuthResult.Success) {
            user = result.user
            // Имитируем ответ от бэкенда: сохраняем тестовый JWT
            tokenManager.saveTokens(
                accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test_access_token",
                refreshToken = "test_refresh_token"
            )
        }
        return result
    }
}