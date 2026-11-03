package com.example.tusumba.ui.auth

import com.example.tusumba.data.auth.normalizeAuthContact

internal enum class AuthField {
    Contact,
    Password
}

internal enum class AuthFieldError {
    Required,
    InvalidContact
}

internal fun validateAuthForm(contact: String, password: String): Map<AuthField, AuthFieldError> =
    buildMap {
        when {
            contact.isBlank() -> put(AuthField.Contact, AuthFieldError.Required)
            normalizeAuthContact(contact) == null -> put(AuthField.Contact, AuthFieldError.InvalidContact)
        }
        if (password.isBlank()) put(AuthField.Password, AuthFieldError.Required)
    }
