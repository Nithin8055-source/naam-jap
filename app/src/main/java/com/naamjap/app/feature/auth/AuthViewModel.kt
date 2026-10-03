package com.naamjap.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naamjap.app.domain.repository.AuthRepository
import com.naamjap.app.domain.repository.AuthSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class AuthActionState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    repository: AuthRepository
) : ViewModel() {
    val sessionState = repository.sessionState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        if (repository.isConfigured) AuthSessionState.Checking else AuthSessionState.ConfigurationMissing
    )

    private val _actionState = MutableStateFlow(AuthActionState())
    val actionState = _actionState.asStateFlow()
    private val actionMutex = Mutex()

    fun signIn(email: String, password: String) = perform(
        successMessage = "Signed in."
    ) {
        repository.signIn(email, password)
        null
    }

    fun signUp(name: String, email: String, password: String) = perform(
        successMessage = "Account created."
    ) {
        val verificationRequired = repository.signUp(name, email, password)
        if (verificationRequired) "Check your email to verify your account before signing in." else "Account created."
    }

    fun requestPasswordReset(email: String) = perform(
        successMessage = "If this email can receive a reset link, Supabase has sent one."
    ) {
        repository.requestPasswordReset(email)
        null
    }

    fun updatePassword(password: String) = perform(
        successMessage = "Your password was updated."
    ) {
        repository.updatePassword(password)
        null
    }

    fun signOut() = perform(successMessage = "Signed out.") {
        repository.signOut()
        null
    }

    fun clearMessage() = _actionState.update { it.copy(message = null, isError = false) }

    private fun perform(successMessage: String, action: suspend () -> String?) {
        if (!actionMutex.tryLock()) return
        viewModelScope.launch {
            _actionState.value = AuthActionState(isLoading = true)
            try {
                val actionMessage = action()
                _actionState.value = AuthActionState(message = actionMessage ?: successMessage)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _actionState.value = AuthActionState(
                    message = safeAuthError(error),
                    isError = true
                )
            } finally {
                actionMutex.unlock()
            }
        }
    }

    private fun safeAuthError(error: Throwable): String {
        val message = error.message.orEmpty().lowercase()
        return when {
            error is IOException || "network" in message || "connect" in message || "timeout" in message ->
                "Can't reach Naam Jap right now. Check your connection and try again."
            "invalid login credentials" in message -> "The email or password is incorrect."
            "already registered" in message || "user already exists" in message -> "An account already exists for this email. Try signing in."
            "password" in message -> "The password was rejected. Check the requirements and try again."
            "email" in message -> "The email address couldn't be used. Check it and try again."
            else -> "The request couldn't be completed. Please try again."
        }
    }
}
