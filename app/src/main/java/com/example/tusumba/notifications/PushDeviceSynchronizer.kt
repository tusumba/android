package com.example.tusumba.notifications

import com.example.tusumba.data.local.TokenManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PushDeviceSynchronizer @Inject constructor(
    private val api: PushDeviceApi,
    private val store: PushDeviceStore,
    private val session: TokenManager,
    private val environment: FirebasePushEnvironment,
    private val firebase: FirebasePushTokenProvider,
) {
    private val mutex = Mutex()

    suspend fun synchronize() = mutex.withLock {
        val userId = session.getUser()?.id ?: return@withLock
        if (session.getAccessToken().isNullOrBlank()) return@withLock
        if (environment.configured() && environment.supported() && environment.notificationsEnabled()) {
            val token = firebase.token()
            currentCoroutineContext().ensureActive()
            if (session.getUser()?.id != userId) return@withLock
            val deviceId = api.register(token)
            currentCoroutineContext().ensureActive()
            if (session.getUser()?.id != userId) return@withLock
            store.replaceBinding(PushDeviceBinding(userId, token, deviceId))
        } else {
            store.retireBinding()
        }
        removeRetiredDevices(userId)
    }

    suspend fun unregister() = mutex.withLock {
        val userId = session.getUser()?.id ?: return@withLock
        store.retireBinding()
        removeRetiredDevices(userId)
    }

    private suspend fun removeRetiredDevices(userId: String) {
        for (deviceId in store.pendingRemovals(userId)) {
            if (session.getUser()?.id != userId) return
            api.remove(deviceId)
            currentCoroutineContext().ensureActive()
            store.forgetRemoval(userId, deviceId)
        }
    }
}
