package com.example.tusumba.data.catalog

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockEventsRepositoryTest {
    @Test
    fun successReturnsWireframeEventsWithUniqueIdsAndRequiredFields() = runTest {
        val events = MockEventsRepository().getEvents()

        assertEquals(6, events.size)
        assertEquals(events.size, events.map { it.id }.distinct().size)
        assertEquals("Вечер настольных игр", events.first().title)
        assertTrue(events.all { it.title.isNotBlank() && it.venue.isNotBlank() })
        assertEquals(events.sortedBy { it.startsAt }, events)
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
