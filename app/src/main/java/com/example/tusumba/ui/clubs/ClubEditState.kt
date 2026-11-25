package com.example.tusumba.ui.clubs

import com.example.tusumba.data.clubs.ClubPhoto
import com.example.tusumba.data.events.model.ClubDetail
import com.example.tusumba.data.events.model.ClubUpdate
import java.util.UUID

internal fun String.clubFieldLength(): Int = codePointCount(0, length)

data class ClubContactDraft(val id: String = UUID.randomUUID().toString(), val channel: String = "", val address: String = "")

data class ClubEditDraft(
    val description: String,
    val meetingPlace: String,
    val schedule: String,
    val contacts: List<ClubContactDraft>,
) {
    val missingRequired: Boolean get() = description.isBlank() || contacts.any { it.channel.isBlank() != it.address.isBlank() }
    val duplicateContacts: Boolean get() = contacts.filter { it.channel.isNotBlank() }
        .groupingBy { it.channel.trim() }.eachCount().any { it.value > 1 }
    val valid: Boolean get() = !missingRequired && !duplicateContacts && description.trim().clubFieldLength() <= 1000 &&
        meetingPlace.trim().clubFieldLength() <= 500 && schedule.trim().clubFieldLength() <= 1000

    fun changes(original: ClubDetail): ClubUpdate? {
        val filledContacts = contacts.filter { it.channel.isNotBlank() || it.address.isNotBlank() }
        val contactsMap = filledContacts
            .associate { it.channel.trim() to it.address.trim() }
        val originalContactsUnchanged = filledContacts.associate { it.channel to it.address } == original.contacts.orEmpty()
        val update = ClubUpdate(
            description = description.trim().takeIf { it != original.description.trim() },
            meetingPlace = meetingPlace.trim().takeIf { it != original.meetingPlace.orEmpty().trim() },
            schedule = schedule.trim().takeIf { it != original.schedule.orEmpty().trim() },
            contacts = contactsMap.takeIf { !originalContactsUnchanged && it != original.contacts.orEmpty() },
        )
        return update.takeIf { it.description != null || it.meetingPlace != null || it.schedule != null || it.contacts != null }
    }

    companion object {
        fun from(club: ClubDetail) = ClubEditDraft(
            club.description, club.meetingPlace.orEmpty(), club.schedule.orEmpty(),
            club.contacts.orEmpty().map { ClubContactDraft(channel = it.key, address = it.value) },
        )
    }
}

sealed interface ClubPhotoChange {
    data object Keep : ClubPhotoChange
    data object Delete : ClubPhotoChange
    data class Replace(val photo: ClubPhoto) : ClubPhotoChange
}

enum class ClubEditError { Required, TooLong, DuplicateContacts, FileInvalid, SaveFailed }

sealed interface ClubEditContent {
    data object Loading : ClubEditContent
    data object Error : ClubEditContent
    data object Forbidden : ClubEditContent
    data object SignInRequired : ClubEditContent
    data object Unavailable : ClubEditContent

    data class Editing(
        val club: ClubDetail,
        val draft: ClubEditDraft = ClubEditDraft.from(club),
        val photo: ClubPhotoChange = ClubPhotoChange.Keep,
        val readingPhoto: Boolean = false,
        val saving: Boolean = false,
        val error: ClubEditError? = null,
        val validationAttempted: Boolean = false,
        val textSaved: Boolean = false,
    ) : ClubEditContent {
        val dirty: Boolean get() = draft.changes(club) != null || photo != ClubPhotoChange.Keep
    }
}
