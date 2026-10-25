package com.pagnchanak.systemtools.tools.systemizer

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import com.pagnchanak.systemtools.apps.InstalledApp
import com.pagnchanak.systemtools.root.CommandResult
import com.pagnchanak.systemtools.root.RootShell
import com.pagnchanak.systemtools.root.shellQuote

/** One app change kept in the module. */
sealed interface Entry {
    val packageName: String

    /** Mounted into /system/priv-app (or /system/app). */
    data class Systemize(override val packageName: String, val privileged: Boolean) : Entry

    /**
     * System copy at [codePath] is hidden. With [keepUserApp] the app lives on
     * as a user app with its data; otherwise it's uninstalled once after boot.
     */
    data class Desystemize(override val packageName: String, val keepUserApp: Boolean, val codePath: String) : Entry
}

/** What is on disk: the mounted module, an update staged for next boot, and modules from older versions. */
data class ModuleState(
    /** The module directory exists (it may be mounted even if flagged for removal). */
    val activeExists: Boolean = false,
    /** Entries of the active module (empty if it doesn't exist or is flagged for removal). */
    val active: Map<String, Entry> = emptyMap(),
    /** Entries staged for the next boot, or null if nothing is staged. */
    val staged: Map<String, Entry>? = null,
    /** Per-app modules created by older versions of the app (not flagged for removal). */
    val legacy: Map<String, Entry> = emptyMap(),
    /** Old per-app modules already flagged for removal, still mounted until reboot. */
    val legacyRemoving: Map<String, Entry> = emptyMap(),
) {
    /** The state the device will be in after the next reboot. */
    val desired: Map<String, Entry> get() = staged ?: (legacy + active)

    /** Entries currently mounted, used to tell what a pending revert will undo. */
    val mounted: Map<String, Entry> get() = legacyRemoving + legacy + active
}

/**
 * Systemizes and de-systemizes apps systemlessly through a single module,
 * /data/adb/modules/systools (understood by KernelSU, Magisk and APatch).
 *
 * Every change rebuilds the whole module from the desired list and stages it
 * in /data/adb/modules_update, the root managers' own module-update path, so
 * the mounted module is never edited in place and the swap happens on reboot.
 * Per-app modules from older versions are folded in and removed on the first
 * change.
 */
object Systemizer {

    private const val ID = "systools"
    private const val MODULES = "/data/adb/modules"
    private const val ACTIVE = "$MODULES/$ID"
    private const val STAGE = "/data/adb/modules_update/$ID"
    private const val LIST = "systools.list"

    private const val LEGACY_PREFIX = "systools_"
    private val legacyDesc = Regex("""Hides the system copy of (\S+) \((.+)\)\.""")

    private fun legacyId(kind: String, packageName: String) = "$LEGACY_PREFIX${kind}_" + packageName.replace('.', '_')

    suspend fun state(): ModuleState {
        val out = RootShell.run(
            """
            [ -d $ACTIVE ] && echo AEXISTS
            if [ -d $ACTIVE ] && [ ! -f $ACTIVE/remove ]; then sed 's/^/A /' $ACTIVE/$LIST 2>/dev/null; fi
            if [ -d $STAGE ]; then echo STAGED; sed 's/^/S /' $STAGE/$LIST 2>/dev/null; fi
            for d in $MODULES/${LEGACY_PREFIX}sys_* $MODULES/${LEGACY_PREFIX}desys_*; do
              [ -d "${'$'}d" ] || continue
              r=0; [ -f "${'$'}d/remove" ] && r=1
              for a in "${'$'}d"/system/priv-app/* "${'$'}d"/system/app/*; do [ -d "${'$'}a" ] && echo "LS ${'$'}r ${'$'}{a#${'$'}d/system/}"; done
              case "${'$'}d" in *_desys_*) echo "LD ${'$'}r ${'$'}(grep '^description=' "${'$'}d/module.prop")";; esac
            done
            """.trimIndent(),
        ).out

        val activeExists = "AEXISTS" in out
        val active = mutableMapOf<String, Entry>()
        var staged: MutableMap<String, Entry>? = null
        val legacy = mutableMapOf<String, Entry>()
        val legacyRemoving = mutableMapOf<String, Entry>()
        for (line in out) {
            when {
                line == "STAGED" -> staged = mutableMapOf()
                line.startsWith("A ") -> parseEntry(line.substring(2))?.let { active[it.packageName] = it }
                line.startsWith("S ") -> parseEntry(line.substring(2))?.let { staged?.put(it.packageName, it) }
                line.startsWith("LS ") || line.startsWith("LD ") -> {
                    val removing = line[3] == '1'
                    val rest = line.substring(5)
                    val entry = if (line.startsWith("LS ")) {
                        Entry.Systemize(rest.substringAfter('/'), privileged = rest.startsWith("priv-app/"))
                    } else {
                        legacyDesc.find(rest)?.let { Entry.Desystemize(it.groupValues[1], keepUserApp = true, codePath = it.groupValues[2]) }
                    } ?: continue
                    (if (removing) legacyRemoving else legacy)[entry.packageName] = entry
                }
            }
        }
        return ModuleState(activeExists, active, staged, legacy, legacyRemoving)
    }

    /**
     * Makes [desired] the state after the next reboot. Old per-app modules are
     * flagged for removal since their entries are carried into [desired].
     */
    suspend fun apply(current: ModuleState, desired: Map<String, Entry>, apps: Map<String, InstalledApp>, pm: PackageManager): CommandResult {
        val retireLegacy = current.legacy.values.map { entry ->
            val kind = if (entry is Entry.Systemize) "sys" else "desys"
            "touch ${shellQuote("$MODULES/${legacyId(kind, entry.packageName)}")}/remove"
        }
        val script = when {
            // Nothing left to do after reboot: drop the module and any staged update.
            desired.isEmpty() -> listOf("rm -rf $STAGE", "[ -d $ACTIVE ] && touch $ACTIVE/remove && rm -f $ACTIVE/update", "true") + retireLegacy
            // Back to exactly what's mounted: just discard the staged update.
            desired == current.active && current.legacy.isEmpty() ->
                listOf("rm -rf $STAGE", "rm -f $ACTIVE/update $ACTIVE/remove")
            else -> buildModule(current, desired, apps, pm) + retireLegacy
        }
        return runScript(script)
    }

    /**
     * Installs the system APKs at [codePath] as a user update, so the app
     * survives its system copy being hidden.
     */
    suspend fun installUserCopy(codePath: String): CommandResult {
        val apks = RootShell.run("ls ${shellQuote(codePath)}/*.apk 2>/dev/null || echo ${shellQuote(codePath)}").out
        val install = RootShell.run("pm install -r ${apks.joinToString(" ") { shellQuote(it) }}")
        return if (install.isSuccess && install.out.any { it.contains("Success") }) install
        else install.copy(code = if (install.code == 0) 1 else install.code)
    }

    /** The package's code path on a system partition, from the package manager's own records. */
    suspend fun systemCodePath(packageName: String): String? =
        RootShell.run("dumpsys package ${shellQuote(packageName)}").out
            .map { it.trim() }
            .filter { it.startsWith("codePath=") }
            .map { it.removePrefix("codePath=") }
            .firstOrNull { !it.startsWith("/data/") }

    private fun buildModule(current: ModuleState, desired: Map<String, Entry>, apps: Map<String, InstalledApp>, pm: PackageManager): List<String> {
        // A module that doesn't exist yet isn't mounted, so it can be written in place; otherwise stage an update.
        val firstInstall = !current.activeExists
        val tmp = "$STAGE.tmp"
        val entries = desired.values.sortedBy { it.packageName }
        return buildList {
            add("rm -rf $tmp")
            add("mkdir -p $tmp")
            entries.forEach { entry ->
                when (entry) {
                    is Entry.Systemize -> addAll(systemizeEntry(tmp, entry, apps[entry.packageName], pm))
                    is Entry.Desystemize -> addAll(desystemizeEntry(tmp, entry))
                }
            }
            // Carry over "already uninstalled" markers so removals stay one-shot.
            entries.filterIsInstance<Entry.Desystemize>()
                .filter { !it.keepUserApp && current.active[it.packageName] == it }
                .forEach {
                    add("mkdir -p $tmp/removed")
                    add("cp $ACTIVE/removed/${shellQuote(it.packageName)} $tmp/removed/ 2>/dev/null || true")
                }
            add(writeFile("$tmp/$LIST", entries.joinToString("") { formatEntry(it) + "\n" }))
            add(writeFile("$tmp/module.prop", moduleProp(entries)))
            add(writeFile("$tmp/service.sh", SERVICE_SH))
            addAll(fixPermissions(tmp))
            add("chmod 755 $tmp/service.sh")
            if (firstInstall) {
                add("rm -rf $STAGE $ACTIVE")
                add("mv $tmp $ACTIVE")
            } else {
                add("rm -rf $STAGE")
                add("mkdir -p ${STAGE.substringBeforeLast('/')}")
                add("mv $tmp $STAGE")
                add("rm -f $ACTIVE/remove")
                add("touch $ACTIVE/update")
            }
        }
    }

    private fun systemizeEntry(root: String, entry: Entry.Systemize, app: InstalledApp?, pm: PackageManager): List<String> {
        val pkg = entry.packageName
        val partition = if (entry.privileged) "priv-app" else "app"
        val appDir = shellQuote("$root/system/$partition/$pkg")
        // Reuse the copy already in a module (this one or an old per-app one) before copying from /data.
        val existing = listOf(ACTIVE, "$MODULES/${legacyId("sys", pkg)}")
            .flatMap { listOf("$it/system/priv-app/$pkg", "$it/system/app/$pkg") }
        return buildList {
            add("mkdir -p $appDir")
            val fresh = buildList {
                app?.apkPaths?.forEach { add("cp ${shellQuote(it)} $appDir/") }
                app?.let { nativeLibArch(it) }?.let { arch ->
                    add("mkdir -p $appDir/lib/$arch")
                    add("cp ${shellQuote(app.nativeLibraryDir!!)}/*.so $appDir/lib/$arch/ 2>/dev/null || true")
                }
                if (app == null) add("echo ${shellQuote("Can't find the APK of $pkg")} >&2; false")
            }
            add(
                existing.joinToString(" el") { "if [ -d ${shellQuote(it)} ]; then cp -a ${shellQuote(it)}/. $appDir/;" } +
                    " else ${fresh.joinToString("; ")}; fi",
            )
            if (entry.privileged) {
                // Unlisted privileged permissions can bootloop devices that enforce the allowlist.
                val permDir = "$root/system/etc/permissions"
                val file = "privapp-permissions-$pkg.xml"
                add("mkdir -p ${shellQuote(permDir)}")
                if (app != null) {
                    add(writeFile("$permDir/$file", privappXml(pkg, privilegedPermissions(pkg, pm))))
                } else {
                    val sources = listOf(ACTIVE, "$MODULES/${legacyId("sys", pkg)}").map { "$it/system/etc/permissions/$file" }
                    add(sources.joinToString(" || ") { "cp ${shellQuote(it)} ${shellQuote(permDir)}/ 2>/dev/null" } + " || true")
                }
            }
        }
    }

    private fun desystemizeEntry(root: String, entry: Entry.Desystemize): List<String> {
        val path = entry.codePath
        // Module paths mirror the real ones; non-/system partitions live under system/ too.
        val target = root + if (path.startsWith("/system/")) path else "/system$path"
        return if (path.endsWith(".apk")) {
            listOf("mkdir -p ${shellQuote(target.substringBeforeLast('/'))}", "mknod ${shellQuote(target)} c 0 0") // whiteout
        } else {
            listOf("mkdir -p ${shellQuote(target)}", "touch ${shellQuote(target)}/.replace") // empty replacement hides the original
        }
    }

    private fun formatEntry(entry: Entry): String = when (entry) {
        is Entry.Systemize -> "sys ${entry.packageName} ${if (entry.privileged) "priv" else "app"}"
        is Entry.Desystemize -> "desys ${entry.packageName} ${if (entry.keepUserApp) "keep" else "remove"} ${entry.codePath}"
    }

    private fun parseEntry(line: String): Entry? {
        val parts = line.trim().split(' ', limit = 4)
        return when {
            parts.size >= 3 && parts[0] == "sys" -> Entry.Systemize(parts[1], privileged = parts[2] == "priv")
            parts.size >= 4 && parts[0] == "desys" -> Entry.Desystemize(parts[1], keepUserApp = parts[2] == "keep", codePath = parts[3])
            else -> null
        }
    }

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

    private fun moduleProp(entries: List<Entry>): String {
        val sys = entries.count { it is Entry.Systemize }
        val desys = entries.size - sys
        return """
            id=$ID
            name=System Tools
            version=${entries.size} app${if (entries.size == 1) "" else "s"}
            versionCode=${System.currentTimeMillis() / 1000}
            author=System Tools
            description=Systemized: $sys, de-systemized: $desys. Managed by the System Tools app; change it there.
        """.trimIndent() + "\n"
    }

    /** Once per removed app after boot: uninstall the leftover user copy (and its data). */
    private val SERVICE_SH = """
        #!/system/bin/sh
        MODDIR=${'$'}{0%/*}
        until [ "${'$'}(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
        sleep 10
        while read -r kind pkg mode path; do
          [ "${'$'}kind" = desys ] && [ "${'$'}mode" = remove ] || continue
          [ -f "${'$'}MODDIR/removed/${'$'}pkg" ] && continue
          # Only touch it if it's now a plain user app, i.e. the system copy is really hidden.
          if pm list packages -3 | grep -qx "package:${'$'}pkg"; then pm uninstall "${'$'}pkg"; fi
          if ! pm list packages | grep -qx "package:${'$'}pkg"; then mkdir -p "${'$'}MODDIR/removed"; touch "${'$'}MODDIR/removed/${'$'}pkg"; fi
        done < "${'$'}MODDIR/$LIST"
    """.trimIndent() + "\n"

    private fun writeFile(path: String, content: String): String =
        "printf '%s' ${shellQuote(content)} > ${shellQuote(path)}"

    private fun fixPermissions(root: String) = listOf(
        "chown -R 0:0 $root",
        "find $root -type d -exec chmod 755 {} +",
        "find $root -type f -exec chmod 644 {} +",
        "[ ! -d $root/system ] || chcon -R u:object_r:system_file:s0 $root/system",
    )

    /** Runs [lines] in a subshell that stops at the first failure, cleaning up the temp dir if it does. */
    private suspend fun runScript(lines: List<String>): CommandResult {
        val result = RootShell.run("(\nset -e\n${lines.joinToString("\n")}\n)")
        if (!result.isSuccess) RootShell.run("rm -rf $STAGE.tmp")
        return result
    }
}
