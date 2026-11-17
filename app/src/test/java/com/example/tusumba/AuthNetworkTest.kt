package com.example.tusumba

import com.example.tusumba.data.auth.AuthResult
import com.example.tusumba.data.auth.NetworkAuthRepository
import com.example.tusumba.data.identity.api.DefaultApi
import com.example.tusumba.data.local.TokenManager
import com.example.tusumba.data.network.AuthInterceptor
import com.example.tusumba.data.network.OffsetDateTimeSerializer
import com.example.tusumba.data.network.TokenAuthenticator
import com.example.tusumba.data.network.UUIDSerializer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
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
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Provider

class AuthNetworkTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenManager: TokenManager
    private lateinit var authApi: DefaultApi
    private lateinit var repository: NetworkAuthRepository

    private val testUuid = "00000000-0000-0000-0000-000000000001"

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenManager = mockk(relaxed = true)

        val authInterceptor = AuthInterceptor(tokenManager)
        val apiProvider = Provider { authApi }
        val tokenAuthenticator = TokenAuthenticator(tokenManager, apiProvider)

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .build()

        val json = Json {
            ignoreUnknownKeys = true
            serializersModule = SerializersModule {
                contextual(UUID::class, UUIDSerializer)
                contextual(OffsetDateTime::class, OffsetDateTimeSerializer)
            }
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        authApi = retrofit.create(DefaultApi::class.java)
        repository = NetworkAuthRepository(authApi, tokenManager)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ДОБАВЛЕНО ПОЛЕ created_at
    private fun authResponseJson(access: String = "access_123", refresh: String = "refresh_123") = """
        {
          "access_token": "$access",
          "refresh_token": "$refresh",
          "expires_in": 3600,
          "user": {
            "id": "$testUuid",
            "email": "test@mail.ru",
            "nickname": "tester",
            "global_role": "user",
            "created_at": "2023-01-01T00:00:00Z"
          }
        }
    """.trimIndent()

    private fun tokenPairJson(access: String = "new_access", refresh: String = "new_refresh") = """
        {
          "access_token": "$access",
          "refresh_token": "$refresh",
          "expires_in": 3600
        }
    """.trimIndent()

    @Test
    fun `signIn success parses tokens and returns user`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(authResponseJson()))

        val result = repository.signIn("test@mail.ru", "password")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).user
        assertEquals("tester", user.nickname)

        verify { tokenManager.saveTokens("access_123", "refresh_123") }

        val request = mockWebServer.takeRequest()
        assertEquals("/v1/auth/login", request.path)
    }

    @Test
    fun `signIn with invalid credentials returns InvalidCredentials`() = runTest {
        every { tokenManager.getAccessToken() } returns null
        every { tokenManager.getRefreshToken() } returns null

        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        val result = repository.signIn("test@mail.ru", "wrong_password")

        assertEquals(AuthResult.InvalidCredentials, result)
    }

    @Test
    fun `authInterceptor adds Authorization header`() = runTest {
        every { tokenManager.getAccessToken() } returns "my_jwt_token"

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(authResponseJson()))

        authApi.login(com.example.tusumba.data.identity.model.LoginRequest("test", "test"))

        val request = mockWebServer.takeRequest()
        assertEquals("Bearer my_jwt_token", request.getHeader("Authorization"))
    }

    @Test
    fun `tokenAuthenticator refreshes token on 401 and retries request`() = runTest {
        every { tokenManager.getAccessToken() } returns "expired_access"
        every { tokenManager.getRefreshToken() } returns "valid_refresh"

        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(tokenPairJson()))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(authResponseJson(access = "new_access")))

        val result = repository.signIn("test@mail.ru", "password")

        assertTrue(result is AuthResult.Success)
        assertEquals(3, mockWebServer.requestCount)

        val request1 = mockWebServer.takeRequest()
        assertEquals("/v1/auth/login", request1.path)
        assertEquals("Bearer expired_access", request1.getHeader("Authorization"))

        val request2 = mockWebServer.takeRequest()
        assertEquals("/v1/auth/refresh", request2.path)

        val request3 = mockWebServer.takeRequest()
        assertEquals("/v1/auth/login", request3.path)
        assertEquals("Bearer new_access", request3.getHeader("Authorization"))

        verify { tokenManager.saveTokens("new_access", "new_refresh") }
    }
}