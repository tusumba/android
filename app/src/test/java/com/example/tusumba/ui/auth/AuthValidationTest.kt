package com.example.tusumba.ui.auth

import com.example.tusumba.data.auth.normalizeAuthContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun requiresContactAndPassword() {
        assertEquals(
            mapOf(AuthField.Contact to AuthFieldError.Required, AuthField.Password to AuthFieldError.Required),
            validateAuthForm(" \t", " \t")
        )
    }

    @Test
    fun acceptsEmailOrPhoneWithAnyNonblankPassword() {
        assertTrue(validateAuthForm("  User.Name+Test@Sub.Example.com  ", "x").isEmpty())
        assertTrue(validateAuthForm("+7 (999) 123-45-67", "x").isEmpty())
    }

    @Test
    fun normalizesEmailAndPhoneFormatting() {
        assertEquals(
            "user.name+test@sub.example.com",
            normalizeAuthContact("  User.Name+Test@Sub.Example.com  ")
        )
        assertEquals("+79991234567", normalizeAuthContact("+7 (999) 123-45-67"))
        assertEquals("+79991234567", normalizeAuthContact("8 999 123 45 67"))
        assertEquals("+79991234567", normalizeAuthContact("79991234567"))
        assertEquals("+442079460000", normalizeAuthContact("+44 (20) 7946-0000"))
        assertEquals("02079460000", normalizeAuthContact("020 7946 0000"))
    }

    @Test
    fun rejectsMalformedEmailAndPhone() {
        val longLocalPart = "x".repeat(65)
        val invalidContacts = listOf(
            "user.example.com",
            "user@localhost",
            "user@@example.com",
            ".user@example.com",
            "user..name@example.com",
            "user name@example.com",
            "user@-example.com",
            "user@example..com",
            "$longLocalPart@example.com",
            "1234567",
            "1234567890123456",
            "++79991234567",
            "+09991234567",
            "+7 (999 123-45-67",
            "+7 999 123--45-67",
            "+7 999 123-45-67-",
            "+7 999 123-45-6a"
        )

        invalidContacts.forEach { contact ->
            assertEquals(
                contact,
                mapOf(AuthField.Contact to AuthFieldError.InvalidContact),
                validateAuthForm(contact, "password")
            )
        }
    }
}
