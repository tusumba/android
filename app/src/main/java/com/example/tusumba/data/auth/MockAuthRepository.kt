package com.example.tusumba.data.auth

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAuthRepository @Inject constructor() : AuthRepository {
    private class Account(val user: AuthUser, val password: String)

    // Demo accounts live only for the lifetime of the app process.
    private val accounts = mutableListOf(
        Account(
            AuthUser(
                id = "5b0d3a4e-2c1f-4e8a-9d7b-000000000001",
                email = "demo@tusumba.example",
                nickname = "demo"
            ),
            password = "Tusumba123!"
        )
    )

    override suspend fun signIn(login: String, password: String): AuthResult {
        val value = login.trim()
        return synchronized(accounts) {
            // Как в Identity: значение с @ ищется по email, иначе по нику, без учёта регистра.
            val account = accounts.firstOrNull {
                if ('@' in value) it.user.email.equals(value, ignoreCase = true)
                else it.user.nickname.equals(value, ignoreCase = true)
            }
            if (account == null || account.password != password) {
                AuthResult.InvalidCredentials
            } else {
                AuthResult.Success(account.user)
            }
        }
    }

    override suspend fun register(email: String, password: String, nickname: String): AuthResult {
        val normalizedEmail = normalizeEmail(email) ?: return AuthResult.RegistrationUnavailable
        val normalizedNickname = nickname.trim()
        // A reserved address makes the temporary failure state reproducible in the demo.
        if (normalizedEmail == "unavailable@tusumba.example") {
            return AuthResult.RegistrationUnavailable
        }
        return synchronized(accounts) {
            val taken = accounts.any {
                it.user.email.equals(normalizedEmail, ignoreCase = true) ||
                    it.user.nickname.equals(normalizedNickname, ignoreCase = true)
            }
            if (taken) {
                AuthResult.UserAlreadyExists
            } else {
                val user = AuthUser(
                    id = UUID.randomUUID().toString(),
                    email = normalizedEmail,
                    nickname = normalizedNickname
                )
                accounts += Account(user, password)
                AuthResult.Success(user)
            }
        }
    }
}
