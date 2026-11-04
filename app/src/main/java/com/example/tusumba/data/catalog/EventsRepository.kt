package com.example.tusumba.data.catalog

/** UI работает с этим контрактом независимо от источника данных: моки или API. */
interface EventsRepository {
    suspend fun getEvents(): List<CatalogEvent>
}
