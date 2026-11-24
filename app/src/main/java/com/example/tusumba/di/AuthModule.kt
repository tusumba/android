package com.example.tusumba.di

import com.example.tusumba.data.auth.AuthRepository
import com.example.tusumba.data.auth.NetworkAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun bindAuthRepository(repository: NetworkAuthRepository): AuthRepository
}
