package com.example.tusumba.data.events

import com.example.tusumba.data.events.model.Organizer
import com.example.tusumba.data.identity.api.DefaultApi as IdentityApi
import com.example.tusumba.data.ticketing.api.ParticipantsApi
import com.example.tusumba.data.ticketing.model.FriendGoing
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class EventFriend(val id: UUID, val nickname: String, val status: RegistrationStatus)

interface EventPeopleRepository {
    suspend fun getOrganizerName(organizer: Organizer): String?

    suspend fun getFriendsGoing(eventId: UUID): List<EventFriend>
}

class ApiEventPeopleRepository(
    private val identityApi: IdentityApi,
    private val participantsApi: ParticipantsApi,
) : EventPeopleRepository {
    override suspend fun getOrganizerName(organizer: Organizer): String? =
        when (organizer.type) {
            Organizer.Type.club -> organizer.clubName?.takeIf { it.isNotBlank() }
            Organizer.Type.user -> organizer.userId?.let { getNickname(it) }
        }

    override suspend fun getFriendsGoing(eventId: UUID): List<EventFriend> =
        try {
            loadFriends(eventId)
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid friends response", error)
        }

    private suspend fun loadFriends(eventId: UUID): List<EventFriend> {
        val friends = mutableListOf<FriendGoing>()
        var page = 1
        while (true) {
            val response = participantsApi.listFriendsGoing(eventId, page = page, size = 100)
            if (!response.isSuccessful)
                throw IOException("Friends request failed with HTTP ${response.code()}")
            val result = response.body() ?: throw IOException("Empty friends response")
            if (result.page != page || result.propertySize !in 1..100 || result.total < 0) {
                throw IOException("Invalid friends pagination")
            }
            friends.addAll(result.items)
            if (result.items.isEmpty() || page.toLong() * result.propertySize >= result.total) break
            page++
        }
        return coroutineScope {
            val permits = Semaphore(3)
            friends
                .distinctBy { it.userId }
                .map { friend ->
                    async {
                        permits.withPermit {
                            getNickname(friend.userId)?.let {
                                EventFriend(friend.userId, it, friend.status)
                            }
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    private suspend fun getNickname(userId: UUID): String? =
        try {
            val response = identityApi.getUser(userId)
            response
                .body()
                ?.takeIf { response.isSuccessful && it.id == userId }
                ?.nickname
                ?.takeIf { it.isNotBlank() }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
