package com.example.tusumba.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun signInRequiresEmailAndPassword() {
        assertEquals(
            mapOf(AuthField.Email to AuthFieldError.Required, AuthField.Password to AuthFieldError.Required),
            validateSignIn(" \t", " \t")
        )
    }

    @Test
    fun signInAcceptsTrimmedEmailAndAnyNonblankPassword() {
        assertTrue(validateSignIn("  user.name+test@sub.example.com  ", "x").isEmpty())
    }

    @Test
    fun signInRejectsMalformedEmail() {
        val invalidAddresses = listOf(
            "user.example.com",
            "user@localhost",
            "user@@example.com",
            ".user@example.com",
            "user..name@example.com",
            "user name@example.com",
            "user@-example.com",
            "user@example..com",
            "${"x".repeat(65)}@example.com"
        )

        invalidAddresses.forEach { address ->
            assertEquals(address, mapOf(AuthField.Email to AuthFieldError.InvalidEmail), validateSignIn(address, "password"))
        }
    }

    @Test
    fun registrationReportsEveryBlankField() {
        assertEquals(
            AuthField.entries.associateWith { AuthFieldError.Required },
            validateRegistration(" ", " ", "        ", "        ")
        )
    }

    @Test
    fun registrationRequiresEightPasswordCharactersAndAnExactConfirmation() {
        assertEquals(
            mapOf(
                AuthField.Password to AuthFieldError.PasswordTooShort,
                AuthField.PasswordConfirmation to AuthFieldError.PasswordMismatch
            ),
            validateRegistration("Имя", "user@example.com", "1234567", "1234567 ")
        )
        assertEquals(
            mapOf(AuthField.Password to AuthFieldError.PasswordTooShort),
            validateRegistration("Имя", "user@example.com", "🔑🔑🔑🔑", "🔑🔑🔑🔑")
        )
        assertTrue(validateRegistration("Имя", "user@example.com", " 123456 ", " 123456 ").isEmpty())
    }
}
