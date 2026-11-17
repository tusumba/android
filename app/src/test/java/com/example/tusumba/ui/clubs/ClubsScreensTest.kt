package com.example.tusumba.ui.clubs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.tusumba.data.events.model.ClubCard
import com.example.tusumba.data.events.model.ClubDetail
import java.util.UUID
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
class ClubsScreensTest {
    @get:Rule val compose = createComposeRule()

    private val clubId = UUID.fromString("e7108803-5f73-4fd6-9262-a2dbb7ce3223")

    @Test
    fun searchSendsInputAndClubCardOpensItsId() {
        var query = ""
        var openedId: String? = null
        val club = ClubCard(clubId, "Настольные игры", "Играем по пятницам")
        showCatalog(
            state = ClubsUiState(content = ClubsContent.Data(listOf(club), hasMore = false)),
            onQuery = { query = it },
            onClub = { openedId = it },
        )

        compose.onNodeWithTag("clubs_search").performTextInput("игры")
        assertEquals("игры", query)
        compose.onNodeWithTag("clubs_list").performScrollToNode(hasText(club.name))
        compose.onNodeWithText(club.description).assertIsDisplayed()
        compose.onNodeWithTag("club_$clubId").performClick()
        assertEquals(clubId.toString(), openedId)
    }

    @Test
    fun catalogFailureUsesCatalogMessageAndRetries() {
        var retries = 0
        showCatalog(ClubsUiState(content = ClubsContent.Error), onRetry = { retries++ })

        compose.onNodeWithTag("clubs_list")
            .performScrollToNode(hasText("Не удалось загрузить каталог секций. Попробуйте позже."))
        compose.onNodeWithText("Не удалось загрузить каталог секций. Попробуйте позже.").assertIsDisplayed()
        compose.onNodeWithText("Не удалось выполнить поиск. Попробуйте позже.").assertDoesNotExist()
        compose.onNodeWithTag("clubs_list").performScrollToNode(hasText("Повторить"))
        compose.onNodeWithText("Повторить").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun searchFailureUsesSearchMessageAndRetries() {
        var retries = 0
        showCatalog(ClubsUiState(query = "игры", content = ClubsContent.Error), onRetry = { retries++ })

        compose.onNodeWithTag("clubs_list")
            .performScrollToNode(hasText("Не удалось выполнить поиск. Попробуйте позже."))
        compose.onNodeWithText("Не удалось выполнить поиск. Попробуйте позже.").assertIsDisplayed()
        compose.onNodeWithText("Не удалось загрузить каталог секций. Попробуйте позже.").assertDoesNotExist()
        compose.onNodeWithTag("clubs_list").performScrollToNode(hasText("Повторить"))
        compose.onNodeWithText("Повторить").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun emptySearchShowsRequirementMessageAndClearsTheQuery() {
        var query = "игры"
        showCatalog(ClubsUiState(query = query, content = ClubsContent.Empty), onQuery = { query = it })

        compose.onNodeWithTag("clubs_list").performScrollToNode(hasText("Ничего не найдено."))
        compose.onNodeWithText("Ничего не найдено.").assertIsDisplayed()
        compose.onNodeWithTag("clubs_list").performScrollToNode(hasText("Очистить поиск"))
        compose.onNodeWithText("Очистить поиск").performClick()
        assertEquals("", query)
    }

    @Test
    fun unavailableClubShowsRequirementMessageAndReturnsToCatalog() {
        var returnedToCatalog = false
        compose.setContent {
            ClubDetailScreenContent(
                state = ClubDetailContent.Unavailable,
                onBackClick = { returnedToCatalog = true },
                onEventClick = {},
                onRetry = {},
                onRetryEvents = {},
                onLoadMoreEvents = {},
            )
        }

        compose.onNodeWithText("Секция недоступна.").assertIsDisplayed()
        compose.onNodeWithText("К каталогу секций").performClick()
        assertEquals(true, returnedToCatalog)
    }

    @Test
    fun eventFailureKeepsClubInformationVisibleAndRetriesOnlyEvents() {
        var pageRetries = 0
        var eventRetries = 0
        val club = ClubDetail(
            id = clubId,
            name = "Настольные игры",
            description = "Обсуждаем правила и играем вместе",
            currentVersionId = UUID.fromString("43c8b5c2-5daf-42d1-b576-7c2a6e18eedc"),
            versionNo = 1,
            isAdmin = false,
            meetingPlace = "НГУ, аудитория 101",
            schedule = "Пятница, 18:00",
            contacts = mapOf("telegram" to "@nsu_boardgames", "email" to "games@example.org"),
        )
        compose.setContent {
            ClubDetailScreenContent(
                state = ClubDetailContent.Loaded(club, ClubEventsContent.Error),
                onBackClick = {},
                onEventClick = {},
                onRetry = { pageRetries++ },
                onRetryEvents = { eventRetries++ },
                onLoadMoreEvents = {},
            )
        }

        val visibleInformation = listOf(
            club.name,
            club.description,
            "Пятница, 18:00",
            "НГУ, аудитория 101",
            "telegram — @nsu_boardgames\nemail — games@example.org",
            "Не удалось загрузить мероприятия секции. Попробуйте позже.",
        )
        visibleInformation.forEach { text ->
            compose.onNodeWithTag("club_detail").performScrollToNode(hasText(text))
            compose.onNodeWithText(text).assertIsDisplayed()
        }
        compose.onNodeWithTag("club_detail").performScrollToNode(hasText("Повторить"))
        compose.onNodeWithText("Повторить").performClick()
        assertEquals(1, eventRetries)
        assertEquals(0, pageRetries)
    }

    private fun showCatalog(
        state: ClubsUiState,
        onQuery: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onClub: (String) -> Unit = {},
    ) {
        compose.setContent {
            ClubsScreenContent(
                state = state,
                onQueryChange = onQuery,
                onRetry = onRetry,
                onLoadMore = {},
                onClubClick = onClub,
                onEventsClick = {},
                onProfileClick = {},
            )
        }
    }
}
