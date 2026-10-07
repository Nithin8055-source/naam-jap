package com.naamjap.app.data.remote.auth

import com.naamjap.app.data.remote.SupabaseProvider
import com.naamjap.app.domain.model.AccountIdentity
import com.naamjap.app.domain.repository.AuthRepository
import com.naamjap.app.domain.repository.AuthSessionState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val provider: SupabaseProvider
) : AuthRepository {
    override val isConfigured: Boolean
        get() = provider.client != null

    override val sessionState: Flow<AuthSessionState> = provider.client?.auth?.sessionStatus
        ?.map { status ->
            when (status) {
                SessionStatus.Initializing -> AuthSessionState.Checking
                is SessionStatus.Authenticated -> {
                    val user = status.session.user
                    val displayName = user?.userMetadata?.get("display_name")
                        ?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                    if (user == null) AuthSessionState.SessionUnavailable
                    else AuthSessionState.SignedIn(AccountIdentity(user.id, user.email, displayName))
                }
                is SessionStatus.NotAuthenticated -> AuthSessionState.SignedOut
                is SessionStatus.RefreshFailure -> AuthSessionState.SessionUnavailable
            }
        } ?: flowOf(AuthSessionState.ConfigurationMissing)

    override suspend fun signIn(email: String, password: String) {
        client().auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(displayName: String, email: String, password: String): Boolean {
        val createdUser = client().auth.signUpWith(Email, redirectUrl = SupabaseProvider.AUTH_REDIRECT_URL) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("display_name", displayName) }
        }
        // Supabase returns a User when confirmation is required and null when it creates a session.
        return createdUser != null
    }

    override suspend fun requestPasswordReset(email: String) {
        client().auth.resetPasswordForEmail(email, redirectUrl = SupabaseProvider.AUTH_REDIRECT_URL)
    }

    override suspend fun reauthenticate(password: String) {
        require(password.isNotBlank()) { "Enter your current password." }
        val auth = client().auth
        val currentUser = requireNotNull(auth.currentUserOrNull()) { "Sign in again before confirming this change." }
        val email = requireNotNull(currentUser.email?.takeIf(String::isNotBlank)) {
            "Password confirmation is unavailable for this account."
        }
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        if (auth.currentUserOrNull()?.id != currentUser.id) {
            auth.signOut()
            error("Password confirmation did not match the signed-in account.")
        }
    }

    override suspend fun updatePassword(password: String) {
        client().auth.updateUser { this.password = password }
    }

    override suspend fun signOut() {
        client().auth.signOut()
    }

    private fun client() = requireNotNull(provider.client) { "Supabase is not configured." }
}
