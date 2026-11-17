package com.example.tusumba.ui.screens

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventMapTest {
    @Test
    fun mapsReceiveAnEncodedAddressWithoutRequiringLocation() {
        val context = RecordingContext()
        val address = "Новосибирск, Пирогова 1 & корпус ? №2"
        assertTrue(openEventMap(context, address))
        val intent = context.intents.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo", intent.data?.scheme)
        assertEquals("0,0?q=$address", android.net.Uri.decode(intent.data?.schemeSpecificPart))
    }

    @Test
    fun fallsBackToBrowserWhenNoMapsApplicationExists() {
        val context = RecordingContext(failures = 1)
        assertTrue(openEventMap(context, "НГУ & библиотека"))
        val intent = context.intents.last()
        assertEquals("https", intent.data?.scheme)
        assertEquals("www.google.com", intent.data?.host)
        assertEquals("НГУ & библиотека", intent.data?.getQueryParameter("query"))
    }

    @Test
    fun absenceOfBothMapsAndBrowserDoesNotCrash() {
        val context = RecordingContext(failures = 2)
        assertFalse(openEventMap(context, "НГУ"))
        assertEquals(2, context.intents.size)
    }

    @Test
    fun blankAddressDoesNotLaunchAnything() {
        val context = RecordingContext()
        assertFalse(openEventMap(context, "  "))
        assertTrue(context.intents.isEmpty())
    }

    private class RecordingContext(private val failures: Int = 0) :
        ContextWrapper(ApplicationProvider.getApplicationContext()) {
        val intents = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            intents += intent
            if (intents.size <= failures) throw ActivityNotFoundException()
        }
    }
}
