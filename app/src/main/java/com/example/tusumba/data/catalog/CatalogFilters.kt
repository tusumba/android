package com.example.tusumba.data.catalog

data class CatalogFilters(val query: String = "", val type: EventType? = null)

fun List<CatalogEvent>.filteredBy(filters: CatalogFilters): List<CatalogEvent> {
    val query = filters.query.trim()
    return filter { event ->
        (filters.type == null || event.type == filters.type) &&
            (query.isEmpty() ||
                event.title.contains(query, ignoreCase = true) ||
                event.venue.contains(query, ignoreCase = true))
    }
}
