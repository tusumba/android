package com.example.tusumba.di

import com.example.tusumba.data.events.api.EventsApi
import com.example.tusumba.data.registrations.ApiRegistrationsRepository
import com.example.tusumba.data.registrations.RegistrationsRepository
import com.example.tusumba.data.ticketing.api.RegistrationsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object RegistrationsModule {
    @Provides
    @Singleton
    fun provideRegistrationsApi(retrofit: Retrofit): RegistrationsApi =
        retrofit.create(RegistrationsApi::class.java)

    @Provides
    @Singleton
    fun provideRegistrationsRepository(
        registrationsApi: RegistrationsApi,
        eventsApi: EventsApi,
    ): RegistrationsRepository = ApiRegistrationsRepository(registrationsApi, eventsApi)
}
