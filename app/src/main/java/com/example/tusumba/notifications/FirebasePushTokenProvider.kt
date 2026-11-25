package com.example.tusumba.notifications

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebasePushTokenProvider @Inject constructor() {
    suspend fun token(): String = FirebaseMessaging.getInstance().token.awaitResult()

    suspend fun deleteToken() {
        FirebaseMessaging.getInstance().deleteToken().awaitResult()
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(IOException("Firebase messaging request failed", task.exception))
        }
    }
}
