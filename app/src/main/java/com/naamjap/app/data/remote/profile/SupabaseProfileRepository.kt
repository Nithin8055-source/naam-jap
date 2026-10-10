package com.naamjap.app.data.remote.profile

import com.naamjap.app.data.remote.SupabaseProvider
import com.naamjap.app.data.remote.DatabaseOperationException
import com.naamjap.app.data.remote.logSafeSupabaseFailure
import com.naamjap.app.domain.model.UserProfile
import com.naamjap.app.domain.repository.ProfileRepository
import com.naamjap.app.notifications.NotificationEvents
import com.naamjap.app.notifications.NotificationEventKind
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import java.time.Instant
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Singleton
class SupabaseProfileRepository @Inject constructor(
    private val provider: SupabaseProvider,
    private val notificationEvents: NotificationEvents
) : ProfileRepository {
    override suspend fun getCurrentProfile(): UserProfile {
        val client = client()
        val userId = currentUserId()
        val profile = traced("profiles.select") {
            client.from("profiles").select(columns = Columns.list("id", "display_name", "email", "avatar_url", "created_at")) {
                filter { eq("id", userId) }
            }.decodeSingle<ProfileRow>()
        }
        return profile.toDomain()
    }

    override suspend fun updateProfile(displayName: String, avatarUrl: String?) {
        val normalizedName = displayName.trim()
        require(normalizedName.length in 2..80) { "Enter a name between 2 and 80 characters." }
        val normalizedAvatar = avatarUrl?.trim()?.takeIf(String::isNotEmpty)
        require(normalizedAvatar == null || normalizedAvatar.length <= 2048) { "Avatar URL is too long." }
        val userId = currentUserId()
        traced("profiles.update") {
            client().from("profiles").update(
                ProfileUpdate(displayName = normalizedName, avatarUrl = normalizedAvatar)
            ) {
                filter { eq("id", userId) }
            }
        }
        notificationEvents.publish(userId, NotificationEventKind.PROFILE_CHANGED, java.util.UUID.randomUUID().toString())
    }

    override suspend fun deleteCurrentAccount() {
        val client = client()
        traced("rpc.delete_my_account") { client.postgrest.rpc("delete_my_account") }
        // The database function has deleted this user's auth row. Clear the device session too.
        try { client.auth.signOut() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
    }

    private fun client() = requireNotNull(provider.client) { "Supabase is not configured." }

    private fun currentUserId(): String = requireNotNull(client().auth.currentUserOrNull()?.id) {
        "Authentication is required."
    }

    private suspend fun <T> traced(operation: String, request: suspend () -> T): T = try {
        request()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        logSafeSupabaseFailure(operation, error)
        throw DatabaseOperationException(operation, error)
    }

    @Serializable
    private data class ProfileRow(
        val id: String,
        @SerialName("display_name") val displayName: String,
        val email: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("created_at") val createdAt: String
    ) {
        fun toDomain() = UserProfile(
            id = id,
            displayName = displayName,
            email = email,
            avatarUrl = avatarUrl,
            createdAt = createdAt.toInstantOrUtc()
        )
    }

    @Serializable
    private data class ProfileUpdate(
        @SerialName("display_name") val displayName: String,
        @SerialName("avatar_url") val avatarUrl: String?
    )
}

private fun String.toInstantOrUtc(): Instant = runCatching { Instant.parse(this) }
    .getOrElse { java.time.OffsetDateTime.parse(this).toInstant() }
