package com.example.tusumba.data.clubs

import com.example.tusumba.data.events.api.ClubsApi
import com.example.tusumba.data.events.api.PhotosApi
import com.example.tusumba.data.events.model.ClubUpdate
import com.example.tusumba.data.local.TokenManager
import com.example.tusumba.data.network.AuthInterceptor
import com.example.tusumba.di.NetworkModule
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ClubEditingRepositoryTest {
    private val server = MockWebServer()
    private val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private lateinit var repository: ClubEditingRepository

    @Before
    fun setUp() {
        server.start()
        val session = mockk<TokenManager>()
        every { session.getAccessToken() } returns "test_access"
        val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).build())
            .addConverterFactory(NetworkModule.provideJson().asConverterFactory("application/json".toMediaType()))
            .build()
        repository = ClubEditingRepository(retrofit.create(ClubsApi::class.java), retrofit.create(PhotosApi::class.java))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun schedulePatchOnlySendsChangedFieldsAndBearerToken() = runTest {
        server.enqueue(clubResponse())
        val club = repository.updateClub(id, ClubUpdate(schedule = "Пятница, 19:00"))
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/v1/clubs/$id", request.path)
        assertEquals("Bearer test_access", request.getHeader("Authorization"))
        assertEquals(Json.parseToJsonElement("""{"schedule":"Пятница, 19:00"}"""), Json.parseToJsonElement(request.body.readUtf8()))
        assertEquals("Пятница, 19:00", club.schedule)
    }

    @Test
    fun optionalFieldsCanBeClearedWithoutSendingNullOrOmittingEmptyContacts() = runTest {
        server.enqueue(clubResponse())
        repository.updateClub(id, ClubUpdate(meetingPlace = "", schedule = "", contacts = emptyMap()))
        assertEquals(
            Json.parseToJsonElement("""{"meeting_place":"","schedule":"","contacts":{}}"""),
            Json.parseToJsonElement(server.takeRequest().body.readUtf8()),
        )
    }

    @Test
    fun uploadUsesPutMultipartAndTheFileField() = runTest {
        server.enqueue(MockResponse().setBody("""{"photo_url":"/v1/clubs/$id/photo"}"""))
        val url = repository.uploadPhoto(id, ClubPhoto("test_image".toByteArray(), "image/png"))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/clubs/$id/photo", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data;"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"file\"; filename=\"cover.png\""))
        assertTrue(body.contains("Content-Type: image/png"))
        assertTrue(body.contains("test_image"))
        assertEquals("/v1/clubs/$id/photo", url)
    }

    @Test
    fun successfulPhotoDeletionUsesDeleteWithoutARequestBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        repository.deletePhoto(id)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v1/clubs/$id/photo", request.path)
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun writeFailuresPreserveTheirStatusAndNeverRetryAsAGuest() = runTest {
        for (status in listOf(401, 403, 404, 422, 503)) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
            try {
                repository.updateClub(id, ClubUpdate(schedule = "Вторник"))
                throw AssertionError("Expected failure")
            } catch (error: ClubEditApiException) {
                assertEquals(status, error.statusCode)
            }
        }
        assertEquals(5, server.requestCount)
    }

    private fun clubResponse() = MockResponse().setBody("""{
        "id":"$id","name":"Шахматы","description":"Играем вместе",
        "schedule":"Пятница, 19:00","current_version_id":"$id","version_no":2,"is_admin":true
    }""")
}
