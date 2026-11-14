package com.example.tusumba.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tusumba.R
import com.example.tusumba.data.catalog.Money
import com.example.tusumba.data.catalog.formatRubles
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import com.example.tusumba.ui.catalog.CatalogTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EventDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    userId: String?,
    onBackClick: () -> Unit,
    onSignIn: () -> Unit,
    onMyRegistrations: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(userId) { viewModel.setUser(userId) }
    CatalogTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.event_detail_title)) },
                    navigationIcon = {
                        TextButton(onClick = onBackClick) { Text(stringResource(R.string.auth_back)) }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (val content = state) {
                    EventDetailContent.Loading -> CircularProgressIndicator()
                    EventDetailContent.Unavailable -> Text(stringResource(R.string.event_detail_unavailable))
                    EventDetailContent.Error -> {
                        Text(stringResource(R.string.event_detail_error))
                        OutlinedButton(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.event_detail_retry))
                        }
                    }
                    is EventDetailContent.Loaded -> {
                        val event = content.event
                        Text(
                            text = event.title,
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(event.description, style = MaterialTheme.typography.bodyLarge)
                        Text(event.date.format(EventDateFormat))
                        Text(event.placeAddress)
                        Text(
                            if (event.isFree) stringResource(R.string.catalog_free)
                            else event.price?.let {
                                stringResource(R.string.catalog_price, Money(it.amount.toLong()).formatRubles())
                            } ?: stringResource(R.string.event_detail_paid)
                        )
                        EventRegistrationControls(
                            state = content,
                            isAuthenticated = userId != null,
                            onSave = viewModel::saveRegistration,
                            onSignIn = {
                                viewModel.clearRegistrationFeedback()
                                onSignIn()
                            },
                            onMyRegistrations = {
                                viewModel.clearRegistrationFeedback()
                                onMyRegistrations()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRegistrationControls(
    state: EventDetailContent.Loaded,
    isAuthenticated: Boolean,
    onSave: (RegistrationStatus) -> Unit,
    onSignIn: () -> Unit,
    onMyRegistrations: () -> Unit,
) {
    when {
        !state.event.isFree || state.saveError == EventRegistrationError.Paid ->
            Text(stringResource(R.string.event_registration_paid))
        state.event.registrationIsClosed() || state.saveError == EventRegistrationError.Closed ->
            Text(stringResource(R.string.event_registration_closed))
        state.saveError == EventRegistrationError.Unavailable ->
            Text(stringResource(R.string.event_detail_unavailable))
        !isAuthenticated || state.saveError == EventRegistrationError.SignInRequired -> {
            Text(stringResource(R.string.event_registration_sign_in_required))
            Button(onClick = onSignIn) { Text(stringResource(R.string.auth_sign_in)) }
        }
        else -> {
            if (state.saveError == EventRegistrationError.Failed) {
                Text(
                    text = stringResource(R.string.event_registration_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(onClick = { onSave(RegistrationStatus.going) }, enabled = !state.saving) {
                Text(stringResource(R.string.event_registration_going))
            }
            OutlinedButton(onClick = { onSave(RegistrationStatus.interested) }, enabled = !state.saving) {
                Text(stringResource(R.string.event_registration_interested))
            }
            if (state.saving) CircularProgressIndicator()
            state.savedStatus?.let { status ->
                Text(
                    text = stringResource(
                        R.string.event_registration_saved,
                        stringResource(status.label()),
                    ),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
    if (isAuthenticated && !state.saving) {
        TextButton(onClick = onMyRegistrations) {
            Text(stringResource(R.string.my_registrations_title))
        }
    }
}

@StringRes
private fun RegistrationStatus.label(): Int = when (this) {
    RegistrationStatus.going -> R.string.event_registration_going
    RegistrationStatus.interested -> R.string.event_registration_interested
}
