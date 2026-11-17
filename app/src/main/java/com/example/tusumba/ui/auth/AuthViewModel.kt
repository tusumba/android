package com.example.tusumba.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tusumba.data.auth.AuthRepository
import com.example.tusumba.data.auth.AuthResult
import com.example.tusumba.data.auth.AuthUser
import com.example.tusumba.data.auth.SessionLogout
import com.example.tusumba.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val tokenManager: TokenManager,
    private val sessionLogout: SessionLogout,
) : ViewModel() {
    var user by mutableStateOf<AuthUser?>(null)
        private set
    var isSigningOut by mutableStateOf(false)
        private set

    private val logoutEvents = Channel<Unit>(Channel.BUFFERED)
    val signedOut = logoutEvents.receiveAsFlow()

    init {
        user = tokenManager.getUser()
    }

    suspend fun signIn(login: String, password: String): AuthResult {
        val result = repository.signIn(login, password)

        if (result is AuthResult.Success) {
            user = result.user
            tokenManager.saveUser(result.user)
        }

        return result
    }

    suspend fun register(email: String, password: String, nickname: String): AuthResult {
        val result = repository.register(email, password, nickname)

        if (result is AuthResult.Success) {
            user = result.user
            tokenManager.saveUser(result.user)
        }

        return result
    }

    fun signOut() {
        if (isSigningOut) return
        isSigningOut = true
        viewModelScope.launch {
            try {
                sessionLogout()
            } finally {
                user = null
                isSigningOut = false
            }
            logoutEvents.trySend(Unit)
        }
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
