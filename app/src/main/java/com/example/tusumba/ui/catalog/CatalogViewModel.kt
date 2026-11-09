package com.example.tusumba.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tusumba.data.catalog.CatalogEvent
import com.example.tusumba.data.catalog.CatalogFilters
import com.example.tusumba.data.catalog.EventsRepository
import com.example.tusumba.data.catalog.filteredBy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CatalogViewModel @Inject constructor(private val repository: EventsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CatalogUiState())
    val state = mutableState.asStateFlow()

    private var loadedEvents: List<CatalogEvent>? = null
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadedEvents = null
        mutableState.update { it.copy(content = CatalogContent.Loading) }
        loadJob =
            viewModelScope.launch {
                try {
                    val events = repository.getEvents()
                    currentCoroutineContext().ensureActive()
                    loadedEvents = events
                    mutableState.update { it.copy(content = contentFor(events, it.filters)) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    mutableState.update { it.copy(content = CatalogContent.Error) }
                }
            }
    }

    fun setQuery(query: String) = updateFilters(state.value.filters.copy(query = query))

    fun setIsFree(isFree: Boolean?) = updateFilters(state.value.filters.copy(isFree = isFree))

    fun resetFilters() = updateFilters(CatalogFilters())

    private fun updateFilters(filters: CatalogFilters) {
        mutableState.update { current ->
            current.copy(
                filters = filters,
                content = loadedEvents?.let { contentFor(it, filters) } ?: current.content,
            )
        }
    }

    private fun contentFor(events: List<CatalogEvent>, filters: CatalogFilters): CatalogContent {
        val visibleEvents = events.filteredBy(filters)
        return if (visibleEvents.isEmpty()) CatalogContent.Empty
        else CatalogContent.Events(visibleEvents)
    }
}
