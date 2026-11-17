package com.example.tusumba.ui.catalog

import com.example.tusumba.data.catalog.*
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    @get:Rule
    val mainDispatcherRule =
        object : TestWatcher() {
            override fun starting(description: Description) =
                Dispatchers.setMain(StandardTestDispatcher())

            override fun finished(description: Description) = Dispatchers.resetMain()
        }

    @Test
    fun firstPageKeepsLoadingUntilResponseAndUsesServerTotal() = runTest {
        val result = CompletableDeferred<CatalogPage>()
        val repo = FakeRepository { _, _ -> result.await() }
        val model = CatalogViewModel(repo)
        runCurrent()
        assertEquals(CatalogContent.Loading, model.state.value.content)
        result.complete(page("one", "two", total = 3))
        advanceUntilIdle()
        val content = content(model)
        assertEquals(listOf("one", "two"), content.events.map { it.id })
        assertEquals(3, content.total)
        assertTrue(content.hasMore)
        assertEquals(listOf(CatalogFilters() to 1), repo.requests)
    }

    @Test
    fun paginationAppendsOnceAndStopsOnLastPage() = runTest {
        val second = CompletableDeferred<CatalogPage>()
        val repo = FakeRepository { _, number ->
            if (number == 1) page("one", "two", total = 3) else second.await()
        }
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        model.loadMore()
        model.loadMore()
        runCurrent()
        assertTrue(content(model).isLoadingMore)
        assertEquals(listOf(1, 2), repo.requests.map { it.second })
        second.complete(page("two", "three", total = 3, number = 2))
        advanceUntilIdle()
        assertEquals(listOf("one", "two", "three"), content(model).events.map { it.id })
        assertFalse(content(model).hasMore)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(2, repo.requests.size)
    }

    @Test
    fun nextPageErrorKeepsCardsAndRetriesTheSamePage() = runTest {
        var failed = false
        val repo = FakeRepository { _, number ->
            if (number == 1) page("one", "two", total = 3)
            else if (!failed) {
                failed = true
                throw IOException("offline")
            } else page("three", total = 3, number = 2)
        }
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertTrue(content(model).loadMoreError)
        assertEquals(listOf("one", "two"), content(model).events.map { it.id })
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), repo.requests.map { it.second })
        assertEquals(listOf("one", "two", "three"), content(model).events.map { it.id })
    }

    @Test
    fun combinedFiltersStartAtPageOneAndReplacePreviousPages() = runTest {
        val filters =
            CatalogFilters(
                isFree = false,
                dateFrom = LocalDate.of(2026, 11, 12),
                dateTo = LocalDate.of(2026, 12, 1),
                place = "НГУ",
                statuses = setOf(EventStatus.CONFIRMED, EventStatus.NOT_CONFIRMED),
            )
        val repo = FakeRepository { current, number ->
            if (current.isActive) page("filtered", total = 1)
            else page("old" + number, total = 5, number = number)
        }
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        model.setFilters(filters)
        advanceUntilIdle()
        assertEquals(filters to 1, repo.requests.last())
        assertEquals(listOf("filtered"), content(model).events.map { it.id })
        model.resetFilters()
        advanceUntilIdle()
        assertEquals(CatalogFilters() to 1, repo.requests.last())
        assertEquals(CatalogFilters(), model.state.value.filters)
        assertEquals(listOf("old1"), content(model).events.map { it.id })
    }

    @Test
    fun textChangesDebounceToTheLastValue() = runTest {
        val repo = FakeRepository { _, _ -> page("one", total = 1) }
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        model.setFilters(CatalogFilters(place = "Н"))
        runCurrent()
        model.setFilters(CatalogFilters(place = "НГ"))
        runCurrent()
        model.setFilters(CatalogFilters(place = "НГУ"))
        runCurrent()
        advanceTimeBy(299)
        assertEquals(1, repo.requests.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repo.requests.size)
        assertEquals("НГУ", repo.requests.last().first.place)
    }

    @Test
    fun obsoleteNextPageCannotOverwriteAChangedFilter() = runTest {
        val late = CompletableDeferred<CatalogPage>()
        val repo = FakeRepository { filters, number ->
            when {
                number == 2 -> withContext(NonCancellable) { late.await() }
                filters.isFree == true -> page("new", total = 1)
                else -> page("old", total = 4)
            }
        }
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        model.loadMore()
        runCurrent()
        model.setFilters(CatalogFilters(isFree = true))
        runCurrent()
        assertEquals(listOf("new"), content(model).events.map { it.id })
        late.complete(page("obsolete", total = 4, number = 2))
        advanceUntilIdle()
        assertEquals(listOf("new"), content(model).events.map { it.id })
        assertFalse(content(model).loadMoreError)
    }

    @Test
    fun invalidRangesAndOverlongInputDoNotRequestTheServer() = runTest {
        val repo = FakeRepository { _, _ -> page("one", total = 1) }
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        val invalid =
            listOf(
                CatalogFilters(
                    dateFrom = LocalDate.of(2026, 12, 1),
                    dateTo = LocalDate.of(2026, 11, 1),
                ),
                CatalogFilters(query = "a".repeat(101)),
                CatalogFilters(place = "a".repeat(201)),
            )
        for (filters in invalid) {
            model.setFilters(filters)
            advanceUntilIdle()
            assertTrue(model.state.value.content is CatalogContent.InvalidFilters)
        }
        assertEquals(1, repo.requests.size)
        model.resetFilters()
        advanceUntilIdle()
        assertTrue(model.state.value.content is CatalogContent.Events)
    }

    @Test
    fun firstPageErrorRetriesWithTheSameFiltersAndCanBecomeEmpty() = runTest {
        var fail = false
        val repo = FakeRepository { filters, _ ->
            if (filters.isFree == true && !fail) {
                fail = true
                throw IOException("offline")
            }
            CatalogPage(emptyList(), 0, 1, 20)
        }
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        assertEquals(CatalogContent.Empty, model.state.value.content)
        model.setFilters(CatalogFilters(isFree = true))
        advanceUntilIdle()
        assertEquals(CatalogContent.Error, model.state.value.content)
        model.refresh()
        advanceUntilIdle()
        assertEquals(CatalogContent.Empty, model.state.value.content)
        assertEquals(CatalogFilters(isFree = true) to 1, repo.requests.last())
    }

    @Test
    fun refreshCancelsTheOldRequestWithoutShowingAnError() = runTest {
        var calls = 0
        var cancelled = false
        val repo = FakeRepository { _, _ ->
            calls++
            if (calls == 1) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            page("new", total = 1)
        }
        val model = CatalogViewModel(repo)
        runCurrent()
        model.refresh()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals(listOf("new"), content(model).events.map { it.id })
    }

    private fun content(model: CatalogViewModel) =
        model.state.value.content as CatalogContent.Events

    private fun page(vararg ids: String, total: Int, number: Int = 1) =
        CatalogPage(
            ids.map {
                CatalogEvent(
                    it,
                    it,
                    LocalDate.of(2026, 11, 20),
                    "НГУ",
                    true,
                    null,
                    EventStatus.CONFIRMED,
                )
            },
            total,
            number,
            2,
        )

    private class FakeRepository(val answer: suspend (CatalogFilters, Int) -> CatalogPage) :
        EventsRepository {
        val requests = mutableListOf<Pair<CatalogFilters, Int>>()

        override suspend fun getEvents(filters: CatalogFilters, page: Int): CatalogPage {
            requests += filters to page
            return answer(filters, page)
        }
    }
}
