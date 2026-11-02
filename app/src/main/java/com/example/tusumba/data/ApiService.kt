package com.example.tusumba.data

import retrofit2.http.GET

interface ApiService {
    // Заглушка для проверки генерации клиента в будущем
    @GET("v1/events")
    suspend fun getEvents(): String
}