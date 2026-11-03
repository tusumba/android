package com.example.tusumba.ui.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.tusumba.data.catalog.EventType
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
internal fun CatalogNavigation(onProfileClick: () -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            NavigationBarItem(
                selected = true,
                onClick = {},
                icon = { Icon(Icons.Outlined.DateRange, null) },
                label = { Text(stringResource(R.string.catalog_nav_events)) },
            )
            // Эти пункты макета не имеют экранов в каркасе и не входят в #18896.
            NavigationBarItem(
                selected = false,
                onClick = {},
                enabled = false,
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
    onQueryChange: (String) -> Unit,
    onTypeChange: (EventType?) -> Unit,
    onResetFilters: () -> Unit,
    onRetry: () -> Unit,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val content = state.content) {
        CatalogContent.Loading -> CatalogLoading(modifier)
        CatalogContent.Error ->
            CatalogFeedback(
                R.string.catalog_error_title,
                R.string.catalog_error_description,
                R.string.catalog_retry,
                Icons.Outlined.Info,
                onRetry,
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("catalog_error"),
            )
        is CatalogContent.Events,
        CatalogContent.Empty -> {
            val events = (content as? CatalogContent.Events)?.events.orEmpty()
            LazyColumn(
                modifier.fillMaxSize().testTag("catalog_list"),
                contentPadding =
                    PaddingValues(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "filters") {
                    CatalogFiltersPanel(state.filters, onQueryChange, onTypeChange)
                }
                item(key = "count") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.catalog_upcoming),
                            Modifier.weight(1f).semantics { heading() },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.catalog_count, events.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
                if (events.isEmpty()) {
                    item(key = "empty") {
                        CatalogFeedback(
                            R.string.catalog_empty_title,
                            R.string.catalog_empty_description,
                            R.string.catalog_reset,
                            Icons.Outlined.Search,
                            onResetFilters,
                            Modifier.testTag("catalog_empty"),
                        )
                    }
                }
                items(events, key = { it.id }) { event -> CatalogEventCard(event, onEventClick) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogFiltersPanel(
    filters: CatalogFilters,
    onQueryChange: (String) -> Unit,
    onTypeChange: (EventType?) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.catalog_kicker),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.catalog_heading),
            Modifier.semantics { heading() },
            fontSize = 25.sp,
            lineHeight = 31.sp,
            letterSpacing = (-0.7).sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.catalog_intro),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = filters.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("catalog_search"),
            placeholder = { Text(stringResource(R.string.catalog_search), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon =
                if (filters.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Outlined.Clear,
                                stringResource(R.string.catalog_clear_search),
                            )
                        }
                    }
                } else null,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            item { CatalogChip(null, filters.type == null, onTypeChange) }
            items(
                listOf(
                    EventType.MUSIC,
                    EventType.SPORT,
                    EventType.CREATIVITY,
                    EventType.EDUCATION,
                    EventType.MEETINGS,
                )
            ) { type ->
                CatalogChip(type, filters.type == type, onTypeChange)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogChip(type: EventType?, selected: Boolean, onTypeChange: (EventType?) -> Unit) {
    FilterChip(
        selected,
        onClick = { onTypeChange(type) },
        label = {
            Text(stringResource(type?.labelResource() ?: R.string.catalog_all), fontSize = 12.sp)
        },
        shape = RoundedCornerShape(100.dp),
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    )
}

@Composable
private fun CatalogEventCard(event: CatalogEvent, onEventClick: (String) -> Unit) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMMM · HH:mm", Locale.forLanguageTag("ru"))
    }
    OutlinedCard(
        Modifier.fillMaxWidth().testTag("event_${event.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        EventCover()
        Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 16.dp, bottom = 17.dp)) {
            Text(
                stringResource(event.type.labelResource()).uppercase(Locale.forLanguageTag("ru")),
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
            EventMetadata(Icons.Outlined.DateRange, event.startsAt.format(formatter))
            EventMetadata(Icons.Outlined.Place, event.venue)
            Spacer(Modifier.height(13.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (event.priceRubles == 0) stringResource(R.string.catalog_free)
                    else stringResource(R.string.catalog_price, event.priceRubles),
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
    LazyColumn(
        modifier.fillMaxSize().testTag("catalog_loading").semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Skeleton(Modifier.fillMaxWidth(0.46f).height(33.dp)) }
        item { Skeleton(Modifier.fillMaxWidth(0.62f).height(13.dp)) }
        items(3) {
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

private fun EventType.labelResource() =
    when (this) {
        EventType.MUSIC -> R.string.catalog_music
        EventType.SPORT -> R.string.catalog_sport
        EventType.CREATIVITY -> R.string.catalog_creativity
        EventType.EDUCATION -> R.string.catalog_education
        EventType.MEETINGS -> R.string.catalog_meetings
        EventType.WALKS -> R.string.catalog_walks
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
