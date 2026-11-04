package com.example.tusumba.data.catalog

import java.io.IOException
import java.time.LocalDateTime
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

    // Демонстрационные события из вайрфрейма #18899, а не данные бэкенда.
    private val events =
        listOf(
            CatalogEvent(
                "board-games",
                "Вечер настольных игр",
                LocalDateTime.of(2026, 11, 14, 17, 0),
                "Молодёжный центр",
                EventType.MEETINGS,
                0,
            ),
            CatalogEvent(
                "open-air-music",
                "Музыка под открытым небом",
                LocalDateTime.of(2026, 11, 21, 18, 0),
                "Городской парк",
                EventType.MUSIC,
                500,
            ),
            CatalogEvent(
                "ceramics",
                "Мастерская керамики",
                LocalDateTime.of(2026, 11, 22, 12, 0),
                "Арт-пространство",
                EventType.CREATIVITY,
                900,
            ),
            CatalogEvent(
                "science-lecture",
                "Научный лекторий",
                LocalDateTime.of(2026, 11, 26, 19, 0),
                "Библиотека",
                EventType.EDUCATION,
                0,
            ),
            CatalogEvent(
                "chess",
                "Турнир по шахматам",
                LocalDateTime.of(2026, 11, 28, 11, 0),
                "Шахматный клуб",
                EventType.SPORT,
                200,
            ),
            CatalogEvent(
                "city-walk",
                "Экскурсия по городу",
                LocalDateTime.of(2026, 11, 29, 14, 0),
                "Центральная площадь",
                EventType.WALKS,
                0,
            ),
        )
}
