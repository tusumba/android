package com.example.tusumba.ui.registrations

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tusumba.R
import com.example.tusumba.data.ticketing.model.RegistrationSource
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import com.example.tusumba.ui.catalog.CatalogTheme
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val EventDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

@Composable
fun MyRegistrationsScreen(
    onBackClick: () -> Unit,
    onEventClick: (String) -> Unit,
    onSignIn: () -> Unit,
    userId: String?,
    viewModel: MyRegistrationsViewModel = hiltViewModel(),
) {
    val session by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, userId) { viewModel.setAccount(userId) }
    LifecycleResumeEffect(viewModel, userId) {
        viewModel.setAccount(userId)
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val content = when {
        userId == null -> MyRegistrationsState.SignInRequired
        session.userId != userId -> MyRegistrationsState.Loading
        else -> session.content
    }
    key(userId) {
        MyRegistrationsScreenContent(
            state = content,
            onBackClick = onBackClick,
            onEventClick = onEventClick,
            onSignIn = onSignIn,
            onRetry = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onCancel = viewModel::cancelRegistration,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyRegistrationsScreenContent(
    state: MyRegistrationsState,
    onBackClick: () -> Unit,
    onEventClick: (String) -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onCancel: (UUID) -> Unit,
) {
    var cancellationCandidate by rememberSaveable { mutableStateOf<String?>(null) }
    val today = todayForRegistrations()
    CatalogTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.my_registrations_title), Modifier.semantics { heading() }) },
                    navigationIcon = { TextButton(onBackClick) { Text(stringResource(R.string.auth_back)) } },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (state) {
                    MyRegistrationsState.Loading -> item { RegistrationsLoading() }
                    MyRegistrationsState.Empty -> item { RegistrationsFeedback(R.string.my_registrations_empty) }
                    MyRegistrationsState.Error -> item {
                        RegistrationsFeedback(R.string.my_registrations_error, R.string.my_registrations_retry, onRetry)
                    }
                    MyRegistrationsState.SignInRequired -> item {
                        RegistrationsFeedback(R.string.my_registrations_sign_in, R.string.auth_sign_in, onSignIn)
                    }
                    is MyRegistrationsState.Data -> {
                        items(state.rows, key = { it.registration.id }) { row ->
                            val eventId = row.registration.eventId
                            RegistrationCard(
                                row = row,
                                canCancel = row.canCancel(today),
                                cancellationEnabled = state.cancellingEventId == null && !state.loadingMore,
                                cancelling = state.cancellingEventId == eventId,
                                failure = state.cancelFailure?.takeIf { it.eventId == eventId }?.error,
                                onDetails = { onEventClick(eventId.toString()) },
                                onCancel = { cancellationCandidate = eventId.toString() },
                            )
                        }
                        item(key = "registration_pages") {
                            when {
                                state.loadingMore -> RegistrationsLoading()
                                state.loadMoreFailed -> RegistrationsFeedback(
                                    R.string.my_registrations_error, R.string.my_registrations_retry, onLoadMore,
                                )
                                state.nextPage != null -> OutlinedButton(
                                    onClick = onLoadMore,
                                    enabled = state.cancellingEventId == null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.my_registrations_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }

        val candidate = (state as? MyRegistrationsState.Data)?.rows?.firstOrNull { row ->
            row.registration.eventId.toString() == cancellationCandidate && row.canCancel(today)
        }
        if (candidate != null) {
            val eventTitle = candidate.event?.title
            AlertDialog(
                onDismissRequest = { cancellationCandidate = null },
                title = { Text(stringResource(R.string.my_registrations_cancel_confirmation)) },
                text = if (eventTitle == null) null else { { Text(eventTitle) } },
                confirmButton = {
                    Button(onClick = {
                        cancellationCandidate = null
                        onCancel(candidate.registration.eventId)
                    }) {
                        Text(stringResource(R.string.my_registrations_cancel))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { cancellationCandidate = null }) {
                        Text(stringResource(R.string.my_registrations_keep))
                    }
                },
            )
        }
    }
}

@Composable
private fun RegistrationCard(
    row: MyRegistrationRow,
    canCancel: Boolean,
    cancellationEnabled: Boolean,
    cancelling: Boolean,
    failure: RegistrationCancelError?,
    onDetails: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val status = when (row.registration.status) {
                RegistrationStatus.going -> R.string.event_registration_going
                RegistrationStatus.interested -> R.string.event_registration_interested
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RegistrationBadge(status)
                if (row.registration.source == RegistrationSource.ticket) {
                    RegistrationBadge(R.string.my_registrations_ticket)
                }
            }
            Text(
                text = row.event?.title ?: stringResource(R.string.my_registrations_event_fallback),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            if (row.event != null) {
                Text(row.event.date.format(EventDateFormat), color = colors.onSurfaceVariant)
                Text(row.event.placeAddress, color = colors.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.my_registrations_event_unavailable), color = colors.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.my_registrations_details), textAlign = TextAlign.Center)
                }
                if (canCancel) {
                    TextButton(onClick = onCancel, enabled = cancellationEnabled, modifier = Modifier.weight(1f)) {
                        val label = if (cancelling) R.string.my_registrations_cancelling else R.string.my_registrations_cancel
                        Text(stringResource(label), textAlign = TextAlign.Center)
                    }
                }
            }
            if (failure != null) {
                val message = when (failure) {
                    RegistrationCancelError.Closed -> R.string.my_registrations_cancel_closed
                    RegistrationCancelError.Ticket -> R.string.my_registrations_cancel_ticket
                    RegistrationCancelError.Generic -> R.string.my_registrations_cancel_error
                }
                Text(
                    text = stringResource(message),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = colors.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RegistrationBadge(@StringRes label: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = stringResource(label),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun RegistrationsLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun RegistrationsFeedback(
    @StringRes message: Int,
    @StringRes actionLabel: Int = R.string.my_registrations_retry,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(message), textAlign = TextAlign.Center)
        if (onAction != null) {
            OutlinedButton(onClick = onAction) { Text(stringResource(actionLabel)) }
        }
    }
}
