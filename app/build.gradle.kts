import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// local.properties (no se sube) gana sobre gradle.properties
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun config(name: String): String =
    localProps.getProperty(name) ?: providers.gradleProperty(name).orNull ?: System.getenv(name) ?: ""

android {
    namespace = "com.cunicella.fichaje"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cunicella.fichaje"
        minSdk = 26
        targetSdk = 35
        // En CI, cada compilación tiene su número: así la app sabe si hay una versión nueva
        val build = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.1.$build"
        buildConfigField("String", "SUPABASE_URL", "\"${config("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${config("SUPABASE_ANON_KEY")}\"")
    }

    // Firma fija para que las actualizaciones se instalen encima. La clave viene de los
    // secrets de GitHub; sin ella (compilación local) se firma con la clave de debug.
    val storeFile = System.getenv("SIGNING_STORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (storeFile != null) {
            create("release") {
                this.storeFile = storeFile
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "fichaje"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
