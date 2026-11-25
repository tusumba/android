package com.example.tusumba.notifications

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PushSessionTest {
    private val scheduler = mockk<PushDeviceScheduler>(relaxed = true)
    private val synchronizer = mockk<PushDeviceSynchronizer>()
    private val environment = mockk<FirebasePushEnvironment>()
    private val firebase = mockk<FirebasePushTokenProvider>()
    private val pushSession = PushSession(scheduler, synchronizer, environment, firebase)

    @Test
    fun unavailableBackendStillAttemptsToInvalidateTheFirebaseToken() = runTest {
        every { environment.configured() } returns true
        coEvery { synchronizer.unregister() } throws IOException("Backend offline")
        coEvery { firebase.deleteToken() } just Runs

        pushSession.signOut()

        verify { scheduler.cancel() }
        coVerify(exactly = 1) { firebase.deleteToken() }
    }

    @Test
    fun unreachableBackendAndFirebaseDoNotBlockLogout() = runTest {
        every { environment.configured() } returns true
        coEvery { synchronizer.unregister() } coAnswers { delay(60_000) }
        coEvery { firebase.deleteToken() } coAnswers { delay(60_000) }

        pushSession.signOut()

        assertTrue(testScheduler.currentTime <= 4_000)
        coVerify(exactly = 1) { firebase.deleteToken() }
    }

    @Test
    fun logoutWorksWithoutFirebaseConfiguration() = runTest {
        every { environment.configured() } returns false
        coEvery { synchronizer.unregister() } just Runs

        pushSession.signOut()

        coVerify(exactly = 0) { firebase.deleteToken() }
    }
}
