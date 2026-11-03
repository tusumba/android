package com.example.tusumba.data.auth

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAuthRepository @Inject constructor() : AuthRepository {
    private class Account(val user: AuthUser, val password: String)

    // Demo accounts live only for the lifetime of the app process.
    private val accounts = mutableMapOf(
        "demo@tusumba.example" to Account(
            AuthUser(contact = "demo@tusumba.example"),
            password = "Tusumba123!"
        )
    )

    override suspend fun signIn(contact: String, password: String): AuthResult {
        val normalizedContact = normalizeAuthContact(contact) ?: return AuthResult.InvalidCredentials
        return synchronized(accounts) {
            val account = accounts[normalizedContact]
            if (account == null || account.password != password) {
                AuthResult.InvalidCredentials
            } else {
                AuthResult.Success(account.user)
            }
        }
    }

    override suspend fun register(contact: String, password: String): AuthResult {
        if (password.isBlank()) return AuthResult.RegistrationUnavailable
        val normalizedContact = normalizeAuthContact(contact) ?: return AuthResult.RegistrationUnavailable
        // A reserved address makes the temporary failure state reproducible in the demo.
        if (normalizedContact == "unavailable@tusumba.example") {
            return AuthResult.RegistrationUnavailable
        }
        return synchronized(accounts) {
            if (normalizedContact in accounts) {
                AuthResult.ContactAlreadyRegistered
            } else {
                val user = AuthUser(contact = normalizedContact)
                accounts[normalizedContact] = Account(user, password)
                AuthResult.Success(user)
            }
        }
    }
}
