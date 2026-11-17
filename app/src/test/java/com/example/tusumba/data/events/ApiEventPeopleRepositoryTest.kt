package com.example.tusumba.data.events

import com.example.tusumba.data.events.model.Organizer
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import com.example.tusumba.di.EventPeopleModule
import com.example.tusumba.di.NetworkModule
import java.io.IOException
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ApiEventPeopleRepositoryTest {
    private val eventId = UUID.randomUUID()
    private val firstUser = UUID.randomUUID()
    private val secondUser = UUID.randomUUID()
    private val responses = mutableMapOf<String, MockResponse>()
    private lateinit var server: MockWebServer
    private lateinit var repository: EventPeopleRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    responses[request.path] ?: MockResponse().setResponseCode(404)
            }
        server.start()
        val retrofit =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(
                    NetworkModule.provideJson().asConverterFactory("application/json".toMediaType())
                )
                .build()
        repository = EventPeopleModule.providePeopleRepository(retrofit)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun clubOrganizerUsesTheEventsNameWithoutLookingUpAPersonalProfile() = runTest {
        assertEquals(
            "Научный клуб",
            repository.getOrganizerName(Organizer(Organizer.Type.club, clubName = "Научный клуб")),
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun userOrganizerLoadsOnlyThePublicNickname() = runTest {
        responses["/v1/users/$firstUser"] = json("""{"id":"$firstUser","nickname":"Организатор"}""")
        assertEquals(
            "Организатор",
            repository.getOrganizerName(Organizer(Organizer.Type.user, userId = firstUser)),
        )
        assertEquals("/v1/users/$firstUser", server.takeRequest().path)
    }

    @Test
    fun unavailableOrMismatchedPublicProfileDoesNotInventAnOrganizerName() = runTest {
        assertNull(repository.getOrganizerName(Organizer(Organizer.Type.user, userId = firstUser)))
        responses["/v1/users/$firstUser"] = json("""{"id":"$secondUser","nickname":"Другой"}""")
        assertNull(repository.getOrganizerName(Organizer(Organizer.Type.user, userId = firstUser)))
    }

    @Test
    fun friendsAreLoadedAcrossPagesAndNamesAreResolvedThroughPublicProfiles() = runTest {
        responses["/v1/events/$eventId/friends-going?page=1&size=100"] =
            json(page(friend(firstUser, "going"), 101, 1))
        responses["/v1/events/$eventId/friends-going?page=2&size=100"] =
            json(page(friend(secondUser, "interested") + "," + friend(firstUser, "going"), 101, 2))
        responses["/v1/users/$firstUser"] = json("""{"id":"$firstUser","nickname":"Саша"}""")
        responses["/v1/users/$secondUser"] = json("""{"id":"$secondUser","nickname":"Маша"}""")
        val friends = repository.getFriendsGoing(eventId)
        assertEquals(listOf("Саша", "Маша"), friends.map { it.nickname })
        assertEquals(
            listOf(RegistrationStatus.going, RegistrationStatus.interested),
            friends.map { it.status },
        )
        assertEquals(4, server.requestCount)
        val paths = List(4) { server.takeRequest().path }
        assertTrue(
            paths.all {
                it?.startsWith("/v1/events/") == true || it?.startsWith("/v1/users/") == true
            }
        )
    }

    @Test
    fun emptyFriendsDoNotTriggerProfileRequests() = runTest {
        responses["/v1/events/$eventId/friends-going?page=1&size=100"] = json(page("", 0, 1))
        assertTrue(repository.getFriendsGoing(eventId).isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun unknownFriendsApiAndInvalidPaginationAreReportedAsUnavailableData() = runTest {
        assertFailsWith<IOException> { repository.getFriendsGoing(eventId) }
        responses["/v1/events/$eventId/friends-going?page=1&size=100"] =
            json("""{"items":[],"total":0,"page":0,"size":0}""")
        assertFailsWith<IOException> { repository.getFriendsGoing(eventId) }
        assertEquals(2, server.requestCount)
    }

    private fun friend(id: UUID, status: String) =
        """{"user_id":"$id","status":"$status","source":"free_signup"}"""

    private fun page(items: String, total: Int, page: Int) =
        """{"items":[$items],"total":$total,"page":$page,"size":100}"""

    private fun json(body: String) =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
