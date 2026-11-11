package com.example.tusumba.ui.registrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tusumba.data.events.model.EventDetail
import com.example.tusumba.data.registrations.RegistrationApiException
import com.example.tusumba.data.registrations.RegistrationsRepository
import com.example.tusumba.data.ticketing.model.Registration
import com.example.tusumba.data.ticketing.model.RegistrationSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val RegistrationTimeZone = ZoneId.of("Asia/Novosibirsk")

internal fun todayForRegistrations(): LocalDate = LocalDate.now(RegistrationTimeZone)

internal data class MyRegistrationRow(val registration: Registration, val event: EventDetail?) {
    fun canCancel(today: LocalDate): Boolean =
        registration.source != RegistrationSource.ticket && event?.date?.isBefore(today) == false
}

internal enum class RegistrationCancelError { Closed, Ticket, Generic }

internal data class RegistrationCancelFailure(val eventId: UUID, val error: RegistrationCancelError)

internal data class MyRegistrationsSession(
    val userId: String? = null,
    val content: MyRegistrationsState = MyRegistrationsState.SignInRequired,
)

internal sealed interface MyRegistrationsState {
    data object Loading : MyRegistrationsState
    data object Empty : MyRegistrationsState
    data object Error : MyRegistrationsState
    data object SignInRequired : MyRegistrationsState

    data class Data(
        val rows: List<MyRegistrationRow>,
        val nextPage: Int?,
        val loadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
        val cancellingEventId: UUID? = null,
        val cancelFailure: RegistrationCancelFailure? = null,
    ) : MyRegistrationsState
}

@HiltViewModel
class MyRegistrationsViewModel @Inject constructor(
    private val repository: RegistrationsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MyRegistrationsSession())
    internal val state = mutableState.asStateFlow()
    private val loadedData get() = state.value.content as? MyRegistrationsState.Data

    private var request: Job? = null
    private var lastLoadedPage = 1
    private val metadataRequests = mutableMapOf<UUID, Job>()
    private val metadataPermits = Semaphore(3)

    fun refresh() {
        val session = state.value
        if (session.userId == null || loadedData?.cancellingEventId != null) return
        request?.cancel()
        cancelMetadata()
        lastLoadedPage = 1
        show(MyRegistrationsState.Loading)
        request = viewModelScope.launch { loadPage(1) }
    }

    fun setAccount(userId: String?) {
        if (state.value.userId == userId) return
        request?.cancel()
        cancelMetadata()
        request = null
        lastLoadedPage = 1
        val content = if (userId == null) MyRegistrationsState.SignInRequired else MyRegistrationsState.Loading
        mutableState.value = MyRegistrationsSession(userId, content)
    }

    fun loadMore() {
        val data = loadedData ?: return
        val page = data.nextPage ?: return
        if (request?.isActive == true) return
        show(data.copy(loadingMore = true, loadMoreFailed = false))
        request = viewModelScope.launch { loadPage(page) }
    }

    fun cancelRegistration(eventId: UUID) {
        val data = loadedData ?: return
        if (request?.isActive == true) return
        val row = data.rows.firstOrNull { it.registration.eventId == eventId } ?: return
        if (!row.canCancel(todayForRegistrations())) return
        show(data.copy(cancellingEventId = eventId, cancelFailure = null))
        request = viewModelScope.launch {
            try {
                repository.cancelRegistration(eventId)
                currentCoroutineContext().ensureActive()
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                val current = loadedData ?: return@launch
                val failure = error as? RegistrationApiException
                val content = if (failure?.statusCode == 401) {
                    MyRegistrationsState.SignInRequired
                } else {
                    val reason = when (failure?.errorCode) {
                        "event_finished" -> RegistrationCancelError.Closed
                        "registration_from_ticket" -> RegistrationCancelError.Ticket
                        else -> RegistrationCancelError.Generic
                    }
                    current.copy(
                        cancellingEventId = null,
                        cancelFailure = RegistrationCancelFailure(eventId, reason),
                    )
                }
                show(content)
                return@launch
            }

            val current = loadedData ?: return@launch
            metadataRequests.remove(eventId)?.cancel()
            val remaining = current.rows.filterNot { it.registration.eventId == eventId }
            if (current.nextPage == null) {
                show(if (remaining.isEmpty()) MyRegistrationsState.Empty
                    else MyRegistrationsState.Data(remaining, nextPage = null))
                return@launch
            }

            // Deletion shifts offsets. Fill the last loaded page before advancing to the next one.
            show(MyRegistrationsState.Data(remaining, nextPage = lastLoadedPage, loadingMore = true))
            loadPage(lastLoadedPage)
        }
    }

    private suspend fun loadPage(pageNumber: Int) {
        try {
            val page = repository.getRegistrations(pageNumber)
            currentCoroutineContext().ensureActive()
            val previousRows = loadedData?.rows.orEmpty()
            val knownEvents = previousRows.mapNotNull { it.event }.associateBy { it.id }
            val incomingRows = page.items.map { MyRegistrationRow(it, knownEvents[it.eventId]) }
            val merged = (previousRows + incomingRows).associateBy { it.registration.id }.values.toList()
            lastLoadedPage = page.page
            val hasMore = page.items.isNotEmpty() && page.page.toLong() * page.propertySize < page.total
            show(if (merged.isEmpty()) MyRegistrationsState.Empty
                else MyRegistrationsState.Data(merged, nextPage = if (hasMore) page.page + 1 else null))
            loadMetadata(incomingRows)
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            val content = if ((error as? RegistrationApiException)?.statusCode == 401) {
                MyRegistrationsState.SignInRequired
            } else {
                loadedData?.copy(loadingMore = false, loadMoreFailed = true) ?: MyRegistrationsState.Error
            }
            show(content)
        }
    }

    private fun show(content: MyRegistrationsState) {
        if (content == MyRegistrationsState.SignInRequired) cancelMetadata()
        mutableState.value = state.value.copy(content = content)
    }

    private fun loadMetadata(rows: List<MyRegistrationRow>) {
        rows.forEach { row ->
            val eventId = row.registration.eventId
            if (row.event != null || eventId in metadataRequests) return@forEach
            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val event = metadataPermits.withPermit { repository.getEvent(eventId) }
                    currentCoroutineContext().ensureActive()
                    val data = loadedData ?: return@launch
                    show(data.copy(rows = data.rows.map {
                        if (it.registration.eventId == eventId) it.copy(event = event) else it
                    }))
                } catch (_: IOException) {
                    // A registration remains visible and can still open its event page.
                } finally {
                    if (metadataRequests[eventId] === currentCoroutineContext()[Job]) {
                        metadataRequests.remove(eventId)
                    }
                }
            }
            metadataRequests[eventId] = job
            job.start()
        }
    }

    private fun cancelMetadata() {
        val jobs = metadataRequests.values.toList()
        metadataRequests.clear()
        jobs.forEach { it.cancel() }
    }
}
