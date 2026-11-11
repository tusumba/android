package com.example.tusumba.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tusumba.R
import com.example.tusumba.data.auth.AuthUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: AuthUser?,
    onBackClick: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onMyRegistrations: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(stringResource(R.string.auth_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (user == null) {
                Text(stringResource(R.string.profile_guest), textAlign = TextAlign.Center)
                Button(onClick = onSignIn) {
                    Text(stringResource(R.string.auth_sign_in))
                }
            } else {
                Text(user.nickname, style = MaterialTheme.typography.headlineSmall)
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
                if (onMyRegistrations != null) {
                    Button(onClick = onMyRegistrations) {
                        Text(stringResource(R.string.my_registrations_title))
                    }
                }
                OutlinedButton(onClick = onSignOut) {
                    Text(stringResource(R.string.profile_sign_out))
                }
            }
        }
    }
}
