package com.example.tusumba.notifications

import android.content.Intent
import java.util.UUID

sealed interface PushDestination {
    data class Event(val id: UUID) : PushDestination
    data object Registrations : PushDestination

    companion object {
        fun from(data: Map<String, String>): PushDestination? {
            validId(data["event_id"])?.let { return Event(it) }
            if (validId(data["registration_id"]) != null || validId(data["ticket_id"]) != null) return Registrations
            return null
        }

        fun from(intent: Intent): PushDestination? = from(
            listOf("event_id", "registration_id", "ticket_id").mapNotNull { key ->
                intent.getStringExtra(key)?.let { key to it }
            }.toMap()
        )

        private fun validId(value: String?): UUID? = value?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
    }
}
