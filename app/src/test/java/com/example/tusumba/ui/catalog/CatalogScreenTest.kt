package com.example.tusumba.ui.catalog

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.tusumba.data.catalog.CatalogEvent
import com.example.tusumba.data.catalog.CatalogFilters
import com.example.tusumba.data.catalog.EventStatus
import com.example.tusumba.data.catalog.Money
import com.example.tusumba.ui.screens.CatalogScreenContent
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatalogScreenTest {
    @get:Rule val compose = createComposeRule()
    private val event =
        CatalogEvent(
            "test-event",
            "Научный лекторий",
            LocalDate.of(2026, 11, 26),
            "Библиотека",
            false,
            Money(90000),
            EventStatus.CONFIRMED,
        )

    @Test
    fun cardShowsRequiredFieldsServerTotalAndOpensItsId() {
        var opened: String? = null
        show(
            CatalogUiState(content = CatalogContent.Events(listOf(event), total = 41)),
            onEvent = { opened = it },
        )
        compose.onNodeWithText("Найдено: 41").assertIsDisplayed()
        scrollToText("Подробнее")
        compose.onNodeWithText("Научный лекторий").assertIsDisplayed()
        compose.onNodeWithText("26 ноября").assertIsDisplayed()
        compose.onNodeWithText("Библиотека").assertIsDisplayed()
        compose.onNodeWithText("900 ₽").assertIsDisplayed()
        compose.onNodeWithText("Подробнее").performClick()
        assertEquals("test-event", opened)
    }

    @Test
    fun typePlaceAndMultipleStatusesAreCombined() {
        var selected = CatalogFilters()
        show(CatalogUiState(content = CatalogContent.Empty), onFilters = { selected = it })
        compose.onNodeWithText("Платные").performClick()
        compose.onNodeWithTag("catalog_more_filters").performClick()
        compose.onNodeWithTag("catalog_place").performScrollTo().performTextInput("НГУ")
        compose.onNodeWithTag("catalog_status_NOT_CONFIRMED").performScrollTo().performClick()
        compose.onNodeWithTag("catalog_status_CONFIRMED").performScrollTo().performClick()
        assertEquals(false, selected.isFree)
        assertEquals("НГУ", selected.place)
        assertEquals(setOf(EventStatus.CONFIRMED, EventStatus.NOT_CONFIRMED), selected.statuses)
        compose.onNodeWithTag("catalog_status_NOT_CONFIRMED").assertIsSelected()
        compose.onNodeWithTag("catalog_status_CONFIRMED").assertIsSelected()
    }

    @Test
    fun datePickerConfirmsAndClearsOnlyTheSelectedDate() {
        val from = LocalDate.of(2026, 11, 20)
        val to = LocalDate.of(2026, 11, 30)
        var selected = CatalogFilters(dateFrom = from, dateTo = to, isFree = true)
        show(CatalogUiState(selected, CatalogContent.Empty), onFilters = { selected = it })
        compose.onNodeWithTag("catalog_date_from").performScrollTo().performClick()
        compose.onNodeWithText("Выбрать").performClick()
        assertEquals(from, selected.dateFrom)
        compose.onNodeWithTag("catalog_date_from").performClick()
        compose.onNodeWithText("Очистить дату").performClick()
        assertNull(selected.dateFrom)
        assertEquals(to, selected.dateTo)
        assertEquals(true, selected.isFree)
        compose.onNodeWithTag("catalog_date_from").assertTextContains("Сегодня")
    }

    @Test
    fun searchAndEmptyFilterResetAreAvailable() {
        var filters = CatalogFilters()
        var reset = false
        show(
            CatalogUiState(content = CatalogContent.Empty),
            onFilters = { filters = it },
            onReset = { reset = true },
        )
        compose.onNodeWithTag("catalog_search").performTextInput("парк")
        assertEquals("парк", filters.query)
        scrollToText("Сбросить фильтры")
        compose.onNodeWithText("По выбранным фильтрам ничего не найдено.").assertIsDisplayed()
        compose.onNodeWithText("Сбросить фильтры").performClick()
        assertTrue(reset)
    }

    @Test
    fun catalogErrorOffersRetry() {
        var retried = false
        show(CatalogUiState(content = CatalogContent.Error), onRetry = { retried = true })
        scrollToText("Повторить")
        compose
            .onNodeWithText("Не удалось загрузить каталог мероприятий. Попробуйте позже.")
            .assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        assertTrue(retried)
    }

    @Test
    fun emptyCatalogUsesTheRequiredMessage() {
        show(CatalogUiState(content = CatalogContent.Empty))
        scrollToText("мероприятий пока нет.")
        compose.onNodeWithText("мероприятий пока нет.").assertIsDisplayed()
    }

    @Test
    fun loadingKeepsSearchEditableAndProfileAvailable() {
        var filters = CatalogFilters()
        var opened = false
        show(CatalogUiState(), onFilters = { filters = it }, onProfile = { opened = true })
        compose.onNodeWithTag("catalog_search").assertIsDisplayed().performTextInput("Лекторий")
        assertEquals("Лекторий", filters.query)
        compose.onNodeWithTag("catalog_search").assertTextContains("Лекторий")
        compose.onNodeWithText("Профиль").performClick()
        compose.onNodeWithText("Друзья").assertIsNotEnabled()
        assertTrue(opened)
    }

    @Test
    fun nextPageErrorKeepsCardsAndRetriesWithoutResettingFilters() {
        var more = false
        val content =
            CatalogContent.Events(listOf(event), total = 41, hasMore = true, loadMoreError = true)
        show(CatalogUiState(content = content), onMore = { more = true })
        scrollToText("Повторить")
        compose.onNodeWithText("Не удалось загрузить следующую страницу.").assertIsDisplayed()
        compose.onNodeWithTag("catalog_load_more").performClick()
        assertTrue(more)
    }

    @Test
    fun loadingNextPageDisablesDuplicateRequests() {
        val content =
            CatalogContent.Events(listOf(event), total = 41, hasMore = true, isLoadingMore = true)
        show(CatalogUiState(content = content))
        compose.onNodeWithTag("catalog_list").performScrollToNode(hasTestTag("catalog_load_more"))
        compose.onNodeWithTag("catalog_load_more").assertIsNotEnabled()
    }

    @Test
    fun filterRequestFailureShowsTheRequiredMessageAndOffersRetry() {
        var retried = false
        show(
            CatalogUiState(CatalogFilters(isFree = false), CatalogContent.Error),
            onRetry = { retried = true },
        )
        scrollToText("Повторить")
        compose
            .onNodeWithText("Не удалось применить фильтры. Попробуйте позже.")
            .assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        assertTrue(retried)
    }

    private fun scrollToText(text: String) {
        compose.onNodeWithTag("catalog_list").performScrollToNode(hasText(text))
    }

    private fun show(
        state: CatalogUiState,
        onFilters: (CatalogFilters) -> Unit = {},
        onReset: () -> Unit = {},
        onRetry: () -> Unit = {},
        onMore: () -> Unit = {},
        onEvent: (String) -> Unit = {},
        onProfile: () -> Unit = {},
    ) {
        compose.setContent {
            var current by remember { mutableStateOf(state) }
            CatalogScreenContent(
                state = current,
                onFiltersChange = {
                    current = current.copy(filters = it)
                    onFilters(it)
                },
                onResetFilters = {
                    current = current.copy(filters = CatalogFilters())
                    onReset()
                },
                onRetry = onRetry,
                onLoadMore = onMore,
                onEventClick = onEvent,
                onProfileClick = onProfile,
            )
        }
    }
}
