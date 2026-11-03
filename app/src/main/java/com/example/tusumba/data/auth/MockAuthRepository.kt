package com.example.tusumba.data.auth

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAuthRepository @Inject constructor() : AuthRepository {
    private class Account(val user: AuthUser, val password: String)

    // Demo accounts live only for the lifetime of the app process.
    private val accounts = mutableMapOf(
        "demo@tusumba.example" to Account(
            AuthUser(name = "Демо", email = "demo@tusumba.example"),
            password = "Tusumba123!"
        )
    )

    override suspend fun signIn(email: String, password: String): AuthResult =
        synchronized(accounts) {
            val account = accounts[email.trim().lowercase()]
            if (account == null || account.password != password) {
                AuthResult.InvalidCredentials
            } else {
                AuthResult.Success(account.user)
            }
        }

    override suspend fun register(name: String, email: String, password: String): AuthResult =
        synchronized(accounts) {
            val normalizedEmail = email.trim().lowercase()
            if (normalizedEmail in accounts) {
                AuthResult.EmailAlreadyRegistered
            } else {
                val user = AuthUser(name = name.trim(), email = normalizedEmail)
                accounts[normalizedEmail] = Account(user, password)
                AuthResult.Success(user)
            }
        }
}
