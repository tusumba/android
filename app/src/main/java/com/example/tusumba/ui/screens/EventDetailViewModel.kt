package com.example.tusumba.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tusumba.data.events.EventFriend
import com.example.tusumba.data.events.EventPeopleRepository
import com.example.tusumba.data.events.model.EventDetail
import com.example.tusumba.data.registrations.RegistrationApiException
import com.example.tusumba.data.registrations.RegistrationsRepository
import com.example.tusumba.data.ticketing.model.Registration
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import com.example.tusumba.data.ticketing.model.Visibility
import com.example.tusumba.ui.registrations.todayForRegistrations
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun EventDetail.registrationIsClosed(): Boolean = date.isBefore(todayForRegistrations())

sealed interface EventDetailContent {
    data object Loading : EventDetailContent

    data object Unavailable : EventDetailContent

    data object Error : EventDetailContent

    data class Loaded(
        val event: EventDetail,
        val accountId: String? = null,
        val organizerName: String? = null,
        val friends: List<EventFriend> = emptyList(),
        val registration: Registration? = null,
        val loadingRegistration: Boolean = false,
        val registrationLoadFailed: Boolean = false,
        val visibility: Visibility = Visibility.`public`,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val saveError: EventRegistrationError? = null,
    ) : EventDetailContent
}

enum class EventRegistrationError {
    SignInRequired,
    Closed,
    Paid,
    Unavailable,
    Failed,
}

@HiltViewModel
class EventDetailViewModel
@Inject
constructor(
    savedState: SavedStateHandle,
    private val repository: RegistrationsRepository,
    private val people: EventPeopleRepository,
) : ViewModel() {
    private val eventId =
        savedState.get<String>("eventId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private val mutableState = MutableStateFlow<EventDetailContent>(EventDetailContent.Loading)
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var registrationJob: Job? = null
    private var organizerJob: Job? = null
    private var friendsJob: Job? = null
    private var saveJob: Job? = null
    private var userId: String? = null
    private val loaded
        get() = state.value as? EventDetailContent.Loaded

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        organizerJob?.cancel()
        cancelPersonalRequests()
        val id = eventId
        if (id == null) {
            mutableState.value = EventDetailContent.Unavailable
            return
        }
        mutableState.value = EventDetailContent.Loading
        loadJob =
            viewModelScope.launch {
                try {
                    val event = repository.getEvent(id)
                    currentCoroutineContext().ensureActive()
                    mutableState.value = EventDetailContent.Loaded(event, accountId = userId)
                    organizerJob =
                        viewModelScope.launch {
                            val name =
                                try {
                                    people.getOrganizerName(event.organizer)
                                } catch (_: IOException) {
                                    null
                                }
                            currentCoroutineContext().ensureActive()
                            updateLoaded { it.copy(organizerName = name) }
                        }
                    refreshRegistration()
                } catch (error: IOException) {
                    currentCoroutineContext().ensureActive()
                    mutableState.value =
                        if ((error as? RegistrationApiException)?.statusCode in listOf(403, 404)) {
                            EventDetailContent.Unavailable
                        } else EventDetailContent.Error
                }
            }
    }

    fun setUser(userId: String?) {
        if (this.userId == userId) return
        this.userId = userId
        cancelPersonalRequests()
        updateLoaded {
            EventDetailContent.Loaded(
                it.event,
                accountId = userId,
                organizerName = it.organizerName,
            )
        }
        refreshRegistration()
    }

    fun refreshRegistration() {
        val current = loaded ?: return
        if (userId == null || current.saving || registrationJob?.isActive == true) return
        updateLoaded {
            it.copy(
                loadingRegistration = true,
                registrationLoadFailed = false,
                saved = false,
                saveError = null,
            )
        }
        registrationJob =
            viewModelScope.launch {
                try {
                    val registration = repository.getRegistration(current.event.id)
                    currentCoroutineContext().ensureActive()
                    updateLoaded {
                        it.copy(
                            registration = registration,
                            visibility = registration?.visibility ?: Visibility.`public`,
                            loadingRegistration = false,
                        )
                    }
                } catch (error: IOException) {
                    currentCoroutineContext().ensureActive()
                    if ((error as? RegistrationApiException)?.statusCode == 401) {
                        friendsJob?.cancel()
                        updateLoaded {
                            it.copy(
                                registration = null,
                                friends = emptyList(),
                                loadingRegistration = false,
                                saveError = EventRegistrationError.SignInRequired,
                            )
                        }
                    } else {
                        updateLoaded {
                            it.copy(loadingRegistration = false, registrationLoadFailed = true)
                        }
                    }
                }
            }
        friendsJob?.cancel()
        friendsJob =
            viewModelScope.launch {
                val friends =
                    try {
                        people.getFriendsGoing(current.event.id)
                    } catch (_: IOException) {
                        emptyList()
                    }
                currentCoroutineContext().ensureActive()
                updateLoaded { it.copy(friends = friends) }
            }
    }

    fun selectVisibility(visibility: Visibility) {
        val current = loaded ?: return
        if (
            userId == null ||
                current.saving ||
                current.loadingRegistration ||
                current.registrationLoadFailed
        )
            return
        updateLoaded { it.copy(visibility = visibility, saved = false) }
    }

    fun saveRegistration(status: RegistrationStatus) {
        val current = loaded ?: return
        if (
            userId == null ||
                current.saving ||
                current.loadingRegistration ||
                current.registrationLoadFailed ||
                !current.event.isFree ||
                current.event.registrationIsClosed() ||
                current.saveError in
                    listOf(
                        EventRegistrationError.SignInRequired,
                        EventRegistrationError.Closed,
                        EventRegistrationError.Paid,
                        EventRegistrationError.Unavailable,
                    )
        )
            return
        updateLoaded { it.copy(saving = true, saved = false, saveError = null) }
        saveJob =
            viewModelScope.launch {
                try {
                    val result =
                        repository.saveRegistration(current.event.id, status, current.visibility)
                    currentCoroutineContext().ensureActive()
                    updateLoaded {
                        it.copy(
                            registration = result,
                            visibility = result.visibility,
                            saving = false,
                            saved = true,
                        )
                    }
                } catch (error: IOException) {
                    currentCoroutineContext().ensureActive()
                    val apiError = error as? RegistrationApiException
                    val reason =
                        when {
                            apiError?.statusCode == 401 -> EventRegistrationError.SignInRequired
                            apiError?.errorCode == "event_finished" -> EventRegistrationError.Closed
                            apiError?.errorCode == "event_is_paid" -> EventRegistrationError.Paid
                            apiError?.statusCode in listOf(403, 404) ->
                                EventRegistrationError.Unavailable
                            else -> EventRegistrationError.Failed
                        }
                    updateLoaded { it.copy(saving = false, saveError = reason) }
                }
            }
    }

    fun clearRegistrationFeedback() {
        updateLoaded { it.copy(saved = false, saveError = null) }
    }

    private fun updateLoaded(transform: (EventDetailContent.Loaded) -> EventDetailContent.Loaded) {
        mutableState.update { if (it is EventDetailContent.Loaded) transform(it) else it }
    }

    private fun cancelPersonalRequests() {
        registrationJob?.cancel()
        friendsJob?.cancel()
        saveJob?.cancel()
    }
}
