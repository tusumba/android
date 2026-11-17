package com.example.tusumba.ui.clubs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tusumba.R
import com.example.tusumba.ui.catalog.CatalogHeader
import com.example.tusumba.ui.catalog.CatalogNavigation
import com.example.tusumba.ui.catalog.CatalogTheme
import com.example.tusumba.ui.components.ContentEmpty
import com.example.tusumba.ui.components.ContentError
import com.example.tusumba.ui.components.ContentLoading
import com.example.tusumba.ui.components.ContentStateAction

@Composable
fun ClubsScreen(
    onClubClick: (String) -> Unit,
    onEventsClick: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: ClubsCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ClubsScreenContent(
        state = state,
        onQueryChange = viewModel::setQuery,
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onClubClick = onClubClick,
        onEventsClick = onEventsClick,
        onProfileClick = onProfileClick,
    )
}

@Composable
internal fun ClubsScreenContent(
    state: ClubsUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onClubClick: (String) -> Unit,
    onEventsClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    CatalogTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = { CatalogHeader(onProfileClick) },
            bottomBar = {
                CatalogNavigation(
                    onProfileClick = onProfileClick,
                    onClubsClick = {},
                    onEventsClick = onEventsClick,
                    clubsSelected = true,
                )
            },
        ) { padding ->
            val error = if (state.query.isBlank()) R.string.clubs_catalog_error else R.string.clubs_search_error
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("clubs_list"),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "search") {
                    Text(
                        text = stringResource(R.string.clubs_heading),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp).testTag("clubs_search"),
                        label = { Text(stringResource(R.string.clubs_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = if (state.query.isEmpty()) null else {
                            {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Outlined.Clear, stringResource(R.string.clubs_clear_search))
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                    )
                }
                when (val content = state.content) {
                    ClubsContent.Loading -> item(key = "loading") { ContentLoading() }
                    ClubsContent.Error -> item(key = "error") { ContentError(error, onRetry) }
                    ClubsContent.Empty -> item(key = "empty") {
                        if (state.query.isBlank()) {
                            ContentEmpty(R.string.clubs_empty)
                        } else {
                            ContentEmpty(
                                message = R.string.clubs_search_empty,
                                action = ContentStateAction(R.string.clubs_clear_search) { onQueryChange("") },
                            )
                        }
                    }
                    is ClubsContent.Data -> {
                        items(content.clubs, key = { it.id }) { club ->
                            OutlinedCard(
                                onClick = { onClubClick(club.id.toString()) },
                                modifier = Modifier.fillMaxWidth().testTag("club_${club.id}"),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                            ) {
                                Text(
                                    text = club.name,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = club.description,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        item(key = "pagination") {
                            ClubsPagination(
                                hasMore = content.hasMore,
                                loading = content.loadingMore,
                                error = content.loadMoreError,
                                errorMessage = error,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }
            }
        }
    }
}
