package com.example.tusumba.data.clubs

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InvalidClubPhotoException : IOException("Invalid club photo")

class ClubPhoto(val bytes: ByteArray, val mimeType: String)

class ClubPhotoReader @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun read(uri: Uri): ClubPhoto = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (output.size() <= MaxBytes) {
                    val count = input.read(buffer, 0, minOf(buffer.size, MaxBytes + 1 - output.size()))
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw InvalidClubPhotoException()
        } catch (_: SecurityException) {
            throw InvalidClubPhotoException()
        }
        validate(bytes)
    }

    internal fun validate(bytes: ByteArray): ClubPhoto {
        if (bytes.isEmpty() || bytes.size > MaxBytes) throw InvalidClubPhotoException()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val mime = bounds.outMimeType
        if (mime !in setOf("image/jpeg", "image/png", "image/webp") || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw InvalidClubPhotoException()
        }
        return ClubPhoto(bytes, requireNotNull(mime))
    }

    companion object {
        const val MaxBytes = 5 * 1024 * 1024
    }
}
