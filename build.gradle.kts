plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0" apply false
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
            ProcessBuilder("npm", "install")
                .directory(mockDir)
                .inheritIO()
                .start()
                .waitFor()
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
        println("[mock] URL: http://your-host-ip:50303")
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
