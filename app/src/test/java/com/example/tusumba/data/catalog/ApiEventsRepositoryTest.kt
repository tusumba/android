package com.example.tusumba.data.catalog

import com.example.tusumba.data.events.api.EventsApi
import com.example.tusumba.di.NetworkModule
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ApiEventsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: EventsRepository
    private val id = "9f1e6a52-3c4b-4d7e-8a10-000000000001"
    private val freeCard =
        """{"id":"$id","title":"Лекторий","date":"2026-11-26","place_address":"НГУ","is_free":true,"status":"confirmed","club_id":null,"photo_url":null}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = createRepository()
    }

    @After fun tearDown() = server.shutdown()

    @Test
    fun allFiltersAreSentTogetherWithRepeatedStatusesAndPagination() = runTest {
        server.enqueue(response("""{"items":[],"total":0,"page":2,"size":20}"""))
        val filters =
            CatalogFilters(
                query = "  Лекция & спорт  ",
                isFree = false,
                dateFrom = LocalDate.of(2026, 11, 12),
                dateTo = LocalDate.of(2026, 12, 1),
                place = "  НГУ / корпус & зал  ",
                statuses = setOf(EventStatus.CONFIRMED, EventStatus.NOT_CONFIRMED),
            )
        val page = repository.getEvents(filters, page = 2)
        val request = server.takeRequest()
        val url = requireNotNull(request.requestUrl)
        assertEquals("GET", request.method)
        assertEquals("/v1/events", url.encodedPath)
        assertEquals("Лекция & спорт", url.queryParameter("query"))
        assertEquals("false", url.queryParameter("is_free"))
        assertEquals("2026-11-12", url.queryParameter("date_from"))
        assertEquals("2026-12-01", url.queryParameter("date_to"))
        assertEquals("НГУ / корпус & зал", url.queryParameter("place"))
        assertEquals(
            setOf("confirmed", "not_confirmed"),
            url.queryParameterValues("status").toSet(),
        )
        assertTrue(requireNotNull(url.encodedQuery).contains("%26"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("size"))
        assertEquals(2, page.page)
        assertFalse(page.hasMore)
    }

    @Test
    fun blankFiltersAreOmittedAndFreeCardIsDecoded() = runTest {
        server.enqueue(response("""{"items":[$freeCard],"total":21,"page":1,"size":20}"""))
        val page = repository.getEvents(CatalogFilters(query = "  ", place = "  "))
        val url = requireNotNull(server.takeRequest().requestUrl)
        assertEquals(setOf("page", "size"), url.queryParameterNames)
        assertTrue(page.hasMore)
        val event = page.items.single()
        assertEquals(id, event.id)
        assertEquals(LocalDate.of(2026, 11, 26), event.date)
        assertEquals("НГУ", event.placeAddress)
        assertEquals(EventStatus.CONFIRMED, event.status)
        assertNull(event.price)
        assertNull(event.clubId)
        assertNull(event.photoUrl)
    }

    @Test
    fun paidPriceIsMappedInKopecksAndLastPageEndsPagination() = runTest {
        val paid =
            """{"id":"$id","title":"Концерт","date":"2026-11-26","place_address":"НГУ","is_free":false,"price":{"amount":125050,"currency":"RUB"},"status":"not_confirmed"}"""
        server.enqueue(response("""{"items":[$paid],"total":21,"page":2,"size":20}"""))
        val page = repository.getEvents(page = 2)
        assertEquals(Money(125050), page.items.single().price)
        assertEquals(EventStatus.NOT_CONFIRMED, page.items.single().status)
        assertFalse(page.hasMore)
    }

    @Test
    fun serverFailureAndMissingBodyAreErrors() = runTest {
        server.enqueue(response("{}", 503))
        try {
            repository.getEvents()
            fail("Expected HTTP error")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
        server.enqueue(MockResponse().setResponseCode(204))
        expectIo { repository.getEvents() }
    }

    @Test
    fun malformedJsonDatesAndPaginationDoNotBecomeAnEmptyCatalog() = runTest {
        val bodies =
            listOf(
                "broken json",
                """{"items":[],"total":0,"page":0,"size":0}""",
                """{"items":[$freeCard],"total":1,"page":1,"size":20}"""
                    .replace("2026-11-26", "2026-15-99"),
            )
        for (body in bodies) {
            server.enqueue(response(body))
            expectIo { repository.getEvents() }
        }
    }

    @Test
    fun rejectedTokenRetriesAsGuestAfterTheClientClearsIt() = runTest {
        var token: String? = "expired"
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val builder = chain.request().newBuilder()
                    token?.let { builder.header("Authorization", "Bearer " + it) }
                    chain.proceed(builder.build())
                }
                .authenticator { _, _ ->
                    token = null
                    null
                }
                .build()
        repository = createRepository(client)
        server.enqueue(response("{}", 401))
        server.enqueue(response("""{"items":[],"total":0,"page":1,"size":20}"""))
        assertTrue(repository.getEvents().items.isEmpty())
        assertEquals("Bearer expired", server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun guestUnauthorizedResponseIsNotRetried() = runTest {
        server.enqueue(response("{}", 401))
        try {
            repository.getEvents()
            fail("Expected unauthorized")
        } catch (error: HttpException) {
            assertEquals(401, error.code())
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun invalidDatesAreRejectedBeforeAnHttpRequest() = runTest {
        try {
            repository.getEvents(
                CatalogFilters(
                    dateFrom = LocalDate.of(2026, 12, 1),
                    dateTo = LocalDate.of(2026, 11, 1),
                )
            )
            fail("Expected invalid filters")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, server.requestCount)
        }
        val query = "🎵".repeat(100)
        server.enqueue(response("""{"items":[],"total":0,"page":1,"size":20}"""))
        repository.getEvents(CatalogFilters(query = query))
        assertEquals(query, requireNotNull(server.takeRequest().requestUrl).queryParameter("query"))
    }

    private fun createRepository(client: OkHttpClient = OkHttpClient()) =
        ApiEventsRepository(
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(client)
                .addConverterFactory(
                    NetworkModule.provideJson().asConverterFactory("application/json".toMediaType())
                )
                .build()
                .create(EventsApi::class.java)
        )

    private fun response(body: String, code: Int = 200) =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private suspend fun expectIo(call: suspend () -> Unit) {
        try {
            call()
            fail("Expected invalid response")
        } catch (_: IOException) {}
    }
}
