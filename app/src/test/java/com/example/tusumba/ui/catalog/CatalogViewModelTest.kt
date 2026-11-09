package com.example.tusumba.ui.catalog

import com.example.tusumba.data.catalog.CatalogEvent
import com.example.tusumba.data.catalog.EventsRepository
import com.example.tusumba.data.catalog.MockEventsRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun loadingIsShownUntilRepositoryCompletes() = runTest {
        val response = CompletableDeferred<List<CatalogEvent>>()
        val viewModel = CatalogViewModel(repository { response.await() })
        runCurrent()
        assertEquals(CatalogContent.Loading, viewModel.state.value.content)

        val events = MockEventsRepository().getEvents()
        response.complete(events)
        advanceUntilIdle()

        assertEquals(CatalogContent.Events(events), viewModel.state.value.content)
    }

    @Test
    fun repositoryCanBeReplacedWithoutChangingTheScreen() = runTest {
        val viewModel = CatalogViewModel(repository { emptyList() })
        advanceUntilIdle()

        assertEquals(CatalogContent.Empty, viewModel.state.value.content)
    }

    @Test
    fun retryRecoversAfterFailure() = runTest {
        var calls = 0
        val viewModel =
            CatalogViewModel(
                repository {
                    calls++
                    if (calls == 1) throw IOException("Unavailable")
                    MockEventsRepository().getEvents()
                }
            )
        advanceUntilIdle()
        assertEquals(CatalogContent.Error, viewModel.state.value.content)

        viewModel.refresh()
        assertEquals(CatalogContent.Loading, viewModel.state.value.content)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.content is CatalogContent.Events)
        assertEquals(2, calls)
    }

    @Test
    fun filteringAndResetDoNotReloadTheRepository() = runTest {
        var calls = 0
        val viewModel =
            CatalogViewModel(
                repository {
                    calls++
                    MockEventsRepository().getEvents()
                }
            )
        advanceUntilIdle()

        viewModel.setQuery("МАСТЕРСКАЯ")
        viewModel.setIsFree(true)
        assertEquals(CatalogContent.Empty, viewModel.state.value.content)

        viewModel.setIsFree(false)
        val content = viewModel.state.value.content as CatalogContent.Events
        assertEquals(listOf("Мастерская керамики"), content.events.map { it.title })

        viewModel.resetFilters()
        assertEquals(6, (viewModel.state.value.content as CatalogContent.Events).events.size)
        assertEquals("", viewModel.state.value.filters.query)
        assertEquals(null, viewModel.state.value.filters.isFree)
        assertEquals(1, calls)
    }

    @Test
    fun filtersChangedDuringLoadingAreAppliedToTheResponse() = runTest {
        val viewModel = CatalogViewModel(MockEventsRepository())
        viewModel.setIsFree(false)
        advanceUntilIdle()

        val content = viewModel.state.value.content as CatalogContent.Events
        assertEquals(
            listOf("Музыка под открытым небом", "Мастерская керамики", "Турнир по шахматам"),
            content.events.map { it.title },
        )
    }

    @Test
    fun retryCancelsPreviousLoadWithoutShowingAnError() = runTest {
        var calls = 0
        var cancelled = false
        val viewModel =
            CatalogViewModel(
                repository {
                    calls++
                    if (calls == 1) {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled = true
                        }
                    }
                    emptyList()
                }
            )
        runCurrent()

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(cancelled)
        assertEquals(CatalogContent.Empty, viewModel.state.value.content)
        assertEquals(2, calls)
    }

    private fun repository(load: suspend () -> List<CatalogEvent>) =
        object : EventsRepository {
            override suspend fun getEvents() = load()
        }
}
