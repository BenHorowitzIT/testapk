package com.example.engine

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Environment
import android.os.StatFs
import com.example.data.local.SandboxedAppEntity
import com.example.data.model.SandboxStorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment
import java.io.File
import java.io.FileOutputStream

/**
 * Talks to the sandbox engine (NewBlackbox). Apps installed here run inside
 * VelvetBox's own processes; Android's package manager never sees them.
 */
class SandboxManager(private val context: Context) {

    companion object {
        /** Sandbox "user". Everything lives in user 0 of the sandbox. */
        const val USER_ID = 0
    }

    val metaStore = AppMetaStore(context)

    private val iconDir: File
        get() = File(context.filesDir, "velvet_icons").apply { if (!exists()) mkdirs() }

    fun iconFileFor(packageName: String) = File(iconDir, "$packageName.png")

    private suspend fun ensureEngine() = withContext(Dispatchers.IO) {
        try {
            if (!BlackBoxCore.get().isServicesAvailable) {
                BlackBoxCore.get().waitForServicesAvailable(8000)
            }
        } catch (_: Throwable) {
        }
    }

    /** All apps currently installed in the sandbox. */
    suspend fun listApps(): List<SandboxedAppEntity> = withContext(Dispatchers.IO) {
        ensureEngine()
        val packages: List<PackageInfo> = try {
            BlackBoxCore.get().getInstalledPackages(0, USER_ID) ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        packages
            .filter { it.packageName != null }
            .map { toEntity(it) }
            .sortedWith(
                compareByDescending<SandboxedAppEntity> { it.lastLaunchedTime }
                    .thenByDescending { it.installTime }
            )
    }

    private fun toEntity(pi: PackageInfo): SandboxedAppEntity {
        val pkg = pi.packageName
        val meta = metaStore.get(pkg)
        val baseApk = BEnvironment.getBaseApkDir(pkg)
        val ai = pi.applicationInfo

        val label = meta?.appName ?: try {
            ai?.let {
                it.sourceDir = baseApk.absolutePath
                it.publicSourceDir = baseApk.absolutePath
                context.packageManager.getApplicationLabel(it).toString()
            } ?: pkg
        } catch (_: Throwable) {
            pkg
        }

        val icon = iconFileFor(pkg)
        if (!icon.exists() && baseApk.exists()) {
            saveIconFromApk(baseApk, pkg)
        }

        val obbDir = BEnvironment.getExternalObbDir(pkg, USER_ID)
        val packageBytes = dirSize(BEnvironment.getAppDir(pkg))
        val dataBytes = dirSize(BEnvironment.getDataDir(pkg, USER_ID)) +
            dirSize(BEnvironment.getDeDataDir(pkg, USER_ID)) +
            dirSize(BEnvironment.getExternalDataDir(pkg, USER_ID)) +
            dirSize(obbDir)

        @Suppress("DEPRECATION")
        val versionCode = try { pi.longVersionCode } catch (_: Throwable) { pi.versionCode.toLong() }

        return SandboxedAppEntity(
            packageName = pkg,
            appName = label,
            versionName = pi.versionName ?: "?",
            versionCode = versionCode,
            minSdk = ai?.minSdkVersion ?: 0,
            targetSdk = ai?.targetSdkVersion ?: 0,
            installTime = meta?.installTime?.takeIf { it > 0 } ?: pi.firstInstallTime,
            lastLaunchedTime = meta?.lastLaunchedTime ?: 0L,
            apkPath = baseApk.absolutePath,
            internalDataDir = BEnvironment.getDataDir(pkg, USER_ID).absolutePath,
            obbDir = obbDir.absolutePath,
            isXapk = meta?.isXapk ?: false,
            hasObb = (meta?.hasObb ?: false) || (obbDir.listFiles()?.isNotEmpty() == true),
            packageSize = packageBytes,
            dataSize = dataBytes,
            permissionsJson = meta?.permissions ?: "",
            status = meta?.status ?: "Active",
            iconPath = if (icon.exists()) icon.absolutePath else null
        )
    }

    suspend fun getStorageInfo(apps: List<SandboxedAppEntity>): SandboxStorageInfo =
        withContext(Dispatchers.IO) {
            val stat = StatFs(Environment.getDataDirectory().path)
            var appsBytes = 0L
            var obbBytes = 0L
            for (app in apps) {
                appsBytes += app.packageSize + app.dataSize
                if (app.obbDir.isNotEmpty()) obbBytes += dirSize(File(app.obbDir))
            }
            SandboxStorageInfo(
                totalDeviceBytes = stat.blockCountLong * stat.blockSizeLong,
                freeDeviceBytes = stat.availableBlocksLong * stat.blockSizeLong,
                sandboxTotalBytes = appsBytes,
                sandboxAppsBytes = appsBytes - obbBytes,
                sandboxObbBytes = obbBytes,
                installedAppsCount = apps.size
            )
        }

    /** Installs a single APK file into the sandbox. */
    suspend fun installApk(apk: File): Result<String> = withContext(Dispatchers.IO) {
        ensureEngine()
        try {
            val expectedPkg = try {
                context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
            } catch (_: Throwable) {
                null
            }
            val result = BlackBoxCore.get().installPackageAsUser(apk, USER_ID)
                ?: return@withContext Result.failure(
                    IllegalStateException("The sandbox engine didn't answer. Tap \"Save log\" and send the log.")
                )
            if (!result.success) {
                return@withContext Result.failure(
                    IllegalStateException(result.msg ?: "The sandbox engine rejected this app.")
                )
            }
            // The engine reports "success" even when it crashes halfway, so check for real.
            val pkg = result.packageName?.takeIf { it.isNotEmpty() } ?: expectedPkg
            val reallyInstalled = pkg != null && try {
                BlackBoxCore.get().isInstalled(pkg, USER_ID)
            } catch (_: Throwable) {
                false
            }
            if (!reallyInstalled) {
                val engineError = lastEngineError()
                Result.failure(
                    IllegalStateException(
                        "The sandbox engine said OK, but the app isn't in the sandbox." +
                            (engineError?.let { " Engine error: $it" } ?: " (the engine probably crashed)")
                    )
                )
            } else {
                Result.success(pkg!!)
            }
        } catch (e: NullPointerException) {
            Result.failure(IllegalStateException("The sandbox engine isn't running.", e))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * The engine swallows its own install errors, but prints them to the log.
     * Pull the most recent exception line from VelvetBox's log.
     */
    private fun lastEngineError(): String? = try {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "400", "-v", "brief"))
        val lines = process.inputStream.bufferedReader().use { it.readLines() }
        lines.lastOrNull { line ->
            (line.contains("System.err") || line.contains("AndroidRuntime")) &&
                (line.contains("Exception") || line.contains("Error:") || line.contains("Error "))
        }?.substringAfter("): ")?.trim()?.take(300)
    } catch (_: Throwable) {
        null
    }

    suspend fun isInstalled(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            BlackBoxCore.get().isInstalled(packageName, USER_ID)
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun launch(app: SandboxedAppEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (app.status != "Active") {
            return@withContext Result.failure(IllegalStateException("${app.appName} is disabled. Enable it first."))
        }
        ensureEngine()
        try {
            val ok = BlackBoxCore.get().launchApk(app.packageName, USER_ID)
            if (ok) {
                metaStore.update(app.packageName) { it.copy(lastLaunchedTime = System.currentTimeMillis()) }
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("${app.appName} has no screen to open."))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun clearAppData(app: SandboxedAppEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            BlackBoxCore.get().stopPackage(app.packageName, USER_ID)
            BlackBoxCore.get().clearPackage(app.packageName, USER_ID)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun uninstallApp(app: SandboxedAppEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            BlackBoxCore.get().uninstallPackageAsUser(app.packageName, USER_ID)
            BEnvironment.getExternalObbDir(app.packageName, USER_ID).deleteRecursively()
            BEnvironment.getExternalDataDir(app.packageName, USER_ID).deleteRecursively()
            metaStore.remove(app.packageName)
            iconFileFor(app.packageName).delete()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun toggleStatus(app: SandboxedAppEntity): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val newStatus = if (app.status == "Active") "Disabled" else "Active"
            if (newStatus == "Disabled") {
                try { BlackBoxCore.get().stopPackage(app.packageName, USER_ID) } catch (_: Throwable) {}
            }
            metaStore.update(app.packageName) { it.copy(appName = app.appName, status = newStatus) }
            newStatus
        }
    }

    /** Where OBB files for [packageName] must go so the sandboxed app finds them. */
    fun obbDirFor(packageName: String): File = BEnvironment.getExternalObbDir(packageName, USER_ID)

    /** The sandbox's own virtual "sdcard" (what sandboxed apps see as /sdcard). */
    fun sandboxStorageRoot(): File = BEnvironment.getExternalUserDir(USER_ID)

    fun saveIconFromApk(apk: File, packageName: String) {
        try {
            val pm = context.packageManager
            val pi = pm.getPackageArchiveInfo(apk.absolutePath, 0) ?: return
            val ai = pi.applicationInfo ?: return
            ai.sourceDir = apk.absolutePath
            ai.publicSourceDir = apk.absolutePath
            saveIcon(ai.loadIcon(pm), packageName)
        } catch (_: Throwable) {
        }
    }

    fun saveIcon(drawable: Drawable?, packageName: String) {
        if (drawable == null) return
        try {
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 144
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 144
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
            FileOutputStream(iconFileFor(packageName)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Throwable) {
        }
    }

    fun copyIcon(from: String?, packageName: String) {
        if (from == null) return
        try {
            val src = File(from)
            if (src.exists()) src.copyTo(iconFileFor(packageName), overwrite = true)
        } catch (_: Throwable) {
        }
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Throwable) {
            0L
        }
    }
}
