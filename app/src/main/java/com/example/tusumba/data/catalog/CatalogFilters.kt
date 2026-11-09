package com.example.tusumba.data.catalog

/** Параметры query и is_free из GET /v1/events: тип мероприятия - платное или бесплатное. */
data class CatalogFilters(val query: String = "", val isFree: Boolean? = null)

fun List<CatalogEvent>.filteredBy(filters: CatalogFilters): List<CatalogEvent> {
    val query = filters.query.trim()
    return filter { event ->
        (filters.isFree == null || event.isFree == filters.isFree) &&
            (query.isEmpty() || event.title.contains(query, ignoreCase = true))
    }
}
