package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import java.lang.reflect.Method
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

/**
 * Starts the sandbox engine. This class runs in every VelvetBox process,
 * including the ones the sandboxed apps run in, so keep it light.
 */
class VelvetBoxApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        unblockHiddenApis()
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

    /**
     * Android 9 hides parts of the system from apps; the sandbox engine needs them.
     * This lifts the restriction for VelvetBox's own processes only (no root needed),
     * using the well-known "meta-reflection" call to VMRuntime.setHiddenApiExemptions.
     */
    private fun unblockHiddenApis() {
        try {
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod",
                String::class.java,
                arrayOf<Class<*>>()::class.java
            )
            val vmRuntime = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(vmRuntime, "getRuntime", null) as Method
            val setExemptions = getDeclaredMethod.invoke(
                vmRuntime,
                "setHiddenApiExemptions",
                arrayOf<Class<*>>(Array<String>::class.java)
            ) as Method
            setExemptions.invoke(getRuntime.invoke(null), arrayOf("L") as Any)
            Log.i("VelvetBox", "Hidden API restrictions lifted for this process")
        } catch (t: Throwable) {
            Log.w("VelvetBox", "Couldn't lift hidden API restrictions", t)
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            BlackBoxCore.get().doCreate()
        } catch (t: Throwable) {
            Log.e("VelvetBox", "Sandbox engine start failed", t)
        }
    }
}
