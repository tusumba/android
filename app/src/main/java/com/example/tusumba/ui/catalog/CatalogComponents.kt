package com.example.tusumba.ui.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tusumba.R
import com.example.tusumba.data.catalog.CatalogEvent
import com.example.tusumba.data.catalog.CatalogFilters
import com.example.tusumba.data.catalog.EventStatus
import com.example.tusumba.data.catalog.formatRubles
import com.example.tusumba.ui.screens.CatalogScreenContent
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CatalogHeader(onProfileClick: () -> Unit) {
    Surface {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.catalog_brand),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                OutlinedButton(onProfileClick, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.catalog_sign_in), fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun CatalogNavigation(
    onProfileClick: () -> Unit,
    onClubsClick: (() -> Unit)? = null,
    onEventsClick: () -> Unit = {},
    clubsSelected: Boolean = false,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            NavigationBarItem(
                selected = !clubsSelected,
                onClick = onEventsClick,
                icon = { Icon(Icons.Outlined.DateRange, null) },
                label = { Text(stringResource(R.string.catalog_nav_events)) },
            )
            NavigationBarItem(
                selected = clubsSelected,
                onClick = { onClubsClick?.invoke() },
                enabled = onClubsClick != null,
                icon = { Icon(Icons.Outlined.Menu, null) },
                label = { Text(stringResource(R.string.catalog_nav_clubs)) },
            )
            NavigationBarItem(
                selected = false,
                onClick = {},
                enabled = false,
                icon = { Icon(Icons.Outlined.FavoriteBorder, null) },
                label = { Text(stringResource(R.string.catalog_nav_friends)) },
            )
            NavigationBarItem(
                selected = false,
                onClick = onProfileClick,
                icon = { Icon(Icons.Outlined.Person, null) },
                label = { Text(stringResource(R.string.catalog_nav_profile)) },
            )
        }
    }
}

@Composable
internal fun CatalogBody(
    state: CatalogUiState,
    onFiltersChange: (CatalogFilters) -> Unit,
    onResetFilters: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state.content
    LazyColumn(
        modifier.fillMaxSize().testTag("catalog_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "filters") {
            CatalogFiltersPanel(state.filters, onFiltersChange, onResetFilters)
        }
        if (content is CatalogContent.Events || content == CatalogContent.Empty) {
            item(key = "count") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.catalog_upcoming),
                        Modifier.weight(1f).semantics { heading() },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(
                            R.string.catalog_count,
                            (content as? CatalogContent.Events)?.total ?: 0,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        when (content) {
            CatalogContent.Loading -> item(key = "loading") { CatalogLoading() }
            CatalogContent.Error ->
                item(key = "error") {
                    CatalogFeedback(
                        if (state.filters.isActive) R.string.catalog_filter_error
                        else R.string.catalog_error_title,
                        R.string.catalog_error_description,
                        R.string.catalog_retry,
                        Icons.Outlined.Info,
                        onRetry,
                        Modifier.testTag("catalog_error"),
                    )
                }
            CatalogContent.Empty ->
                item(key = "empty") {
                    CatalogFeedback(
                        if (state.filters.isActive) R.string.catalog_filtered_empty
                        else R.string.catalog_empty_title,
                        R.string.catalog_empty_description,
                        R.string.catalog_reset,
                        Icons.Outlined.Search,
                        onResetFilters,
                        Modifier.testTag("catalog_empty"),
                    )
                }
            is CatalogContent.InvalidFilters ->
                item(key = "validation") {
                    val message =
                        when (content.error) {
                            com.example.tusumba.data.catalog.CatalogFilterError.DATE_RANGE ->
                                R.string.catalog_invalid_dates
                            com.example.tusumba.data.catalog.CatalogFilterError.QUERY_LENGTH ->
                                R.string.catalog_query_length
                            com.example.tusumba.data.catalog.CatalogFilterError.PLACE_LENGTH ->
                                R.string.catalog_place_length
                        }
                    Text(
                        stringResource(message),
                        Modifier.testTag("catalog_validation_error").semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            is CatalogContent.Events -> {
                items(content.events, key = { it.id }) { event ->
                    CatalogEventCard(event, onEventClick)
                }
                if (content.hasMore) {
                    item(key = "pagination") {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (content.loadMoreError) {
                                Text(
                                    stringResource(R.string.catalog_page_error),
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            OutlinedButton(
                                onClick = onLoadMore,
                                enabled = !content.isLoadingMore,
                                modifier = Modifier.testTag("catalog_load_more"),
                            ) {
                                if (content.isLoadingMore) {
                                    CircularProgressIndicator(
                                        Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    stringResource(
                                        if (content.isLoadingMore) R.string.catalog_loading
                                        else if (content.loadMoreError) R.string.catalog_retry
                                        else R.string.catalog_load_more
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CatalogEventCard(event: CatalogEvent, onEventClick: (String) -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")) }
    OutlinedCard(
        Modifier.fillMaxWidth().testTag("event_${event.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        EventCover()
        Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 16.dp, bottom = 17.dp)) {
            Text(
                stringResource(event.status.labelResource()).uppercase(Locale.forLanguageTag("ru")),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                event.title,
                Modifier.heightIn(min = 40.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            EventMetadata(Icons.Outlined.DateRange, event.date.format(formatter))
            EventMetadata(Icons.Outlined.Place, event.placeAddress)
            Spacer(Modifier.height(13.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (event.isFree || event.price == null) stringResource(R.string.catalog_free)
                    else stringResource(R.string.catalog_price, event.price.formatRubles()),
                    Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton({ onEventClick(event.id) }, shape = RoundedCornerShape(11.dp)) {
                    Text(stringResource(R.string.catalog_details), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EventCover() {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.catalog_cover)
    Box(
        Modifier.fillMaxWidth()
            .height(145.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.surfaceVariant.copy(alpha = 0.35f),
                        colors.surfaceVariant.copy(alpha = 0.65f),
                    )
                )
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(32.dp)) {
            drawRect(
                colors.outline,
                Offset(2f, 2f),
                Size(size.width - 4f, size.height - 4f),
                style = Stroke(2f),
            )
            drawPath(
                Path().apply {
                    moveTo(3f, size.height * 0.75f)
                    lineTo(size.width * 0.4f, size.height * 0.4f)
                    lineTo(size.width * 0.65f, size.height * 0.65f)
                    lineTo(size.width * 0.8f, size.height * 0.5f)
                    lineTo(size.width - 3f, size.height * 0.75f)
                },
                colors.outline,
                style = Stroke(2f),
            )
        }
        Surface(
            Modifier.align(Alignment.TopStart).padding(12.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                stringResource(R.string.catalog_event_tag),
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EventMetadata(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 21.sp,
        )
    }
}

@Composable
private fun CatalogFeedback(
    title: Int,
    description: Int,
    action: Int,
    icon: ImageVector,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 48.dp).semantics {
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(icon, null, Modifier.padding(20.dp).size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(title),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Button(onAction, shape = RoundedCornerShape(11.dp)) { Text(stringResource(action)) }
    }
}

@Composable
private fun CatalogLoading(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.catalog_loading)
    Column(
        modifier.fillMaxWidth().testTag("catalog_loading").semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(3) {
            OutlinedCard(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(126.dp))
                    Skeleton(Modifier.fillMaxWidth(0.8f).height(15.dp))
                    Skeleton(Modifier.fillMaxWidth(0.55f).height(13.dp))
                    Skeleton(Modifier.fillMaxWidth(0.95f).height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun Skeleton(modifier: Modifier) =
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )

internal fun EventStatus.labelResource() =
    when (this) {
        EventStatus.NOT_CONFIRMED -> R.string.catalog_status_not_confirmed
        EventStatus.CONFIRMED -> R.string.catalog_status_confirmed
        EventStatus.HELD -> R.string.catalog_status_held
        EventStatus.NOT_HELD -> R.string.catalog_status_not_held
    }

@Preview(name = "Загрузка", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LoadingPreview() = StatePreview(CatalogContent.Loading)

@Preview(name = "Пустой список", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun EmptyPreview() = StatePreview(CatalogContent.Empty)

@Preview(name = "Ошибка", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ErrorPreview() = StatePreview(CatalogContent.Error)

@Composable
private fun StatePreview(content: CatalogContent) {
    CatalogScreenContent(CatalogUiState(content = content), {}, {}, {}, {}, {}, {})
}
