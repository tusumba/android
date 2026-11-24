package com.example.tusumba.data.applications

import com.example.tusumba.data.moderation.api.ApplicationsApi
import com.example.tusumba.data.moderation.model.*
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.json.*
import retrofit2.Response

interface ApplicationsRepository {
    suspend fun submit(value: ApplicationCreate): Application
    suspend fun list(page: Int = 1): ApplicationPage
    suspend fun get(id: UUID): Application
}

class ApplicationFailure(
    val status: Int,
    val code: String?,
    override val message: String,
    val fields: Map<String, String> = emptyMap(),
) : IOException(message)

class ApiApplicationsRepository(private val api: ApplicationsApi, private val json: Json) : ApplicationsRepository {
    override suspend fun submit(value: ApplicationCreate) = request("Не удалось отправить заявку. Попробуйте позже.") { api.createApplication(value) }
    override suspend fun list(page: Int) = request { api.listMyApplications(page = page, size = 20) }
    override suspend fun get(id: UUID) = request { api.getMyApplication(id) }

    private suspend fun <T> request(fallback: String = "Не удалось загрузить заявки. Попробуйте позже.", call: suspend () -> Response<T>): T {
        val response = try { call() } catch (error: IllegalArgumentException) {
            throw IOException("Некорректный ответ Moderation", error)
        } catch (error: java.time.DateTimeException) {
            throw IOException("Некорректная дата в ответе Moderation", error)
        }
        if (response.isSuccessful) return response.body() ?: throw IOException("Пустой ответ Moderation")
        val body = response.errorBody()?.use { it.string() }
        val error = try { body?.let { json.parseToJsonElement(it).jsonObject["error"]?.jsonObject } }
            catch (_: IllegalArgumentException) { null }
        val fields = runCatching {
            error?.get("details")?.jsonArray?.associate {
                val item = it.jsonObject
                item.getValue("field").jsonPrimitive.content to item.getValue("message").jsonPrimitive.content
            }.orEmpty()
        }.getOrDefault(emptyMap())
        throw ApplicationFailure(response.code(), (error?.get("code") as? JsonPrimitive)?.contentOrNull,
            if (response.code() >= 500) fallback
            else (error?.get("message") as? JsonPrimitive)?.contentOrNull ?: fallback, fields)
    }
}
