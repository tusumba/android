package com.example.tusumba.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tusumba.R
import com.example.tusumba.data.auth.AuthResult
import kotlinx.coroutines.launch

private val AuthBrandBlue = Color(0xFF4757BE)
private val AuthBrandMint = Color(0xFF9AF0DD)
private val AuthFieldShape = RoundedCornerShape(12.dp)

/** [login] - почта или ник при входе, email при регистрации. */
internal data class AuthForm(val login: String, val password: String, val nickname: String = "")

@Composable
internal fun AuthScreen(
    isRegistration: Boolean,
    onBackClick: () -> Unit,
    onSwitchMode: () -> Unit,
    onAuthenticate: suspend (AuthForm) -> AuthResult,
    onAuthenticated: () -> Unit
) = AuthTheme {
    var login by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<AuthResult?>(null) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    fun validate() =
        if (isRegistration) validateRegistrationForm(login, nickname, password)
        else validateSignInForm(login, password)

    fun submit() {
        if (isSubmitting) return
        attemptedSubmit = true
        submitError = null
        if (validate().isNotEmpty()) return

        isSubmitting = true
        focusManager.clearFocus()
        scope.launch {
            try {
                val result = onAuthenticate(AuthForm(login.trim(), password, nickname.trim()))
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
    val loginField = if (isRegistration) AuthField.Email else AuthField.Login
    val userAlreadyExists = submitError == AuthResult.UserAlreadyExists
    val loginMessage = when (errors[loginField]) {
        AuthFieldError.InvalidEmail -> stringResource(R.string.auth_invalid_email)
        else -> null
    }
    val nicknameMessage = when (errors[AuthField.Nickname]) {
        AuthFieldError.InvalidNickname -> stringResource(R.string.auth_invalid_nickname)
        else -> null
    }
    val passwordMessage = when (errors[AuthField.Password]) {
        AuthFieldError.InvalidPassword -> stringResource(R.string.auth_invalid_password)
        else -> null
    }
    val formError = when {
        submitError == AuthResult.InvalidCredentials -> R.string.auth_invalid_credentials
        submitError == AuthResult.SignInUnavailable -> R.string.auth_sign_in_unavailable
        submitError == AuthResult.RegistrationUnavailable -> R.string.auth_registration_unavailable
        userAlreadyExists -> R.string.auth_user_exists
        AuthFieldError.Required in errors.values ->
            if (isRegistration) R.string.auth_required else R.string.auth_required_both
        else -> null
    }
    val title = if (isRegistration) R.string.auth_register_title else R.string.auth_sign_in_title
    val overline = if (isRegistration) R.string.auth_register_overline else R.string.auth_sign_in_overline
    val intro = if (isRegistration) R.string.auth_register_intro else R.string.auth_sign_in_intro
    val action = if (isRegistration) R.string.auth_register else R.string.auth_sign_in
    val switchAction = when {
        userAlreadyExists -> R.string.auth_sign_in
        isRegistration -> R.string.auth_to_sign_in
        else -> R.string.auth_to_register
    }

    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface,
        errorContainerColor = colors.surface,
        unfocusedBorderColor = colors.outlineVariant
    )
    val loginLabel = stringResource(if (isRegistration) R.string.auth_email else R.string.auth_login)
    val loginHint = stringResource(if (isRegistration) R.string.auth_email_hint else R.string.auth_login_hint)
    val passwordLabel = stringResource(R.string.auth_password)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
        contentColor = colors.onBackground
    ) {
        Box(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 418.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 22.dp, top = 24.dp, end = 22.dp, bottom = 28.dp)
            ) {
                AuthHeader(onBackClick)
                Spacer(Modifier.height(if (isRegistration) 34.dp else 52.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(overline),
                        color = colors.tertiary,
                        style = typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = stringResource(title),
                        modifier = Modifier.semantics { heading() },
                        style = typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(intro),
                        color = colors.onSurfaceVariant,
                        style = typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(28.dp))
                AuthTextField(
                    label = loginLabel,
                    hint = loginHint,
                    value = login,
                    onValueChange = {
                        login = it
                        submitError = null
                    },
                    message = loginMessage,
                    isError = loginField in errors,
                    enabled = !isSubmitting,
                    keyboardType = KeyboardType.Email,
                    colors = fieldColors
                )
                if (isRegistration) {
                    Spacer(Modifier.height(13.dp))
                    AuthTextField(
                        label = stringResource(R.string.auth_nickname),
                        hint = stringResource(R.string.auth_nickname_hint),
                        value = nickname,
                        onValueChange = {
                            nickname = it
                            submitError = null
                        },
                        message = nicknameMessage,
                        isError = AuthField.Nickname in errors,
                        enabled = !isSubmitting,
                        keyboardType = KeyboardType.Ascii,
                        colors = fieldColors
                    )
                }
                Spacer(Modifier.height(13.dp))
                AuthFieldLabel(passwordLabel)
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        submitError = null
                    },
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = passwordLabel
                        if (passwordMessage != null) error(passwordMessage)
                    },
                    enabled = !isSubmitting,
                    placeholder = { Text(stringResource(R.string.auth_password_hint)) },
                    supportingText = if (passwordMessage == null) null else {
                        { Text(passwordMessage) }
                    },
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
                    shape = AuthFieldShape,
                    colors = fieldColors,
                    textStyle = typography.bodyMedium,
                    singleLine = true
                )
                if (formError != null) {
                    Text(
                        text = stringResource(formError),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(colors.errorContainer, AuthFieldShape)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = colors.onErrorContainer,
                        style = typography.bodyMedium
                    )
                }
                Button(
                    onClick = ::submit,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuthBrandBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(action), fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onSwitchMode,
                    enabled = !isSubmitting,
                    modifier = Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(switchAction))
                }
                Text(
                    text = stringResource(R.string.auth_footer),
                    modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally),
                    color = colors.onSurfaceVariant,
                    style = typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun AuthTextField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    message: String?,
    isError: Boolean,
    enabled: Boolean,
    keyboardType: KeyboardType,
    colors: TextFieldColors
) {
    AuthFieldLabel(label)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = label
            if (message != null) error(message)
        },
        enabled = enabled,
        placeholder = { Text(hint) },
        supportingText = if (message == null) null else {
            { Text(message) }
        },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        shape = AuthFieldShape,
        colors = colors,
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true
    )
}

@Composable
private fun AuthHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(35.dp).background(AuthBrandBlue, RoundedCornerShape(12.dp))) {
                Box(Modifier.align(Alignment.Center).size(16.dp).background(AuthBrandMint, CircleShape))
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(5.dp).size(7.dp)
                        .background(Color.White, CircleShape)
                )
            }
            Text(
                text = stringResource(R.string.auth_brand),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
        TextButton(onClick = onBackClick) {
            Text(
                text = stringResource(R.string.auth_back_with_arrow),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AuthFieldLabel(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(bottom = 6.dp).clearAndSetSemantics {},
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
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
