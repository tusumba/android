package com.example.tusumba.di

import com.example.tusumba.data.identity.api.DefaultApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object LogoutModule {
    @Provides
    @Singleton
    @Named("identityLogout")
    fun provideIdentityLogoutApi(retrofit: Retrofit): DefaultApi {
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        return retrofit.newBuilder().client(client).build().create(DefaultApi::class.java)
    }
}
