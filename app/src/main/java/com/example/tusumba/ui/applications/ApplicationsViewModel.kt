package com.example.tusumba.ui.applications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tusumba.data.applications.*
import com.example.tusumba.data.moderation.model.Application
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class ApplicationsState(
    val userId: String? = null,
    val loading: Boolean = false,
    val items: List<Application> = emptyList(),
    val nextPage: Int? = null,
    val detail: Application? = null,
    val form: ApplicationForm = ApplicationForm(),
    val fields: Map<String, String> = emptyMap(),
    val error: String? = null,
    val signInRequired: Boolean = false,
    val submittedId: String? = null,
)

@HiltViewModel
class ApplicationsViewModel @Inject constructor(private val repository: ApplicationsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ApplicationsState())
    internal val state = mutable.asStateFlow()
    private var job: Job? = null

    fun account(userId: String?) {
        if (state.value.userId == userId) {
            if (state.value.signInRequired) mutable.value = state.value.copy(signInRequired = false, error = null)
            return
        }
        job?.cancel()
        mutable.value = ApplicationsState(userId = userId)
    }

    internal fun edit(form: ApplicationForm) {
        if (state.value.loading) return
        mutable.value = state.value.copy(form = form, fields = emptyMap(), error = null)
    }

    fun list(more: Boolean = false) {
        if (state.value.userId == null || state.value.loading) return
        val page = if (more) state.value.nextPage ?: return else 1
        launchRequest {
            val result = repository.list(page)
            currentCoroutineContext().ensureActive()
            mutable.value = state.value.copy(loading = false,
                items = ((if (more) state.value.items else emptyList()) + result.items).distinctBy { it.id },
                nextPage = if (result.items.isNotEmpty() && result.page.toLong() * result.propertySize < result.total) result.page + 1 else null)
        }
    }

    fun detail(id: String) {
        if (state.value.userId == null || state.value.loading) return
        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
        if (uuid == null) {
            mutable.value = state.value.copy(error = "Заявка недоступна.")
            return
        }
        launchRequest {
            val result = repository.get(uuid)
            currentCoroutineContext().ensureActive()
            mutable.value = state.value.copy(loading = false, detail = result)
        }
    }

    fun submit() {
        if (state.value.userId == null || state.value.loading || state.value.submittedId != null) return
        val form = state.value.form
        val errors = form.errors()
        mutable.value = state.value.copy(fields = errors, error = null)
        if (errors.isNotEmpty()) return
        launchRequest("Не удалось отправить заявку. Попробуйте позже.") {
            val result = repository.submit(form.request())
            currentCoroutineContext().ensureActive()
            mutable.value = state.value.copy(loading = false, submittedId = result.id.toString())
        }
    }

    private fun launchRequest(fallback: String = "Не удалось загрузить заявки. Попробуйте позже.", block: suspend () -> Unit) {
        job?.cancel()
        mutable.value = state.value.copy(loading = true, error = null, fields = emptyMap(), signInRequired = false)
        job = viewModelScope.launch {
            try { block() }
            catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                val failure = error as? ApplicationFailure
                mutable.value = state.value.copy(loading = false,
                    fields = failure?.fields.orEmpty(), signInRequired = failure?.status == 401,
                    error = when {
                        failure?.status == 401 -> "Войдите, чтобы продолжить."
                        failure?.status == 404 -> "Заявка недоступна."
                        failure?.code == "application_duplicate" -> "У вас уже есть активная заявка на этот объект."
                        failure != null -> failure.message
                        else -> fallback
                    })
            }
        }
    }
}
