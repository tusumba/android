package com.example.tusumba.notifications

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TusumbaMessagingService : FirebaseMessagingService() {
    @Inject lateinit var scheduler: PushDeviceScheduler
    @Inject lateinit var publisher: PushNotificationPublisher

    override fun onNewToken(token: String) {
        // The worker reads Firebase's latest token, including changes while an earlier request was running.
        scheduler.synchronize()
    }

    @SuppressLint("MissingPermission")
    override fun onMessageReceived(message: RemoteMessage) {
        publisher.publish(message)
    }
}
