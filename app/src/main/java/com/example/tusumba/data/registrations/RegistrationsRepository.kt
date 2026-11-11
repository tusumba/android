package com.example.tusumba.data.registrations

import com.example.tusumba.data.events.model.EventDetail
import com.example.tusumba.data.ticketing.model.Registration
import com.example.tusumba.data.ticketing.model.RegistrationPage
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import java.io.IOException
import java.util.UUID

interface RegistrationsRepository {
    suspend fun getRegistrations(page: Int): RegistrationPage

    suspend fun getEvent(eventId: UUID): EventDetail

    suspend fun saveRegistration(eventId: UUID, status: RegistrationStatus): Registration

    suspend fun cancelRegistration(eventId: UUID)
}

class RegistrationApiException(val statusCode: Int, val errorCode: String?) :
    IOException("Registration request failed with HTTP $statusCode")
