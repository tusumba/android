package com.example.tusumba.notifications

import com.example.tusumba.data.local.TokenManager
import com.example.tusumba.data.network.AuthInterceptor
import com.example.tusumba.data.notifications.api.DefaultApi
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
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class PushDeviceApiTest {
    private val server = MockWebServer()
    private val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private lateinit var api: PushDeviceApi

    @Before
    fun setUp() {
        server.start()
        val tokens = mockk<TokenManager>()
        every { tokens.getAccessToken() } returns "access_for_test"
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens)).build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(NetworkModule.provideJson().asConverterFactory("application/json".toMediaType())).build()
        api = PushDeviceApi(retrofit.create(DefaultApi::class.java))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun newAndRepeatedRegistrationSendCurrentTokenWithAuthentication() = runTest {
        for (status in listOf(201, 200)) {
            server.enqueue(MockResponse().setResponseCode(status)
                .setBody("""{"id":"$id","platform":"android","token":"updated_token"}"""))
            assertEquals(id, api.register("updated_token"))
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/me/devices", request.path)
            assertEquals("Bearer access_for_test", request.getHeader("Authorization"))
            assertEquals(
                Json.parseToJsonElement("""{"platform":"android","token":"updated_token"}"""),
                Json.parseToJsonElement(request.body.readUtf8()),
            )
        }
    }

    @Test
    fun deletionUsesReturnedDeviceIdAndMissingDeviceIsAlreadyRemoved() = runTest {
        for (status in listOf(204, 404)) {
            server.enqueue(MockResponse().setResponseCode(status))
            api.remove(id)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/v1/me/devices/$id", request.path)
        }
    }

    @Test
    fun unavailableServerAndExpiredSessionAreDistinguishable() = runTest {
        for (status in listOf(401, 503)) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
            try {
                api.register("test_token")
                throw AssertionError("Request should fail")
            } catch (error: PushDeviceApiException) {
                assertEquals(status, error.statusCode)
            }
        }
    }
}
