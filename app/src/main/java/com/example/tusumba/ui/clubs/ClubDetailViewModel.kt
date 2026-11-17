package com.example.tusumba.ui.clubs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tusumba.data.clubs.ClubsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ClubDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ClubsRepository,
) : ViewModel() {
    private val clubId = savedStateHandle.get<String>("clubId")?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    }
    private val mutableState = MutableStateFlow<ClubDetailContent>(ClubDetailContent.Loading)
    val state = mutableState.asStateFlow()

    private var detailJob: Job? = null
    private var eventsJob: Job? = null
    private var nextEventsPage = 1

    init {
        refresh()
    }

    fun refresh() {
        detailJob?.cancel()
        eventsJob?.cancel()
        if (clubId == null) {
            mutableState.value = ClubDetailContent.Unavailable
            return
        }
        mutableState.value = ClubDetailContent.Loading
        detailJob = viewModelScope.launch {
            try {
                val club = repository.getClub(clubId)
                currentCoroutineContext().ensureActive()
                if (club == null) {
                    mutableState.value = ClubDetailContent.Unavailable
                } else {
                    mutableState.value = ClubDetailContent.Loaded(club, ClubEventsContent.Loading)
                    retryEvents()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = ClubDetailContent.Error
            }
        }
    }

    fun retryEvents() {
        val loaded = state.value as? ClubDetailContent.Loaded ?: return
        eventsJob?.cancel()
        nextEventsPage = 1
        mutableState.value = loaded.copy(events = ClubEventsContent.Loading)
        eventsJob = viewModelScope.launch {
            try {
                val page = repository.getEvents(loaded.club.id, page = 1)
                currentCoroutineContext().ensureActive()
                nextEventsPage = page.page + 1
                val events = page.items.distinctBy { it.id }
                val content = if (events.isEmpty()) ClubEventsContent.Empty else ClubEventsContent.Data(
                    events = events,
                    hasMore = page.items.isNotEmpty() && page.page.toLong() * page.propertySize < page.total,
                )
                mutableState.value = loaded.copy(events = content)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = loaded.copy(events = ClubEventsContent.Error)
            }
        }
    }

    fun loadMoreEvents() {
        val loaded = state.value as? ClubDetailContent.Loaded ?: return
        val content = loaded.events as? ClubEventsContent.Data ?: return
        if (!content.hasMore || content.loadingMore || eventsJob?.isActive == true) return
        val requestedPage = nextEventsPage
        mutableState.value = loaded.copy(events = content.copy(loadingMore = true, loadMoreError = false))
        eventsJob = viewModelScope.launch {
            try {
                val page = repository.getEvents(loaded.club.id, requestedPage)
                currentCoroutineContext().ensureActive()
                nextEventsPage = page.page + 1
                mutableState.value = loaded.copy(events = ClubEventsContent.Data(
                    events = (content.events + page.items).distinctBy { it.id },
                    hasMore = page.items.isNotEmpty() && page.page.toLong() * page.propertySize < page.total,
                ))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = loaded.copy(events = content.copy(loadingMore = false, loadMoreError = true))
            }
        }
    }
}
