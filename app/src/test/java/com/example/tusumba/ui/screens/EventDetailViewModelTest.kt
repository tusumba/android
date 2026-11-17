package com.example.tusumba.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.example.tusumba.data.events.model.EventDetail
import com.example.tusumba.data.events.model.EventStatus
import com.example.tusumba.data.events.model.Organizer
import com.example.tusumba.data.registrations.RegistrationsRepository
import com.example.tusumba.data.ticketing.model.Registration
import com.example.tusumba.data.ticketing.model.RegistrationSource
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import com.example.tusumba.data.ticketing.model.Visibility
import com.example.tusumba.ui.registrations.todayForRegistrations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {
    @get:Rule
    val mainDispatcher = object : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(StandardTestDispatcher())
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    private val event = EventDetail(
        id = UUID.randomUUID(),
        title = "Настольные игры",
        date = todayForRegistrations().plusDays(1),
        placeAddress = "Библиотека",
        isFree = true,
        status = EventStatus.confirmed,
        description = "Встреча клуба",
        organizer = Organizer(Organizer.Type.user),
        currentVersionId = UUID.randomUUID(),
        versionNo = 1,
    )
    private val registration = Registration(
        id = UUID.randomUUID(),
        eventId = event.id,
        status = RegistrationStatus.going,
        visibility = Visibility.`public`,
        source = RegistrationSource.free_signup,
        createdAt = OffsetDateTime.parse("2026-11-11T06:00:00Z"),
    )
    private val repository = mockk<RegistrationsRepository> {
        coEvery { getEvent(event.id) } returns event
    }

    @Test
    fun onlySignedInUserCanSaveAndRepeatedTapDoesNotSendAnotherRequest() = runTest {
        val result = CompletableDeferred<Registration>()
        coEvery { repository.saveRegistration(event.id, RegistrationStatus.going) } coAnswers { result.await() }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.saveRegistration(RegistrationStatus.going)
        runCurrent()
        coVerify(exactly = 0) { repository.saveRegistration(any(), any()) }

        viewModel.setUser("first-user")
        viewModel.saveRegistration(RegistrationStatus.going)
        viewModel.saveRegistration(RegistrationStatus.going)
        runCurrent()
        assertTrue(loaded(viewModel).saving)

        result.complete(registration)
        runCurrent()

        coVerify(exactly = 1) { repository.saveRegistration(event.id, RegistrationStatus.going) }
        assertFalse(loaded(viewModel).saving)
        assertEquals(RegistrationStatus.going, loaded(viewModel).savedStatus)
        viewModel.clearRegistrationFeedback()
        assertNull(loaded(viewModel).savedStatus)
    }

    @Test
    fun accountChangeDiscardsDelayedResponseFromPreviousUser() = runTest {
        val stale = CompletableDeferred<Registration>()
        coEvery { repository.saveRegistration(event.id, RegistrationStatus.going) } coAnswers {
            withContext(NonCancellable) { stale.await() }
        }
        val viewModel = createViewModel()
        runCurrent()
        viewModel.setUser("first-user")
        viewModel.saveRegistration(RegistrationStatus.going)
        runCurrent()

        viewModel.setUser("second-user")
        stale.complete(registration)
        runCurrent()

        assertNull(loaded(viewModel).savedStatus)
        assertFalse(loaded(viewModel).saving)
    }

    private fun createViewModel() =
        EventDetailViewModel(SavedStateHandle(mapOf("eventId" to event.id.toString())), repository)

    private fun loaded(viewModel: EventDetailViewModel) =
        viewModel.state.value as EventDetailContent.Loaded
}
