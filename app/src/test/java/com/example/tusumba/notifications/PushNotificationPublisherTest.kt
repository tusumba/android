package com.example.tusumba.notifications

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.example.tusumba.data.auth.AuthUser
import com.example.tusumba.data.local.TokenManager
import com.google.firebase.messaging.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PushNotificationPublisherTest {
    private val session = mockk<TokenManager>()
    private val environment = mockk<FirebasePushEnvironment>()
    private lateinit var publisher: PushNotificationPublisher
    private lateinit var notifications: NotificationManager
    private val eventId = "550e8400-e29b-41d4-a716-446655440000"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        notifications = context.getSystemService(NotificationManager::class.java)
        every { session.getUser() } returns AuthUser("first", "first@example.org", "first")
        every { environment.notificationsEnabled() } returns true
        publisher = PushNotificationPublisher(context, environment, session)
    }

    @Test
    fun dataPushCreatesNotificationWithItsOwnEventDestination() {
        publisher.publish(message("first", eventId))
        val delivered = shadowOf(notifications).allNotifications.single()
        assertEquals("Лекция скоро начнётся", delivered.extras.getString("android.title"))
        assertEquals(PushNotificationPublisher.ChannelId, delivered.channelId)
        assertEquals(eventId, shadowOf(delivered.contentIntent).savedIntent.getStringExtra("event_id"))
        assertNotNull(notifications.getNotificationChannel(PushNotificationPublisher.ChannelId))
    }

    @Test
    fun deniedPermissionAndSignedOutSessionNeverPostNotifications() {
        every { environment.notificationsEnabled() } returns false
        publisher.publish(message("first", eventId))
        every { environment.notificationsEnabled() } returns true
        every { session.getUser() } returns null
        publisher.publish(message("second", eventId))
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun twoMessagesKeepTheirOwnPendingIntentTargets() {
        val otherEvent = "650e8400-e29b-41d4-a716-446655440000"
        publisher.publish(message("first", eventId))
        publisher.publish(message("second", otherEvent))
        val destinations = shadowOf(notifications).allNotifications.map {
            shadowOf(it.contentIntent).savedIntent.getStringExtra("event_id")
        }.toSet()
        assertEquals(setOf(eventId, otherEvent), destinations)
    }

    @Test
    fun malformedDestinationsFallBackToOpeningTheApp() {
        assertEquals(PushDestination.Event(java.util.UUID.fromString(eventId)), PushDestination.from(mapOf("event_id" to eventId)))
        assertEquals(PushDestination.Registrations, PushDestination.from(mapOf("registration_id" to eventId)))
        assertEquals(null, PushDestination.from(mapOf("event_id" to "not-an-id")))
    }

    private fun message(id: String, event: String): RemoteMessage = RemoteMessage.Builder("test")
        .setMessageId(id)
        .setData(mapOf("title" to "Лекция скоро начнётся", "body" to "Начало через час", "event_id" to event))
        .build()
}
