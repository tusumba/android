package com.example.tusumba

import android.app.Application
import com.example.tusumba.notifications.PushNotificationPublisher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TusumbaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PushNotificationPublisher.createChannel(this)
    }
}
