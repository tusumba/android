package com.example.tusumba.di

import com.example.tusumba.data.clubs.ApiClubsRepository
import com.example.tusumba.data.clubs.ClubsRepository
import com.example.tusumba.data.events.api.ClubsApi
import com.example.tusumba.data.events.api.EventsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object ClubsModule {
    @Provides
    @Singleton
    fun provideClubsApi(retrofit: Retrofit): ClubsApi = retrofit.create(ClubsApi::class.java)

    @Provides
    @Singleton
    fun provideEventsApi(retrofit: Retrofit): EventsApi = retrofit.create(EventsApi::class.java)

    @Provides
    @Singleton
    fun provideClubsRepository(clubsApi: ClubsApi, eventsApi: EventsApi): ClubsRepository =
        ApiClubsRepository(clubsApi, eventsApi)
}
