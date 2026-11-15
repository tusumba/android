package com.example.tusumba.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tusumba.data.events.model.EventDetail
import com.example.tusumba.data.registrations.RegistrationApiException
import com.example.tusumba.data.registrations.RegistrationsRepository
import com.example.tusumba.data.ticketing.model.RegistrationStatus
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
import kotlinx.coroutines.launch

internal fun EventDetail.registrationIsClosed(): Boolean = date.isBefore(todayForRegistrations())

sealed interface EventDetailContent {
    data object Loading : EventDetailContent
    data object Unavailable : EventDetailContent
    data object Error : EventDetailContent

    data class Loaded(
        val event: EventDetail,
        val savedStatus: RegistrationStatus? = null,
        val saving: Boolean = false,
        val saveError: EventRegistrationError? = null,
    ) : EventDetailContent
}

enum class EventRegistrationError {
    SignInRequired, Closed, Paid, Unavailable, Failed,
}

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: RegistrationsRepository,
) : ViewModel() {
    private val eventId = savedState.get<String>("eventId")?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    }
    private val mutableState = MutableStateFlow<EventDetailContent>(EventDetailContent.Loading)
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var userId: String? = null

    init {
        refresh()
    }

    fun refresh() {
        val id = eventId
        if (id == null) {
            mutableState.value = EventDetailContent.Unavailable
            return
        }
        loadJob?.cancel()
        saveJob?.cancel()
        mutableState.value = EventDetailContent.Loading
        loadJob = viewModelScope.launch {
            mutableState.value = try {
                val event = repository.getEvent(id)
                currentCoroutineContext().ensureActive()
                EventDetailContent.Loaded(event)
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                if (error is RegistrationApiException && error.statusCode == 404) {
                    EventDetailContent.Unavailable
                } else {
                    EventDetailContent.Error
                }
            }
        }
    }

    fun saveRegistration(status: RegistrationStatus) {
        val current = state.value as? EventDetailContent.Loaded ?: return
        if (userId == null || current.saving || !current.event.isFree || current.event.registrationIsClosed()) return

        mutableState.value = current.copy(saving = true, saveError = null)
        saveJob = viewModelScope.launch {
            mutableState.value = try {
                val result = repository.saveRegistration(current.event.id, status)
                currentCoroutineContext().ensureActive()
                current.copy(savedStatus = result.status, saveError = null)
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                val apiError = error as? RegistrationApiException
                val reason = when {
                    apiError?.statusCode == 401 -> EventRegistrationError.SignInRequired
                    apiError?.errorCode == "event_finished" -> EventRegistrationError.Closed
                    apiError?.errorCode == "event_is_paid" -> EventRegistrationError.Paid
                    apiError?.statusCode == 404 -> EventRegistrationError.Unavailable
                    else -> EventRegistrationError.Failed
                }
                current.copy(saveError = reason)
            }
        }
    }

    fun setUser(userId: String?) {
        if (this.userId == userId) return
        this.userId = userId
        saveJob?.cancel()
        val current = state.value as? EventDetailContent.Loaded ?: return
        mutableState.value = EventDetailContent.Loaded(current.event)
    }

    fun clearRegistrationFeedback() {
        val current = state.value as? EventDetailContent.Loaded ?: return
        mutableState.value = current.copy(savedStatus = null, saveError = null)
    }
}
