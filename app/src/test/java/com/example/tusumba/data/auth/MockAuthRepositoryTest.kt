package com.example.tusumba.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MockAuthRepositoryTest {
    private val repository = MockAuthRepository()

    @Test
    fun demoAccountCanSignInWithItsPassword() = runBlocking {
        assertEquals(
            AuthResult.Success(AuthUser("Демо", "demo@tusumba.example")),
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
        val registered = AuthResult.Success(AuthUser("Анна", "anna@tusumba.example"))
        assertEquals(registered, repository.register(" Анна ", "Anna@tusumba.example", "Password1!"))
        assertEquals(
            AuthResult.EmailAlreadyRegistered,
            repository.register("Борис", "  anna@TUSUMBA.EXAMPLE ", "Password2!")
        )
        assertEquals(registered, repository.signIn("anna@tusumba.example", "Password1!"))
        assertEquals(
            AuthResult.InvalidCredentials,
            repository.signIn("anna@tusumba.example", "Password2!")
        )
    }
}
