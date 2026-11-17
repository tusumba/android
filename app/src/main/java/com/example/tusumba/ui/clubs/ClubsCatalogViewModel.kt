package com.example.tusumba.ui.clubs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tusumba.data.clubs.ClubsRepository
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
class ClubsCatalogViewModel @Inject constructor(private val repository: ClubsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ClubsUiState())
    val state = mutableState.asStateFlow()

    private var loadJob: Job? = null
    private var nextPage = 1

    init {
        refresh()
    }

    fun setQuery(query: String) {
        val queryEnd = query.offsetByCodePoints(0, query.codePointCount(0, query.length).coerceAtMost(100))
        val limitedQuery = query.substring(0, queryEnd)
        if (limitedQuery == state.value.query) return
        mutableState.update { it.copy(query = limitedQuery) }
        loadFirstPage(debounce = true)
    }

    fun refresh() = loadFirstPage(debounce = false)

    private fun loadFirstPage(debounce: Boolean) {
        loadJob?.cancel()
        nextPage = 1
        val query = state.value.query.trim().ifEmpty { null }
        mutableState.update { it.copy(content = ClubsContent.Loading) }
        loadJob = viewModelScope.launch {
            try {
                if (debounce) delay(300)
                val page = repository.getClubs(query, page = 1)
                currentCoroutineContext().ensureActive()
                nextPage = page.page + 1
                val clubs = page.items.distinctBy { it.id }
                val content = if (clubs.isEmpty()) ClubsContent.Empty else ClubsContent.Data(
                    clubs = clubs,
                    hasMore = page.items.isNotEmpty() && page.page.toLong() * page.propertySize < page.total,
                )
                mutableState.update { it.copy(content = content) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(content = ClubsContent.Error) }
            }
        }
    }

    fun loadMore() {
        val content = state.value.content as? ClubsContent.Data ?: return
        if (!content.hasMore || content.loadingMore || loadJob?.isActive == true) return
        val query = state.value.query.trim().ifEmpty { null }
        val requestedPage = nextPage
        mutableState.update {
            it.copy(content = content.copy(loadingMore = true, loadMoreError = false))
        }
        loadJob = viewModelScope.launch {
            try {
                val page = repository.getClubs(query, requestedPage)
                currentCoroutineContext().ensureActive()
                nextPage = page.page + 1
                mutableState.update {
                    it.copy(content = ClubsContent.Data(
                        clubs = (content.clubs + page.items).distinctBy { club -> club.id },
                        hasMore = page.items.isNotEmpty() && page.page.toLong() * page.propertySize < page.total,
                    ))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.update {
                    it.copy(content = content.copy(loadingMore = false, loadMoreError = true))
                }
            }
        }
    }
}
