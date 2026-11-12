package com.example.tusumba.data.catalog

/** UI работает с этим контрактом независимо от источника данных: моки или API. */
interface EventsRepository {
    suspend fun getEvents(filters: CatalogFilters = CatalogFilters(), page: Int = 1): CatalogPage
}

data class CatalogPage(
    val items: List<CatalogEvent>,
    val total: Int,
    val page: Int,
    val size: Int,
) {
    val hasMore: Boolean
        get() = items.isNotEmpty() && page.toLong() * size < total
}
