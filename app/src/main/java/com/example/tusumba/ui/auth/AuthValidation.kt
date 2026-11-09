package com.example.tusumba.ui.auth

import com.example.tusumba.data.auth.normalizeEmail

internal enum class AuthField {
    Login,
    Email,
    Nickname,
    Password
}

internal enum class AuthFieldError {
    Required,
    InvalidEmail,
    InvalidNickname,
    InvalidPassword
}

private val nicknamePattern = Regex("^[A-Za-z0-9_.-]{3,32}$")
private const val MinimumPasswordLength = 8
private const val MaximumPasswordLength = 128

/** Вход по LoginRequest: логин (почта или ник) и пароль, оба непустые. */
internal fun validateSignInForm(login: String, password: String): Map<AuthField, AuthFieldError> =
    buildMap {
        if (login.isBlank()) put(AuthField.Login, AuthFieldError.Required)
        if (password.isBlank()) put(AuthField.Password, AuthFieldError.Required)
    }

/** Регистрация по RegisterRequest: email, ник 3-32 символа [A-Za-z0-9_.-], пароль 8-128 символов. */
internal fun validateRegistrationForm(
    email: String,
    nickname: String,
    password: String
): Map<AuthField, AuthFieldError> = buildMap {
    when {
        email.isBlank() -> put(AuthField.Email, AuthFieldError.Required)
        normalizeEmail(email) == null -> put(AuthField.Email, AuthFieldError.InvalidEmail)
    }
    when {
        nickname.isBlank() -> put(AuthField.Nickname, AuthFieldError.Required)
        !nicknamePattern.matches(nickname.trim()) ->
            put(AuthField.Nickname, AuthFieldError.InvalidNickname)
    }
    when {
        password.isBlank() -> put(AuthField.Password, AuthFieldError.Required)
        password.length !in MinimumPasswordLength..MaximumPasswordLength ->
            put(AuthField.Password, AuthFieldError.InvalidPassword)
    }
}
