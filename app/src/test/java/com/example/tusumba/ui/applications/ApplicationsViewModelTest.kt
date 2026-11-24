package com.example.tusumba.ui.applications

import com.example.tusumba.data.applications.*
import com.example.tusumba.data.moderation.model.*
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationsViewModelTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private fun application() = Application(id, ApplicationType.event, id, ApplicationStatus.on_review, OffsetDateTime.parse("2026-11-25T00:00:00Z"))
    private open inner class Repository : ApplicationsRepository {
        override suspend fun submit(value: ApplicationCreate): Application = application()
        override suspend fun list(page: Int): ApplicationPage = ApplicationPage(emptyList(), 0, page, 20)
        override suspend fun get(id: UUID): Application = application()
    }

    @Test fun invalidFormNeverCallsServerAndDoubleTapSubmitsOnce() = runTest {
        val pending = CompletableDeferred<Application>()
        var calls = 0
        val vm = ApplicationsViewModel(object : Repository() {
            override suspend fun submit(value: ApplicationCreate): Application { calls++; return pending.await() }
        })
        vm.account("user")
        vm.submit()
        assertTrue(vm.state.value.fields.isNotEmpty())
        assertEquals(0, calls)
        vm.edit(ApplicationForm(title = "Тест", description = "Описание", date = applicationToday().toString(), place = "НГУ"))
        vm.submit(); vm.submit(); runCurrent()
        assertEquals(1, calls)
        pending.complete(application()); advanceUntilIdle()
        assertEquals(id.toString(), vm.state.value.submittedId)
        vm.submit(); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun accountChangeDiscardsLatePersonalResponseAndDraft() = runTest {
        val pending = CompletableDeferred<ApplicationPage>()
        val vm = ApplicationsViewModel(object : Repository() {
            override suspend fun list(page: Int): ApplicationPage = withContext(NonCancellable) { pending.await() }
        })
        vm.account("first")
        vm.edit(ApplicationForm(title = "Личный черновик"))
        vm.list(); runCurrent()
        vm.account("second")
        pending.complete(ApplicationPage(listOf(application()), 1, 1, 20)); advanceUntilIdle()
        assertTrue(vm.state.value.items.isEmpty())
        assertEquals("", vm.state.value.form.title)
        assertEquals("second", vm.state.value.userId)
    }

    @Test fun failedNextPageRetainsRowsAndRetriesSamePage() = runTest {
        val pages = mutableListOf<Int>()
        var fail = true
        val vm = ApplicationsViewModel(object : Repository() {
            override suspend fun list(page: Int): ApplicationPage {
                pages += page
                if (page == 2 && fail) throw ApplicationFailure(503, "service_unavailable", "Ошибка")
                return ApplicationPage(listOf(application()), 21, page, 20)
            }
        })
        vm.account("user"); vm.list(); advanceUntilIdle()
        vm.list(true); advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)
        assertEquals(2, vm.state.value.nextPage)
        fail = false; vm.list(true); advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), pages)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.nextPage)
    }

    @Test fun serverValidationIsAssignedToFieldsAndUnauthorizedRequiresLogin() = runTest {
        val vm = ApplicationsViewModel(object : Repository() {
            override suspend fun submit(value: ApplicationCreate): Application = throw ApplicationFailure(422, "validation_error", "Проверьте поля", mapOf("event.date" to "Дата в прошлом"))
            override suspend fun list(page: Int): ApplicationPage = throw ApplicationFailure(401, "invalid_token", "Войдите")
        })
        vm.account("user")
        vm.edit(ApplicationForm(title = "Тест", description = "Описание", date = applicationToday().toString(), place = "НГУ"))
        vm.submit(); advanceUntilIdle()
        assertEquals("Дата в прошлом", vm.state.value.fields["event.date"])
        assertFalse(vm.state.value.loading)
        vm.list(); advanceUntilIdle()
        assertTrue(vm.state.value.signInRequired)
    }
}
