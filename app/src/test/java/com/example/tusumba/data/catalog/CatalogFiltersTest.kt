package com.example.tusumba.data.catalog

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFiltersTest {
    @Test
    fun searchMatchesTitleIgnoringCaseAndSurroundingSpaces() = runTest {
        val result = MockEventsRepository().getEvents().filteredBy(CatalogFilters("  ШАХМАТ  "))

        assertEquals(listOf("Турнир по шахматам"), result.map { it.title })
    }

    @Test
    fun searchDoesNotMatchPlaceAddress() = runTest {
        val result = MockEventsRepository().getEvents().filteredBy(CatalogFilters("парк"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun queryAndIsFreeAreAppliedTogether() = runTest {
        val events = MockEventsRepository().getEvents()

        assertTrue(events.filteredBy(CatalogFilters("мастерская", isFree = true)).isEmpty())
        assertEquals(
            listOf("Мастерская керамики"),
            events.filteredBy(CatalogFilters("мастерская", isFree = false)).map { it.title },
        )
        assertEquals(
            listOf("Вечер настольных игр", "Научный лекторий", "Экскурсия по городу"),
            events.filteredBy(CatalogFilters(isFree = true)).map { it.title },
        )
        assertEquals(events, events.filteredBy(CatalogFilters()))
    }

    @Test
    fun priceInKopecksIsShownInRubles() {
        assertEquals("500", Money(50000).formatRubles())
        assertEquals("499,90", Money(49990).formatRubles())
        assertEquals("0,05", Money(5).formatRubles())
    }
}
