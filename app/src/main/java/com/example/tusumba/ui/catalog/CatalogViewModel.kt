package com.example.tusumba.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tusumba.data.catalog.CatalogFilters
import com.example.tusumba.data.catalog.EventsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CatalogViewModel @Inject constructor(private val repository: EventsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CatalogUiState())
    val state = mutableState.asStateFlow()
    private var requestJob: Job? = null

    init {
        refresh()
    }

    fun setFilters(filters: CatalogFilters) {
        val previous = state.value.filters
        if (previous == filters) return
        mutableState.update { it.copy(filters = filters) }
        val textChanged = previous.query != filters.query || previous.place != filters.place
        requestFirstPage(if (textChanged) 300 else 0)
    }

    fun resetFilters() {
        mutableState.update { it.copy(filters = CatalogFilters()) }
        refresh()
    }

    fun refresh() = requestFirstPage()

    private fun requestFirstPage(debounceMillis: Long = 0) {
        requestJob?.cancel()
        val filters = state.value.filters
        val error = filters.validationError
        mutableState.update {
            it.copy(
                content =
                    error?.let { value -> CatalogContent.InvalidFilters(value) }
                        ?: CatalogContent.Loading
            )
        }
        if (error != null) return
        requestJob =
            viewModelScope.launch {
                try {
                    delay(debounceMillis)
                    val result = repository.getEvents(filters, page = 1)
                    currentCoroutineContext().ensureActive()
                    mutableState.update {
                        it.copy(
                            content =
                                if (result.items.isEmpty()) CatalogContent.Empty
                                else {
                                    CatalogContent.Events(
                                        events = result.items.distinctBy { event -> event.id },
                                        total = result.total,
                                        page = result.page,
                                        hasMore = result.hasMore,
                                    )
                                }
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    mutableState.update { it.copy(content = CatalogContent.Error) }
                }
            }
    }

    fun loadMore() {
        val content = state.value.content as? CatalogContent.Events ?: return
        if (!content.hasMore || content.isLoadingMore) return
        val filters = state.value.filters
        mutableState.update {
            it.copy(content = content.copy(isLoadingMore = true, loadMoreError = false))
        }
        requestJob =
            viewModelScope.launch {
                try {
                    val result = repository.getEvents(filters, page = content.page + 1)
                    currentCoroutineContext().ensureActive()
                    mutableState.update {
                        it.copy(
                            content =
                                CatalogContent.Events(
                                    events =
                                        (content.events + result.items).distinctBy { event ->
                                            event.id
                                        },
                                    total = result.total,
                                    page = result.page,
                                    hasMore = result.hasMore,
                                )
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    mutableState.update {
                        it.copy(content = content.copy(isLoadingMore = false, loadMoreError = true))
                    }
                }
            }
    }
}
