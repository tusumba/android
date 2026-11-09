package com.example.tusumba

import com.example.tusumba.data.local.TokenManager
import com.example.tusumba.data.network.AuthInterceptor
import com.example.tusumba.data.notifications.api.DefaultApi
import io.mockk.every
import io.mockk.mockk
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

class NetworkIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: DefaultApi
    private lateinit var tokenManager: TokenManager

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenManager = mockk()
        every { tokenManager.getAccessToken() } returns "test_jwt_token_123"

        val authInterceptor = AuthInterceptor(tokenManager)
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        api = retrofit.create(DefaultApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `healthcheck request sends JWT and parses response`() = runTest {
        // 1. Готовим ответ от фейкового сервера
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody("""{"push_enabled": true, "email_enabled": false}""")
        mockWebServer.enqueue(mockResponse)

        // 2. Делаем запрос (теперь забираем Response<...>)
        val response = api.getNotificationSettings()

        // 3. Проверяем, что запрос реально ушел
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/v1/me/notification-settings", recordedRequest.path)
        assertEquals("GET", recordedRequest.method)

        // 4. САМОЕ ГЛАВНОЕ: Проверяем, что подставился JWT!
        assertEquals("Bearer test_jwt_token_123", recordedRequest.getHeader("Authorization"))

        // 5. Проверяем, что ответ успешный и достаем тело
        assertTrue(response.isSuccessful)
        val result = response.body() ?: error("Body is null")

        assertEquals(true, result.pushEnabled)
    }
}