package com.example.tusumba.ui.clubs

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.tusumba.data.auth.AuthUser
import com.example.tusumba.data.clubs.ClubEditApiException
import com.example.tusumba.data.clubs.ClubEditingRepository
import com.example.tusumba.data.clubs.ClubPhoto
import com.example.tusumba.data.clubs.ClubPhotoReader
import com.example.tusumba.data.clubs.InvalidClubPhotoException
import com.example.tusumba.data.events.model.ClubDetail
import com.example.tusumba.data.events.model.ClubUpdate
import com.example.tusumba.data.local.TokenManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClubEditViewModelTest {
    private val repository = mockk<ClubEditingRepository>()
    private val reader = mockk<ClubPhotoReader>()
    private val session = mockk<TokenManager>()
    private val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val club = ClubDetail(id, "Шахматы", "Играем вместе", id, 1, true,
        schedule = "Среда, 18:00", meetingPlace = "НГУ", contacts = mapOf("email" to "chess@example.org"), photoUrl = "/photo")
    private var user: AuthUser? = AuthUser("first", "first@example.org", "first")
    private val photo = ClubPhoto(byteArrayOf(1, 2), "image/png")
    private val selectedUri = mockk<Uri>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { session.getUser() } answers { user }
        every { session.getAccessToken() } returns "test_access"
        coEvery { repository.getClub(id) } returns club
        coEvery { reader.read(selectedUri) } returns photo
        coEvery { repository.uploadPhoto(id, photo) } returns "/new-photo"
        coEvery { repository.deletePhoto(id) } just Runs
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun scheduleIsSavedAsASparsePatchAndSignalsNavigationBack() = runTest {
        val vm = viewModel(); runCurrent()
        val changes = ClubUpdate(schedule = "Пятница, 19:00")
        coEvery { repository.updateClub(id, changes) } returns club.copy(schedule = changes.schedule, versionNo = 2)
        vm.edit { it.copy(schedule = requireNotNull(changes.schedule)) }
        val saved = async { vm.saved.first() }
        vm.save(); runCurrent()
        assertTrue(saved.isCompleted)
        coVerify(exactly = 1) { repository.updateClub(id, changes) }
        coVerify(exactly = 0) { repository.uploadPhoto(any(), any()) }
    }

    @Test
    fun draftTextAndPhotoChangesDoNotWriteBeforeSaving() = runTest {
        val vm = viewModel(); runCurrent()
        vm.edit { it.copy(schedule = "Новое расписание") }
        vm.selectPhoto(selectedUri); runCurrent()
        assertTrue(vm.editing().photo is ClubPhotoChange.Replace)
        vm.deletePhoto()
        assertEquals(ClubPhotoChange.Delete, vm.editing().photo)
        coVerify(exactly = 0) { repository.updateClub(any(), any()) }
        coVerify(exactly = 0) { repository.uploadPhoto(any(), any()) }
        coVerify(exactly = 0) { repository.deletePhoto(any()) }
    }

    @Test
    fun requiredDescriptionAndFieldLimitsAreCheckedBeforeAnyWrite() = runTest {
        val vm = viewModel(); runCurrent()
        vm.edit { it.copy(description = "  ") }
        vm.save()
        assertEquals(ClubEditError.Required, vm.editing().error)
        vm.edit { it.copy(description = club.description, meetingPlace = "x".repeat(501)) }
        vm.save()
        assertEquals(ClubEditError.TooLong, vm.editing().error)
        coVerify(exactly = 0) { repository.updateClub(any(), any()) }
    }

    @Test
    fun deletingPhotoOnlySendsDeleteWhenSaveIsPressed() = runTest {
        val vm = viewModel(); runCurrent()
        vm.deletePhoto()
        vm.save(); runCurrent()
        coVerify(exactly = 1) { repository.deletePhoto(id) }
        coVerify(exactly = 0) { repository.updateClub(any(), any()) }
    }

    @Test
    fun failedPhotoUploadRetainsDraftAndDoesNotRepeatAlreadySavedText() = runTest {
        val vm = viewModel(); runCurrent()
        val changes = ClubUpdate(schedule = "Суббота, 12:00")
        coEvery { repository.updateClub(id, changes) } returns club.copy(schedule = changes.schedule, versionNo = 2)
        coEvery { repository.uploadPhoto(id, photo) } throws ClubEditApiException(503)
        vm.edit { it.copy(schedule = requireNotNull(changes.schedule)) }
        vm.selectPhoto(selectedUri); runCurrent()
        vm.save(); runCurrent()
        assertTrue(vm.editing().textSaved)
        assertEquals(ClubEditError.SaveFailed, vm.editing().error)
        assertTrue(vm.editing().photo is ClubPhotoChange.Replace)
        coEvery { repository.uploadPhoto(id, photo) } returns "/new-photo"
        vm.save(); runCurrent()
        coVerify(exactly = 1) { repository.updateClub(id, changes) }
        coVerify(exactly = 2) { repository.uploadPhoto(id, photo) }
    }

    @Test
    fun foreignClubCannotBeEditedEvenViaDirectRoute() = runTest {
        coEvery { repository.getClub(id) } returns club.copy(isAdmin = false)
        val vm = viewModel(); runCurrent()
        assertEquals(ClubEditContent.Forbidden, vm.state.value)
        vm.edit { it.copy(description = "Чужие данные") }; vm.deletePhoto(); vm.save(); runCurrent()
        coVerify(exactly = 0) { repository.updateClub(any(), any()) }
        coVerify(exactly = 0) { repository.deletePhoto(any()) }
    }

    @Test
    fun revokedPermissionOnSaveClosesTheForm() = runTest {
        val vm = viewModel(); runCurrent()
        coEvery { repository.updateClub(id, any()) } throws ClubEditApiException(403)
        vm.edit { it.copy(schedule = "Понедельник") }
        vm.save(); runCurrent()
        assertEquals(ClubEditContent.Forbidden, vm.state.value)
    }

    @Test
    fun accountChangeDropsOldDraftAndIgnoresLateAccountResponses() = runTest {
        val pending = CompletableDeferred<ClubDetail>()
        coEvery { repository.getClub(id) } coAnswers { withContext(NonCancellable) { pending.await() } }
        val vm = viewModel(); runCurrent()
        user = user?.copy(id = "second")
        coEvery { repository.getClub(id) } returns club.copy(isAdmin = false)
        vm.accountChanged("second"); runCurrent()
        pending.complete(club); runCurrent()
        assertEquals(ClubEditContent.Forbidden, vm.state.value)
        user = null
        vm.accountChanged(null)
        assertEquals(ClubEditContent.SignInRequired, vm.state.value)
    }

    @Test
    fun invalidNewSelectionKeepsTheExistingPhotoAndShowsFileError() = runTest {
        val vm = viewModel(); runCurrent()
        coEvery { reader.read(selectedUri) } throws InvalidClubPhotoException()
        vm.selectPhoto(selectedUri); runCurrent()
        assertEquals(ClubPhotoChange.Keep, vm.editing().photo)
        assertEquals(ClubEditError.FileInvalid, vm.editing().error)
    }

    @Test
    fun legacyWhitespaceAndUntouchedContactsDoNotCreateAnImplicitPatch() {
        val legacy = club.copy(description = " ${club.description} ", schedule = " ${club.schedule} ",
            contacts = mapOf("custom" to "  old contact value  "))
        assertEquals(null, ClubEditDraft.from(legacy).changes(legacy))
        assertFalse(ClubEditContent.Editing(legacy).dirty)
    }

    private fun viewModel() = ClubEditViewModel(SavedStateHandle(mapOf("clubId" to id.toString())), repository, reader, session)
    private fun ClubEditViewModel.editing() = state.value as ClubEditContent.Editing
}
