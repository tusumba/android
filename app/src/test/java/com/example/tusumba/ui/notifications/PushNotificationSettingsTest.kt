package com.example.tusumba.ui.notifications

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.tusumba.ui.theme.TusumbaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru-rRU")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PushNotificationSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun refusedPermissionShowsRequirementMessagesAndSettingsAction() {
        var openedSettings = false
        val state = PushPermissionState(configured = true, supported = true, requested = true)
        compose.setContent {
            TusumbaTheme { PushNotificationSettings(PushNotificationActions(state, { openedSettings = true }, {})) }
        }
        compose.onNodeWithText("Уведомления отключены.").assertIsDisplayed()
        compose.onNodeWithText("Разрешите уведомления на устройстве.").assertIsDisplayed()
        compose.onNodeWithText("Открыть настройки").performClick()
        assertTrue(openedSettings)
    }

    @Test
    fun failedRegistrationOffersRetryWithRequirementErrorText() {
        var retried = false
        val state = PushPermissionState(configured = true, supported = true, enabled = true, synchronizationFailed = true)
        compose.setContent {
            TusumbaTheme { PushNotificationSettings(PushNotificationActions(state, {}, { retried = true })) }
        }
        compose.onNodeWithText("Не удалось обновить настройки уведомлений. Попробуйте позже.").assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        assertTrue(retried)
    }
}
