package com.example.tusumba.ui.clubs

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tusumba.R
import com.example.tusumba.ui.catalog.CatalogTheme
import com.example.tusumba.ui.components.ContentEmpty
import com.example.tusumba.ui.components.ContentError
import com.example.tusumba.ui.components.ContentLoading
import com.example.tusumba.ui.components.ContentStateAction

@Composable
fun ClubEditScreen(
    userId: String?,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: ClubEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::selectPhoto)
    }
    LaunchedEffect(userId) { viewModel.accountChanged(userId) }
    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved() } }
    ClubEditScreenContent(
        state, onCancel, viewModel::save, viewModel::edit,
        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        viewModel::deletePhoto, viewModel::refresh, onSignIn,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClubEditScreenContent(
    state: ClubEditContent,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onEdit: ((ClubEditDraft) -> ClubEditDraft) -> Unit,
    onSelectPhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
) {
    val editing = state as? ClubEditContent.Editing
    val listState = rememberLazyListState()
    LaunchedEffect(editing?.error) {
        if (editing?.error != null) listState.animateScrollToItem(2)
    }
    BackHandler(enabled = editing?.saving == true) { }
    CatalogTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.club_edit_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        TextButton(onCancel, enabled = editing?.saving != true) { Text(stringResource(R.string.club_edit_cancel)) }
                    },
                    actions = {
                        if (editing != null) {
                            TextButton(
                                onClick = onSave,
                                enabled = !editing.saving && !editing.readingPhoto && editing.dirty,
                                modifier = Modifier.testTag("club_edit_save"),
                            ) {
                                Text(stringResource(if (editing.saving) R.string.club_edit_saving else R.string.club_edit_save))
                            }
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).testTag("club_edit_form"),
                state = listState,
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state) {
                    ClubEditContent.Loading -> item { ContentLoading() }
                    ClubEditContent.Error -> item { ContentError(R.string.club_edit_load_error, onRetry) }
                    ClubEditContent.Forbidden -> item { ContentEmpty(R.string.club_edit_forbidden) }
                    ClubEditContent.Unavailable -> item { ContentEmpty(R.string.club_unavailable) }
                    ClubEditContent.SignInRequired -> item {
                        ContentEmpty(R.string.club_edit_sign_in, action = ContentStateAction(R.string.auth_sign_in, onSignIn))
                    }
                    is ClubEditContent.Editing -> {
                        item {
                            Text(state.club.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                        }
                        item {
                            val preview = (state.photo as? ClubPhotoChange.Replace)?.photo?.bytes
                            ClubCover(
                                if (state.photo == ClubPhotoChange.Delete) null else state.club.photoUrl,
                                state.club.currentVersionId.toString(), preview,
                            )
                            Text(stringResource(R.string.club_edit_photo_requirements), Modifier.padding(top = 8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onSelectPhoto, enabled = !state.saving && !state.readingPhoto) {
                                    Text(stringResource(R.string.club_edit_select_photo))
                                }
                                val hasPhoto = preview != null || (state.photo != ClubPhotoChange.Delete && !state.club.photoUrl.isNullOrBlank())
                                if (hasPhoto) {
                                    TextButton(onDeletePhoto, enabled = !state.saving) {
                                        Text(stringResource(R.string.club_edit_delete_photo))
                                    }
                                }
                            }
                            if (state.readingPhoto) CircularProgressIndicator()
                        }
                        if (state.error != null) item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.textSaved) Text(stringResource(R.string.club_edit_partial))
                                val message = when (state.error) {
                                    ClubEditError.Required -> R.string.club_edit_required
                                    ClubEditError.FileInvalid -> R.string.club_edit_file_invalid
                                    ClubEditError.DuplicateContacts -> R.string.club_edit_duplicate_contacts
                                    ClubEditError.TooLong -> R.string.club_edit_too_long
                                    ClubEditError.SaveFailed -> R.string.club_edit_save_error
                                }
                                Text(
                                    stringResource(message),
                                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        item {
                            ClubEditField(
                                state.draft.description, { value -> onEdit { it.copy(description = value) } },
                                stringResource(R.string.club_about), 1000, !state.saving,
                                state.validationAttempted && state.draft.description.isBlank(), "club_edit_description", 4,
                            )
                        }
                        item {
                            ClubEditField(
                                state.draft.schedule, { value -> onEdit { it.copy(schedule = value) } },
                                stringResource(R.string.club_schedule), 1000, !state.saving, tag = "club_edit_schedule", lines = 3,
                            )
                        }
                        item {
                            ClubEditField(
                                state.draft.meetingPlace, { value -> onEdit { it.copy(meetingPlace = value) } },
                                stringResource(R.string.club_place), 500, !state.saving, tag = "club_edit_place",
                            )
                        }
                        item { Text(stringResource(R.string.club_contacts), style = MaterialTheme.typography.titleMedium) }
                        items(state.draft.contacts, key = { it.id }) { contact ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val missing = state.validationAttempted && contact.channel.isBlank() != contact.address.isBlank()
                                OutlinedTextField(
                                    contact.channel,
                                    { value -> onEdit { draft -> draft.copy(contacts = draft.contacts.map {
                                        if (it.id == contact.id) it.copy(channel = value) else it
                                    }) } },
                                    Modifier.fillMaxWidth(),
                                    enabled = !state.saving,
                                    label = { Text(stringResource(R.string.club_edit_contact_channel)) },
                                    isError = missing && contact.channel.isBlank(), singleLine = true,
                                )
                                OutlinedTextField(
                                    contact.address,
                                    { value -> onEdit { draft -> draft.copy(contacts = draft.contacts.map {
                                        if (it.id == contact.id) it.copy(address = value) else it
                                    }) } },
                                    Modifier.fillMaxWidth(),
                                    enabled = !state.saving,
                                    label = { Text(stringResource(R.string.club_edit_contact_address)) },
                                    isError = missing && contact.address.isBlank(), singleLine = true,
                                )
                                TextButton(
                                    onClick = { onEdit { it.copy(contacts = it.contacts.filterNot { row -> row.id == contact.id }) } },
                                    enabled = !state.saving,
                                ) { Text(stringResource(R.string.club_edit_remove_contact)) }
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { onEdit { it.copy(contacts = it.contacts + ClubContactDraft()) } },
                                enabled = !state.saving,
                            ) { Text(stringResource(R.string.club_edit_add_contact)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClubEditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    limit: Int,
    enabled: Boolean,
    missing: Boolean = false,
    tag: String,
    lines: Int = 2,
) {
    val tooLong = value.trim().length > limit
    OutlinedTextField(
        value, onValueChange,
        Modifier.fillMaxWidth().testTag(tag),
        enabled = enabled, label = { Text(label) }, minLines = lines, maxLines = lines + 3,
        isError = missing || tooLong,
        supportingText = {
            when {
                missing -> Text(stringResource(R.string.club_edit_required))
                tooLong -> Text(stringResource(R.string.club_edit_limit, limit))
                else -> Text("${value.length} / $limit")
            }
        },
    )
}
