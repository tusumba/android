package com.example.tusumba.data.auth

data class AuthUser(val contact: String)

sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data object InvalidCredentials : AuthResult
    data object ContactAlreadyRegistered : AuthResult
    data object RegistrationUnavailable : AuthResult
}

interface AuthRepository {
    suspend fun signIn(contact: String, password: String): AuthResult
    suspend fun register(contact: String, password: String): AuthResult
}
