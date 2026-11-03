package com.example.tusumba.ui.auth

internal enum class AuthField {
    Name,
    Email,
    Password,
    PasswordConfirmation
}

internal enum class AuthFieldError {
    Required,
    InvalidEmail,
    PasswordTooShort,
    PasswordMismatch
}

private const val MinimumPasswordLength = 8
private const val MaximumEmailLength = 254
private const val MaximumLocalPartLength = 64

private val emailPattern = Regex(
    """[A-Za-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*@""" +
        """(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+""" +
        """[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"""
)

internal fun validateSignIn(email: String, password: String): Map<AuthField, AuthFieldError> =
    buildMap {
        emailError(email)?.let { put(AuthField.Email, it) }
        if (password.isBlank()) put(AuthField.Password, AuthFieldError.Required)
    }

internal fun validateRegistration(
    name: String,
    email: String,
    password: String,
    passwordConfirmation: String
): Map<AuthField, AuthFieldError> = buildMap {
    if (name.isBlank()) put(AuthField.Name, AuthFieldError.Required)
    emailError(email)?.let { put(AuthField.Email, it) }

    when {
        password.isBlank() -> put(AuthField.Password, AuthFieldError.Required)
        password.codePointCount(0, password.length) < MinimumPasswordLength ->
            put(AuthField.Password, AuthFieldError.PasswordTooShort)
    }

    when {
        passwordConfirmation.isBlank() -> put(AuthField.PasswordConfirmation, AuthFieldError.Required)
        passwordConfirmation != password -> put(AuthField.PasswordConfirmation, AuthFieldError.PasswordMismatch)
    }
}

private fun emailError(value: String): AuthFieldError? {
    val email = value.trim()
    return when {
        email.isEmpty() -> AuthFieldError.Required
        email.length > MaximumEmailLength ||
            email.indexOf('@') > MaximumLocalPartLength ||
            !emailPattern.matches(email) -> AuthFieldError.InvalidEmail
        else -> null
    }
}
