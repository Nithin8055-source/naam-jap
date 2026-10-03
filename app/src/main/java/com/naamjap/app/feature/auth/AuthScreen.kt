package com.naamjap.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naamjap.app.domain.repository.AuthSessionState
import com.naamjap.app.ui.components.GlassSurface
import com.naamjap.app.ui.components.NaamJapLogo

private enum class AuthPage { LOGIN, SIGN_UP, FORGOT_PASSWORD, RESET_PASSWORD }

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    passwordRecovery: Boolean = false,
    onPasswordRecoveryComplete: () -> Unit = {}
) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val action by viewModel.actionState.collectAsStateWithLifecycle()
    var page by rememberSaveable(passwordRecovery) {
        mutableStateOf(if (passwordRecovery) AuthPage.RESET_PASSWORD else AuthPage.LOGIN)
    }
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmVisible by rememberSaveable { mutableStateOf(false) }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(page, action.message, action.isError) {
        if (page == AuthPage.RESET_PASSWORD && action.message == "Your password was updated." && !action.isError) {
            onPasswordRecoveryComplete()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NaamJapLogo(Modifier.fillMaxWidth(.54f).height(136.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            when (page) {
                AuthPage.LOGIN -> "Welcome back"
                AuthPage.SIGN_UP -> "Create your account"
                AuthPage.FORGOT_PASSWORD -> "Reset your password"
                AuthPage.RESET_PASSWORD -> "Choose a new password"
            },
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (page) {
                AuthPage.LOGIN -> "A quiet moment is waiting for you."
                AuthPage.SIGN_UP -> "Create a private space for your practice."
                AuthPage.FORGOT_PASSWORD -> "We'll send a password reset link if this address can receive one."
                AuthPage.RESET_PASSWORD -> "Set a new password for your account."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (session == AuthSessionState.ConfigurationMissing) {
                    Text("Supabase is not configured", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in your untracked local.properties file. Never use a service_role key in this app.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    if (page == AuthPage.SIGN_UP) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(80); validationMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Full name") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }
                    if (page != AuthPage.RESET_PASSWORD) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.take(254); validationMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                        )
                    }
                    if (page == AuthPage.LOGIN || page == AuthPage.SIGN_UP || page == AuthPage.RESET_PASSWORD) {
                        PasswordField(
                            value = password,
                            onValueChange = { password = it.take(128); validationMessage = null },
                            visible = passwordVisible,
                            onVisibilityChange = { passwordVisible = !passwordVisible },
                            label = if (page == AuthPage.RESET_PASSWORD) "New password" else "Password"
                        )
                    }
                    if (page == AuthPage.SIGN_UP || page == AuthPage.RESET_PASSWORD) {
                        PasswordField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it.take(128); validationMessage = null },
                            visible = confirmVisible,
                            onVisibilityChange = { confirmVisible = !confirmVisible },
                            label = "Confirm password"
                        )
                    }

                    val message = validationMessage ?: action.message ?: sessionMessage(session)
                    if (message != null) {
                        Text(
                            message,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = if (validationMessage != null || action.isError || session == AuthSessionState.SessionUnavailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.clearMessage()
                            validationMessage = validate(page, displayName, email, password, confirmPassword)
                            if (validationMessage == null) {
                                when (page) {
                                    AuthPage.LOGIN -> viewModel.signIn(email.trim(), password)
                                    AuthPage.SIGN_UP -> viewModel.signUp(displayName.trim(), email.trim(), password)
                                    AuthPage.FORGOT_PASSWORD -> viewModel.requestPasswordReset(email.trim())
                                    AuthPage.RESET_PASSWORD -> viewModel.updatePassword(password)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !action.isLoading && session != AuthSessionState.ConfigurationMissing
                    ) {
                        if (action.isLoading) CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
                        else Text(
                            when (page) {
                                AuthPage.LOGIN -> "Sign in"
                                AuthPage.SIGN_UP -> "Create account"
                                AuthPage.FORGOT_PASSWORD -> "Send reset link"
                                AuthPage.RESET_PASSWORD -> "Update password"
                            }
                        )
                    }

                    when (page) {
                        AuthPage.LOGIN -> {
                            TextButton(onClick = { page = AuthPage.FORGOT_PASSWORD; viewModel.clearMessage() }, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }
                            TextButton(onClick = { page = AuthPage.SIGN_UP; viewModel.clearMessage() }, modifier = Modifier.fillMaxWidth()) { Text("New here? Create an account") }
                        }
                        AuthPage.SIGN_UP -> TextButton(onClick = { page = AuthPage.LOGIN; viewModel.clearMessage() }, modifier = Modifier.fillMaxWidth()) { Text("Already have an account? Sign in") }
                        AuthPage.FORGOT_PASSWORD -> TextButton(onClick = { page = AuthPage.LOGIN; viewModel.clearMessage() }, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
                        AuthPage.RESET_PASSWORD -> TextButton(onClick = { page = AuthPage.LOGIN; viewModel.clearMessage() }, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, visible: Boolean, onVisibilityChange: () -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        trailingIcon = {
            IconButton(onClick = onVisibilityChange) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (visible) "Hide password" else "Show password")
            }
        }
    )
}

private fun validate(page: AuthPage, name: String, email: String, password: String, confirmation: String): String? {
    if (page != AuthPage.RESET_PASSWORD && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) return "Enter a valid email address."
    if (page == AuthPage.SIGN_UP && name.trim().length < 2) return "Enter your name."
    if (page == AuthPage.LOGIN || page == AuthPage.SIGN_UP || page == AuthPage.RESET_PASSWORD) {
        if (password.length < 8) return "Use a password with at least 8 characters."
    }
    if ((page == AuthPage.SIGN_UP || page == AuthPage.RESET_PASSWORD) && password != confirmation) return "The passwords don't match."
    return null
}

private fun sessionMessage(session: AuthSessionState): String? = when (session) {
    AuthSessionState.SessionUnavailable -> "Your saved session couldn't be restored. Sign in again."
    else -> null
}
