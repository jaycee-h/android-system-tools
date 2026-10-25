package com.pagnchanak.systemtools.tools.profiles

import com.pagnchanak.systemtools.root.CommandResult
import com.pagnchanak.systemtools.root.RootShell
import com.pagnchanak.systemtools.root.shellQuote

data class UserProfile(val id: Int, val name: String, val flags: Int, val running: Boolean) {
    val isWorkProfile: Boolean get() = flags and FLAG_MANAGED_PROFILE != 0

    companion object {
        private const val FLAG_MANAGED_PROFILE = 0x20
    }
}

/**
 * Makes already-installed packages available in other users/profiles. APKs
 * are shared between users, so `pm install-existing` just enables the package
 * for that user (the same thing that happens to new system apps), with no
 * download and no store needed.
 */
object Profiles {

    private val userLine = Regex("""UserInfo\{(\d+):([^:]*):([0-9a-fA-F]+)\}(.*)""")

    /** Every user/profile on the device except [currentUserId]. */
    suspend fun list(currentUserId: Int): List<UserProfile> =
        RootShell.run("pm list users").out.mapNotNull { line ->
            val match = userLine.find(line) ?: return@mapNotNull null
            val (id, name, flags, rest) = match.destructured
            UserProfile(id.toInt(), name, flags.toInt(16), rest.contains("running"))
        }.filter { it.id != currentUserId }

    /** Package names installed for [userId]. */
    suspend fun installedPackages(userId: Int): Set<String> =
        RootShell.run("pm list packages --user $userId").out
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .toSet()

    suspend fun install(userId: Int, packageName: String): CommandResult {
        val result = RootShell.run("pm install-existing --user $userId ${shellQuote(packageName)}")
        val ok = result.isSuccess && result.out.any { it.contains("installed for user", ignoreCase = true) }
        return if (ok) result else result.copy(code = if (result.code == 0) 1 else result.code)
    }

    /** Removes the package (and its data) from [userId] only; other users keep it. */
    suspend fun uninstall(userId: Int, packageName: String): CommandResult {
        val result = RootShell.run("pm uninstall --user $userId ${shellQuote(packageName)}")
        val ok = result.isSuccess && result.out.any { it.contains("Success") }
        return if (ok) result else result.copy(code = if (result.code == 0) 1 else result.code)
    }
}
