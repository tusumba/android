package com.example.tusumba.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException

class PushDeviceWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val synchronizer = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).synchronizer()
        return try {
            synchronizer.synchronize()
            Result.success()
        } catch (error: PushDeviceApiException) {
            when {
                error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500 -> Result.retry()
                else -> Result.failure()
            }
        } catch (_: IOException) {
            Result.retry()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun synchronizer(): PushDeviceSynchronizer
    }
}
