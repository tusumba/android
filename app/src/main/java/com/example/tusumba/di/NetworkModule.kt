package com.example.tusumba.di

import com.example.tusumba.BuildConfig
import com.example.tusumba.data.network.AuthInterceptor
import com.example.tusumba.data.network.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton
import kotlin.jvm.java

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    // ==========================================
    // ПРОВАЙДЕРЫ ДЛЯ СГЕНЕРИРОВАННЫХ API
    // ==========================================

    // При необходимости использовать api в коде подключить нужный файл таким же образом
    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): com.example.tusumba.data.notifications.api.DefaultApi {
        return retrofit.create(com.example.tusumba.data.notifications.api.DefaultApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): com.example.tusumba.data.identity.api.DefaultApi {
        return retrofit.create(com.example.tusumba.data.identity.api.DefaultApi::class.java)
    }
}