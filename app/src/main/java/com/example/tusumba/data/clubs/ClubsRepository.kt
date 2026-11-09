package com.example.tusumba.data.clubs

import com.example.tusumba.data.events.model.ClubCardPage
import com.example.tusumba.data.events.model.ClubDetail
import com.example.tusumba.data.events.model.EventCardPage
import java.util.UUID

interface ClubsRepository {
    suspend fun getClubs(query: String?, page: Int = 1): ClubCardPage

    suspend fun getClub(id: UUID): ClubDetail?

    suspend fun getEvents(clubId: UUID, page: Int = 1): EventCardPage
}
