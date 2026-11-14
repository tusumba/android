package com.example.tusumba.data.registrations

import com.example.tusumba.data.events.api.EventsApi
import com.example.tusumba.data.events.model.EventDetail
import com.example.tusumba.data.ticketing.api.RegistrationsApi
import com.example.tusumba.data.ticketing.model.Registration
import com.example.tusumba.data.ticketing.model.RegistrationPage
import com.example.tusumba.data.ticketing.model.RegistrationRequest
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import java.io.IOException
import java.time.DateTimeException
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response

private const val PageSize = 20

class ApiRegistrationsRepository(
    private val registrationsApi: RegistrationsApi,
    private val eventsApi: EventsApi,
) : RegistrationsRepository {
    override suspend fun getRegistrations(page: Int): RegistrationPage =
        request { registrationsApi.listMyRegistrations(page = page, size = PageSize) }.requireBody()

    override suspend fun getEvent(eventId: UUID): EventDetail = request {
        val response = eventsApi.getEvent(eventId)
        if (response.code() == 401 && response.raw().request.header("Authorization") != null) {
            // The authenticator cleared the rejected token. Event details also allow guest access.
            response.errorBody()?.close()
            eventsApi.getEvent(eventId)
        } else {
            response
        }
    }.requireBody()

    override suspend fun saveRegistration(eventId: UUID, status: RegistrationStatus): Registration =
        request { registrationsApi.registerForEvent(eventId, RegistrationRequest(status)) }.requireBody()

    override suspend fun cancelRegistration(eventId: UUID) {
        val response = request { registrationsApi.cancelRegistration(eventId) }
        if (response.isSuccessful) return
        val failure = response.toApiException()
        if (failure.statusCode == 404 && failure.errorCode == "registration_not_found") return
        throw failure
    }

    private suspend fun <T> request(call: suspend () -> Response<T>): Response<T> =
        try {
            call()
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid registration API response", error)
        } catch (error: DateTimeException) {
            throw IOException("Invalid registration API response", error)
        }

    private fun <T> Response<T>.requireBody(): T {
        if (!isSuccessful) throw toApiException()
        return body() ?: throw IOException("Empty registration API response")
    }

    private fun Response<*>.toApiException(): RegistrationApiException {
        val errorCode = try {
            errorBody()?.use { body ->
                val envelope = Json.parseToJsonElement(body.string()) as? JsonObject
                val error = envelope?.get("error") as? JsonObject
                val code = error?.get("code") as? JsonPrimitive
                code?.takeIf { it.isString }?.content
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IOException) {
            null
        }
        return RegistrationApiException(code(), errorCode)
    }
}
