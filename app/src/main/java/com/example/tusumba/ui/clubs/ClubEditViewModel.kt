package com.example.tusumba.ui.clubs

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tusumba.data.clubs.ClubEditApiException
import com.example.tusumba.data.clubs.ClubEditingRepository
import com.example.tusumba.data.clubs.ClubPhotoReader
import com.example.tusumba.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ClubEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ClubEditingRepository,
    private val photoReader: ClubPhotoReader,
    private val session: TokenManager,
) : ViewModel() {
    private val clubId = savedStateHandle.get<String>("clubId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private val mutableState = MutableStateFlow<ClubEditContent>(ClubEditContent.Loading)
    val state = mutableState.asStateFlow()
    private val savedEvents = Channel<Unit>(Channel.BUFFERED)
    val saved = savedEvents.receiveAsFlow()
    private var request: Job? = null
    private var photoRequest: Job? = null
    private var accountId: String? = null

    init { refresh() }

    fun accountChanged(userId: String?) {
        if (accountId != userId) refresh()
    }

    fun refresh() {
        request?.cancel()
        photoRequest?.cancel()
        accountId = session.getUser()?.id
        if (!hasSession()) {
            mutableState.value = ClubEditContent.SignInRequired
            return
        }
        val id = clubId ?: run {
            mutableState.value = ClubEditContent.Unavailable
            return
        }
        mutableState.value = ClubEditContent.Loading
        request = viewModelScope.launch {
            try {
                val club = repository.getClub(id)
                currentCoroutineContext().ensureActive()
                mutableState.value = when {
                    !hasSession() -> ClubEditContent.SignInRequired
                    !club.isAdmin -> ClubEditContent.Forbidden
                    else -> ClubEditContent.Editing(club)
                }
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                mutableState.value = accessError(error) ?: ClubEditContent.Error
            }
        }
    }

    fun edit(transform: (ClubEditDraft) -> ClubEditDraft) {
        val current = state.value as? ClubEditContent.Editing ?: return
        if (!current.saving) mutableState.value = current.copy(draft = transform(current.draft), error = null)
    }

    fun selectPhoto(uri: Uri) {
        val current = state.value as? ClubEditContent.Editing ?: return
        if (current.saving) return
        photoRequest?.cancel()
        mutableState.value = current.copy(readingPhoto = true, error = null)
        photoRequest = viewModelScope.launch {
            try {
                val photo = photoReader.read(uri)
                currentCoroutineContext().ensureActive()
                val latest = state.value as? ClubEditContent.Editing ?: return@launch
                mutableState.value = latest.copy(readingPhoto = false, photo = ClubPhotoChange.Replace(photo))
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                val latest = state.value as? ClubEditContent.Editing ?: return@launch
                mutableState.value = latest.copy(readingPhoto = false, error = ClubEditError.FileInvalid)
            }
        }
    }

    fun deletePhoto() {
        val current = state.value as? ClubEditContent.Editing ?: return
        if (current.saving) return
        photoRequest?.cancel()
        val change = if (current.club.photoUrl.isNullOrBlank()) ClubPhotoChange.Keep else ClubPhotoChange.Delete
        mutableState.value = current.copy(photo = change, readingPhoto = false, error = null)
    }

    fun save() {
        val current = state.value as? ClubEditContent.Editing ?: return
        if (current.saving || current.readingPhoto || !current.dirty) return
        if (!hasSession()) {
            mutableState.value = ClubEditContent.SignInRequired
            return
        }
        val validationError = when {
            current.draft.missingRequired -> ClubEditError.Required
            current.draft.duplicateContacts -> ClubEditError.DuplicateContacts
            !current.draft.valid -> ClubEditError.TooLong
            else -> null
        }
        if (validationError != null) {
            mutableState.value = current.copy(error = validationError, validationAttempted = true)
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        request = viewModelScope.launch {
            var latest = current.copy(saving = true)
            var changingPhoto = false
            try {
                val changes = current.draft.changes(current.club)
                if (changes != null) {
                    val updated = repository.updateClub(current.club.id, changes)
                    currentCoroutineContext().ensureActive()
                    latest = latest.copy(club = updated, textSaved = true)
                    mutableState.value = latest
                }
                if (!hasSession()) {
                    mutableState.value = ClubEditContent.SignInRequired
                    return@launch
                }
                changingPhoto = current.photo != ClubPhotoChange.Keep
                when (val photo = current.photo) {
                    ClubPhotoChange.Keep -> Unit
                    ClubPhotoChange.Delete -> repository.deletePhoto(current.club.id)
                    is ClubPhotoChange.Replace -> repository.uploadPhoto(current.club.id, photo.photo)
                }
                currentCoroutineContext().ensureActive()
                savedEvents.send(Unit)
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                val reason = when {
                    changingPhoto && (error as? ClubEditApiException)?.statusCode in setOf(413, 415, 422) -> ClubEditError.FileInvalid
                    (error as? ClubEditApiException)?.statusCode == 422 -> ClubEditError.Required
                    else -> ClubEditError.SaveFailed
                }
                mutableState.value = accessError(error) ?: latest.copy(saving = false, error = reason)
            }
        }
    }

    private fun hasSession(): Boolean = accountId != null && accountId == session.getUser()?.id &&
        !session.getAccessToken().isNullOrBlank()

    private fun accessError(error: IOException): ClubEditContent? = when ((error as? ClubEditApiException)?.statusCode) {
        401 -> ClubEditContent.SignInRequired
        403 -> ClubEditContent.Forbidden
        404 -> ClubEditContent.Unavailable
        else -> null
    }
}
