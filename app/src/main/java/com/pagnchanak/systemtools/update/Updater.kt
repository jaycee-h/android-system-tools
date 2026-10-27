package com.pagnchanak.systemtools.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.pagnchanak.systemtools.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val channel: String,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String?,
    val notes: String,
    /** "GitHub API" or "Release mirror", shown so it's clear which path worked. */
    val source: String,
) {
    val isNewer get() = versionCode > BuildConfig.VERSION_CODE
}

class NoReleaseException(channel: String) : IOException("No $channel release has been published yet.")

/**
 * Two ways to find an update, so GitHub's API limit (60 requests/hour per IP) never blocks updating:
 *  1. GitHub REST API: the release for the channel, versionCode read from the release notes.
 *  2. Release mirror: the static update.json attached to the same release, fetched through
 *     github.com/.../releases/download/..., which isn't subject to the API rate limit.
 * The APK itself always comes from the release asset URL.
 */
object Updater {
    private val repo = BuildConfig.GITHUB_REPO
    const val CHANNEL_STABLE = "stable"
    const val CHANNEL_DEV = "dev"

    private fun apiUrl(channel: String) =
        if (channel == CHANNEL_STABLE) "https://api.github.com/repos/$repo/releases/latest"
        else "https://api.github.com/repos/$repo/releases/tags/dev"

    private fun mirrorUrl(channel: String) =
        if (channel == CHANNEL_STABLE) "https://github.com/$repo/releases/latest/download/update.json"
        else "https://github.com/$repo/releases/download/dev/update.json"

    private fun open(url: String, accept: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "SystemTools/${BuildConfig.VERSION_NAME}")
            accept?.let { setRequestProperty("Accept", it) }
        }

    private suspend fun getText(url: String, accept: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val c = open(url, accept)
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }

    private suspend fun fromApi(channel: String): UpdateInfo {
        val (code, body) = getText(apiUrl(channel), "application/vnd.github+json")
        if (code == 404) throw NoReleaseException(channel)
        if (code != 200) throw IOException("GitHub API returned $code")
        val o = JSONObject(body)
        val notes = o.optString("body")
        val versionCode = Regex("""versionCode:\s*(\d+)""").find(notes)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw IOException("Release has no versionCode")
        val assets = o.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") } ?: throw IOException("Release has no APK")
        return UpdateInfo(
            channel = channel,
            versionCode = versionCode,
            versionName = Regex("""versionName:\s*(\S+)""").find(notes)?.groupValues?.get(1) ?: o.optString("name"),
            apkUrl = apk.getString("browser_download_url"),
            apkSize = apk.optLong("size"),
            sha256 = Regex("""sha256:\s*([0-9a-fA-F]{64})""").find(notes)?.groupValues?.get(1),
            notes = notes.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "").trim(),
            source = "GitHub API",
        )
    }

    private suspend fun fromMirror(channel: String): UpdateInfo {
        val (code, body) = getText(mirrorUrl(channel), "application/json")
        if (code == 404) throw NoReleaseException(channel)
        if (code != 200) throw IOException("Release mirror returned $code")
        val o = JSONObject(body)
        return UpdateInfo(
            channel = o.optString("channel", channel),
            versionCode = o.getInt("versionCode"),
            versionName = o.getString("versionName"),
            apkUrl = o.getString("apkUrl"),
            apkSize = o.optLong("apkSize"),
            sha256 = o.optString("sha256").ifBlank { null },
            notes = o.optString("notes"),
            source = "Release mirror",
        )
    }

    /** Returns the latest release for [channel], trying the API first and the mirror second. */
    suspend fun latest(channel: String): UpdateInfo {
        val primary = runCatching { fromApi(channel) }
        primary.getOrNull()?.let { return it }
        val backup = runCatching { fromMirror(channel) }
        backup.getOrNull()?.let { return it }
        // Prefer the clearer "no release" message over a generic network error.
        val errs = listOfNotNull(primary.exceptionOrNull(), backup.exceptionOrNull())
        throw errs.firstOrNull { it is NoReleaseException } ?: errs.last()
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "SystemTools-${info.versionCode}.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            val c = open(info.apkUrl)
            try {
                if (c.responseCode !in 200..299) throw IOException("Download failed (HTTP ${c.responseCode})")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
                c.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && !hash.equals(info.sha256, ignoreCase = true)) {
                out.delete()
                throw IOException("Downloaded file is corrupted (checksum mismatch). Try again.")
            }
            out
        }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Hands the APK to Android's package installer. The system shows its own confirmation. */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            // The installer fills in the result extras, so the intent has to be mutable on Android 12+.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> {}
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed"
                Toast.makeText(context, "Update failed: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
