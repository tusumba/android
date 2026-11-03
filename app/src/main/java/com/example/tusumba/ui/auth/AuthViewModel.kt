package com.example.tusumba.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.tusumba.data.auth.AuthRepository
import com.example.tusumba.data.auth.AuthResult
import com.example.tusumba.data.auth.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    var user by mutableStateOf<AuthUser?>(null)
        private set

    suspend fun authenticate(name: String?, email: String, password: String): AuthResult {
        val result = if (name == null) {
            repository.signIn(email, password)
        } else {
            repository.register(name, email, password)
        }
        if (result is AuthResult.Success) user = result.user
        return result
    }

    fun signOut() {
        user = null
    }
}
