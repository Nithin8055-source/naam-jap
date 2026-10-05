package com.naamjap.app.data.remote

import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import android.util.Log
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerializationException

private const val DATA_ACCESS_TAG = "NaamJapData"

/** Logs only server diagnostics that are useful for support; never logs request headers or credentials. */
fun logSafeSupabaseFailure(operation: String, error: Throwable) {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    val postgrest = causes.filterIsInstance<PostgrestRestException>().firstOrNull()
    val safeMessage = postgrest?.let { it.message?.toSafeDiagnosticMessage(it.code) }
    val exceptionType = causes.firstOrNull { it !is DatabaseOperationException }?.javaClass?.simpleName ?: "UnknownException"
    val status = postgrest?.response?.status?.value
    val code = postgrest?.code
    Log.w(DATA_ACCESS_TAG, "operation=$operation exception=$exceptionType httpStatus=${status ?: "none"} postgrestCode=${code ?: "none"} serverMessage=${safeMessage ?: "none"}")
}

private fun String.toSafeDiagnosticMessage(code: String?): String {
    if (code !in setOf("42P01", "42703", "42883", "42501", "PGRST202", "PGRST204", "PGRST205", "PGRST116", "28000", "28P01")) {
        return "[server message withheld for privacy]"
    }
    return replace(
    Regex("(?i)(apikey|authorization|access_token|refresh_token|service_role|sb_[a-z0-9_]+|bearer)[=: ]+\\S+")
) { "${it.groupValues[1]}=[redacted]" }
    .replace(Regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"), "[token]")
    .replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[email]")
    .replace(Regex("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", RegexOption.IGNORE_CASE), "[id]")
    .replace(Regex("(?i)\\b(details?|hint)\\s*[:=].*$"), "[details redacted]")
    .replace(Regex("(?i)\\b(email|user_?id|user|name|notes?|value|token|key)\\b\\s*[:=]\\s*[^,;\\s]+")) { "${it.groupValues[1]}=[redacted]" }
    .replace(Regex("'[^']{1,200}'|\\\"[^\\\"]{1,200}\\\""), "[value]")
    .replace(Regex("https?://\\S+"), "[url]")
    .replace(Regex("[\\r\\n\\t]+"), " ")
    .take(180)
}

class DatabaseOperationException(val operation: String, cause: Throwable) :
    RuntimeException("Supabase data operation failed: $operation", cause)

fun safeSupabaseError(error: Throwable, hasValidatedInternet: Boolean): String {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    val names = causes.map { it::class.simpleName.orEmpty() }

    if (causes.any { it is SerializationException } || names.any { it.contains("Serialization", ignoreCase = true) }) {
        return "Supabase returned data the app couldn't read. Please try again later."
    }
    if (causes.any { it.message == "Supabase is not configured." }) {
        return "Supabase isn't configured on this build."
    }
    if (causes.any { it is UnknownHostException }) {
        return "The Supabase host couldn't be resolved. Check the project URL or DNS settings."
    }
    if (causes.any { it is SocketTimeoutException } || names.any { it.contains("Timeout", ignoreCase = true) }) {
        return "Supabase didn't respond in time. Try again."
    }
    if (causes.any { it is ConnectException }) {
        return if (hasValidatedInternet) "Couldn't connect to Supabase. The service may be unavailable." else "No validated internet connection is available. Reconnect and try again."
    }

    val postgrestError = causes.filterIsInstance<PostgrestRestException>().firstOrNull()
    val status = postgrestError?.response?.status?.value
    when (postgrestError?.code) {
        "42P01", "42703", "42883", "PGRST202", "PGRST204", "PGRST205" ->
            return "The app's database API is out of date. Contact support or update the Supabase staging schema."
        "42501" -> return "The database denied this operation. Check the authenticated account's table permissions and row policy."
        "PGRST116" -> return "The requested database row was missing or ambiguous. Refresh and try again."
    }
    if (status == 401) return "Your session is no longer valid. Sign in again."
    if (status == 403) return "Your account doesn't have permission to access this data."
    if (status != null && status in 400..499) return "Supabase rejected the database request. Check the account data and try again."
    if (status != null && status in 500..599) return "Supabase encountered a server error. Try again later."
    if (names.any { it.contains("Unauthorized", ignoreCase = true) }) {
        return "Authentication failed or your session expired. Sign in again."
    }
    if (names.any { it.contains("Forbidden", ignoreCase = true) }) {
        return "Your account doesn't have permission to access this data."
    }
    if (causes.any { it is IOException }) {
        return if (hasValidatedInternet) "The connection to Supabase was interrupted. Try again." else "No validated internet connection is available. Reconnect and try again."
    }
    if (postgrestError != null) {
        return "Supabase couldn't complete the database request. Check the service configuration and try again."
    }
    return "Couldn't complete the Supabase request. Try again later."
}
