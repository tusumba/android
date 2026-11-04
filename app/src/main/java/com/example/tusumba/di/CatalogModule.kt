package com.example.tusumba.di

import com.example.tusumba.BuildConfig
import com.example.tusumba.data.catalog.EventsRepository
import com.example.tusumba.data.catalog.MockCatalogScenario
import com.example.tusumba.data.catalog.MockEventsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    @Singleton
    fun provideEventsRepository(): EventsRepository {
        val scenario =
            if (BuildConfig.DEBUG) {
                MockCatalogScenario.valueOf(
                    BuildConfig.CATALOG_MOCK_SCENARIO.uppercase(Locale.ROOT)
                )
            } else {
                MockCatalogScenario.SUCCESS
            }
        return MockEventsRepository(scenario)
    }
}
