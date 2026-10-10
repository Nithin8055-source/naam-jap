package com.naamjap.counterapp.domain.repository

import com.naamjap.counterapp.domain.model.AccountIdentity
import kotlinx.coroutines.flow.Flow

sealed interface AuthSessionState {
    data object Checking : AuthSessionState
    data object ConfigurationMissing : AuthSessionState
    data object SignedOut : AuthSessionState
    data class SignedIn(val account: AccountIdentity) : AuthSessionState
    data object SessionUnavailable : AuthSessionState
}

interface AuthRepository {
    val sessionState: Flow<AuthSessionState>
    val isConfigured: Boolean

    suspend fun signIn(email: String, password: String)
    suspend fun signUp(displayName: String, email: String, password: String): Boolean
    suspend fun requestPasswordReset(email: String)
    suspend fun reauthenticate(password: String)
    suspend fun updatePassword(password: String)
    suspend fun signOut()
}
