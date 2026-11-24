package com.example.tusumba.di

import com.example.tusumba.data.applications.*
import com.example.tusumba.data.moderation.api.ApplicationsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationsModule {
    @Provides @Singleton
    fun repository(retrofit: Retrofit, json: Json): ApplicationsRepository =
        ApiApplicationsRepository(retrofit.create(ApplicationsApi::class.java), json)
}
