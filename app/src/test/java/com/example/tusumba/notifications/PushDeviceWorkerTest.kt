package com.example.tusumba.notifications

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.hilt.internal.GeneratedComponent
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class PushWorkerTestApplication : Application(), GeneratedComponent, PushDeviceWorker.Dependencies {
    val deviceSynchronizer = mockk<PushDeviceSynchronizer>()
    override fun synchronizer(): PushDeviceSynchronizer = deviceSynchronizer
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PushWorkerTestApplication::class)
class PushDeviceWorkerTest {
    private lateinit var worker: PushDeviceWorker
    private lateinit var synchronizer: PushDeviceSynchronizer

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<PushWorkerTestApplication>()
        synchronizer = application.deviceSynchronizer
        worker = TestListenableWorkerBuilder<PushDeviceWorker>(application).build()
    }

    @Test
    fun networkFailureAndUnavailableServerRemainRetryable() = runTest {
        for (error in listOf(IOException("Offline"), PushDeviceApiException(503), PushDeviceApiException(429))) {
            coEvery { synchronizer.synchronize() } throws error
            assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        }
    }

    @Test
    fun rejectedSessionDoesNotRetryForever() = runTest {
        coEvery { synchronizer.synchronize() } throws PushDeviceApiException(401)
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    @Test
    fun successfulSynchronizationCompletesTheJob() = runTest {
        coEvery { synchronizer.synchronize() } just Runs
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
