package com.example.tusumba.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.example.tusumba.data.events.EventFriend
import com.example.tusumba.data.events.EventPeopleRepository
import com.example.tusumba.data.events.model.EventDetail
import com.example.tusumba.data.events.model.EventStatus
import com.example.tusumba.data.events.model.Organizer
import com.example.tusumba.data.registrations.RegistrationApiException
import com.example.tusumba.data.registrations.RegistrationsRepository
import com.example.tusumba.data.ticketing.model.Registration
import com.example.tusumba.data.ticketing.model.RegistrationSource
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import com.example.tusumba.data.ticketing.model.Visibility
import com.example.tusumba.ui.registrations.todayForRegistrations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {
    @get:Rule
    val mainDispatcher =
        object : TestWatcher() {
            override fun starting(description: Description) =
                Dispatchers.setMain(StandardTestDispatcher())

            override fun finished(description: Description) = Dispatchers.resetMain()
        }
    private val event =
        EventDetail(
            id = UUID.randomUUID(),
            title = "Настольные игры",
            date = todayForRegistrations().plusDays(1),
            placeAddress = "Библиотека",
            isFree = true,
            status = EventStatus.confirmed,
            description = "Встреча клуба",
            organizer = Organizer(Organizer.Type.club, clubName = "Игротека"),
            currentVersionId = UUID.randomUUID(),
            versionNo = 1,
        )
    private val registration =
        Registration(
            id = UUID.randomUUID(),
            eventId = event.id,
            status = RegistrationStatus.going,
            visibility = Visibility.anonymous,
            source = RegistrationSource.free_signup,
            createdAt = OffsetDateTime.parse("2026-11-11T06:00:00Z"),
        )
    private val repository =
        mockk<RegistrationsRepository> {
            coEvery { getEvent(event.id) } returns event
            coEvery { getRegistration(event.id) } returns null
        }
    private val people =
        mockk<EventPeopleRepository> {
            coEvery { getOrganizerName(any()) } returns "Игротека"
            coEvery { getFriendsGoing(any()) } returns emptyList()
        }

    @Test
    fun guestLoadsPublicDetailsAndOrganizerWithoutPersonalRequests() = runTest {
        val vm = createViewModel()
        assertEquals(EventDetailContent.Loading, vm.state.value)
        runCurrent()
        assertEquals(event, loaded(vm).event)
        assertEquals("Игротека", loaded(vm).organizerName)
        vm.saveRegistration(RegistrationStatus.going)
        coVerify(exactly = 0) { repository.getRegistration(any()) }
        coVerify(exactly = 0) { repository.saveRegistration(any(), any(), any()) }
        coVerify(exactly = 0) { people.getFriendsGoing(any()) }
    }

    @Test
    fun loadsCurrentStatusAndVisibilityWhenUserArrivesBeforeEventResponse() = runTest {
        coEvery { repository.getRegistration(event.id) } returns registration
        val vm = createViewModel()
        vm.setUser("first-user")
        runCurrent()
        assertEquals(registration, loaded(vm).registration)
        assertEquals(Visibility.anonymous, loaded(vm).visibility)
        assertFalse(loaded(vm).loadingRegistration)
        coVerify(exactly = 1) { repository.getRegistration(event.id) }
    }

    @Test
    fun selectedVisibilityAndStatusAreSavedTogetherAndDuplicateTapsAreBlocked() = runTest {
        val result = CompletableDeferred<Registration>()
        coEvery {
            repository.saveRegistration(
                event.id,
                RegistrationStatus.interested,
                Visibility.friends_only,
            )
        } coAnswers { result.await() }
        val vm = createViewModel()
        vm.setUser("first-user")
        runCurrent()
        vm.selectVisibility(Visibility.friends_only)
        vm.saveRegistration(RegistrationStatus.interested)
        vm.saveRegistration(RegistrationStatus.going)
        vm.selectVisibility(Visibility.anonymous)
        runCurrent()
        assertTrue(loaded(vm).saving)
        assertEquals(Visibility.friends_only, loaded(vm).visibility)
        result.complete(
            registration.copy(
                status = RegistrationStatus.interested,
                visibility = Visibility.friends_only,
            )
        )
        runCurrent()
        coVerify(exactly = 1) { repository.saveRegistration(any(), any(), any()) }
        assertFalse(loaded(vm).saving)
        assertTrue(loaded(vm).saved)
        assertEquals(RegistrationStatus.interested, loaded(vm).registration?.status)
        vm.clearRegistrationFeedback()
        assertFalse(loaded(vm).saved)
        assertNotNull(loaded(vm).registration)
    }

    @Test
    fun preservesAnonymousVisibilityWhenOnlyStatusChanges() = runTest {
        coEvery { repository.getRegistration(event.id) } returns registration
        coEvery {
            repository.saveRegistration(
                event.id,
                RegistrationStatus.interested,
                Visibility.anonymous,
            )
        } returns registration.copy(status = RegistrationStatus.interested)
        val vm = createViewModel()
        vm.setUser("first-user")
        runCurrent()
        vm.saveRegistration(RegistrationStatus.interested)
        runCurrent()
        coVerify {
            repository.saveRegistration(
                event.id,
                RegistrationStatus.interested,
                Visibility.anonymous,
            )
        }
        assertEquals(Visibility.anonymous, loaded(vm).registration?.visibility)
    }

    @Test
    fun cannotOverwriteAnUnknownRegistrationUntilLoadingSucceeds() = runTest {
        coEvery { repository.getRegistration(event.id) } throws IOException("offline")
        val vm = createViewModel()
        vm.setUser("first-user")
        runCurrent()
        assertTrue(loaded(vm).registrationLoadFailed)
        vm.saveRegistration(RegistrationStatus.going)
        coVerify(exactly = 0) { repository.saveRegistration(any(), any(), any()) }
        coEvery { repository.getRegistration(event.id) } returns registration
        vm.refreshRegistration()
        runCurrent()
        assertFalse(loaded(vm).registrationLoadFailed)
        assertEquals(registration, loaded(vm).registration)
    }

    @Test
    fun loadingPersonalDataBlocksSavingAndVisibilityChanges() = runTest {
        val result = CompletableDeferred<Registration?>()
        coEvery { repository.getRegistration(event.id) } coAnswers { result.await() }
        val vm = createViewModel()
        vm.setUser("first-user")
        runCurrent()
        assertTrue(loaded(vm).loadingRegistration)
        vm.selectVisibility(Visibility.anonymous)
        vm.saveRegistration(RegistrationStatus.going)
        assertEquals(Visibility.`public`, loaded(vm).visibility)
        coVerify(exactly = 0) { repository.saveRegistration(any(), any(), any()) }
        result.complete(null)
        runCurrent()
    }

    @Test
    fun resumeReadsChangedOrCancelledRegistrationAgain() = runTest {
        coEvery { repository.getRegistration(event.id) } returns registration andThen null
        val vm = createViewModel()
        vm.setUser("first-user")
        runCurrent()
        assertEquals(registration, loaded(vm).registration)
        vm.refreshRegistration()
        runCurrent()
        assertNull(loaded(vm).registration)
        assertEquals(Visibility.`public`, loaded(vm).visibility)
    }

    @Test
    fun accountChangeDiscardsDelayedSaveAndAllPreviousPersonalData() = runTest {
        val stale = CompletableDeferred<Registration>()
        coEvery { repository.getRegistration(event.id) } returns registration andThen null
        coEvery {
            repository.saveRegistration(event.id, RegistrationStatus.going, Visibility.anonymous)
        } coAnswers { withContext(NonCancellable) { stale.await() } }
        val vm = createViewModel()
        vm.setUser("first-user")
        runCurrent()
        vm.saveRegistration(RegistrationStatus.going)
        runCurrent()
        vm.setUser("second-user")
        runCurrent()
        stale.complete(registration)
        runCurrent()
        assertEquals("second-user", loaded(vm).accountId)
        assertNull(loaded(vm).registration)
        assertFalse(loaded(vm).saving)
        assertFalse(loaded(vm).saved)
    }

    @Test
    fun signOutDiscardsDelayedRegistrationAndFriendsResponses() = runTest {
        val stale = CompletableDeferred<Registration?>()
        val staleFriends = CompletableDeferred<List<EventFriend>>()
        coEvery { repository.getRegistration(event.id) } coAnswers
            {
                withContext(NonCancellable) { stale.await() }
            }
        coEvery { people.getFriendsGoing(event.id) } coAnswers
            {
                withContext(NonCancellable) { staleFriends.await() }
            }
        val vm = createViewModel()
        vm.setUser("first-user")
        runCurrent()
        vm.setUser(null)
        stale.complete(registration)
        staleFriends.complete(
            listOf(EventFriend(UUID.randomUUID(), "Друг", RegistrationStatus.going))
        )
        runCurrent()
        assertNull(loaded(vm).registration)
        assertTrue(loaded(vm).friends.isEmpty())
        assertNull(loaded(vm).accountId)
    }

    @Test
    fun failedOptionalMetadataDoesNotHideEventOrRegistration() = runTest {
        coEvery { people.getOrganizerName(any()) } throws IOException("offline")
        coEvery { people.getFriendsGoing(any()) } throws IOException("not implemented")
        val vm = createViewModel()
        vm.setUser("first-user")
        runCurrent()
        assertEquals(event, loaded(vm).event)
        assertNull(loaded(vm).organizerName)
        assertTrue(loaded(vm).friends.isEmpty())
        assertFalse(loaded(vm).registrationLoadFailed)
    }

    @Test
    fun rendersAvailableFriendsWithNamesAndStatuses() = runTest {
        val friend = EventFriend(UUID.randomUUID(), "Саша", RegistrationStatus.interested)
        coEvery { people.getFriendsGoing(event.id) } returns listOf(friend)
        val vm = createViewModel()
        vm.setUser("first-user")
        runCurrent()
        assertEquals(listOf(friend), loaded(vm).friends)
    }

    @Test
    fun distinguishesUnavailableEventFromTemporaryErrorAndSupportsRetry() = runTest {
        coEvery { repository.getEvent(event.id) } throws
            RegistrationApiException(404, "event_not_found")
        val vm = createViewModel()
        runCurrent()
        assertEquals(EventDetailContent.Unavailable, vm.state.value)
        coEvery { repository.getEvent(event.id) } throws IOException("offline")
        vm.refresh()
        runCurrent()
        assertEquals(EventDetailContent.Error, vm.state.value)
        coEvery { repository.getEvent(event.id) } returns event
        vm.refresh()
        runCurrent()
        assertEquals(event, loaded(vm).event)
    }

    @Test
    fun invalidIdDoesNotSendRequests() = runTest {
        val vm =
            EventDetailViewModel(SavedStateHandle(mapOf("eventId" to "bad-id")), repository, people)
        runCurrent()
        assertEquals(EventDetailContent.Unavailable, vm.state.value)
        coVerify(exactly = 0) { repository.getEvent(any()) }
    }

    @Test
    fun paidAndPastEventsCannotBeRegistered() = runTest {
        for (blocked in
            listOf(
                event.copy(isFree = false),
                event.copy(date = todayForRegistrations().minusDays(1)),
            )) {
            coEvery { repository.getEvent(event.id) } returns blocked
            val vm = createViewModel()
            vm.setUser("first-user")
            runCurrent()
            vm.saveRegistration(RegistrationStatus.going)
        }
        coVerify(exactly = 0) { repository.saveRegistration(any(), any(), any()) }
    }

    @Test
    fun mapsSaveErrorsAndKeepsSelectionForRetry() = runTest {
        for ((failure, expected) in
            listOf(
                RegistrationApiException(401, "token_expired") to
                    EventRegistrationError.SignInRequired,
                RegistrationApiException(409, "event_finished") to EventRegistrationError.Closed,
                RegistrationApiException(409, "event_is_paid") to EventRegistrationError.Paid,
                RegistrationApiException(404, "event_not_found") to
                    EventRegistrationError.Unavailable,
                IOException("offline") to EventRegistrationError.Failed,
            )) {
            coEvery {
                repository.saveRegistration(
                    event.id,
                    RegistrationStatus.going,
                    Visibility.anonymous,
                )
            } throws failure
            val vm = createViewModel()
            vm.setUser("first-user")
            runCurrent()
            vm.selectVisibility(Visibility.anonymous)
            vm.saveRegistration(RegistrationStatus.going)
            runCurrent()
            assertEquals(expected, loaded(vm).saveError)
            assertEquals(Visibility.anonymous, loaded(vm).visibility)
            assertFalse(loaded(vm).saving)
        }
    }

    private fun createViewModel() =
        EventDetailViewModel(
            SavedStateHandle(mapOf("eventId" to event.id.toString())),
            repository,
            people,
        )

    private fun loaded(viewModel: EventDetailViewModel) =
        viewModel.state.value as EventDetailContent.Loaded
}
