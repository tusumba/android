package com.example.tusumba.data.catalog

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.delay

enum class MockCatalogScenario {
    SUCCESS,
    EMPTY,
    ERROR,
}

class MockEventsRepository(
    private val scenario: MockCatalogScenario = MockCatalogScenario.SUCCESS,
    private val loadDelayMillis: Long = 350,
) : EventsRepository {
    override suspend fun getEvents(): List<CatalogEvent> {
        delay(loadDelayMillis)
        return when (scenario) {
            MockCatalogScenario.SUCCESS -> events
            MockCatalogScenario.EMPTY -> emptyList()
            MockCatalogScenario.ERROR -> throw IOException("Mock catalog is unavailable")
        }
    }

    // Демонстрационные события из вайрфрейма #18899 в форме EventCard, а не данные бэкенда.
    private val events =
        listOf(
            CatalogEvent(
                id = "9f1e6a52-3c4b-4d7e-8a10-000000000001",
                title = "Вечер настольных игр",
                date = LocalDate.of(2026, 11, 14),
                placeAddress = "Молодёжный центр",
                isFree = true,
                price = null,
                status = EventStatus.CONFIRMED,
            ),
            CatalogEvent(
                id = "9f1e6a52-3c4b-4d7e-8a10-000000000002",
                title = "Музыка под открытым небом",
                date = LocalDate.of(2026, 11, 21),
                placeAddress = "Городской парк",
                isFree = false,
                price = Money(50000),
                status = EventStatus.CONFIRMED,
            ),
            CatalogEvent(
                id = "9f1e6a52-3c4b-4d7e-8a10-000000000003",
                title = "Мастерская керамики",
                date = LocalDate.of(2026, 11, 22),
                placeAddress = "Арт-пространство",
                isFree = false,
                price = Money(90000),
                status = EventStatus.NOT_CONFIRMED,
            ),
            CatalogEvent(
                id = "9f1e6a52-3c4b-4d7e-8a10-000000000004",
                title = "Научный лекторий",
                date = LocalDate.of(2026, 11, 26),
                placeAddress = "Библиотека",
                isFree = true,
                price = null,
                status = EventStatus.CONFIRMED,
            ),
            CatalogEvent(
                id = "9f1e6a52-3c4b-4d7e-8a10-000000000005",
                title = "Турнир по шахматам",
                date = LocalDate.of(2026, 11, 28),
                placeAddress = "Шахматный клуб",
                isFree = false,
                price = Money(20000),
                status = EventStatus.NOT_CONFIRMED,
            ),
            CatalogEvent(
                id = "9f1e6a52-3c4b-4d7e-8a10-000000000006",
                title = "Экскурсия по городу",
                date = LocalDate.of(2026, 11, 29),
                placeAddress = "Центральная площадь",
                isFree = true,
                price = null,
                status = EventStatus.NOT_CONFIRMED,
            ),
        )
}
