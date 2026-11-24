package com.example.tusumba.ui.applications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.tusumba.data.moderation.model.*
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApplicationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun formSwitchesTypeAndShowsFieldError() {
        compose.setContent {
            var form by remember { mutableStateOf(ApplicationForm()) }
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ApplicationFormContent(form, mapOf("event.date" to "Дата в прошлом"), false, { form = it }, {})
                }
            }
        }
        compose.onNodeWithText("Дата в прошлом").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Страница секции").performScrollTo().performClick()
        compose.onNodeWithText("Расписание").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Дата * (ГГГГ-ММ-ДД)").assertDoesNotExist()
    }

    @Test fun rejectionDisplaysCommentAndCannotOpenObject() {
        val id = UUID.randomUUID()
        compose.setContent {
            MaterialTheme {
                ApplicationDetails(Application(id, ApplicationType.club_page, id, ApplicationStatus.rejected,
                    OffsetDateTime.parse("2026-11-25T00:00:00Z"), moderatorComment = "Добавьте контакты", resultObjectId = id), { _, _ -> fail() })
            }
        }
        compose.onNodeWithText("Отклонена").assertIsDisplayed()
        compose.onNodeWithText("Добавьте контакты").assertIsDisplayed()
        compose.onNodeWithText("Открыть секцию").assertDoesNotExist()
    }

    @Test fun submissionIsDisabledWhileSending() {
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ApplicationFormContent(ApplicationForm(), emptyMap(), true, {}, {})
            }
        } }
        compose.onNodeWithText("Отправка…").performScrollTo().assertIsNotEnabled()
    }
}
