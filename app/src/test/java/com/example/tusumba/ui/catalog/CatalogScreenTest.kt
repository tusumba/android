package com.example.tusumba.ui.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.tusumba.data.catalog.CatalogEvent
import com.example.tusumba.data.catalog.CatalogFilters
import com.example.tusumba.data.catalog.EventStatus
import com.example.tusumba.data.catalog.Money
import com.example.tusumba.ui.screens.CatalogScreenContent
import java.time.LocalDate
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
class CatalogScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun cardContainsRequiredFieldsAndOpensItsEvent() {
        var openedId: String? = null
        val event =
            CatalogEvent(
                id = "test-event",
                title = "Научный лекторий",
                date = LocalDate.of(2026, 11, 26),
                placeAddress = "Библиотека",
                isFree = true,
                price = null,
                status = EventStatus.CONFIRMED,
            )
        show(
            CatalogUiState(content = CatalogContent.Events(listOf(event))),
            onEvent = { openedId = it },
        )

        compose
            .onNodeWithTag("catalog_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("Научный лекторий"))
        compose.onNodeWithText("Научный лекторий").assertIsDisplayed()
        compose.onNodeWithText("ПОДТВЕРЖДЕНО").assertIsDisplayed()
        compose.onNodeWithText("26 ноября").assertIsDisplayed()
        compose.onNodeWithText("Библиотека").assertIsDisplayed()
        compose.onNodeWithText("Бесплатно").assertIsDisplayed()
        compose
            .onNodeWithTag("catalog_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("Подробнее"))
        compose.onNodeWithText("Подробнее").performClick()
        assertEquals("test-event", openedId)
    }

    @Test
    fun paidCardShowsPriceInRublesAndStatus() {
        val event =
            CatalogEvent(
                id = "paid-event",
                title = "Мастерская керамики",
                date = LocalDate.of(2026, 11, 22),
                placeAddress = "Арт-пространство",
                isFree = false,
                price = Money(90000),
                status = EventStatus.NOT_CONFIRMED,
            )
        show(CatalogUiState(content = CatalogContent.Events(listOf(event))))

        compose
            .onNodeWithTag("catalog_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("900 ₽"))
        compose.onNodeWithText("900 ₽").assertIsDisplayed()
        compose.onNodeWithText("НЕ ПОДТВЕРЖДЕНО").assertIsDisplayed()
    }

    @Test
    fun isFreeChipSendsTheContractFilter() {
        var isFree: Boolean? = null
        show(CatalogUiState(CatalogFilters(), CatalogContent.Empty), onIsFree = { isFree = it })

        compose.onNodeWithText("Платные").performClick()
        assertEquals(false, isFree)
        compose.onNodeWithText("Бесплатные").performClick()
        assertEquals(true, isFree)
    }

    @Test
    fun emptyStateResetsFiltersAndSearchSendsInput() {
        var query = ""
        var reset = false
        show(
            CatalogUiState(CatalogFilters(), CatalogContent.Empty),
            onQuery = { query = it },
            onReset = { reset = true },
        )

        compose.onNodeWithTag("catalog_search").performTextInput("парк")
        assertEquals("парк", query)
        compose
            .onNodeWithTag("catalog_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("Сбросить фильтры"))
        compose.onNodeWithText("Мероприятия не найдены").assertIsDisplayed()
        compose.onNodeWithText("Сбросить фильтры").performClick()
        assertEquals(true, reset)
    }

    @Test
    fun errorOffersRetryAndUnavailableRoutesAreDisabled() {
        var retried = false
        show(CatalogUiState(content = CatalogContent.Error), onRetry = { retried = true })

        compose.onNodeWithText("Не удалось загрузить данные").assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        assertEquals(true, retried)
        compose.onNodeWithText("Секции").assertIsNotEnabled()
        compose.onNodeWithText("Друзья").assertIsNotEnabled()
    }

    @Test
    fun loadingAndProfileCallbackAreAvailableWithoutRepository() {
        var profileOpened = false
        show(CatalogUiState(), onProfile = { profileOpened = true })

        compose.onNodeWithTag("catalog_loading").assertIsDisplayed()
        compose.onNodeWithText("Профиль").performClick()
        assertEquals(true, profileOpened)
    }

    private fun show(
        state: CatalogUiState,
        onQuery: (String) -> Unit = {},
        onIsFree: (Boolean?) -> Unit = {},
        onReset: () -> Unit = {},
        onRetry: () -> Unit = {},
        onEvent: (String) -> Unit = {},
        onProfile: () -> Unit = {},
    ) {
        compose.setContent {
            CatalogScreenContent(state, onQuery, onIsFree, onReset, onRetry, onEvent, onProfile)
        }
    }
}
