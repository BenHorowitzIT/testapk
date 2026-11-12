package com.example.data.local

/**
 * One app installed inside the VelvetBox sandbox.
 * The sandbox engine is the source of truth for which apps exist; the extra
 * display details (name, icon, XAPK/OBB flags, enabled state) live in [com.example.engine.AppMetaStore].
 */
data class SandboxedAppEntity(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val installTime: Long = System.currentTimeMillis(),
    val lastLaunchedTime: Long = 0L,
    val apkPath: String,
    val internalDataDir: String,
    val obbDir: String = "",
    val isXapk: Boolean = false,
    val hasObb: Boolean = false,
    val packageSize: Long = 0L,
    val dataSize: Long = 0L,
    val launchActivity: String? = null,
    val permissionsJson: String = "",
    val status: String = "Active", // Active, Disabled
    val iconPath: String? = null
)
