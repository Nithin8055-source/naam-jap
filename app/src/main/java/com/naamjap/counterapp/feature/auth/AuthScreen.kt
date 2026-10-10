package com.naamjap.counterapp.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naamjap.counterapp.R
import com.naamjap.counterapp.domain.repository.AuthSessionState

private enum class AuthPage { LOGIN, SIGN_UP, FORGOT_PASSWORD, RESET_PASSWORD }

private val AuthTextColor = Color(0xFF30251A)
private val AuthMutedColor = Color(0xFF74695E)

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

    fun submit() {
        viewModel.clearMessage()
        validationMessage = validate(page, displayName, email, password, confirmPassword)
        if (validationMessage != null) return
        when (page) {
            AuthPage.LOGIN -> viewModel.signIn(email.trim(), password)
            AuthPage.SIGN_UP -> viewModel.signUp(displayName.trim(), email.trim(), password)
            AuthPage.FORGOT_PASSWORD -> viewModel.requestPasswordReset(email.trim())
            AuthPage.RESET_PASSWORD -> viewModel.updatePassword(password)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
        contentColor = AuthTextColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(Modifier.height(8.dp))
        Image(
            painter = painterResource(R.drawable.ic_golden_lotus),
            contentDescription = "Golden lotus",
            modifier = Modifier.height(92.dp).fillMaxWidth(.32f),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Naam Jap",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Begin your journey of mindful remembrance.",
            style = MaterialTheme.typography.bodyMedium,
            color = AuthMutedColor
        )
        Spacer(Modifier.height(24.dp))

        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 16 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 20 })
            },
            label = "authentication page transition"
        ) { shownPage ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (shownPage) {
                        AuthPage.LOGIN -> "Welcome back"
                        AuthPage.SIGN_UP -> "Create your account"
                        AuthPage.FORGOT_PASSWORD -> "Reset your password"
                        AuthPage.RESET_PASSWORD -> "Choose a new password"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = AuthTextColor
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (shownPage) {
                        AuthPage.LOGIN -> "Continue your Naam Jap practice."
                        AuthPage.SIGN_UP -> "Create a private space for your practice."
                        AuthPage.FORGOT_PASSWORD -> "Enter your email to request a secure reset link."
                        AuthPage.RESET_PASSWORD -> "Choose a new password for your account."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuthMutedColor
                )
                Spacer(Modifier.height(20.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color.White,
                    contentColor = AuthTextColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .8f)),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (session == AuthSessionState.ConfigurationMissing) {
                            Text("Supabase is not configured", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in your untracked local.properties file. Never use a service_role key in this app.",
                                color = AuthMutedColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            if (shownPage == AuthPage.SIGN_UP) {
                                OutlinedTextField(
                                    value = displayName,
                                    onValueChange = { displayName = it.take(80); validationMessage = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Full name") },
                                    singleLine = true,
                                    colors = authFieldColors(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                                )
                            }

                            if (shownPage != AuthPage.RESET_PASSWORD) {
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it.take(254); validationMessage = null },
                                    modifier = Modifier.fillMaxWidth().semantics {
                                        contentType = ContentType.EmailAddress
                                    },
                                    label = { Text("Email address") },
                                    singleLine = true,
                                    colors = authFieldColors(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = if (shownPage == AuthPage.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { submit() })
                                )
                            }

                            if (shownPage == AuthPage.LOGIN || shownPage == AuthPage.SIGN_UP || shownPage == AuthPage.RESET_PASSWORD) {
                                PasswordField(
                                    value = password,
                                    onValueChange = { password = it.take(128); validationMessage = null },
                                    visible = passwordVisible,
                                    onVisibilityChange = { passwordVisible = !passwordVisible },
                                    label = if (shownPage == AuthPage.RESET_PASSWORD) "New password" else "Password",
                                    contentType = if (shownPage == AuthPage.LOGIN) ContentType.Password else ContentType.NewPassword,
                                    imeAction = if (shownPage == AuthPage.SIGN_UP) ImeAction.Next else ImeAction.Done,
                                    onSubmit = { submit() }
                                )
                            }

                            if (shownPage == AuthPage.SIGN_UP || shownPage == AuthPage.RESET_PASSWORD) {
                                PasswordField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it.take(128); validationMessage = null },
                                    visible = confirmVisible,
                                    onVisibilityChange = { confirmVisible = !confirmVisible },
                                    label = "Confirm password",
                                    contentType = ContentType.NewPassword,
                                    imeAction = ImeAction.Done,
                                    onSubmit = { submit() }
                                )
                            }

                            val message = validationMessage ?: action.message ?: sessionMessage(session)
                            if (message != null) {
                                Text(
                                    message,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    color = if (validationMessage != null || action.isError || session == AuthSessionState.SessionUnavailable) {
                                        MaterialTheme.colorScheme.error
                                    } else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Button(
                                onClick = { submit() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = !action.isLoading && session != AuthSessionState.ConfigurationMissing
                            ) {
                                if (action.isLoading) {
                                    CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        when (shownPage) {
                                            AuthPage.LOGIN -> "Log in"
                                            AuthPage.SIGN_UP -> "Create account"
                                            AuthPage.FORGOT_PASSWORD -> "Send reset link"
                                            AuthPage.RESET_PASSWORD -> "Update password"
                                        }
                                    )
                                }
                            }

                            when (shownPage) {
                                AuthPage.LOGIN -> {
                                    TextButton(
                                        onClick = { page = AuthPage.FORGOT_PASSWORD; viewModel.clearMessage() },
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Forgot password?") }
                                    TextButton(
                                        onClick = { page = AuthPage.SIGN_UP; viewModel.clearMessage() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("New here? Create an account") }
                                }
                                AuthPage.SIGN_UP -> TextButton(
                                    onClick = { page = AuthPage.LOGIN; viewModel.clearMessage() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Already have an account? Log in") }
                                AuthPage.FORGOT_PASSWORD, AuthPage.RESET_PASSWORD -> TextButton(
                                    onClick = { page = AuthPage.LOGIN; viewModel.clearMessage() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Back to log in") }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onVisibilityChange: () -> Unit,
    label: String,
    contentType: ContentType,
    imeAction: ImeAction,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().semantics {
            this.contentType = contentType
        },
        label = { Text(label) },
        singleLine = true,
        colors = authFieldColors(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        trailingIcon = {
            IconButton(onClick = onVisibilityChange) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        }
    )
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AuthTextColor,
    unfocusedTextColor = AuthTextColor,
    disabledTextColor = AuthMutedColor,
    errorTextColor = MaterialTheme.colorScheme.error,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White,
    errorContainerColor = Color.White,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = AuthMutedColor,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = AuthMutedColor.copy(alpha = 0.7f),
    cursorColor = MaterialTheme.colorScheme.primary
)

private fun validate(page: AuthPage, name: String, email: String, password: String, confirmation: String): String? {
    if (page != AuthPage.RESET_PASSWORD && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
        return "Enter a valid email address."
    }
    if (page == AuthPage.SIGN_UP && name.trim().length < 2) return "Enter your name."
    if (page == AuthPage.LOGIN || page == AuthPage.SIGN_UP || page == AuthPage.RESET_PASSWORD) {
        if (password.length < 8) return "Use a password with at least 8 characters."
    }
    if ((page == AuthPage.SIGN_UP || page == AuthPage.RESET_PASSWORD) && password != confirmation) {
        return "The passwords don't match."
    }
    return null
}

private fun sessionMessage(session: AuthSessionState): String? = when (session) {
    AuthSessionState.SessionUnavailable -> "Your saved session couldn't be restored. Sign in again."
    else -> null
}
