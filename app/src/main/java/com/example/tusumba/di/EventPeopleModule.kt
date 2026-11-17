package com.example.tusumba.di

import com.example.tusumba.data.events.ApiEventPeopleRepository
import com.example.tusumba.data.events.EventPeopleRepository
import com.example.tusumba.data.identity.api.DefaultApi as IdentityApi
import com.example.tusumba.data.ticketing.api.ParticipantsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object EventPeopleModule {
    @Provides
    @Singleton
    fun providePeopleRepository(retrofit: Retrofit): EventPeopleRepository =
        ApiEventPeopleRepository(
            retrofit.create(IdentityApi::class.java),
            retrofit.create(ParticipantsApi::class.java),
        )
}
