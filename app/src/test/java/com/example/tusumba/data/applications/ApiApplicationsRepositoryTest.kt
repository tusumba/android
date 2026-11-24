package com.example.tusumba.data.applications

import com.example.tusumba.data.moderation.api.ApplicationsApi
import com.example.tusumba.data.moderation.model.*
import com.example.tusumba.di.NetworkModule
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ApiApplicationsRepositoryTest {
    private val server = MockWebServer()
    private val json = NetworkModule.provideJson()
    private lateinit var repository: ApplicationsRepository
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val application = """{"id":"$id","type":"event","author_user_id":"$id","status":"rejected","created_at":"2026-11-25T00:00:00Z","moderator_comment":"Уточните адрес","event":{"title":"Концерт","description":"Описание","date":"2026-12-01","place_address":"НГУ","is_free":true}}"""
    @Before fun setup() {
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer test").build()) }.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(ApplicationsApi::class.java)
        repository = ApiApplicationsRepository(api, json)
    }
    @After fun cleanup() { server.shutdown() }
    private fun response(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test fun submitUsesModerationContractAndAuthorization() = runTest {
        server.enqueue(response(application, 201))
        repository.submit(ApplicationCreate(ApplicationType.event, event = EventApplicationData("Концерт", "Описание", LocalDate.of(2026,12,1), "НГУ", false, Money(12345, Money.Currency.RUB))))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/applications", request.path)
        assertEquals("Bearer test", request.getHeader("Authorization"))
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("event", body.getValue("type").jsonPrimitive.content)
        assertEquals(12345, body.getValue("event").jsonObject.getValue("price").jsonObject.getValue("amount").jsonPrimitive.int)
        assertFalse(body.containsKey("club_page"))
    }

    @Test fun listPaginatesAndDetailDecodesModeratorComment() = runTest {
        server.enqueue(response("""{"items":[$application],"total":21,"page":2,"size":20}"""))
        val page = repository.list(2)
        assertEquals(21, page.total)
        assertEquals("2", server.takeRequest().requestUrl?.queryParameter("page"))
        server.enqueue(response(application))
        assertEquals("Уточните адрес", repository.get(id).moderatorComment)
        assertEquals("/v1/me/applications/$id", server.takeRequest().path)
    }

    @Test fun validationAndDuplicateErrorsRemainStructured() = runTest {
        server.enqueue(response("""{"error":{"code":"validation_error","message":"Проверьте поля","details":[{"field":"event.date","message":"Дата в прошлом"}]}}""", 422))
        try { repository.get(id); fail() } catch (error: ApplicationFailure) {
            assertEquals("Дата в прошлом", error.fields["event.date"])
            assertEquals(422, error.status)
        }
        server.enqueue(response("""{"error":{"code":"application_duplicate","message":"Дубликат"}}""", 409))
        try { repository.get(id); fail() } catch (error: ApplicationFailure) { assertEquals("application_duplicate", error.code) }
        server.enqueue(response("unavailable", 503))
        try { repository.get(id); fail() } catch (error: ApplicationFailure) { assertTrue(error.message.contains("Попробуйте позже")) }
    }
}
