package com.example.tusumba.di

import com.example.tusumba.data.catalog.ApiEventsRepository
import com.example.tusumba.data.catalog.EventsRepository
import com.example.tusumba.data.events.api.EventsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    @Singleton
    fun provideEventsRepository(api: EventsApi): EventsRepository = ApiEventsRepository(api)
}
