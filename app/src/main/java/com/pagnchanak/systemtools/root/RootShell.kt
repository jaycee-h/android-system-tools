package com.pagnchanak.systemtools.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Single-quotes [value] for safe interpolation into a shell command. */
fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/** Result of running one or more shell commands. */
data class CommandResult(
    val code: Int,
    val out: List<String>,
    val err: List<String>,
) {
    val isSuccess: Boolean get() = code == 0

    /** stdout and stderr joined, for showing to the user. */
    val output: String get() = (out + err).joinToString("\n").trim()
}

/** How the app currently stands with respect to root access. */
sealed interface RootStatus {
    data object Checking : RootStatus
    data object Denied : RootStatus
    data class Granted(val provider: String?) : RootStatus
}

/**
 * Thin wrapper around libsu. Every tool should go through here rather than
 * touching [Shell] directly, so root handling stays in one place.
 */
object RootShell {

    fun configure(debug: Boolean) {
        Shell.enableVerboseLogging = debug
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }

    /** Opens (or reuses) the main shell, prompting for root if needed. */
    suspend fun requestRoot(): RootStatus = withContext(Dispatchers.IO) {
        val shell = Shell.getShell()
        if (shell.isRoot) RootStatus.Granted(provider()) else RootStatus.Denied
    }

    /** Runs [commands] in the main shell (root if granted, otherwise a plain sh). */
    suspend fun run(vararg commands: String): CommandResult = withContext(Dispatchers.IO) {
        val result = Shell.cmd(*commands).to(ArrayList(), ArrayList()).exec()
        CommandResult(result.code, result.out, result.err)
    }

    /** Reboots the device normally. */
    suspend fun reboot(): CommandResult = run("svc power reboot || reboot")

    /** Best-effort detection of the root solution (Magisk, KernelSU, APatch...). */
    private fun provider(): String? {
        val version = Shell.cmd("su -v").exec().out.firstOrNull()?.trim().orEmpty()
        return when {
            version.isEmpty() -> null
            version.contains("MAGISK", ignoreCase = true) -> "Magisk ${version.substringBefore(':')}"
            version.contains("KernelSU", ignoreCase = true) -> "KernelSU"
            version.contains("APatch", ignoreCase = true) -> "APatch"
            else -> version
        }
    }
}
