package com.pagnchanak.systemtools.tools.systemizer

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import com.pagnchanak.systemtools.apps.InstalledApp
import com.pagnchanak.systemtools.root.CommandResult
import com.pagnchanak.systemtools.root.RootShell
import com.pagnchanak.systemtools.root.shellQuote

/**
 * Systemizes and de-systemizes apps systemlessly, by writing a module into
 * /data/adb/modules (understood by KernelSU, Magisk and APatch). Nothing on
 * the real system partition is touched; every change applies on reboot and
 * can be reverted by removing the module.
 */
object Systemizer {

    private const val MODULES = "/data/adb/modules"
    private const val PREFIX = "systools_"

    enum class Kind(val tag: String) { Systemize("sys"), Desystemize("desys") }

    data class Module(val kind: Kind, val packageName: String, val pendingRemoval: Boolean)

    fun moduleId(kind: Kind, packageName: String) = "$PREFIX${kind.tag}_" + packageName.replace('.', '_')

    /** Modules this app created, keyed by package name. */
    suspend fun modules(packageNames: Collection<String>): Map<String, Module> {
        val result = RootShell.run(
            "for d in $MODULES/$PREFIX*; do [ -d \"\$d\" ] || continue; " +
                "if [ -f \"\$d/remove\" ]; then echo \"\${d##*/} remove\"; else echo \"\${d##*/}\"; fi; done",
        )
        val byId = result.out.associate { line ->
            val parts = line.trim().split(' ')
            parts[0] to (parts.getOrNull(1) == "remove")
        }
        return packageNames.mapNotNull { pkg ->
            Kind.entries.firstNotNullOfOrNull { kind ->
                byId[moduleId(kind, pkg)]?.let { Module(kind, pkg, it) }
            }?.let { pkg to it }
        }.toMap()
    }

    /**
     * Mounts a copy of [app] into /system/priv-app (or /system/app). The /data
     * copy stays installed as an update of that "system" package, so app data
     * and Play Store updates are unaffected.
     */
    suspend fun systemize(app: InstalledApp, privileged: Boolean, pm: PackageManager): CommandResult {
        val id = moduleId(Kind.Systemize, app.packageName)
        val root = "$MODULES/$id"
        val partitionDir = if (privileged) "priv-app" else "app"
        val appDir = "$root/system/$partitionDir/${app.packageName}"

        val script = buildList {
            add("rm -rf ${shellQuote(root)}")
            add("mkdir -p ${shellQuote(appDir)}")
            app.apkPaths.forEach { add("cp ${shellQuote(it)} ${shellQuote(appDir)}/") }
            nativeLibArch(app)?.let { arch ->
                val libDir = "$appDir/lib/$arch"
                add("mkdir -p ${shellQuote(libDir)}")
                add("cp ${shellQuote(app.nativeLibraryDir!!)}/*.so ${shellQuote(libDir)}/ 2>/dev/null || true")
            }
            if (privileged) {
                // Unlisted privileged permissions can bootloop devices that enforce the allowlist.
                val permDir = "$root/system/etc/permissions"
                add("mkdir -p ${shellQuote(permDir)}")
                add(writeFile("$permDir/privapp-permissions-${app.packageName}.xml", privappXml(app.packageName, privilegedPermissions(app.packageName, pm))))
            }
            add(writeFile("$root/module.prop", moduleProp(id, "Systemize: ${app.label}", "Mounts ${app.packageName} as a /system/$partitionDir app.")))
            addAll(fixPermissions(root))
        }
        return runScript(script, cleanup = root)
    }

    /**
     * Hides the system copy of [app] so it becomes a normal user app. A system
     * app that was never updated is first installed as a user update, so it
     * keeps working (and keeps its data) once the system copy disappears.
     */
    suspend fun desystemize(app: InstalledApp): CommandResult {
        val codePath = systemCodePath(app.packageName)
            ?: return CommandResult(1, emptyList(), listOf("Could not find the system copy of ${app.packageName}"))

        if (!app.isUpdatedSystem) {
            val apks = RootShell.run("ls ${shellQuote(codePath)}/*.apk 2>/dev/null || echo ${shellQuote(codePath)}").out
            val install = RootShell.run("pm install -r ${apks.joinToString(" ") { shellQuote(it) }}")
            if (!install.isSuccess || install.out.none { it.contains("Success") }) {
                return CommandResult(1, emptyList(), listOf("Couldn't install a user copy first, so nothing was changed.", install.output))
            }
        }

        val id = moduleId(Kind.Desystemize, app.packageName)
        val root = "$MODULES/$id"
        // Module paths mirror the real ones; non-/system partitions live under system/ too.
        val target = root + if (codePath.startsWith("/system/")) codePath else "/system$codePath"

        val script = buildList {
            add("rm -rf ${shellQuote(root)}")
            if (codePath.endsWith(".apk")) {
                add("mkdir -p ${shellQuote(target.substringBeforeLast('/'))}")
                add("mknod ${shellQuote(target)} c 0 0") // whiteout
            } else {
                add("mkdir -p ${shellQuote(target)}")
                add("touch ${shellQuote(target)}/.replace") // empty, replacing dir hides the original
            }
            add(writeFile("$root/module.prop", moduleProp(id, "De-systemize: ${app.label}", "Hides the system copy of ${app.packageName} ($codePath).")))
            addAll(fixPermissions(root))
        }
        return runScript(script, cleanup = root)
    }

    /**
     * Undoes a module. A module that is already mounted is flagged for removal
     * on the next boot; one that never got applied is deleted straight away.
     */
    suspend fun revert(module: Module, applied: Boolean): CommandResult {
        val root = shellQuote("$MODULES/${moduleId(module.kind, module.packageName)}")
        return RootShell.run(if (applied) "touch $root/remove" else "rm -rf $root")
    }

    /** Cancels a pending [revert]. */
    suspend fun cancelRevert(module: Module): CommandResult =
        RootShell.run("rm -f ${shellQuote("$MODULES/${moduleId(module.kind, module.packageName)}")}/remove")

    /** The package's code path on a system partition, from the package manager's own records. */
    private suspend fun systemCodePath(packageName: String): String? =
        RootShell.run("dumpsys package ${shellQuote(packageName)}").out
            .map { it.trim() }
            .filter { it.startsWith("codePath=") }
            .map { it.removePrefix("codePath=") }
            .firstOrNull { !it.startsWith("/data/") }

    private fun nativeLibArch(app: InstalledApp): String? {
        val dir = app.nativeLibraryDir ?: return null
        val arch = dir.substringAfterLast('/')
        return arch.takeIf { it in setOf("arm64", "arm", "x86_64", "x86") && dir.startsWith("/data/") }
    }

    private fun privilegedPermissions(packageName: String, pm: PackageManager): List<String> {
        val requested = runCatching {
            @Suppress("DEPRECATION") // flags overload is API 33+, minSdk is 29
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        }.getOrNull().orEmpty()
        return requested.filter { name ->
            runCatching { pm.getPermissionInfo(name, 0).protectionFlags and PermissionInfo.PROTECTION_FLAG_PRIVILEGED != 0 }
                .getOrDefault(false)
        }.sorted()
    }

    private fun privappXml(packageName: String, permissions: List<String>) = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<permissions>")
        appendLine("""    <privapp-permissions package="$packageName">""")
        permissions.forEach { appendLine("""        <permission name="$it"/>""") }
        appendLine("    </privapp-permissions>")
        appendLine("</permissions>")
    }

    private fun moduleProp(id: String, name: String, description: String) = """
        id=$id
        name=$name
        version=1.0
        versionCode=1
        author=System Tools
        description=$description Managed by System Tools.
    """.trimIndent() + "\n"

    private fun writeFile(path: String, content: String): String =
        "printf '%s' ${shellQuote(content)} > ${shellQuote(path)}"

    private fun fixPermissions(root: String) = listOf(
        "chown -R 0:0 ${shellQuote(root)}",
        "find ${shellQuote(root)} -type d -exec chmod 755 {} +",
        "find ${shellQuote(root)} -type f -exec chmod 644 {} +",
        "chcon -R u:object_r:system_file:s0 ${shellQuote("$root/system")}",
    )

    /** Runs [lines] in a subshell that stops at the first failure, removing [cleanup] if it does. */
    private suspend fun runScript(lines: List<String>, cleanup: String): CommandResult {
        val result = RootShell.run("(\nset -e\n${lines.joinToString("\n")}\n)")
        if (!result.isSuccess) RootShell.run("rm -rf ${shellQuote(cleanup)}")
        return result
    }
}
