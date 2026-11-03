package com.example.tusumba.data.catalog

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFiltersTest {
    @Test
    fun searchMatchesTitleIgnoringCaseAndSurroundingSpaces() = runTest {
        val result = MockEventsRepository().getEvents().filteredBy(CatalogFilters("  ШАХМАТ  "))

        assertEquals(listOf("chess"), result.map { it.id })
    }

    @Test
    fun searchAlsoMatchesVenue() = runTest {
        val result = MockEventsRepository().getEvents().filteredBy(CatalogFilters("парк"))

        assertEquals(listOf("open-air-music"), result.map { it.id })
    }

    @Test
    fun queryAndCategoryAreAppliedTogether() = runTest {
        val events = MockEventsRepository().getEvents()

        assertTrue(events.filteredBy(CatalogFilters("парк", EventType.SPORT)).isEmpty())
        assertEquals(
            listOf("open-air-music"),
            events.filteredBy(CatalogFilters("парк", EventType.MUSIC)).map { it.id },
        )
        assertEquals(events, events.filteredBy(CatalogFilters()))
    }
}
