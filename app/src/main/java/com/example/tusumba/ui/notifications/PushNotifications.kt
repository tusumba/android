package com.example.tusumba.ui.notifications

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tusumba.R

data class PushNotificationActions(val state: PushPermissionState, val enable: () -> Unit, val retry: () -> Unit)

@Composable
fun rememberPushNotificationActions(
    userId: String?,
    viewModel: PushNotificationsViewModel = hiltViewModel(),
): PushNotificationActions {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
        viewModel.synchronize()
    }

    LifecycleResumeEffect(viewModel, userId) {
        viewModel.refresh()
        if (userId != null) viewModel.synchronize()
        onPauseOrDispose { }
    }
    LaunchedEffect(userId, state.configured, state.supported, state.requested) {
        if (userId != null && state.configured && state.supported && !state.requested &&
            !state.permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.permissionRequested()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    return PushNotificationActions(
        state = state,
        enable = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.permissionGranted && !state.requested) {
                viewModel.permissionRequested()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        },
        retry = viewModel::synchronize,
    )
}

@Composable
fun PushNotificationSettings(actions: PushNotificationActions, enabled: Boolean = true) {
    val state = actions.state
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.notifications_title), style = MaterialTheme.typography.titleMedium)
        when {
            !state.configured -> Text(stringResource(R.string.notifications_unavailable))
            !state.supported -> Text(stringResource(R.string.notifications_device_unsupported))
            !state.enabled -> {
                Text(stringResource(R.string.notifications_disabled))
                Text(stringResource(R.string.notifications_permission_required))
                OutlinedButton(onClick = actions.enable, enabled = enabled) {
                    Text(stringResource(if (state.requested) R.string.notifications_open_settings else R.string.notifications_enable))
                }
            }
            state.synchronizationFailed -> {
                Text(
                    stringResource(R.string.notifications_update_error),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = actions.retry, enabled = enabled) {
                    Text(stringResource(R.string.my_registrations_retry))
                }
            }
            else -> Text(stringResource(R.string.notifications_enabled))
        }
    }
}
