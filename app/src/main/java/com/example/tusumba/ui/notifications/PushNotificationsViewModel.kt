package com.example.tusumba.ui.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.tusumba.notifications.FirebasePushEnvironment
import com.example.tusumba.notifications.PushDeviceScheduler
import com.example.tusumba.notifications.PushDeviceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PushPermissionState(
    val configured: Boolean = false,
    val supported: Boolean = false,
    val permissionGranted: Boolean = false,
    val enabled: Boolean = false,
    val requested: Boolean = false,
    val synchronizationFailed: Boolean = false,
)

@HiltViewModel
class PushNotificationsViewModel @Inject constructor(
    private val environment: FirebasePushEnvironment,
    private val store: PushDeviceStore,
    private val scheduler: PushDeviceScheduler,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PushPermissionState())
    val state = mutableState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PushDeviceScheduler.WorkName).collect { jobs ->
                val failed = jobs.any { it.state == WorkInfo.State.FAILED ||
                    (it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0) }
                mutableState.update { it.copy(synchronizationFailed = failed) }
            }
        }
    }

    fun refresh() {
        val permissionGranted = environment.permissionGranted()
        if (permissionGranted) store.markPermissionRequested()
        mutableState.update {
            it.copy(
                configured = environment.configured(),
                supported = environment.supported(),
                permissionGranted = permissionGranted,
                enabled = environment.notificationsEnabled(),
                requested = store.permissionWasRequested(),
            )
        }
    }

    fun permissionRequested() {
        store.markPermissionRequested()
        refresh()
    }

    fun synchronize() = scheduler.synchronize()
}
