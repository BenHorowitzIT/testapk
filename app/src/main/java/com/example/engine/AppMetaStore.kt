package com.example.engine

import android.content.Context
import org.json.JSONObject

/** Small per-app details VelvetBox keeps next to the engine's own records. */
data class AppMeta(
    val appName: String,
    val isXapk: Boolean = false,
    val hasObb: Boolean = false,
    val installTime: Long = System.currentTimeMillis(),
    val lastLaunchedTime: Long = 0L,
    val status: String = "Active",
    val permissions: String = ""
)

class AppMetaStore(context: Context) {
    private val prefs = context.getSharedPreferences("velvet_app_meta", Context.MODE_PRIVATE)

    fun get(packageName: String): AppMeta? {
        val raw = prefs.getString(packageName, null) ?: return null
        return try {
            val o = JSONObject(raw)
            AppMeta(
                appName = o.optString("appName", packageName),
                isXapk = o.optBoolean("isXapk", false),
                hasObb = o.optBoolean("hasObb", false),
                installTime = o.optLong("installTime", 0L),
                lastLaunchedTime = o.optLong("lastLaunchedTime", 0L),
                status = o.optString("status", "Active"),
                permissions = o.optString("permissions", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun put(packageName: String, meta: AppMeta) {
        val o = JSONObject()
            .put("appName", meta.appName)
            .put("isXapk", meta.isXapk)
            .put("hasObb", meta.hasObb)
            .put("installTime", meta.installTime)
            .put("lastLaunchedTime", meta.lastLaunchedTime)
            .put("status", meta.status)
            .put("permissions", meta.permissions)
        prefs.edit().putString(packageName, o.toString()).apply()
    }

    fun update(packageName: String, change: (AppMeta) -> AppMeta) {
        val current = get(packageName) ?: AppMeta(appName = packageName)
        put(packageName, change(current))
    }

    fun remove(packageName: String) {
        prefs.edit().remove(packageName).apply()
    }
}
