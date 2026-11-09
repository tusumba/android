package com.example.tusumba.data.catalog

import java.time.LocalDate

/** Карточка каталога по схеме EventCard из OpenAPI Events & Clubs (GET /v1/events). */
data class CatalogEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val placeAddress: String,
    val isFree: Boolean,
    val price: Money?,
    val status: EventStatus,
    val clubId: String? = null,
    val photoUrl: String? = null,
)

/** Сумма в копейках, как схема Money в OpenAPI. */
data class Money(val amount: Long, val currency: String = "RUB")

enum class EventStatus {
    NOT_CONFIRMED,
    CONFIRMED,
    HELD,
    NOT_HELD,
}

fun Money.formatRubles(): String {
    val rubles = amount / 100
    val kopecks = amount % 100
    return if (kopecks == 0L) rubles.toString() else "%d,%02d".format(rubles, kopecks)
}
