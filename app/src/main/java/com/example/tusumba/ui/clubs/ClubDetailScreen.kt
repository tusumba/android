package com.example.tusumba.ui.clubs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tusumba.R
import com.example.tusumba.data.catalog.CatalogEvent
import com.example.tusumba.data.catalog.EventStatus
import com.example.tusumba.data.catalog.Money
import com.example.tusumba.data.events.model.EventCard
import com.example.tusumba.ui.catalog.CatalogEventCard
import com.example.tusumba.ui.catalog.CatalogTheme
import java.util.Locale

@Composable
fun ClubDetailScreen(
    onBackClick: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: ClubDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ClubDetailScreenContent(
        state = state,
        onBackClick = onBackClick,
        onEventClick = onEventClick,
        onRetry = viewModel::refresh,
        onRetryEvents = viewModel::retryEvents,
        onLoadMoreEvents = viewModel::loadMoreEvents,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClubDetailScreenContent(
    state: ClubDetailContent,
    onBackClick: () -> Unit,
    onEventClick: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryEvents: () -> Unit,
    onLoadMoreEvents: () -> Unit,
) {
    CatalogTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.club_page)) },
                    navigationIcon = {
                        TextButton(onClick = onBackClick) { Text(stringResource(R.string.clubs_back)) }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("club_detail"),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state) {
                    ClubDetailContent.Loading -> item { ClubsLoading() }
                    ClubDetailContent.Unavailable -> item {
                        ClubsFeedback(R.string.club_unavailable, onBackClick, R.string.clubs_back_to_catalog)
                    }
                    ClubDetailContent.Error -> item { ClubsFeedback(R.string.club_page_error, onRetry) }
                    is ClubDetailContent.Loaded -> {
                        val club = state.club
                        item(key = "title") {
                            Text(
                                text = club.name,
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        item(key = "description") {
                            ClubInformation(stringResource(R.string.club_about), club.description)
                        }
                        item(key = "schedule") {
                            ClubInformation(stringResource(R.string.club_schedule), club.schedule)
                        }
                        item(key = "place") {
                            ClubInformation(stringResource(R.string.club_place), club.meetingPlace)
                        }
                        item(key = "contacts") {
                            ClubInformation(
                                stringResource(R.string.club_contacts),
                                club.contacts?.entries?.joinToString("\n") { "${it.key} — ${it.value}" },
                            )
                        }
                        item(key = "events_title") {
                            Text(
                                text = stringResource(R.string.club_events),
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        when (val events = state.events) {
                            ClubEventsContent.Loading -> item { ClubsLoading() }
                            ClubEventsContent.Error -> item {
                                ClubsFeedback(R.string.club_events_error, onRetryEvents)
                            }
                            ClubEventsContent.Empty -> item {
                                ClubsFeedback(R.string.club_events_empty)
                            }
                            is ClubEventsContent.Data -> {
                                items(events.events, key = { it.id }) { event ->
                                    CatalogEventCard(event.toCatalogEvent(), onEventClick)
                                }
                                item(key = "events_pagination") {
                                    ClubsPagination(
                                        hasMore = events.hasMore,
                                        loading = events.loadingMore,
                                        error = events.loadMoreError,
                                        errorMessage = R.string.club_events_error,
                                        onLoadMore = onLoadMoreEvents,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClubInformation(title: String, value: String?) {
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
    SelectionContainer {
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.club_not_specified),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun EventCard.toCatalogEvent() = CatalogEvent(
    id = id.toString(),
    title = title,
    date = date,
    placeAddress = placeAddress,
    isFree = isFree,
    price = price?.let { Money(it.amount.toLong(), it.currency.value) },
    status = EventStatus.valueOf(status.value.uppercase(Locale.ROOT)),
    clubId = clubId?.toString(),
    photoUrl = photoUrl,
)
