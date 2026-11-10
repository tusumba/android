package com.example.tusumba.data.clubs

import com.example.tusumba.data.events.api.ClubsApi
import com.example.tusumba.data.events.api.EventsApi
import com.example.tusumba.data.events.model.ClubCardPage
import com.example.tusumba.data.events.model.ClubDetail
import com.example.tusumba.data.events.model.EventCardPage
import java.io.IOException
import java.time.DateTimeException
import java.util.UUID
import retrofit2.HttpException
import retrofit2.Response

private const val PageSize = 20

class ApiClubsRepository(
    private val clubsApi: ClubsApi,
    private val eventsApi: EventsApi
) : ClubsRepository {
    override suspend fun getClubs(query: String?, page: Int): ClubCardPage =
        request {
            clubsApi.listClubs(query = query?.trim()?.takeIf { it.isNotEmpty() }, page = page, size = PageSize)
        }.requireBody()

    override suspend fun getClub(id: UUID): ClubDetail? {
        val response = request { clubsApi.getClub(id) }
        return if (response.code() == 404) null else response.requireBody()
    }

    override suspend fun getEvents(clubId: UUID, page: Int): EventCardPage =
        request {
            eventsApi.listEvents(clubId = clubId, page = page, size = PageSize)
        }.requireBody()

    private suspend fun <T> request(call: suspend () -> Response<T>): Response<T> =
        try {
            val response = call()
            if (response.code() == 401 && response.raw().request.header("Authorization") != null) {
                // TokenAuthenticator cleared the rejected token. These GETs also allow guest access.
                response.errorBody()?.close()
                call()
            } else {
                response
            }
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid Events & Clubs response", error)
        } catch (error: DateTimeException) {
            throw IOException("Invalid Events & Clubs response", error)
        }

    private fun <T> Response<T>.requireBody(): T {
        if (!isSuccessful) throw HttpException(this)
        return body() ?: throw IOException("Empty Events & Clubs response")
    }
}
