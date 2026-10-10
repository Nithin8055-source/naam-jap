package com.naamjap.counterapp.data.remote

import com.naamjap.counterapp.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.logging.LogLevel
import kotlinx.serialization.json.Json

data class SupabaseConfiguration(
    val url: String,
    val publishableKey: String
) {
    val isValid: Boolean
        get() = runCatching {
            val parsed = java.net.URI(url)
            parsed.scheme == "https" && !parsed.host.isNullOrBlank() && publishableKey.isNotBlank()
        }.getOrDefault(false)
}

@Singleton
class SupabaseProvider @Inject constructor() {
    val configuration = SupabaseConfiguration(
        url = BuildConfig.SUPABASE_URL.trim(),
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
    )

    val client = if (configuration.isValid) {
        createSupabaseClient(configuration.url, configuration.publishableKey) {
            defaultLogLevel = LogLevel.NONE
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = AUTH_REDIRECT_SCHEME
                host = AUTH_REDIRECT_HOST
            }
            install(Postgrest)
        }
    } else {
        null
    }

    companion object {
        const val AUTH_REDIRECT_SCHEME = "com.naamjap.counterapp"
        const val AUTH_REDIRECT_HOST = "auth-callback"
        const val AUTH_REDIRECT_URL = "$AUTH_REDIRECT_SCHEME://$AUTH_REDIRECT_HOST"
    }
}
