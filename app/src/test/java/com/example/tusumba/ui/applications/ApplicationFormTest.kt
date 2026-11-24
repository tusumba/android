package com.example.tusumba.ui.applications

import com.example.tusumba.data.moderation.model.ApplicationType
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ApplicationFormTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val valid = ApplicationForm(title = "Концерт", description = "Описание", date = today.toString(), place = "НГУ")

    @Test fun requiredFieldsAndPastDateAreRejected() {
        assertEquals(setOf("event.title", "event.description", "event.date", "event.place_address"), ApplicationForm().errors(today).keys)
        assertEquals("Дата мероприятия не может быть в прошлом.", valid.copy(date = "2026-11-24").errors(today)["event.date"])
        assertTrue(valid.errors(today).isEmpty())
        assertTrue(valid.copy(date = "2026-02-30").errors(today).containsKey("event.date"))
    }

    @Test fun priceUsesExactKopecksAndRejectsOverflowAndFractions() {
        assertEquals(12345, valid.copy(price = "123,45").priceInKopecks())
        listOf("", "-1", "1.001", "NaN", "1e3", "9999999999999").forEach {
            assertTrue(valid.copy(free = false, price = it).errors(today).containsKey("event.price"))
        }
        assertEquals(12345, valid.copy(free = false, price = "123.45").request().event?.price?.amount)
        assertNull(valid.copy(price = "123").request().event?.price)
    }

    @Test fun clubRequestContainsOnlyClubDataAndTrimmedContacts() {
        val form = ApplicationForm(type = ApplicationType.club_page, title = " Клуб ", description = " Игры ", telegram = " @club ")
        assertTrue(form.errors(today).isEmpty())
        val request = form.request()
        assertNull(request.event)
        assertEquals("Клуб", request.clubPage?.name)
        assertEquals(mapOf("telegram" to "@club"), request.clubPage?.contacts)
        assertTrue(form.copy(description = "x".repeat(1001)).errors(today).containsKey("club_page.description"))
    }
}
