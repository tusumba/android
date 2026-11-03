package com.example.tusumba.ui.auth

import androidx.annotation.StringRes
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    onAuthenticate: suspend (name: String?, email: String, password: String) -> AuthResult,
    onAuthenticated: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<AuthResult?>(null) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    fun validate() = if (isRegistration) {
        validateRegistration(name, email, password, confirmation)
    } else {
        validateSignIn(email, password)
    }

    fun submit() {
        if (isSubmitting) return
        attemptedSubmit = true
        submitError = null
        if (validate().isNotEmpty()) return

        isSubmitting = true
        focusManager.clearFocus()
        scope.launch {
            try {
                val result = onAuthenticate(
                    name.trim().takeIf { isRegistration },
                    email.trim(),
                    password
                )
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

    val errors = if (attemptedSubmit) validate() else emptyMap()
    val title = if (isRegistration) R.string.auth_register_title else R.string.auth_sign_in_title
    val action = if (isRegistration) R.string.auth_register else R.string.auth_sign_in
    val switchAction = if (isRegistration) R.string.auth_to_sign_in else R.string.auth_to_register
    val emailError = if (submitError == AuthResult.EmailAlreadyRegistered) {
        R.string.auth_email_registered
    } else {
        errors[AuthField.Email].message()
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
                if (isRegistration) {
                    AuthFieldInput(
                        value = name,
                        onValueChange = { name = it },
                        label = R.string.auth_name,
                        error = errors[AuthField.Name].message(),
                        enabled = !isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        )
                    )
                }
                AuthFieldInput(
                    value = email,
                    onValueChange = {
                        email = it
                        submitError = null
                    },
                    label = R.string.auth_email,
                    error = emailError,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                )
                PasswordInput(
                    value = password,
                    onValueChange = {
                        password = it
                        submitError = null
                    },
                    label = R.string.auth_password,
                    error = errors[AuthField.Password].message(),
                    enabled = !isSubmitting,
                    onDone = if (isRegistration) null else ::submit
                )
                if (isRegistration) {
                    PasswordInput(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = R.string.auth_password_confirmation,
                        error = errors[AuthField.PasswordConfirmation].message(),
                        enabled = !isSubmitting,
                        onDone = ::submit
                    )
                }
                if (submitError == AuthResult.InvalidCredentials) {
                    Text(
                        text = stringResource(R.string.auth_invalid_credentials),
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
private fun PasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes error: Int?,
    enabled: Boolean,
    onDone: (() -> Unit)?
) {
    var visible by remember { mutableStateOf(false) }
    AuthFieldInput(
        value = value,
        onValueChange = onValueChange,
        label = label,
        error = error,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (onDone == null) ImeAction.Next else ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }, enabled = enabled) {
                val action = if (visible) R.string.auth_hide_password else R.string.auth_show_password
                Text(stringResource(action))
            }
        }
    )
}

@Composable
private fun AuthFieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes error: Int?,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    transformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(label)) },
        trailingIcon = trailingIcon,
        supportingText = if (error == null) null else { { Text(stringResource(error)) } },
        isError = error != null,
        visualTransformation = transformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true
    )
}

@StringRes
private fun AuthFieldError?.message(): Int? = when (this) {
    AuthFieldError.Required -> R.string.auth_required
    AuthFieldError.InvalidEmail -> R.string.auth_invalid_email
    AuthFieldError.PasswordTooShort -> R.string.auth_short_password
    AuthFieldError.PasswordMismatch -> R.string.auth_password_mismatch
    null -> null
}
