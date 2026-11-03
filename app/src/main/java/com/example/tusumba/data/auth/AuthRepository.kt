package com.example.tusumba.data.auth

data class AuthUser(val name: String, val email: String)

sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data object InvalidCredentials : AuthResult
    data object EmailAlreadyRegistered : AuthResult
}

interface AuthRepository {
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun register(name: String, email: String, password: String): AuthResult
}
