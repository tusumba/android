package com.example.tusumba.data.clubs

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClubPhotoReaderTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val reader = ClubPhotoReader(context)

    @Test
    fun actualImageTypeDeterminesMimeForJpegPngAndWebp() {
        for ((format, mime) in listOf(Bitmap.CompressFormat.PNG to "image/png",
            Bitmap.CompressFormat.JPEG to "image/jpeg", Bitmap.CompressFormat.WEBP to "image/webp")) {
            assertEquals(mime, reader.validate(image(format)).mimeType)
        }
    }

    @Test
    fun emptyTextAndUnsupportedGifAreRejected() {
        val gif = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==")
        for (bytes in listOf(byteArrayOf(), "not_an_image".toByteArray(), gif)) {
            try { reader.validate(bytes); throw AssertionError("Expected invalid photo") }
            catch (_: InvalidClubPhotoException) { }
        }
    }

    @Test
    fun fiveMegabyteBoundaryIsEnforcedBeforeUploading() {
        val bytes = image(Bitmap.CompressFormat.PNG).copyOf(ClubPhotoReader.MaxBytes)
        assertEquals(ClubPhotoReader.MaxBytes, reader.validate(bytes).bytes.size)
        try { reader.validate(bytes.copyOf(ClubPhotoReader.MaxBytes + 1)); throw AssertionError("Oversized photo accepted") }
        catch (_: InvalidClubPhotoException) { }
    }

    @Test
    fun selectedFileIsReadFromItsUriAndInvalidFileIsNotAcceptedAsAnImage() = runTest {
        val file = java.io.File(context.cacheDir, "selected.dat")
        val bytes = image(Bitmap.CompressFormat.PNG)
        file.writeBytes(bytes)
        assertTrue(reader.read(Uri.fromFile(file)).bytes.contentEquals(bytes))
        file.writeText("renamed non-image")
        try { reader.read(Uri.fromFile(file)); throw AssertionError("Invalid image accepted") }
        catch (_: IOException) { }
    }

    private fun image(format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(format, 90, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
