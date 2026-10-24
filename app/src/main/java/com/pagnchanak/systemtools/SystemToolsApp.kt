package com.pagnchanak.systemtools

import android.app.Application
import com.pagnchanak.systemtools.root.RootShell

class SystemToolsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RootShell.configure(debug = BuildConfig.DEBUG)
    }
}
