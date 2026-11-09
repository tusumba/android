package com.example.tusumba.ui.auth

import com.example.tusumba.data.auth.normalizeEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun signInRequiresBothFieldsAndAcceptsEmailOrNickname() {
        assertEquals(
            mapOf(AuthField.Login to AuthFieldError.Required, AuthField.Password to AuthFieldError.Required),
            validateSignInForm(" \t", " \t")
        )
        assertTrue(validateSignInForm("user@example.com", "x").isEmpty())
        assertTrue(validateSignInForm("anna_k", "x").isEmpty())
    }

    @Test
    fun registrationRequiresEmailNicknameAndPassword() {
        assertEquals(
            mapOf(
                AuthField.Email to AuthFieldError.Required,
                AuthField.Nickname to AuthFieldError.Required,
                AuthField.Password to AuthFieldError.Required
            ),
            validateRegistrationForm(" ", "", " ")
        )
        assertTrue(validateRegistrationForm("  User.Name+Test@Sub.Example.com  ", "anna.k-2", "Password1").isEmpty())
    }

    @Test
    fun registrationFollowsRegisterRequestLimits() {
        assertEquals(
            mapOf(AuthField.Email to AuthFieldError.InvalidEmail),
            validateRegistrationForm("+7 999 123-45-67", "anna", "Password1")
        )
        listOf("an", "a".repeat(33), "anna k", "anna@k", "анна").forEach { nickname ->
            assertEquals(
                nickname,
                mapOf(AuthField.Nickname to AuthFieldError.InvalidNickname),
                validateRegistrationForm("anna@example.com", nickname, "Password1")
            )
        }
        assertTrue(validateRegistrationForm("anna@example.com", "a".repeat(32), "Password1").isEmpty())
        listOf("Short1!", "x".repeat(129)).forEach { password ->
            assertEquals(
                mapOf(AuthField.Password to AuthFieldError.InvalidPassword),
                validateRegistrationForm("anna@example.com", "anna", password)
            )
        }
    }

    @Test
    fun normalizesEmailAndRejectsMalformedOnes() {
        assertEquals("user.name+test@sub.example.com", normalizeEmail("  User.Name+Test@Sub.Example.com  "))
        val longLocalPart = "x".repeat(65)
        listOf(
            "user.example.com",
            "user@localhost",
            "user@@example.com",
            ".user@example.com",
            "user..name@example.com",
            "user name@example.com",
            "user@-example.com",
            "user@example..com",
            "$longLocalPart@example.com",
            "+79991234567"
        ).forEach { email -> assertNull(email, normalizeEmail(email)) }
    }
}
