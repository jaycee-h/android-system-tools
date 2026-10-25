package com.pagnchanak.systemtools.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Snapshot of an installed package, as seen from the current user. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    /** Has a copy on a system partition (possibly with an update in /data). */
    val isSystem: Boolean,
    /** System app that has been updated, so the active code lives in /data/app. */
    val isUpdatedSystem: Boolean,
    /** Installed for the current user (false = only installed in another user/profile). */
    val installedForMe: Boolean,
    val sourceDir: String,
    val splitSourceDirs: List<String>,
    val nativeLibraryDir: String?,
    val info: ApplicationInfo,
) {
    val apkPaths: List<String> get() = listOf(sourceDir) + splitSourceDirs
}

object AppRepository {

    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        @Suppress("DEPRECATION") // flags overload is API 33+, minSdk is 29
        pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .mapNotNull { pkg ->
                val info = pkg.applicationInfo ?: return@mapNotNull null
                InstalledApp(
                    packageName = pkg.packageName,
                    label = info.loadLabel(pm).toString(),
                    versionName = pkg.versionName,
                    versionCode = pkg.longVersionCode,
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    isUpdatedSystem = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                    installedForMe = info.flags and ApplicationInfo.FLAG_INSTALLED != 0,
                    sourceDir = info.sourceDir,
                    splitSourceDirs = info.splitSourceDirs?.toList().orEmpty(),
                    nativeLibraryDir = info.nativeLibraryDir,
                    info = info,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
