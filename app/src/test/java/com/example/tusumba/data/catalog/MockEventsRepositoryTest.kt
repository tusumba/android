package com.example.tusumba.data.catalog

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockEventsRepositoryTest {
    @Test
    fun successReturnsEventCardsMatchingTheContract() = runTest {
        val events = MockEventsRepository().getEvents()

        assertEquals(6, events.size)
        assertEquals(events.size, events.map { it.id }.distinct().size)
        events.forEach { UUID.fromString(it.id) }
        assertEquals("Вечер настольных игр", events.first().title)
        assertTrue(events.all { it.title.isNotBlank() && it.placeAddress.isNotBlank() })
        assertTrue(events.all { it.isFree == (it.price == null) })
        assertTrue(events.mapNotNull { it.price }.all { it.amount > 0 && it.currency == "RUB" })
        assertEquals(events.sortedBy { it.date }, events)
    }

    @Test
    fun emptyScenarioHasNoEvents() = runTest {
        assertTrue(MockEventsRepository(MockCatalogScenario.EMPTY).getEvents().isEmpty())
    }

    @Test
    fun errorScenarioCanBeReproducedWithoutNetwork() = runTest {
        val repository = MockEventsRepository(MockCatalogScenario.ERROR)
        try {
            repository.getEvents()
            throw AssertionError("Expected the mock failure")
        } catch (_: IOException) {
            // Ошибка источника должна дойти до ViewModel.
        }
    }
}
