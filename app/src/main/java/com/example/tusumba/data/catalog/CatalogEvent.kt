package com.example.tusumba.data.catalog

import java.time.LocalDateTime

data class CatalogEvent(
    val id: String,
    val title: String,
    val startsAt: LocalDateTime,
    val venue: String,
    val type: EventType,
    val priceRubles: Int,
)

enum class EventType {
    MUSIC,
    SPORT,
    CREATIVITY,
    EDUCATION,
    MEETINGS,
    WALKS,
}
