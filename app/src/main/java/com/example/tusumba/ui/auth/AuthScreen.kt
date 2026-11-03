package com.example.tusumba.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.tusumba.R
import com.example.tusumba.data.auth.AuthResult
import kotlinx.coroutines.launch

@Composable
internal fun AuthScreen(
    isRegistration: Boolean,
    onBackClick: () -> Unit,
    onSwitchMode: () -> Unit,
    onAuthenticate: suspend (contact: String, password: String) -> AuthResult,
    onAuthenticated: () -> Unit
) {
    var contact by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<AuthResult?>(null) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    fun submit() {
        if (isSubmitting) return
        attemptedSubmit = true
        submitError = null
        if (validateAuthForm(contact, password).isNotEmpty()) return

        isSubmitting = true
        focusManager.clearFocus()
        scope.launch {
            try {
                val result = onAuthenticate(contact.trim(), password)
                if (result is AuthResult.Success) {
                    onAuthenticated()
                } else {
                    submitError = result
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    val errors = if (attemptedSubmit) validateAuthForm(contact, password) else emptyMap()
    val contactAlreadyRegistered = submitError == AuthResult.ContactAlreadyRegistered
    val contactMessage = when {
        contactAlreadyRegistered -> stringResource(R.string.auth_contact_registered)
        errors[AuthField.Contact] == AuthFieldError.InvalidContact ->
            stringResource(R.string.auth_invalid_contact)
        else -> null
    }
    val formError = when {
        submitError == AuthResult.InvalidCredentials -> R.string.auth_invalid_credentials
        submitError == AuthResult.RegistrationUnavailable -> R.string.auth_registration_unavailable
        AuthFieldError.Required in errors.values -> R.string.auth_required
        else -> null
    }
    val title = if (isRegistration) R.string.auth_register_title else R.string.auth_sign_in_title
    val action = if (isRegistration) R.string.auth_register else R.string.auth_sign_in
    val switchAction = when {
        contactAlreadyRegistered -> R.string.auth_sign_in
        isRegistration -> R.string.auth_to_sign_in
        else -> R.string.auth_to_register
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TextButton(onClick = onBackClick, modifier = Modifier.padding(start = 12.dp)) {
            Text(stringResource(R.string.auth_back))
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(title),
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.headlineMedium
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = {
                        contact = it
                        submitError = null
                    },
                    modifier = Modifier.fillMaxWidth().semantics {
                        if (contactMessage != null) error(contactMessage)
                    },
                    enabled = !isSubmitting,
                    label = { Text(stringResource(R.string.auth_contact)) },
                    supportingText = if (contactMessage == null) null else {
                        { Text(contactMessage) }
                    },
                    isError = AuthField.Contact in errors || contactAlreadyRegistered,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (!contactAlreadyRegistered) submitError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    label = { Text(stringResource(R.string.auth_password)) },
                    trailingIcon = {
                        PasswordVisibilityButton(
                            visible = passwordVisible,
                            enabled = !isSubmitting,
                            onClick = { passwordVisible = !passwordVisible }
                        )
                    },
                    isError = AuthField.Password in errors,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    singleLine = true
                )
                if (formError != null) {
                    Text(
                        text = stringResource(formError),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = ::submit,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(action))
                }
                TextButton(
                    onClick = onSwitchMode,
                    enabled = !isSubmitting,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(switchAction))
                }
            }
        }
    }
}

@Composable
private fun PasswordVisibilityButton(visible: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val action = if (visible) R.string.auth_hide_password else R.string.auth_show_password
    val description = stringResource(
        if (visible) R.string.auth_hide_password_description else R.string.auth_show_password_description
    )
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(
            text = stringResource(action),
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}
