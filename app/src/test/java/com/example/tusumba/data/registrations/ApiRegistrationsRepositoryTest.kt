package com.example.tusumba.data.registrations

import com.example.tusumba.data.local.TokenManager
import com.example.tusumba.data.network.AuthInterceptor
import com.example.tusumba.data.network.TokenAuthenticator
import com.example.tusumba.data.ticketing.model.RegistrationSource
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import com.example.tusumba.data.ticketing.model.Visibility
import com.example.tusumba.di.ClubsModule
import com.example.tusumba.di.NetworkModule
import com.example.tusumba.di.RegistrationsModule
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ApiRegistrationsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RegistrationsRepository
    private val eventId = UUID.fromString("c2dc131f-ff82-4bf4-a89c-1f448c5fedb1")
    private val registrationId = UUID.fromString("e7108803-5f73-4fd6-9262-a2dbb7ce3223")
    private val versionId = UUID.fromString("43c8b5c2-5daf-42d1-b576-7c2a6e18eedc")
    private val registrationPath = "/v1/events/$eventId/registration"
    private val authorization = "Bearer test-access-token"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        var token: String? = "test-access-token"
        val tokenManager = mockk<TokenManager>()
        every { tokenManager.getAccessToken() } answers { token }
        every { tokenManager.clearTokens() } answers { token = null }
        val client = NetworkModule.provideOkHttpClient(AuthInterceptor(tokenManager), TokenAuthenticator(tokenManager))
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(NetworkModule.provideJson().asConverterFactory("application/json".toMediaType()))
            .build()
        repository = RegistrationsModule.provideRegistrationsRepository(
            RegistrationsModule.provideRegistrationsApi(retrofit),
            ClubsModule.provideEventsApi(retrofit),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loadsAuthenticatedRegistrationPageAndDecodesItsFields() = runTest {
        server.enqueue(jsonResponse(
            """{"items":[${registrationJson("interested")}],"total":21,"page":2,"size":20}"""
        ))

        val page = repository.getRegistrations(page = 2)

        val request = takeRequest()
        val url = requireNotNull(request.requestUrl)
        assertEquals("GET", request.method)
        assertEquals("/v1/me/registrations", url.encodedPath)
        assertEquals("2", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("size"))
        assertEquals(authorization, request.getHeader("Authorization"))
        assertEquals(21, page.total)
        assertEquals(2, page.page)
        assertEquals(20, page.propertySize)
        val registration = page.items.single()
        assertEquals(registrationId, registration.id)
        assertEquals(eventId, registration.eventId)
        assertEquals(RegistrationStatus.interested, registration.status)
        assertEquals(RegistrationSource.free_signup, registration.source)
        assertEquals(Visibility.friends_only, registration.visibility)
        assertEquals(OffsetDateTime.parse("2026-11-11T08:00:00Z"), registration.createdAt)
    }

    @Test
    fun retriesPublicEventDetailOnceAfterRejectedToken() = runTest {
        server.enqueue(errorResponse(401, "invalid_token"))
        server.enqueue(jsonResponse("""
            {"id":"$eventId","title":"Игротека","date":"2026-12-02","place_address":"НГУ",
             "is_free":true,"status":"confirmed","description":"Настольные игры",
             "organizer":{"type":"user"},"current_version_id":"$versionId","version_no":1}
        """.trimIndent()))

        val event = repository.getEvent(eventId)

        val first = takeRequest()
        val second = takeRequest()
        assertEquals("GET", first.method)
        assertEquals("/v1/events/$eventId", first.path)
        assertEquals(first.path, second.path)
        assertEquals(authorization, first.getHeader("Authorization"))
        assertNull(second.getHeader("Authorization"))
        assertEquals(2, server.requestCount)
        assertEquals(eventId, event.id)
        assertEquals("Игротека", event.title)
        assertEquals(LocalDate.of(2026, 12, 2), event.date)
        assertEquals("НГУ", event.placeAddress)
    }

    @Test
    fun eventDetailPreservesUnavailableEventCode() = runTest {
        server.enqueue(errorResponse(404, "event_not_found"))

        val error = assertFailsWith<RegistrationApiException> { repository.getEvent(eventId) }

        assertEquals(404, error.statusCode)
        assertEquals("event_not_found", error.errorCode)
        assertEquals("/v1/events/$eventId", takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun protectedRegistrationListDoesNotRetryAnonymously() = runTest {
        server.enqueue(errorResponse(401, "token_expired"))
        server.enqueue(jsonResponse("""{"items":[],"total":0,"page":1,"size":20}"""))

        val error = assertFailsWith<RegistrationApiException> { repository.getRegistrations(page = 1) }

        assertEquals(401, error.statusCode)
        assertEquals("token_expired", error.errorCode)
        assertEquals(authorization, takeRequest().getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun savesBothStatusesWithoutChangingVisibility() = runTest {
        server.enqueue(jsonResponse(registrationJson("going"), status = 201))
        server.enqueue(jsonResponse(registrationJson("interested")))

        val created = repository.saveRegistration(eventId, RegistrationStatus.going)
        val updated = repository.saveRegistration(eventId, RegistrationStatus.interested)

        assertEquals(RegistrationStatus.going, created.status)
        assertEquals(RegistrationStatus.interested, updated.status)
        assertEquals(Visibility.friends_only, updated.visibility)
        for (status in listOf("going", "interested")) {
            val request = takeRequest()
            assertEquals("PUT", request.method)
            assertEquals(registrationPath, request.path)
            assertEquals(authorization, request.getHeader("Authorization"))
            assertEquals(
                Json.parseToJsonElement("""{"status":"$status"}"""),
                Json.parseToJsonElement(request.body.readUtf8()),
            )
        }
    }

    @Test
    fun cancellationAcceptsNoContentAndAnAlreadyRemovedRegistration() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(errorResponse(404, "registration_not_found"))

        repeat(2) { repository.cancelRegistration(eventId) }

        repeat(2) {
            val request = takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals(registrationPath, request.path)
            assertEquals(authorization, request.getHeader("Authorization"))
        }
    }

    @Test
    fun cancellationPreservesOtherFailuresAndHandlesMalformedErrorEnvelopes() = runTest {
        server.enqueue(errorResponse(409, "registration_from_ticket"))
        server.enqueue(errorResponse(404, "event_not_found"))
        server.enqueue(jsonResponse("""{"error":{"code":123}}""", status = 404))
        server.enqueue(jsonResponse("<html>Unavailable</html>", status = 503))

        for ((status, code) in listOf(409 to "registration_from_ticket", 404 to "event_not_found", 404 to null, 503 to null)) {
            val error = assertFailsWith<RegistrationApiException> { repository.cancelRegistration(eventId) }
            assertEquals(status, error.statusCode)
            assertEquals(code, error.errorCode)
        }
        assertEquals(4, server.requestCount)
    }

    private fun takeRequest() = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))

    private fun registrationJson(status: String) = """
        {"id":"$registrationId","event_id":"$eventId","status":"$status","visibility":"friends_only",
         "source":"free_signup","created_at":"2026-11-11T08:00:00Z"}
    """.trimIndent()

    private fun errorResponse(status: Int, code: String) =
        jsonResponse("""{"error":{"code":"$code","message":"Ошибка"}}""", status)

    private fun jsonResponse(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
