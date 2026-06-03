import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
}

// Load .env file if it exists
val envFile = rootProject.file(".env")
val envProps = if (envFile.exists()) {
    Properties().apply { load(envFile.inputStream()) }
} else {
    Properties()
}

// Helper to get value from environment variable or .env file
fun envOrProp(envKey: String, propKey: String = envKey): String? {
    return System.getenv(envKey) ?: envProps.getProperty(propKey)
}

android {
    namespace = "me.eroi.lolidaily.muzei"

    compileSdk = 37

    defaultConfig {
        applicationId = "me.eroi.lolidaily.muzei"
        minSdk = 28
        targetSdk = 37
        versionName = envOrProp("VERSION_NAME") ?: "0.1.0"
        versionCode = envOrProp("VERSION_CODE")?.toIntOrNull()
            ?: versionName?.split(".")?.let { parts ->
                val major = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
                val minor = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
                val patch = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
                major * 1000000 + minor * 1000 + patch
            } ?: 1

        buildConfigField("String", "API_BASE_URL", "\"https://loliconey.tsuki.ga\"")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = envOrProp("KEYSTORE_FILE") ?: "../release.keystore"
            storeFile = file(storeFilePath)
            storePassword = envOrProp("KEYSTORE_PASSWORD") ?: ""
            keyAlias = envOrProp("KEY_ALIAS") ?: "release"
            keyPassword = envOrProp("KEY_PASSWORD") ?: ""
        }
    }

    // Check if release keystore exists for fallback logic
    val releaseKeystorePath = envOrProp("KEYSTORE_FILE") ?: "../release.keystore"
    val releaseKeystoreExists = file(releaseKeystorePath).exists()

    buildTypes {
        debug {
            signingConfig = if (releaseKeystoreExists) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "consumer-proguard-rules.pro",
            )
            signingConfig = if (releaseKeystoreExists) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    lint {
        // Abort build on lint errors
        abortOnError = true
        // Treat all warnings as errors
        warningsAsErrors = true
    }
}

dependencies {
    // ── Compose BOM ──────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Material Design 3 (override BOM for LinearWavyProgressIndicator)
    implementation("androidx.compose.material3:material3:1.5.0-alpha20")
    implementation("androidx.compose.material3:material3-window-size-class:1.5.0-alpha20")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity & Lifecycle Compose integration
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Coil — image loading in Compose
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    implementation("io.coil-kt.coil3:coil-gif:3.4.0")
    implementation("io.coil-kt.coil3:coil-svg:3.4.0")

    // Zoomable — pinch-to-zoom with snap-back
    implementation("net.engawapg.lib:zoomable:2.12.0")

    // AndroidX
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Muzei API — wallpaper plugin framework
    implementation("com.google.android.apps.muzei:muzei-api:3.4.2")

    // OkHttp for network requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Kotlin Serialization for JSON parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // WorkManager for background image loading
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Material Color Utilities — M3 color scheme generation from source color
    implementation("me.tatarka.google.material:material-color-utilities:0.1.2")

    // Palette — extract dominant color from bitmap
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Wavy Slider — wave-style progress indicator
    implementation("ir.mahozad.multiplatform:wavy-slider:2.2.0")

    // Room — local database for artwork metadata persistence
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
}

ktlint {
}

tasks.named("lint") {
    dependsOn("ktlintCheck")
}
