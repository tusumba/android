package com.example.tusumba.ui.auth

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.tusumba.data.auth.AuthResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuthScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun registrationSendsEmailNicknameAndPassword() {
        var sent: AuthForm? = null
        show(isRegistration = true) {
            sent = it
            AuthResult.UserAlreadyExists
        }

        compose.onNodeWithContentDescription("Email").performTextInput(" anna@example.com ")
        compose.onNodeWithContentDescription("Ник").performTextInput("anna_k")
        compose.onNodeWithContentDescription("Пароль").performTextInput("Password1")
        compose.onNodeWithText("Зарегистрироваться").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(AuthForm("anna@example.com", "Password1", "anna_k"), sent)
        compose.onNodeWithText("Пользователь с такими данными уже зарегистрирован.").assertIsDisplayed()
    }

    @Test
    fun signInHasNoNicknameFieldAndUsesLoginErrors() {
        var sent: AuthForm? = null
        show(isRegistration = false) {
            sent = it
            AuthResult.InvalidCredentials
        }

        compose.onAllNodesWithContentDescription("Ник").assertCountEquals(0)
        compose.onNodeWithText("Войти").performScrollTo().performClick()
        compose.onNodeWithText("Заполните оба поля.").assertIsDisplayed()

        compose.onNodeWithContentDescription("Email или ник").performTextInput("anna_k")
        compose.onNodeWithContentDescription("Пароль").performTextInput("wrong")
        compose.onNodeWithText("Войти").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(AuthForm("anna_k", "wrong", ""), sent)
        compose.onNodeWithText("Неверный логин или пароль.").assertIsDisplayed()
    }

    private fun show(isRegistration: Boolean, onAuthenticate: suspend (AuthForm) -> AuthResult) {
        compose.setContent {
            AuthScreen(
                isRegistration = isRegistration,
                onBackClick = {},
                onSwitchMode = {},
                onAuthenticate = onAuthenticate,
                onAuthenticated = {}
            )
        }
    }
}
