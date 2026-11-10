package com.example.tusumba.ui.clubs

import com.example.tusumba.data.events.model.ClubCard
import com.example.tusumba.data.events.model.ClubDetail
import com.example.tusumba.data.events.model.EventCard

data class ClubsUiState(
    val query: String = "",
    val content: ClubsContent = ClubsContent.Loading,
)

sealed interface ClubsContent {
    data object Loading : ClubsContent
    data object Error : ClubsContent
    data object Empty : ClubsContent

    data class Data(
        val clubs: List<ClubCard>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val loadMoreError: Boolean = false,
    ) : ClubsContent
}

sealed interface ClubDetailContent {
    data object Loading : ClubDetailContent
    data object Unavailable : ClubDetailContent
    data object Error : ClubDetailContent

    data class Loaded(val club: ClubDetail, val events: ClubEventsContent) : ClubDetailContent
}

sealed interface ClubEventsContent {
    data object Loading : ClubEventsContent
    data object Error : ClubEventsContent
    data object Empty : ClubEventsContent

    data class Data(
        val events: List<EventCard>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val loadMoreError: Boolean = false,
    ) : ClubEventsContent
}
