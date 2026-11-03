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

    suspend fun signIn(contact: String, password: String): AuthResult =
        repository.signIn(contact, password).recordSession()

    suspend fun register(contact: String, password: String): AuthResult =
        repository.register(contact, password).recordSession()

    fun signOut() {
        user = null
    }

    private fun AuthResult.recordSession(): AuthResult {
        if (this is AuthResult.Success) user = this.user
        return this
    }
}
