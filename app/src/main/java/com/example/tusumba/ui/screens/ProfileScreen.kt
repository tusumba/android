package com.example.tusumba.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tusumba.R
import com.example.tusumba.data.auth.AuthUser
import com.example.tusumba.data.auth.GlobalRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: AuthUser?,
    onBackClick: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    isSigningOut: Boolean = false,
) {
    BackHandler(enabled = isSigningOut) {}
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    TextButton(onClick = onBackClick, enabled = !isSigningOut) {
                        Text(stringResource(R.string.auth_back))
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (user == null) {
                Text(stringResource(R.string.profile_guest), textAlign = TextAlign.Center)
                Button(onClick = onSignIn) {
                    Text(stringResource(R.string.auth_sign_in))
                }
            } else {
                OutlinedCard(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = user.nickname,
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        ProfileField(stringResource(R.string.profile_email), user.email)
                        val role = when (user.globalRole) {
                            GlobalRole.USER -> R.string.profile_role_user
                            GlobalRole.MODERATOR -> R.string.profile_role_moderator
                        }
                        ProfileField(stringResource(R.string.profile_role), stringResource(role))
                    }
                }
                OutlinedButton(onClick = onSignOut, enabled = !isSigningOut) {
                    Text(
                        text = stringResource(
                            if (isSigningOut) R.string.profile_signing_out else R.string.profile_sign_out
                        ),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
