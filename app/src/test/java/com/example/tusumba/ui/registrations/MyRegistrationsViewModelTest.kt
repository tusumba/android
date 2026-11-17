package com.example.tusumba.ui.registrations

import com.example.tusumba.data.events.model.EventDetail
import com.example.tusumba.data.events.model.EventStatus
import com.example.tusumba.data.events.model.Organizer
import com.example.tusumba.data.registrations.RegistrationApiException
import com.example.tusumba.data.registrations.RegistrationsRepository
import com.example.tusumba.data.ticketing.model.Registration
import com.example.tusumba.data.ticketing.model.RegistrationPage
import com.example.tusumba.data.ticketing.model.RegistrationSource
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import com.example.tusumba.data.ticketing.model.Visibility
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyRegistrationsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun registrationRowsAndPaginationDoNotWaitForEventMetadata() = runTest {
        val registrations = (1..3).map(::registration)
        val first = registrations[0].eventId
        val second = registrations[1].eventId
        val pendingEvent = CompletableDeferred<EventDetail>()
        val repository = TestRepository(registrations).apply {
            eventLookup = { eventId ->
                when (eventId) {
                    first -> pendingEvent.await()
                    second -> throw IOException("Event metadata unavailable")
                    else -> event(eventId)
                }
            }
        }
        val viewModel = authenticatedViewModel(repository)
        runCurrent()

        assertEquals(registrations.take(2), viewModel.rows().map { it.registration })
        assertNull(viewModel.rows().first().event)
        viewModel.loadMore()
        runCurrent()
        assertEquals(listOf(1, 2), repository.requestedPages)
        assertEquals(registrations, viewModel.rows().map { it.registration })
        assertNull(viewModel.rows()[1].event)

        pendingEvent.complete(event(first))
        runCurrent()
        assertEquals(first, viewModel.rows().first().event?.id)
        assertEquals(registrations.last().eventId, viewModel.rows().last().event?.id)
    }

    @Test
    fun deletingAnEarlierRowFillsTheShiftedLastLoadedPage() = runTest {
        val registrations = (1..5).map(::registration)
        val repository = TestRepository(registrations)
        val viewModel = authenticatedViewModel(repository)
        runCurrent()
        viewModel.loadMore()
        runCurrent()

        viewModel.cancelRegistration(registrations.first().eventId)
        runCurrent()

        assertEquals(listOf(registrations.first().eventId), repository.cancellations)
        assertEquals(listOf(1, 2, 2), repository.requestedPages)
        assertEquals(registrations.drop(1), viewModel.rows().map { it.registration })
        val content = viewModel.state.value.content as MyRegistrationsState.Data
        assertNull(content.nextPage)
    }

    @Test
    fun cancellationRequiresKnownNonPastFreeRegistration() = runTest {
        val past = registration(1)
        val ticket = registration(2).copy(source = RegistrationSource.ticket)
        val unknown = registration(3)
        val registrations = listOf(past, ticket, unknown)
        val repository = TestRepository(registrations, pageSize = 3).apply {
            eventLookup = { eventId ->
                when (eventId) {
                    past.eventId -> event(eventId, todayForRegistrations().minusDays(1))
                    unknown.eventId -> throw IOException("Event metadata unavailable")
                    else -> event(eventId)
                }
            }
        }
        val viewModel = authenticatedViewModel(repository)
        runCurrent()

        registrations.forEach { viewModel.cancelRegistration(it.eventId) }
        runCurrent()

        assertEquals(registrations, viewModel.rows().map { it.registration })
        assertTrue(repository.cancellations.isEmpty())
    }

    @Test
    fun accountChangeDiscardsOldRowsAndLateMetadata() = runTest {
        val previous = registration(1)
        val current = registration(2)
        val oldMetadata = CompletableDeferred<EventDetail>()
        val repository = TestRepository(listOf(previous)).apply {
            eventLookup = { eventId ->
                if (eventId == previous.eventId) withContext(NonCancellable) { oldMetadata.await() }
                else event(eventId)
            }
        }
        val viewModel = authenticatedViewModel(repository)
        runCurrent()
        assertEquals(previous, viewModel.rows().single().registration)

        repository.registrations.apply {
            clear()
            add(current)
        }
        viewModel.setAccount("second-account")
        assertEquals(MyRegistrationsState.Loading, viewModel.state.value.content)
        viewModel.refresh()
        runCurrent()
        oldMetadata.complete(event(previous.eventId))
        runCurrent()

        assertEquals("second-account", viewModel.state.value.userId)
        assertEquals(current, viewModel.rows().single().registration)
        assertEquals(current.eventId, viewModel.rows().single().event?.id)

        viewModel.setAccount(null)
        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(1, 1), repository.requestedPages)
        assertEquals(MyRegistrationsState.SignInRequired, viewModel.state.value.content)
    }

    @Test
    fun rejectedSessionOffersSignIn() = runTest {
        val repository = TestRepository(emptyList()).apply {
            pageFailure = RegistrationApiException(401, "token_expired")
        }
        val viewModel = authenticatedViewModel(repository)
        runCurrent()

        assertEquals(MyRegistrationsState.SignInRequired, viewModel.state.value.content)
    }

    private fun authenticatedViewModel(repository: RegistrationsRepository) = MyRegistrationsViewModel(repository).apply {
        setAccount("first-account")
        refresh()
    }

    private fun MyRegistrationsViewModel.rows(): List<MyRegistrationRow> =
        (state.value.content as MyRegistrationsState.Data).rows

    private class TestRepository(items: List<Registration>, private val pageSize: Int = 2) : RegistrationsRepository {
        val registrations = items.toMutableList()
        val requestedPages = mutableListOf<Int>()
        val cancellations = mutableListOf<UUID>()
        var pageFailure: IOException? = null
        var eventLookup: suspend (UUID) -> EventDetail = { event(it) }

        override suspend fun getRegistrations(page: Int): RegistrationPage {
            requestedPages += page
            pageFailure?.let { throw it }
            return RegistrationPage(registrations.drop((page - 1) * pageSize).take(pageSize), registrations.size, page, pageSize)
        }

        override suspend fun getEvent(eventId: UUID): EventDetail = eventLookup(eventId)

        override suspend fun saveRegistration(eventId: UUID, status: RegistrationStatus): Registration = error("Unused")

        override suspend fun cancelRegistration(eventId: UUID) {
            cancellations += eventId
            registrations.removeAll { it.eventId == eventId }
        }
    }

    private companion object {
        fun registration(number: Int) = Registration(
            id = UUID(1, number.toLong()),
            eventId = UUID(2, number.toLong()),
            status = RegistrationStatus.going,
            visibility = Visibility.`public`,
            source = RegistrationSource.free_signup,
            createdAt = OffsetDateTime.parse("2026-11-10T00:00:00Z"),
        )

        fun event(id: UUID, date: LocalDate = todayForRegistrations().plusDays(1)) = EventDetail(
            id = id,
            title = "Настольные игры",
            date = date,
            placeAddress = "НГУ",
            isFree = true,
            status = EventStatus.confirmed,
            description = "Встреча клуба",
            organizer = Organizer(Organizer.Type.club, clubName = "Клуб настольных игр"),
            currentVersionId = UUID(3, 1),
            versionNo = 1,
        )
    }
}
