package com.example.tusumba.ui.applications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tusumba.data.moderation.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationsScreen(
    userId: String?,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onCreate: () -> Unit = {},
    onApplication: (String) -> Unit = {},
    onObject: (String, Boolean) -> Unit = { _, _ -> },
    mode: String = "list",
    applicationId: String? = null,
    viewModel: ApplicationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(userId, mode, applicationId) {
        viewModel.account(userId)
        if (mode == "list") viewModel.list()
        if (mode == "detail" && applicationId != null) viewModel.detail(applicationId)
        onPauseOrDispose { }
    }
    LaunchedEffect(state.submittedId) {
        state.submittedId?.let(onApplication)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(
            title = { Text(when (mode) { "form" -> "Новая заявка"; "detail" -> "Моя заявка"; else -> "Мои заявки" }) },
            navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (userId == null || state.signInRequired) {
                item { Text("Войдите, чтобы подавать заявки и следить за их рассмотрением.") }
                item { Button(onClick = onSignIn) { Text("Войти") } }
            } else if (state.userId == userId) {
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { error ->
                    item {
                        Text(error, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        if (mode != "form") TextButton(onClick = {
                            if (mode == "list") viewModel.list()
                            else applicationId?.let(viewModel::detail)
                        }, enabled = !state.loading) { Text("Повторить") }
                    }
                }
                when (mode) {
                    "form" -> item {
                        ApplicationFormContent(state.form, state.fields, state.loading || state.submittedId != null,
                            viewModel::edit, viewModel::submit)
                    }
                    "detail" -> state.detail?.let { application ->
                        item { ApplicationDetails(application, onObject) }
                        item { OutlinedButton(onClick = { applicationId?.let(viewModel::detail) }, enabled = !state.loading) { Text("Обновить статус") } }
                    }
                    else -> {
                        item {
                            Button(onClick = onCreate) { Text("Подать заявку") }
                            TextButton(onClick = { viewModel.list() }, enabled = !state.loading) { Text("Обновить") }
                        }
                        if (!state.loading && state.error == null && state.items.isEmpty()) item { Text("У вас пока нет заявок.") }
                        items(state.items, key = { it.id.toString() }) { application ->
                            OutlinedCard(onClick = { onApplication(application.id.toString()) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(application.title(), style = MaterialTheme.typography.titleMedium)
                                    Text(if (application.type == ApplicationType.event) "Мероприятие" else "Страница секции")
                                    Text(application.status.label())
                                    Text("Подана: ${application.createdAt.toLocalDate()}")
                                }
                            }
                        }
                        if (state.nextPage != null) item {
                            OutlinedButton(onClick = { viewModel.list(true) }, enabled = !state.loading) { Text("Показать ещё") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ApplicationFormContent(
    form: ApplicationForm,
    errors: Map<String, String>,
    busy: Boolean,
    onChange: (ApplicationForm) -> Unit,
    onSubmit: () -> Unit,
) {
    val event = form.type == ApplicationType.event
    val prefix = if (event) "event" else "club_page"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Тип заявки", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = event, onClick = { onChange(form.copy(type = ApplicationType.event)) },
                enabled = !busy, label = { Text("Мероприятие") })
            FilterChip(selected = !event, onClick = { onChange(form.copy(type = ApplicationType.club_page)) },
                enabled = !busy, label = { Text("Страница секции") })
        }
        Text("Поля со звёздочкой обязательны. После отправки заявка поступит модератору.")
        ApplicationField("Название *", form.title, errors["$prefix.${if (event) "title" else "name"}"], busy) { onChange(form.copy(title = it)) }
        ApplicationField("Описание *", form.description, errors["$prefix.description"], busy, multiline = true) { onChange(form.copy(description = it)) }
        if (event) {
            Text("Организатор: вы. После одобрения вы станете владельцем команды мероприятия.")
            ApplicationField("Дата * (ГГГГ-ММ-ДД)", form.date, errors["event.date"], busy) { onChange(form.copy(date = it)) }
            ApplicationField("Место проведения *", form.place, errors["event.place_address"], busy) { onChange(form.copy(place = it)) }
            Row {
                Checkbox(checked = form.free, onCheckedChange = { onChange(form.copy(free = it)) }, enabled = !busy)
                Text("Бесплатное мероприятие", Modifier.padding(top = 12.dp))
            }
            if (!form.free) ApplicationField("Цена, ₽ *", form.price, errors["event.price"] ?: errors["event.price.amount"], busy,
                keyboard = KeyboardType.Decimal) { onChange(form.copy(price = it)) }
        } else {
            ApplicationField("Место встреч", form.place, errors["club_page.meeting_place"], busy) { onChange(form.copy(place = it)) }
            ApplicationField("Расписание", form.schedule, errors["club_page.schedule"], busy) { onChange(form.copy(schedule = it)) }
            ApplicationField("Telegram", form.telegram, errors["club_page.contacts.telegram"], busy) { onChange(form.copy(telegram = it)) }
            ApplicationField("Email", form.email, errors["club_page.contacts.email"], busy, keyboard = KeyboardType.Email) { onChange(form.copy(email = it)) }
        }
        val visibleFields = setOf("$prefix.${if (event) "title" else "name"}", "$prefix.description",
            "event.date", "event.place_address", "event.price", "event.price.amount", "club_page.meeting_place",
            "club_page.schedule", "club_page.contacts.telegram", "club_page.contacts.email")
        errors.filterKeys { it !in visibleFields }.values.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Отправка…" else "Отправить на проверку")
        }
    }
}

@Composable
private fun ApplicationField(label: String, value: String, error: String?, busy: Boolean,
    multiline: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = !multiline,
        minLines = if (multiline) 4 else 1, isError = error != null,
        supportingText = error?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}

internal fun Application.title(): String = event?.title ?: clubPage?.name ?: "Заявка"
internal fun ApplicationStatus.label(): String = when (this) {
    ApplicationStatus.on_review -> "На проверке"
    ApplicationStatus.approved -> "Одобрена"
    ApplicationStatus.rejected -> "Отклонена"
}

@Composable
internal fun ApplicationDetails(application: Application, onObject: (String, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(application.title(), style = MaterialTheme.typography.headlineSmall)
        Text(application.status.label(), style = MaterialTheme.typography.titleMedium)
        Text("Подана: ${application.createdAt.toLocalDate()}")
        application.processedAt?.let { Text("Рассмотрена: ${it.toLocalDate()}") }
        if (application.status == ApplicationStatus.rejected) Text("Заявка отклонена. Ознакомьтесь с комментарием модератора.")
        application.moderatorComment?.takeIf { it.isNotBlank() }?.let {
            Text("Комментарий модератора", style = MaterialTheme.typography.titleMedium)
            Text(it)
        }
        application.event?.let {
            Text(it.description)
            Text("Дата: ${it.date}")
            Text("Место: ${it.placeAddress}")
            Text(if (it.isFree) "Бесплатно" else "Цена: ${it.price?.amount?.toBigDecimal()?.movePointLeft(2)?.toPlainString() ?: "—"} ₽")
        }
        application.clubPage?.let {
            Text(it.description)
            it.meetingPlace?.let { place -> Text("Место встреч: $place") }
            it.schedule?.let { schedule -> Text("Расписание: $schedule") }
            it.contacts.orEmpty().forEach { (channel, address) -> Text("$channel: $address") }
        }
        if (application.status == ApplicationStatus.approved) application.resultObjectId?.let { id ->
            Button(onClick = { onObject(id.toString(), application.type == ApplicationType.event) }) {
                Text(if (application.type == ApplicationType.event) "Открыть мероприятие" else "Открыть секцию")
            }
        }
    }
}
