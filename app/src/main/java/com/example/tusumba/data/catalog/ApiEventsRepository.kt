package com.example.tusumba.data.catalog

import com.example.tusumba.data.events.api.EventsApi
import com.example.tusumba.data.events.model.EventCard
import com.example.tusumba.data.events.model.EventStatus as ApiEventStatus
import java.io.IOException
import java.time.DateTimeException
import java.util.Locale
import retrofit2.HttpException

class ApiEventsRepository(private val api: EventsApi) : EventsRepository {
    override suspend fun getEvents(filters: CatalogFilters, page: Int): CatalogPage {
        require(page >= 1)
        require(filters.validationError == null)
        try {
            suspend fun request() =
                api.listEvents(
                    query = filters.query.trim().takeIf { it.isNotEmpty() },
                    isFree = filters.isFree,
                    dateFrom = filters.dateFrom,
                    dateTo = filters.dateTo,
                    place = filters.place.trim().takeIf { it.isNotEmpty() },
                    status =
                        ApiEventStatus.entries
                            .filter { status ->
                                filters.statuses.any {
                                    it.name.equals(status.value, ignoreCase = true)
                                }
                            }
                            .takeIf { it.isNotEmpty() },
                    page = page,
                    size = 20,
                )

            var response = request()
            if (response.code() == 401 && response.raw().request.header("Authorization") != null) {
                // TokenAuthenticator удаляет отклонённый токен; каталог доступен и гостю.
                response.errorBody()?.close()
                response = request()
            }
            if (!response.isSuccessful) throw HttpException(response)
            val body = response.body() ?: throw IOException("Empty events response")
            if (body.page != page || body.propertySize !in 1..100 || body.total < 0) {
                throw IOException("Invalid events pagination")
            }
            return CatalogPage(
                body.items.map { it.toCatalogEvent() },
                body.total,
                body.page,
                body.propertySize,
            )
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid events response", error)
        } catch (error: DateTimeException) {
            throw IOException("Invalid events response", error)
        }
    }
}

private fun EventCard.toCatalogEvent() =
    CatalogEvent(
        id = id.toString(),
        title = title,
        date = date,
        placeAddress = placeAddress,
        isFree = isFree,
        price = price?.let { Money(it.amount.toLong(), it.currency.value) },
        status = EventStatus.valueOf(status.value.uppercase(Locale.ROOT)),
        clubId = clubId?.toString(),
        photoUrl = photoUrl,
    )
