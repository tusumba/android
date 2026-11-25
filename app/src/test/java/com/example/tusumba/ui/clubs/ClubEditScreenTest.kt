package com.example.tusumba.ui.clubs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.tusumba.data.events.model.ClubDetail
import java.util.UUID
import org.junit.Assert.assertEquals
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
class ClubEditScreenTest {
    @get:Rule val compose = createComposeRule()
    private val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val club = ClubDetail(id, "Шахматы", "Играем вместе", id, 1, true, schedule = "Пятница")

    @Test
    fun formEditsScheduleAndCancelDoesNotInvokeSave() {
        val state = mutableStateOf(ClubEditContent.Editing(club))
        var cancelled = false
        var saves = 0
        compose.setContent {
            ClubEditScreenContent(state.value, { cancelled = true }, { saves++ },
                { transform -> state.value = state.value.copy(draft = transform(state.value.draft)) }, {}, {}, {}, {})
        }
        compose.onNodeWithTag("club_edit_form").performScrollToNode(hasText("Расписание встреч"))
        compose.onNodeWithTag("club_edit_schedule").performTextReplacement("Суббота, 12:00")
        assertEquals("Суббота, 12:00", state.value.draft.schedule)
        compose.onNodeWithText("Отмена").performClick()
        assertTrue(cancelled)
        assertEquals(0, saves)
    }

    @Test
    fun deletedPhotoUsesStandardCoverBeforeSaving() {
        show(ClubEditContent.Editing(club.copy(photoUrl = "/old-photo"), photo = ClubPhotoChange.Delete))
        compose.onNodeWithTag("club_default_cover").assertIsDisplayed()
        compose.onNodeWithText("Удалить фото").assertDoesNotExist()
    }

    @Test
    fun fileErrorUsesExactRequirementText() {
        show(ClubEditContent.Editing(club, error = ClubEditError.FileInvalid))
        compose.onNodeWithTag("club_edit_form").performScrollToNode(hasText("Файл не соответствует требованиям."))
        compose.onNodeWithText("Файл не соответствует требованиям.").assertIsDisplayed()
    }

    @Test
    fun foreignClubFormHasNoSaveOrPhotoActions() {
        show(ClubEditContent.Forbidden)
        compose.onNodeWithText("Редактировать страницу может только администратор этой секции.").assertIsDisplayed()
        compose.onNodeWithTag("club_edit_save").assertDoesNotExist()
        compose.onNodeWithText("Выбрать фото").assertDoesNotExist()
    }

    @Test
    fun savingDisablesCancelAndRepeatedSave() {
        show(ClubEditContent.Editing(club, draft = ClubEditDraft.from(club).copy(schedule = "Новое"), saving = true))
        compose.onNodeWithTag("club_edit_save").assertIsNotEnabled()
        compose.onNodeWithText("Отмена").assertIsNotEnabled()
    }

    @Test
    fun publicClubPageHidesEditingActionForNonAdmin() {
        compose.setContent {
            ClubDetailScreenContent(ClubDetailContent.Loaded(club.copy(isAdmin = false), ClubEventsContent.Empty),
                {}, {}, {}, {}, {}, onEditClick = {})
        }
        compose.onNodeWithTag("club_edit").assertDoesNotExist()
    }

    private fun show(state: ClubEditContent) {
        compose.setContent { ClubEditScreenContent(state, {}, {}, {}, {}, {}, {}, {}) }
    }
}
