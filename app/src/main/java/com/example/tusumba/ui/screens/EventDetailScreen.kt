package com.example.tusumba.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tusumba.R
import com.example.tusumba.data.catalog.Money
import com.example.tusumba.data.catalog.formatRubles
import com.example.tusumba.data.events.model.Organizer
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import com.example.tusumba.data.ticketing.model.Visibility
import com.example.tusumba.ui.catalog.CatalogTheme
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val EventDateFormat =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

@Composable
fun EventDetailScreen(
    userId: String?,
    onBackClick: () -> Unit,
    onSignIn: () -> Unit,
    onMyRegistrations: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel, userId) {
        viewModel.setUser(userId)
        viewModel.refreshRegistration()
        onPauseOrDispose {}
    }
    val visibleState =
        (state as? EventDetailContent.Loaded)?.let {
            if (it.accountId == userId) it
            else
                EventDetailContent.Loaded(
                    it.event,
                    accountId = userId,
                    organizerName = it.organizerName,
                    loadingRegistration = userId != null,
                )
        } ?: state
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val mapsError = stringResource(R.string.event_map_unavailable)
    EventDetailScreenContent(
        state = visibleState,
        isAuthenticated = userId != null,
        onBackClick = onBackClick,
        onSignIn = onSignIn,
        onMyRegistrations = onMyRegistrations,
        onRetry = viewModel::refresh,
        onRetryRegistration = viewModel::refreshRegistration,
        onSave = viewModel::saveRegistration,
        onVisibilityChange = viewModel::selectVisibility,
        onOpenMap = { address ->
            if (!openEventMap(context, address)) scope.launch { snackbar.showSnackbar(mapsError) }
        },
        snackbar = snackbar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventDetailScreenContent(
    state: EventDetailContent,
    isAuthenticated: Boolean,
    onBackClick: () -> Unit,
    onSignIn: () -> Unit,
    onMyRegistrations: () -> Unit,
    onRetry: () -> Unit,
    onRetryRegistration: () -> Unit,
    onSave: (RegistrationStatus) -> Unit,
    onVisibilityChange: (Visibility) -> Unit,
    onOpenMap: (String) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    CatalogTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.event_detail_title)) },
                    navigationIcon = {
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.auth_back))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                        .testTag("event_detail_content"),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (state) {
                    EventDetailContent.Loading -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.event_detail_loading))
                    }
                    EventDetailContent.Unavailable ->
                        Text(stringResource(R.string.event_detail_unavailable))
                    EventDetailContent.Error -> {
                        Text(stringResource(R.string.event_detail_error))
                        OutlinedButton(onClick = onRetry) {
                            Text(stringResource(R.string.event_detail_retry))
                        }
                    }
                    is EventDetailContent.Loaded -> {
                        val event = state.event
                        Text(
                            event.title,
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                event.date.format(EventDateFormat),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (event.isFree) stringResource(R.string.catalog_free)
                                else
                                    event.price?.let {
                                        stringResource(
                                            R.string.catalog_price,
                                            Money(it.amount.toLong(), it.currency.value)
                                                .formatRubles(),
                                        )
                                    } ?: stringResource(R.string.event_detail_paid),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val organizer =
                                state.organizerName
                                    ?: stringResource(
                                        if (event.organizer.type == Organizer.Type.club)
                                            R.string.event_organizer_club
                                        else R.string.event_organizer_user
                                    )
                            Text(stringResource(R.string.event_organizer, organizer))
                        }
                        HorizontalDivider()
                        Text(event.description, style = MaterialTheme.typography.bodyLarge)
                        OutlinedCard(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.event_place),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(event.placeAddress)
                                OutlinedButton(
                                    onClick = { onOpenMap(event.placeAddress) },
                                    enabled = event.placeAddress.isNotBlank(),
                                    modifier = Modifier.testTag("event_open_map"),
                                ) {
                                    Text(stringResource(R.string.event_show_map))
                                }
                            }
                        }
                        EventRegistrationControls(
                            state,
                            isAuthenticated,
                            onSave,
                            onVisibilityChange,
                            onRetryRegistration,
                            onSignIn,
                        )
                        if (isAuthenticated && state.friends.isNotEmpty()) {
                            Text(
                                stringResource(R.string.event_friends),
                                Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            state.friends.forEach { friend ->
                                Text(
                                    stringResource(
                                        R.string.event_friend_status,
                                        friend.nickname,
                                        stringResource(friend.status.label()),
                                    )
                                )
                            }
                        }
                        if (isAuthenticated) {
                            TextButton(onClick = onMyRegistrations, enabled = !state.saving) {
                                Text(stringResource(R.string.my_registrations_title))
                            }
                        }
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
    onVisibilityChange: (Visibility) -> Unit,
    onRetryRegistration: () -> Unit,
    onSignIn: () -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.event_participation),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            when {
                !state.event.isFree || state.saveError == EventRegistrationError.Paid -> {
                    Button(onClick = {}, enabled = false) {
                        Text(stringResource(R.string.event_buy_ticket))
                    }
                    Text(stringResource(R.string.event_purchase_unavailable))
                }
                state.saveError == EventRegistrationError.Unavailable ->
                    Text(stringResource(R.string.event_detail_unavailable))
                state.event.registrationIsClosed() ||
                    state.saveError == EventRegistrationError.Closed ->
                    Text(stringResource(R.string.event_registration_closed))
                !isAuthenticated || state.saveError == EventRegistrationError.SignInRequired -> {
                    Text(stringResource(R.string.event_registration_sign_in_required))
                    Button(onClick = onSignIn) { Text(stringResource(R.string.auth_sign_in)) }
                }
                state.loadingRegistration -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.event_registration_loading))
                }
                state.registrationLoadFailed -> {
                    Text(stringResource(R.string.event_registration_load_error))
                    OutlinedButton(onClick = onRetryRegistration) {
                        Text(stringResource(R.string.event_detail_retry))
                    }
                }
                else -> {
                    state.registration?.let { registration ->
                        Text(stringResource(R.string.event_registration_exists))
                        Text(
                            stringResource(
                                R.string.event_registration_current,
                                stringResource(registration.status.label()),
                                stringResource(registration.visibility.label()),
                            )
                        )
                        Text(stringResource(R.string.event_registration_change))
                    }
                    Text(
                        stringResource(R.string.event_visibility),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Column(Modifier.selectableGroup()) {
                        Visibility.entries.forEach { visibility ->
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .selectable(
                                            selected = state.visibility == visibility,
                                            enabled = !state.saving,
                                            role = Role.RadioButton,
                                            onClick = { onVisibilityChange(visibility) },
                                        )
                                        .padding(vertical = 4.dp)
                                        .testTag("event_visibility_${visibility.value}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.visibility == visibility,
                                    onClick = null,
                                    enabled = !state.saving,
                                )
                                Text(
                                    stringResource(visibility.label()),
                                    Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.event_visibility_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.event_registration_save_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { onSave(RegistrationStatus.going) },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.event_registration_going))
                    }
                    OutlinedButton(
                        onClick = { onSave(RegistrationStatus.interested) },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.event_registration_interested))
                    }
                    if (state.saving) CircularProgressIndicator()
                    if (state.saveError == EventRegistrationError.Failed) {
                        Text(
                            stringResource(R.string.event_registration_failed),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (state.saved) {
                        Text(
                            stringResource(
                                R.string.event_registration_saved,
                                stringResource(requireNotNull(state.registration).status.label()),
                            ),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
    }
}

@StringRes
internal fun RegistrationStatus.label(): Int =
    when (this) {
        RegistrationStatus.going -> R.string.event_registration_going
        RegistrationStatus.interested -> R.string.event_registration_interested
    }

@StringRes
internal fun Visibility.label(): Int =
    when (this) {
        Visibility.`public` -> R.string.event_visibility_public
        Visibility.friends_only -> R.string.event_visibility_friends
        Visibility.anonymous -> R.string.event_visibility_anonymous
    }
