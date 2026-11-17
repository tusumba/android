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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tusumba.R
import com.example.tusumba.data.catalog.CatalogEvent
import com.example.tusumba.data.catalog.CatalogFilters
import com.example.tusumba.data.catalog.EventStatus
import com.example.tusumba.data.catalog.formatRubles
import com.example.tusumba.ui.components.ContentEmpty
import com.example.tusumba.ui.components.ContentError
import com.example.tusumba.ui.components.ContentLoading
import com.example.tusumba.ui.components.ContentStateAction
import com.example.tusumba.ui.components.ContentStateIllustration
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
    onQueryChange: (String) -> Unit,
    onIsFreeChange: (Boolean?) -> Unit,
    onResetFilters: () -> Unit,
    onRetry: () -> Unit,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val content = state.content) {
        CatalogContent.Loading -> CatalogLoading(modifier)
        CatalogContent.Error ->
            ContentError(
                message = R.string.catalog_error_description,
                onRetry = onRetry,
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("catalog_error"),
                illustration = ContentStateIllustration(R.string.catalog_error_title, Icons.Outlined.Info),
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
                    CatalogFiltersPanel(state.filters, onQueryChange, onIsFreeChange)
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
                        ContentEmpty(
                            message = R.string.catalog_empty_description,
                            modifier = Modifier.testTag("catalog_empty"),
                            action = ContentStateAction(R.string.catalog_reset, onResetFilters),
                            illustration = ContentStateIllustration(R.string.catalog_empty_title, Icons.Outlined.Search),
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
    onIsFreeChange: (Boolean?) -> Unit,
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
            items(listOf(null, true, false)) { isFree ->
                CatalogChip(isFree, filters.isFree == isFree, onIsFreeChange)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogChip(
    isFree: Boolean?,
    selected: Boolean,
    onIsFreeChange: (Boolean?) -> Unit,
) {
    val label =
        when (isFree) {
            null -> R.string.catalog_all
            true -> R.string.catalog_free_events
            false -> R.string.catalog_paid_events
        }
    FilterChip(
        selected,
        onClick = { onIsFreeChange(isFree) },
        label = { Text(stringResource(label), fontSize = 12.sp) },
        shape = RoundedCornerShape(100.dp),
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    )
}

@Composable
internal fun CatalogEventCard(event: CatalogEvent, onEventClick: (String) -> Unit) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
    }
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
private fun CatalogLoading(modifier: Modifier = Modifier) {
    ContentLoading(
        modifier = modifier.fillMaxSize().testTag("catalog_loading"),
        label = R.string.catalog_loading,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
}

@Composable
private fun Skeleton(modifier: Modifier) =
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )

private fun EventStatus.labelResource() =
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
