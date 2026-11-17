package com.example.tusumba.ui.clubs

import androidx.lifecycle.SavedStateHandle
import com.example.tusumba.data.clubs.ClubsRepository
import com.example.tusumba.data.events.model.ClubCard
import com.example.tusumba.data.events.model.ClubCardPage
import com.example.tusumba.data.events.model.ClubDetail
import com.example.tusumba.data.events.model.EventCard
import com.example.tusumba.data.events.model.EventCardPage
import com.example.tusumba.data.events.model.EventStatus
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ClubsViewModelsTest {
    @get:Rule
    val mainDispatcherRule = object : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(StandardTestDispatcher())
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    @Test
    fun searchDebouncesEditsAndIgnoresCancelledResponse() = runTest {
        val stale = CompletableDeferred<ClubCardPage>()
        val queries = mutableListOf<String?>()
        val currentClub = clubCard(2)
        val viewModel = ClubsCatalogViewModel(repository(clubs = { query, _ ->
            queries += query
            when (query) {
                "old" -> withContext(NonCancellable) { stale.await() }
                "new" -> clubPage(listOf(currentClub))
                else -> clubPage(emptyList())
            }
        }))
        runCurrent()
        viewModel.setQuery("old")
        advanceTimeBy(300)
        runCurrent()
        viewModel.setQuery("n")
        viewModel.setQuery("new")
        advanceTimeBy(299)
        runCurrent()
        assertEquals(listOf(null, "old"), queries)
        assertEquals(ClubsContent.Loading, viewModel.state.value.content)

        advanceTimeBy(1)
        runCurrent()
        stale.complete(clubPage(listOf(clubCard(1))))
        runCurrent()

        assertEquals(listOf(null, "old", "new"), queries)
        assertEquals("new", viewModel.state.value.query)
        assertEquals(listOf(currentClub), (viewModel.state.value.content as ClubsContent.Data).clubs)
    }

    @Test
    fun refreshRetriesFailureAndLimitsSearchToOneHundredCharacters() = runTest {
        var calls = 0
        var lastQuery: String? = null
        val viewModel = ClubsCatalogViewModel(repository(clubs = { query, _ ->
            calls++
            lastQuery = query
            if (calls == 1) throw IOException()
            clubPage(emptyList())
        }))
        runCurrent()
        assertEquals(ClubsContent.Error, viewModel.state.value.content)
        viewModel.refresh()
        runCurrent()
        assertEquals(ClubsContent.Empty, viewModel.state.value.content)

        viewModel.setQuery("x".repeat(101))
        advanceUntilIdle()
        assertEquals("x".repeat(100), viewModel.state.value.query)
        assertEquals("x".repeat(100), lastQuery)
        assertEquals(3, calls)

        viewModel.setQuery("😀".repeat(101))
        advanceUntilIdle()
        assertEquals("😀".repeat(100), viewModel.state.value.query)
        assertEquals("😀".repeat(100), lastQuery)
        assertEquals(4, calls)
    }

    @Test
    fun catalogPaginationKeepsItemsOnFailureRetriesSamePageAndDeduplicates() = runTest {
        val nextPage = CompletableDeferred<ClubCardPage>()
        val pages = mutableListOf<Int>()
        var failed = false
        val firstClub = clubCard(1)
        val secondClub = clubCard(2)
        val viewModel = ClubsCatalogViewModel(repository(clubs = { _, page ->
            pages += page
            if (page == 1) clubPage(listOf(firstClub), total = 3)
            else if (!failed) {
                failed = true
                throw IOException()
            } else nextPage.await()
        }))
        runCurrent()
        viewModel.loadMore()
        runCurrent()
        val failedPage = viewModel.state.value.content as ClubsContent.Data
        assertEquals(listOf(firstClub), failedPage.clubs)
        assertTrue(failedPage.loadMoreError)
        assertFalse(failedPage.loadingMore)

        viewModel.loadMore()
        viewModel.loadMore()
        runCurrent()
        assertTrue((viewModel.state.value.content as ClubsContent.Data).loadingMore)
        nextPage.complete(clubPage(listOf(firstClub, secondClub), page = 2, total = 3, size = 2))
        runCurrent()

        assertEquals(listOf(1, 2, 2), pages)
        val loaded = viewModel.state.value.content as ClubsContent.Data
        assertEquals(listOf(firstClub, secondClub), loaded.clubs)
        assertFalse(loaded.hasMore)
        assertFalse(loaded.loadMoreError)
    }

    @Test
    fun refreshPreventsOldCatalogPageFromAppending() = runTest {
        val stale = CompletableDeferred<ClubCardPage>()
        var firstPageCalls = 0
        val currentClub = clubCard(3)
        val viewModel = ClubsCatalogViewModel(repository(clubs = { _, page ->
            if (page == 2) withContext(NonCancellable) { stale.await() }
            else {
                firstPageCalls++
                clubPage(listOf(if (firstPageCalls == 1) clubCard(1) else currentClub), total = 2)
            }
        }))
        runCurrent()
        viewModel.loadMore()
        runCurrent()
        viewModel.refresh()
        runCurrent()
        stale.complete(clubPage(listOf(clubCard(2)), page = 2))
        runCurrent()

        assertEquals(listOf(currentClub), (viewModel.state.value.content as ClubsContent.Data).clubs)
    }

    @Test
    fun missingInvalidAndUnavailableClubNeverLoadEvents() = runTest {
        var detailCalls = 0
        var eventCalls = 0
        val repository = repository(
            club = {
                detailCalls++
                null
            },
            events = { _, _ ->
                eventCalls++
                eventPage(emptyList())
            },
        )
        val missing = ClubDetailViewModel(SavedStateHandle(), repository)
        val invalid = ClubDetailViewModel(SavedStateHandle(mapOf("clubId" to "invalid")), repository)
        val unavailable = ClubDetailViewModel(savedClubId(), repository)
        advanceUntilIdle()

        assertEquals(ClubDetailContent.Unavailable, missing.state.value)
        assertEquals(ClubDetailContent.Unavailable, invalid.state.value)
        assertEquals(ClubDetailContent.Unavailable, unavailable.state.value)
        assertEquals(1, detailCalls)
        assertEquals(0, eventCalls)
    }

    @Test
    fun eventFailurePreservesClubAndCanBeRetriedWithoutReloadingDetail() = runTest {
        var detailCalls = 0
        var eventCalls = 0
        val club = clubDetail()
        val viewModel = ClubDetailViewModel(savedClubId(), repository(
            club = {
                detailCalls++
                club
            },
            events = { _, _ ->
                eventCalls++
                if (eventCalls == 1) throw IOException()
                eventPage(emptyList())
            },
        ))
        runCurrent()
        assertEquals(ClubDetailContent.Loaded(club, ClubEventsContent.Error), viewModel.state.value)

        viewModel.retryEvents()
        assertEquals(ClubDetailContent.Loaded(club, ClubEventsContent.Loading), viewModel.state.value)
        runCurrent()
        assertEquals(ClubDetailContent.Loaded(club, ClubEventsContent.Empty), viewModel.state.value)
        assertEquals(1, detailCalls)
        assertEquals(2, eventCalls)
    }

    @Test
    fun eventPaginationPreservesDetailAndRetriesWithoutDuplicates() = runTest {
        val pending = CompletableDeferred<EventCardPage>()
        val pages = mutableListOf<Int>()
        var failed = false
        val club = clubDetail()
        val first = eventCard(1)
        val second = eventCard(2)
        val viewModel = ClubDetailViewModel(savedClubId(), repository(
            club = { club },
            events = { _, page ->
                pages += page
                if (page == 1) eventPage(listOf(first), total = 3)
                else if (!failed) {
                    failed = true
                    throw IOException()
                } else pending.await()
            },
        ))
        runCurrent()
        viewModel.loadMoreEvents()
        runCurrent()
        val failedContent = viewModel.state.value as ClubDetailContent.Loaded
        val failedEvents = failedContent.events as ClubEventsContent.Data
        assertEquals(club, failedContent.club)
        assertTrue(failedEvents.loadMoreError)
        assertEquals(listOf(first), failedEvents.events)

        viewModel.loadMoreEvents()
        viewModel.loadMoreEvents()
        runCurrent()
        pending.complete(eventPage(listOf(first, second), page = 2, total = 3, size = 2))
        runCurrent()
        val loaded = viewModel.state.value as ClubDetailContent.Loaded
        val loadedEvents = loaded.events as ClubEventsContent.Data
        assertEquals(listOf(1, 2, 2), pages)
        assertEquals(listOf(first, second), loadedEvents.events)
        assertFalse(loadedEvents.hasMore)
    }

    @Test
    fun refreshingDetailCancelsOldEventsAndRecoversFromDetailFailure() = runTest {
        var detailCalls = 0
        var eventCalls = 0
        val stale = CompletableDeferred<EventCardPage>()
        val club = clubDetail()
        val viewModel = ClubDetailViewModel(savedClubId(), repository(
            club = {
                detailCalls++
                if (detailCalls == 1) throw IOException()
                club
            },
            events = { _, _ ->
                eventCalls++
                if (eventCalls == 1) withContext(NonCancellable) { stale.await() }
                else eventPage(emptyList())
            },
        ))
        runCurrent()
        assertEquals(ClubDetailContent.Error, viewModel.state.value)
        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        runCurrent()
        stale.complete(eventPage(listOf(eventCard(1))))
        runCurrent()

        assertEquals(ClubDetailContent.Loaded(club, ClubEventsContent.Empty), viewModel.state.value)
        assertEquals(3, detailCalls)
        assertEquals(2, eventCalls)
    }

    private fun repository(
        clubs: suspend (String?, Int) -> ClubCardPage = { _, _ -> error("Unexpected catalog request") },
        club: suspend (UUID) -> ClubDetail? = { error("Unexpected detail request") },
        events: suspend (UUID, Int) -> EventCardPage = { _, _ -> error("Unexpected event request") },
    ) = object : ClubsRepository {
        override suspend fun getClubs(query: String?, page: Int) = clubs(query, page)
        override suspend fun getClub(id: UUID) = club(id)
        override suspend fun getEvents(clubId: UUID, page: Int) = events(clubId, page)
    }

    private fun clubCard(id: Int) = ClubCard(UUID(0, id.toLong()), "Секция $id", "Описание")

    private fun clubPage(items: List<ClubCard>, page: Int = 1, total: Int = items.size, size: Int = 1) =
        ClubCardPage(items = items, total = total, page = page, propertySize = size)

    private fun clubDetail() = ClubDetail(
        id = UUID(0, 1), name = "Секция", description = "Описание", currentVersionId = UUID(0, 2),
        versionNo = 1, isAdmin = false,
    )

    private fun savedClubId() = SavedStateHandle(mapOf("clubId" to UUID(0, 1).toString()))

    private fun eventCard(id: Int) = EventCard(
        id = UUID(0, id.toLong()), title = "Мероприятие $id", date = LocalDate.of(2026, 12, 1),
        placeAddress = "НГУ", isFree = true, status = EventStatus.entries.first(),
    )

    private fun eventPage(items: List<EventCard>, page: Int = 1, total: Int = items.size, size: Int = 1) =
        EventCardPage(items = items, total = total, page = page, propertySize = size)
}
