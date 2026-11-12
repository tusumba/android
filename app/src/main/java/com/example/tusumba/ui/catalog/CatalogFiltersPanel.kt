package com.example.tusumba.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tusumba.R
import com.example.tusumba.data.catalog.CatalogFilters
import com.example.tusumba.data.catalog.EventStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun CatalogFiltersPanel(
    filters: CatalogFilters,
    onChange: (CatalogFilters) -> Unit,
    onReset: () -> Unit,
) {
    var expanded by rememberSaveable {
        mutableStateOf(
            filters.dateFrom != null ||
                filters.dateTo != null ||
                filters.place.isNotEmpty() ||
                filters.statuses.isNotEmpty()
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.catalog_kicker),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.catalog_heading),
            Modifier.semantics { heading() },
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.catalog_intro),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = filters.query,
            onValueChange = { onChange(filters.copy(query = it)) },
            modifier = Modifier.fillMaxWidth().testTag("catalog_search"),
            placeholder = { Text(stringResource(R.string.catalog_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon =
                if (filters.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onChange(filters.copy(query = "")) }) {
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf(null, true, false)) { isFree ->
                val label =
                    when (isFree) {
                        null -> R.string.catalog_all
                        true -> R.string.catalog_free_events
                        false -> R.string.catalog_paid_events
                    }
                FilterChip(
                    selected = filters.isFree == isFree,
                    onClick = { onChange(filters.copy(isFree = isFree)) },
                    label = { Text(stringResource(label)) },
                    shape = RoundedCornerShape(100.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = { expanded = !expanded },
                Modifier.testTag("catalog_more_filters"),
            ) {
                Text(stringResource(R.string.catalog_more_filters))
            }
            if (filters.isActive) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.catalog_reset_short)) }
            }
        }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogDateFilter(
                    R.string.catalog_date_from,
                    filters.dateFrom,
                    { onChange(filters.copy(dateFrom = it)) },
                    Modifier.weight(1f).testTag("catalog_date_from"),
                    emptyLabel = R.string.catalog_today,
                )
                CatalogDateFilter(
                    R.string.catalog_date_to,
                    filters.dateTo,
                    { onChange(filters.copy(dateTo = it)) },
                    Modifier.weight(1f).testTag("catalog_date_to"),
                )
            }
            OutlinedTextField(
                value = filters.place,
                onValueChange = { onChange(filters.copy(place = it)) },
                label = { Text(stringResource(R.string.catalog_place)) },
                placeholder = { Text(stringResource(R.string.catalog_place_hint)) },
                modifier = Modifier.fillMaxWidth().testTag("catalog_place"),
                singleLine = true,
            )
            Text(
                stringResource(R.string.catalog_status_filter),
                style = MaterialTheme.typography.labelLarge,
            )
            LazyRow(
                Modifier.testTag("catalog_statuses"),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item {
                    FilterChip(
                        selected = filters.statuses.isEmpty(),
                        onClick = { onChange(filters.copy(statuses = emptySet())) },
                        label = { Text(stringResource(R.string.catalog_all_statuses)) },
                    )
                }
                items(EventStatus.entries) { status ->
                    FilterChip(
                        selected = status in filters.statuses,
                        onClick = {
                            val statuses =
                                if (status in filters.statuses) filters.statuses - status
                                else filters.statuses + status
                            onChange(filters.copy(statuses = statuses))
                        },
                        label = { Text(stringResource(status.labelResource())) },
                        modifier = Modifier.testTag("catalog_status_" + status.name),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogDateFilter(
    label: Int,
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    emptyLabel: Int = R.string.catalog_any_date,
) {
    var open by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Column {
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
            Text(date?.format(formatter) ?: stringResource(emptyLabel))
        }
    }
    if (open) {
        val picker =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
            )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        onChange(
                            picker.selectedDateMillis?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            }
                        )
                        open = false
                    },
                ) {
                    Text(stringResource(R.string.catalog_date_choose))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onChange(null)
                            open = false
                        }
                    ) {
                        Text(stringResource(R.string.catalog_date_clear))
                    }
                    TextButton(onClick = { open = false }) {
                        Text(stringResource(R.string.catalog_date_cancel))
                    }
                }
            },
        ) {
            DatePicker(state = picker)
        }
    }
}
