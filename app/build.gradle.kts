import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun readPropertiesFile(fileName: String) = Properties().apply {
    val propertiesFile = rootProject.file(fileName)
    if (propertiesFile.isFile) propertiesFile.inputStream().use(::load)
}

val localSupabaseProperties = readPropertiesFile("local.properties")
val exampleSupabaseProperties = readPropertiesFile("local.properties.example")

fun supabaseSetting(name: String): String = sequenceOf(
    providers.gradleProperty(name).orNull,
    System.getenv(name),
    localSupabaseProperties.getProperty(name),
    exampleSupabaseProperties.getProperty(name)
).firstOrNull { !it.isNullOrBlank() }.orEmpty().trim()

fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val supabaseUrl = supabaseSetting("SUPABASE_URL")
val supabasePublishableKey = supabaseSetting("SUPABASE_PUBLISHABLE_KEY")

if (supabasePublishableKey.isNotEmpty() && !supabasePublishableKey.startsWith("sb_publishable_")) {
    error("SUPABASE_PUBLISHABLE_KEY must be a Supabase publishable key. Do not use a secret or service_role key.")
}

android {
    namespace = "com.naamjap.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.naamjap.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "SUPABASE_URL", buildConfigString(supabaseUrl))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", buildConfigString(supabasePublishableKey))
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.haze)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
