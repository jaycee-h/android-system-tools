package com.pagnchanak.systemtools.update

import android.content.Context
import androidx.core.content.edit
import com.pagnchanak.systemtools.BuildConfig

/** Update settings: channel (defaults to the one this build came from), auto-check, skipped version. */
class UpdatePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)

    var channel: String
        get() = prefs.getString("channel", null) ?: BuildConfig.CHANNEL
        set(v) = prefs.edit { putString("channel", v) }

    var autoCheck: Boolean
        get() = prefs.getBoolean("auto_check", true)
        set(v) = prefs.edit { putBoolean("auto_check", v) }

    var skippedVersion: Int
        get() = prefs.getInt("skipped_version", 0)
        set(v) = prefs.edit { putInt("skipped_version", v) }
}
