package com.example.tusumba.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAuthRepositoryTest {
    private val repository = MockAuthRepository()

    @Test
    fun demoAccountSignsInByEmailOrNicknameIgnoringCase() = runBlocking {
        val byEmail = repository.signIn("  DEMO@tusumba.example ", "Tusumba123!")
        val byNickname = repository.signIn("Demo", "Tusumba123!")

        assertTrue(byEmail is AuthResult.Success)
        assertEquals(byEmail, byNickname)
        val user = (byEmail as AuthResult.Success).user
        assertEquals("demo@tusumba.example", user.email)
        assertEquals("demo", user.nickname)
        assertEquals(GlobalRole.USER, user.globalRole)
    }

    @Test
    fun wrongPasswordAndUnknownLoginGiveTheSameError() = runBlocking {
        assertEquals(AuthResult.InvalidCredentials, repository.signIn("demo", "WrongPassword"))
        assertEquals(AuthResult.InvalidCredentials, repository.signIn("unknown@tusumba.example", "Tusumba123!"))
        assertEquals(AuthResult.InvalidCredentials, repository.signIn("unknown", "Tusumba123!"))
    }

    @Test
    fun registrationSignsInAndEmailOrNicknameCannotBeReused() = runBlocking {
        val registered = repository.register("Anna@tusumba.example", "Password1!", "anna_k")
        assertTrue(registered is AuthResult.Success)
        val user = (registered as AuthResult.Success).user
        assertEquals("anna@tusumba.example", user.email)
        assertEquals("anna_k", user.nickname)

        assertEquals(
            AuthResult.UserAlreadyExists,
            repository.register("  anna@TUSUMBA.EXAMPLE ", "Password2!", "another")
        )
        assertEquals(
            AuthResult.UserAlreadyExists,
            repository.register("other@tusumba.example", "Password2!", "ANNA_K")
        )
        assertEquals(registered, repository.signIn("anna_k", "Password1!"))
        assertEquals(registered, repository.signIn("anna@tusumba.example", "Password1!"))
        assertEquals(AuthResult.InvalidCredentials, repository.signIn("anna_k", "Password2!"))
    }

    @Test
    fun temporaryFailureDoesNotCreateAccountOrBlockNextRegistration() = runBlocking {
        assertEquals(
            AuthResult.RegistrationUnavailable,
            repository.register(" UNAVAILABLE@tusumba.example ", "Password1!", "unavailable")
        )
        assertEquals(
            AuthResult.InvalidCredentials,
            repository.signIn("unavailable@tusumba.example", "Password1!")
        )
        assertTrue(repository.register("anna@tusumba.example", "Password1!", "anna") is AuthResult.Success)
    }
}
