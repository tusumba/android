package com.example.tusumba.di

import com.example.tusumba.BuildConfig
import com.example.tusumba.data.network.AuthInterceptor
import com.example.tusumba.data.network.OffsetDateTimeSerializer
import com.example.tusumba.data.network.TokenAuthenticator
import com.example.tusumba.data.network.UUIDSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.OffsetDateTime
import java.util.UUID
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
        serializersModule = SerializersModule {
            contextual(UUID::class, UUIDSerializer)
            contextual(OffsetDateTime::class, OffsetDateTimeSerializer)
        }
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