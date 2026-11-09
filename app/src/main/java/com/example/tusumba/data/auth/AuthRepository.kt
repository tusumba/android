package com.example.tusumba.data.auth

/** Пользователь из ответа Identity (UserPrivate). */
data class AuthUser(
    val id: String,
    val email: String,
    val nickname: String,
    val globalRole: GlobalRole = GlobalRole.USER,
)

enum class GlobalRole {
    USER,
    MODERATOR,
}

/** Результаты POST /v1/auth/login и /v1/auth/register по OpenAPI Identity. */
sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult

    /** 401 invalid_credentials. */
    data object InvalidCredentials : AuthResult

    /** 409 user_already_exists: email или ник заняты. */
    data object UserAlreadyExists : AuthResult

    /** 5xx при входе. */
    data object SignInUnavailable : AuthResult

    /** 5xx при регистрации. */
    data object RegistrationUnavailable : AuthResult
}

interface AuthRepository {
    /** [login] - почта (с символом @) или ник. */
    suspend fun signIn(login: String, password: String): AuthResult

    suspend fun register(email: String, password: String, nickname: String): AuthResult
}
