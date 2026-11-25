package com.example.tusumba.notifications

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.withTimeoutOrNull

class PushSession @Inject constructor(
    private val scheduler: PushDeviceScheduler,
    private val synchronizer: PushDeviceSynchronizer,
    private val environment: FirebasePushEnvironment,
    private val firebase: FirebasePushTokenProvider,
) {
    fun signedIn() = scheduler.synchronize()

    suspend fun signOut() {
        scheduler.cancel()
        try {
            withTimeoutOrNull(2_000) { synchronizer.unregister() }
        } catch (_: IOException) {
            // Failed removals are retained for the next session of this user.
        }
        if (environment.configured()) {
            try {
                withTimeoutOrNull(2_000) { firebase.deleteToken() }
            } catch (_: IOException) {
                // Ending the local session must remain possible without Firebase connectivity.
            }
        }
    }
}
