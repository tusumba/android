package com.example.tusumba.data.clubs

import com.example.tusumba.data.events.api.ClubsApi
import com.example.tusumba.data.events.api.EventsApi
import com.example.tusumba.data.events.model.EventStatus
import com.example.tusumba.data.local.TokenManager
import com.example.tusumba.data.network.AuthInterceptor
import com.example.tusumba.data.network.TokenAuthenticator
import com.example.tusumba.di.NetworkModule
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ApiClubsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ClubsRepository
    private val clubId = UUID.fromString("e7108803-5f73-4fd6-9262-a2dbb7ce3223")
    private val versionId = UUID.fromString("43c8b5c2-5daf-42d1-b576-7c2a6e18eedc")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = createRepository()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun searchEncodesTheTrimmedQueryAndRequestsTheSelectedPage() = runTest {
        server.enqueue(jsonResponse("""{"items":[],"total":0,"page":2,"size":20}"""))

        val page = repository.getClubs("  Музыка & спорт/дети  ", page = 2)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        val url = requireNotNull(request.requestUrl)
        assertEquals("/v1/clubs", url.encodedPath)
        assertEquals("Музыка & спорт/дети", url.queryParameter("query"))
        assertTrue(requireNotNull(url.encodedQuery).contains("%26"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("size"))
        assertEquals(2, page.page)
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun blankSearchOmitsQueryAndDecodesClubCards() = runTest {
        server.enqueue(jsonResponse("""
            {"items":[{"id":"$clubId","name":"Настольные игры","description":"Игры по пятницам","photo_url":null}],"total":1,"page":1,"size":20}
        """.trimIndent()))

        val page = repository.getClubs(" \t ")

        assertFalse(requireNotNull(server.takeRequest().requestUrl).queryParameterNames.contains("query"))
        assertEquals(1, page.total)
        assertEquals(20, page.propertySize)
        assertEquals(clubId, page.items.single().id)
        assertEquals("Настольные игры", page.items.single().name)
        assertNull(page.items.single().photoUrl)
    }

    @Test
    fun detailDecodesMeetingInformationAndArbitraryContactChannels() = runTest {
        server.enqueue(jsonResponse("""
            {
              "id":"$clubId","name":"Настольные игры","description":"Играем вместе",
              "current_version_id":"$versionId","version_no":3,"is_admin":false,
              "meeting_place":"НГУ, аудитория 101","schedule":"Пятница, 18:00",
              "contacts":{"telegram":"@nsu_boardgames","email":"games@example.org"},
              "photo_url":"/v1/clubs/$clubId/photo"
            }
        """.trimIndent()))

        val club = requireNotNull(repository.getClub(clubId))

        assertEquals("/v1/clubs/$clubId", server.takeRequest().path)
        assertEquals(clubId, club.id)
        assertEquals(versionId, club.currentVersionId)
        assertEquals(3, club.versionNo)
        assertFalse(club.isAdmin)
        assertEquals("НГУ, аудитория 101", club.meetingPlace)
        assertEquals("Пятница, 18:00", club.schedule)
        assertEquals(mapOf("telegram" to "@nsu_boardgames", "email" to "games@example.org"), club.contacts)
        assertEquals("/v1/clubs/$clubId/photo", club.photoUrl)
    }

    @Test
    fun clubEventsUseTheClubFilterAndDecodeDatesAndPrices() = runTest {
        val eventId = UUID.fromString("c2dc131f-ff82-4bf4-a89c-1f448c5fedb1")
        server.enqueue(jsonResponse("""
            {"items":[{
              "id":"$eventId","title":"Игротека","date":"2026-11-20",
              "place_address":"НГУ","is_free":false,"status":"confirmed",
              "price":{"amount":30000,"currency":"RUB"},"club_id":"$clubId"
            }],"total":21,"page":2,"size":20}
        """.trimIndent()))

        val page = repository.getEvents(clubId, page = 2)

        val url = requireNotNull(server.takeRequest().requestUrl)
        assertEquals("/v1/events", url.encodedPath)
        assertEquals(clubId.toString(), url.queryParameter("club_id"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("size"))
        val event = page.items.single()
        assertEquals(eventId, event.id)
        assertEquals(clubId, event.clubId)
        assertEquals(LocalDate.of(2026, 11, 20), event.date)
        assertEquals(EventStatus.confirmed, event.status)
        assertEquals(30000, requireNotNull(event.price).amount)
    }

    @Test
    fun blockedClubIsMissingButServerFailurePropagates() = runTest {
        server.enqueue(jsonResponse("""{"error":{"code":"club_not_found","message":"Секция недоступна."}}""", 404))
        assertNull(repository.getClub(clubId))

        server.enqueue(jsonResponse("""{"error":{"code":"internal_error","message":"Ошибка сервиса"}}""", 500))
        try {
            repository.getClub(clubId)
            throw AssertionError("Expected the server error")
        } catch (error: HttpException) {
            assertEquals(500, error.code())
        }
    }

    @Test
    fun rejectedTokenRetriesThePublicRequestWithoutAuthorization() = runTest {
        repository = authenticatedRepository()
        server.enqueue(jsonResponse("""{"error":{"code":"invalid_token","message":"Недействительный токен"}}""", 401))
        server.enqueue(jsonResponse("""{"items":[],"total":0,"page":1,"size":20}"""))

        val page = repository.getClubs("шахматы")

        assertTrue(page.items.isEmpty())
        assertEquals(2, server.requestCount)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer mock-access-token", first.getHeader("Authorization"))
        assertNull(second.getHeader("Authorization"))
        assertEquals(first.path, second.path)
        assertEquals("GET", second.method)
    }

    @Test
    fun anonymousUnauthorizedResponseDoesNotRetry() = runTest {
        server.enqueue(jsonResponse("""{"error":{"code":"not_authenticated","message":"Требуется авторизация"}}""", 401))

        try {
            repository.getClubs(null)
            throw AssertionError("Expected the anonymous authorization failure")
        } catch (error: HttpException) {
            assertEquals(401, error.code())
        }
        assertEquals(1, server.requestCount)
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun rejectedAnonymousRetryPropagatesWithoutFurtherRequests() = runTest {
        repository = authenticatedRepository()
        server.enqueue(jsonResponse("""{"error":{"code":"invalid_token","message":"Недействительный токен"}}""", 401))
        server.enqueue(jsonResponse("""{"error":{"code":"not_authenticated","message":"Требуется авторизация"}}""", 401))
        server.enqueue(jsonResponse("{}", 500))

        try {
            repository.getClubs(null)
            throw AssertionError("Expected the retried authorization failure")
        } catch (error: HttpException) {
            assertEquals(401, error.code())
        }
        assertEquals(2, server.requestCount)
        assertEquals("Bearer mock-access-token", server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun emptyAndMalformedSuccessfulResponsesAreIoFailures() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        try {
            repository.getClubs(null)
            throw AssertionError("Expected the empty response failure")
        } catch (_: IOException) {
            // Empty transport success must appear as a retryable failure in the UI.
        }

        server.enqueue(jsonResponse("""{"items":[]}"""))
        try {
            repository.getClubs(null)
            throw AssertionError("Expected the malformed response failure")
        } catch (_: IOException) {
            // Missing required page fields must not masquerade as an empty catalog.
        }

        server.enqueue(jsonResponse("""
            {"items":[{"id":"invalid-uuid","name":"Секция","description":"Описание"}],"total":1,"page":1,"size":20}
        """.trimIndent()))
        try {
            repository.getClubs(null)
            throw AssertionError("Expected the invalid UUID failure")
        } catch (_: IOException) {
            // The generated UUID adapter throws IllegalArgumentException.
        }

        server.enqueue(jsonResponse("""
            {"items":[{
              "id":"$clubId","title":"Мероприятие","date":"2026-99-99",
              "place_address":"НГУ","is_free":true,"status":"confirmed"
            }],"total":1,"page":1,"size":20}
        """.trimIndent()))
        try {
            repository.getEvents(clubId)
            throw AssertionError("Expected the invalid date failure")
        } catch (_: IOException) {
            // The generated LocalDate adapter throws DateTimeParseException.
        }
    }

    private fun jsonResponse(body: String, status: Int = 200): MockResponse =
        MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun authenticatedRepository(): ClubsRepository {
        var accessToken: String? = "mock-access-token"
        val tokenManager = mockk<TokenManager>()
        every { tokenManager.getAccessToken() } answers { accessToken }
        every { tokenManager.clearTokens() } answers { accessToken = null }
        return createRepository(
            NetworkModule.provideOkHttpClient(AuthInterceptor(tokenManager), TokenAuthenticator(tokenManager))
        )
    }

    private fun createRepository(client: OkHttpClient = OkHttpClient()): ClubsRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(NetworkModule.provideJson().asConverterFactory("application/json".toMediaType()))
            .build()
        return ApiClubsRepository(
            retrofit.create(ClubsApi::class.java),
            retrofit.create(EventsApi::class.java)
        )
    }
}
