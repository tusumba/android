package com.example.tusumba.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MockAuthRepositoryTest {
    private val repository = MockAuthRepository()

    @Test
    fun demoAccountCanSignInWithItsPassword() = runBlocking {
        assertEquals(
            AuthResult.Success(AuthUser("demo@tusumba.example")),
            repository.signIn("  DEMO@tusumba.example ", "Tusumba123!")
        )
        assertEquals(
            AuthResult.InvalidCredentials,
            repository.signIn("demo@tusumba.example", "WrongPassword")
        )
        assertEquals(
            AuthResult.InvalidCredentials,
            repository.signIn("unknown@tusumba.example", "Tusumba123!")
        )
    }

    @Test
    fun registeredAccountCanSignInAndCannotBeOverwritten() = runBlocking {
        val registered = AuthResult.Success(AuthUser("anna@tusumba.example"))
        assertEquals(registered, repository.register("Anna@tusumba.example", "Password1!"))
        assertEquals(
            AuthResult.ContactAlreadyRegistered,
            repository.register("  anna@TUSUMBA.EXAMPLE ", "Password2!")
        )
        assertEquals(registered, repository.signIn("anna@tusumba.example", "Password1!"))
        assertEquals(
            AuthResult.InvalidCredentials,
            repository.signIn("anna@tusumba.example", "Password2!")
        )
    }

    @Test
    fun phoneAliasesIdentifyTheSameAccount() = runBlocking {
        val registered = AuthResult.Success(AuthUser("+79991234567"))
        assertEquals(registered, repository.register("+7 (999) 123-45-67", " Password1! "))
        assertEquals(
            AuthResult.ContactAlreadyRegistered,
            repository.register("8 999 123 45 67", "Password2!")
        )
        assertEquals(registered, repository.signIn("79991234567", " Password1! "))
        assertEquals(AuthResult.InvalidCredentials, repository.signIn("79991234567", "Password1!"))
    }

    @Test
    fun temporaryFailureDoesNotCreateAccountOrBlockNextRegistration() = runBlocking {
        assertEquals(
            AuthResult.RegistrationUnavailable,
            repository.register(" UNAVAILABLE@tusumba.example ", "Password1!")
        )
        assertEquals(
            AuthResult.InvalidCredentials,
            repository.signIn("unavailable@tusumba.example", "Password1!")
        )
        assertEquals(
            AuthResult.Success(AuthUser("anna@tusumba.example")),
            repository.register("anna@tusumba.example", "Password1!")
        )
    }
}
