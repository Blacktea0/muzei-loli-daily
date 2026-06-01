plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "me.eroi.lolidaily.muzei"

    compileSdk = 36

    defaultConfig {
        applicationId = "me.eroi.lolidaily.muzei"
        minSdk = 28
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"

        buildConfigField("String", "API_BASE_URL", "\"https://loliconey.tsuki.ga\"")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "consumer-proguard-rules.pro",
            )
            val storeFilePath = System.getenv("KEYSTORE_FILE")
            if (storeFilePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            // If no keystore is configured, the release build will use debug signing
            // This allows CI to build release APKs without signing configuration
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ── Mock server ─────────────────────────────────────────
    val mockDir = rootProject.projectDir.resolve("mock")

    tasks.register("startMockServer") {
        group = "mock"
        description = "Start the local API mock server in background (requires Node.js)"
        doLast {
            // Kill any existing server first
            val pidFile = mockDir.resolve(".server.pid")
            if (pidFile.exists()) {
                val oldPid = pidFile.readText().trim()
                runCatching { ProcessBuilder("taskkill", "/F", "/PID", oldPid).start().waitFor() }
                pidFile.delete()
            }

            if (!mockDir.resolve("node_modules").exists()) {
                println("[mock] Installing dependencies first...")
                exec {
                    workingDir = mockDir
                    commandLine("npm", "install")
                }
            }

            val logFile = mockDir.resolve("server.log")
            val process =
                ProcessBuilder("node", "server.js")
                    .directory(mockDir)
                    .redirectOutput(ProcessBuilder.Redirect.to(logFile))
                    .redirectError(ProcessBuilder.Redirect.to(logFile))
                    .start()
            pidFile.writeText(process.pid().toString())
            println("[mock] Server started (PID ${process.pid()})")
            println("[mock] URL: http://192.168.31.129:50303")
            println("[mock] Logs: $logFile")
            println("[mock] Run './gradlew stopMockServer' to stop")
        }
    }

    tasks.register("stopMockServer") {
        group = "mock"
        description = "Stop the mock server started by startMockServer"
        doLast {
            val pidFile = mockDir.resolve(".server.pid")
            if (!pidFile.exists()) {
                println("[mock] No PID file found — server not running?")
                return@doLast
            }
            val pid = pidFile.readText().trim()
            try {
                val result = ProcessBuilder("taskkill", "/F", "/PID", pid).start()
                result.waitFor()
                if (result.exitValue() == 0) {
                    pidFile.delete()
                    println("[mock] Server stopped (PID $pid)")
                } else {
                    println("[mock] Failed to stop PID $pid (exit ${result.exitValue()})")
                }
            } catch (e: Exception) {
                println("[mock] Error killing process: ${e.message}")
            }
        }
    }

    tasks.register("mockLogs") {
        group = "mock"
        description = "Print the mock server log file"
        doLast {
            val logFile = mockDir.resolve("server.log")
            if (logFile.exists()) {
                println(logFile.readText())
            } else {
                println("[mock] No log file found at $logFile")
            }
        }
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

    // Material Design 3
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
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
