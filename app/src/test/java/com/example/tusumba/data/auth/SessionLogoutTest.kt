package com.example.tusumba.data.auth

import com.example.tusumba.data.local.TokenManager
import com.example.tusumba.data.network.AuthInterceptor
import com.example.tusumba.data.network.TokenAuthenticator
import com.example.tusumba.di.LogoutModule
import com.example.tusumba.di.NetworkModule
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SessionLogoutTest {
    private lateinit var server: MockWebServer
    private lateinit var tokenManager: TokenManager
    private lateinit var logout: SessionLogout

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenManager = mockk()
        every { tokenManager.getAccessToken() } returns "test_access_token"
        every { tokenManager.getRefreshToken() } returns "test_refresh_token"
        every { tokenManager.clearTokens() } just Runs

        val sharedClient = NetworkModule.provideOkHttpClient(
            AuthInterceptor(tokenManager),
            TokenAuthenticator(tokenManager),
        )
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(sharedClient)
            .addConverterFactory(NetworkModule.provideJson().asConverterFactory("application/json".toMediaType()))
            .build()
        logout = SessionLogout(LogoutModule.provideIdentityLogoutApi(retrofit), tokenManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun successfulLogoutSendsTheRefreshTokenWithoutBearerAndClearsLocalTokens() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        logout()

        val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/v1/auth/logout", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals(
            Json.parseToJsonElement("""{"refresh_token":"test_refresh_token"}"""),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
        verify(exactly = 0) { tokenManager.getAccessToken() }
        verify(exactly = 1) { tokenManager.clearTokens() }
    }

    @Test
    fun serverFailureStillClearsLocalTokens() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"code":"internal_error"}}"""))

        logout()

        assertEquals(1, server.requestCount)
        verify(exactly = 1) { tokenManager.clearTokens() }
    }

    @Test
    fun unauthorizedLogoutDoesNotInvokeTheSharedAuthenticator() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"invalid_refresh_token"}}"""))

        logout()

        assertEquals(1, server.requestCount)
        verify(exactly = 1) { tokenManager.clearTokens() }
    }

    @Test
    fun transportFailureStillClearsLocalTokens() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        logout()

        verify(exactly = 1) { tokenManager.clearTokens() }
    }

    @Test
    fun missingAndBlankRefreshTokensOnlyClearLocalTokens() = runTest {
        every { tokenManager.getRefreshToken() } returnsMany listOf(null, " \t ")

        logout()
        logout()

        assertEquals(0, server.requestCount)
        verify(exactly = 2) { tokenManager.clearTokens() }
    }

    @Test
    fun cancellationPropagatesAndStillClearsLocalTokens() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        var completedNormally = false
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            logout()
            completedNormally = true
        }
        requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))

        request.cancelAndJoin()

        assertFalse(completedNormally)
        verify(exactly = 1) { tokenManager.clearTokens() }
    }
}
