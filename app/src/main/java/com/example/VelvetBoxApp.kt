package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

/**
 * Starts the sandbox engine. This class runs in every VelvetBox process,
 * including the ones the sandboxed apps run in, so keep it light.
 */
class VelvetBoxApp : Application() {

    /** The XAPK merger runs in its own ":merge" process and must not start the engine. */
    private val isMergeProcess: Boolean
        get() = Application.getProcessName()?.endsWith(":merge") == true

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (isMergeProcess) return
        try {
            BlackBoxCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
                override fun getHostPackageName(): String = base.packageName

                // No background daemon: fewer crashes on Android 9, nothing running when unused
                override fun isEnableDaemonService(): Boolean = false

                // The engine would otherwise upload crash logs to its developer's
                // Telegram chat. Turn that off: nothing leaves the phone.
                override fun getLogSenderChatId(): String = ""
            })
        } catch (t: Throwable) {
            Log.e("VelvetBox", "Sandbox engine attach failed", t)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (isMergeProcess) return
        try {
            BlackBoxCore.get().doCreate()
        } catch (t: Throwable) {
            Log.e("VelvetBox", "Sandbox engine start failed", t)
        }
    }
}
