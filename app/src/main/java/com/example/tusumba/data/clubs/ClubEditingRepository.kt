package com.example.tusumba.data.clubs

import com.example.tusumba.data.events.api.ClubsApi
import com.example.tusumba.data.events.api.PhotosApi
import com.example.tusumba.data.events.model.ClubDetail
import com.example.tusumba.data.events.model.ClubUpdate
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class ClubEditApiException(val statusCode: Int) : IOException("Club editing request failed ($statusCode)")

class ClubEditingRepository @Inject constructor(
    private val clubsApi: ClubsApi,
    private val photosApi: PhotosApi,
) {
    suspend fun getClub(id: UUID): ClubDetail = request { clubsApi.getClub(id) }.requiredBody()

    suspend fun updateClub(id: UUID, changes: ClubUpdate): ClubDetail = request { clubsApi.updateClub(id, changes) }.requiredBody()

    suspend fun uploadPhoto(id: UUID, photo: ClubPhoto): String {
        val extension = when (photo.mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> "webp"
        }
        val file = MultipartBody.Part.createFormData(
            "file", "cover.$extension", photo.bytes.toRequestBody(photo.mimeType.toMediaType()),
        )
        return request { photosApi.uploadClubPhoto(id, file) }.requiredBody().photoUrl
    }

    suspend fun deletePhoto(id: UUID) {
        request { photosApi.deleteClubPhoto(id) }.requireSuccess()
    }

    private suspend fun <T> request(call: suspend () -> Response<T>): Response<T> = try {
        call()
    } catch (error: IllegalArgumentException) {
        throw IOException("Invalid club editing response", error)
    }

    private fun <T> Response<T>.requiredBody(): T {
        requireSuccess()
        return body() ?: throw IOException("Empty club editing response")
    }

    private fun Response<*>.requireSuccess() {
        if (!isSuccessful) {
            errorBody()?.close()
            throw ClubEditApiException(code())
        }
    }
}
