package com.example.tusumba.ui.applications

import com.example.tusumba.data.moderation.model.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

internal fun applicationToday(): LocalDate = LocalDate.now(ZoneId.of("Asia/Novosibirsk"))

internal data class ApplicationForm(
    val type: ApplicationType = ApplicationType.event,
    val title: String = "",
    val description: String = "",
    val date: String = "",
    val place: String = "",
    val free: Boolean = true,
    val price: String = "",
    val schedule: String = "",
    val telegram: String = "",
    val email: String = "",
) {
    fun priceInKopecks(): Int? = try {
        price.trim().replace(',', '.').takeIf { it.matches(Regex("[0-9]+(\\.[0-9]{1,2})?")) }
            ?.let { BigDecimal(it).movePointRight(2).intValueExact() }
    } catch (_: ArithmeticException) { null }

    fun errors(today: LocalDate = applicationToday()): Map<String, String> = buildMap {
        val prefix = if (type == ApplicationType.event) "event" else "club_page"
        fun text(key: String, value: String, max: Int, required: Boolean = false) {
            if (required && value.isBlank()) put("$prefix.$key", "Заполните обязательное поле.")
            else if (value.trim().length > max) put("$prefix.$key", "Не более $max символов.")
        }
        text(if (type == ApplicationType.event) "title" else "name", title, 255, true)
        text("description", description, 1000, true)
        if (type == ApplicationType.event) {
            text("place_address", place, 500, true)
            val parsed = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
            if (parsed == null) put("event.date", "Укажите дату в формате ГГГГ-ММ-ДД.")
            else if (parsed < today) put("event.date", "Дата мероприятия не может быть в прошлом.")
            if (!free && priceInKopecks() == null) put("event.price", "Укажите цену в рублях, не более двух знаков после запятой.")
        } else {
            text("meeting_place", place, 500)
            text("schedule", schedule, 1000)
        }
    }

    fun request(): ApplicationCreate = if (type == ApplicationType.event) {
        ApplicationCreate(type, event = EventApplicationData(title.trim(), description.trim(),
            LocalDate.parse(date.trim()), place.trim(), free,
            if (free) null else Money(requireNotNull(priceInKopecks()), Money.Currency.RUB)))
    } else {
        ApplicationCreate(type, clubPage = ClubPageApplicationData(title.trim(), description.trim(),
            place.trim().takeIf { it.isNotEmpty() }, schedule.trim().takeIf { it.isNotEmpty() },
            buildMap {
                if (telegram.isNotBlank()) put("telegram", telegram.trim())
                if (email.isNotBlank()) put("email", email.trim())
            }.takeIf { it.isNotEmpty() }))
    }
}
