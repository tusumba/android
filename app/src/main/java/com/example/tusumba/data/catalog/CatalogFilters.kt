package com.example.tusumba.data.catalog

import java.time.LocalDate

/** Параметры GET /v1/events; пустые значения не передаются серверу. */
data class CatalogFilters(
    val query: String = "",
    val isFree: Boolean? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val place: String = "",
    val statuses: Set<EventStatus> = emptySet(),
) {
    val isActive: Boolean
        get() =
            query.isNotBlank() ||
                isFree != null ||
                dateFrom != null ||
                dateTo != null ||
                place.isNotBlank() ||
                statuses.isNotEmpty()

    val validationError: CatalogFilterError?
        get() =
            when {
                dateFrom != null && dateTo != null && dateFrom > dateTo ->
                    CatalogFilterError.DATE_RANGE
                query.exceedsLimit(100) -> CatalogFilterError.QUERY_LENGTH
                place.exceedsLimit(200) -> CatalogFilterError.PLACE_LENGTH
                else -> null
            }
}

private fun String.exceedsLimit(limit: Int) = trim().let { it.codePointCount(0, it.length) > limit }

enum class CatalogFilterError {
    DATE_RANGE,
    QUERY_LENGTH,
    PLACE_LENGTH,
}
