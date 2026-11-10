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
    private val tokenManager: TokenManager
) : ViewModel() {
    var user by mutableStateOf<AuthUser?>(null)
        private set

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
        user = null
        tokenManager.clearTokens()
    }
}