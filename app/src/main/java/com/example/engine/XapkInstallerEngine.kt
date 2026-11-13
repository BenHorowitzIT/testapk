package com.example.engine

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.example.data.model.ParsedPackage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads APK files and installs them into the sandbox. (Regular APKs only.)
 */
class XapkInstallerEngine(
    private val context: Context,
    private val sandbox: SandboxManager
) {

    companion object {
        val PACKAGE_EXTENSIONS = setOf("apk")
        private val BUNDLE_EXTENSIONS = setOf("xapk", "apks", "apkm")

        /** Steps of the last install, included in saved logs. */
        fun installLogFile(context: Context) = File(context.cacheDir, "install.log")
    }

    private val inspectDir: File
        get() = File(context.cacheDir, "pkg_inspect").apply { if (!exists()) mkdirs() }

    suspend fun inspectPackage(file: File): Result<ParsedPackage> = withContext(Dispatchers.IO) {
        runCatching {
            if (file.extension.lowercase() in BUNDLE_EXTENSIONS) {
                throw IllegalStateException("XAPK files aren't supported. Use a regular APK.")
            }
            inspectApk(file)
        }
    }

    private fun archiveInfo(apk: File): PackageInfo? {
        val pm = context.packageManager
        val pi = pm.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS
        ) ?: pm.getPackageArchiveInfo(apk.absolutePath, 0)
        pi?.applicationInfo?.let {
            it.sourceDir = apk.absolutePath
            it.publicSourceDir = apk.absolutePath
        }
        return pi
    }

    private fun inspectApk(file: File): ParsedPackage {
        val pm = context.packageManager
        val pi = archiveInfo(file)
            ?: throw IllegalStateException("This doesn't look like a valid APK: ${file.name}")
        val ai = pi.applicationInfo
        val label = ai?.loadLabel(pm)?.toString() ?: pi.packageName ?: file.nameWithoutExtension
        val iconPath = saveTempIcon(pi, pi.packageName ?: "apk")

        return ParsedPackage(
            appName = label,
            packageName = pi.packageName ?: "unknown.package",
            versionName = pi.versionName ?: "1.0",
            versionCode = pi.longVersionCode,
            minSdk = ai?.minSdkVersion ?: 21,
            targetSdk = ai?.targetSdkVersion ?: 28,
            isXapk = false,
            permissions = pi.requestedPermissions?.toList() ?: emptyList(),
            activities = pi.activities?.map { it.name } ?: emptyList(),
            sourceFile = file,
            fileSize = file.length(),
            iconPath = iconPath
        )
    }

    /** Installs into the sandbox. Returns the installed package name. */
    suspend fun installIntoSandbox(
        parsed: ParsedPackage,
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val logFile = installLogFile(context)
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)
        fun log(msg: String) {
            try { logFile.appendText("${stamp.format(Date())} $msg\n") } catch (_: Throwable) {}
        }
        fun step(p: Float, msg: String) {
            onProgress(p, msg)
            log(msg)
        }
        try { logFile.writeText("Install of ${parsed.packageName} from ${parsed.sourceFile.name}\n") } catch (_: Throwable) {}

        try {
            step(0.3f, "Installing into the sandbox...")
            val installedPkg = installWithTimeout(parsed.sourceFile)

            step(0.95f, "Finishing up...")
            sandbox.copyIcon(parsed.iconPath, installedPkg)
            val previous = sandbox.metaStore.get(installedPkg)
            sandbox.metaStore.put(
                installedPkg,
                AppMeta(
                    appName = parsed.appName,
                    isXapk = false,
                    hasObb = false,
                    installTime = System.currentTimeMillis(),
                    lastLaunchedTime = previous?.lastLaunchedTime ?: 0L,
                    status = previous?.status ?: "Active",
                    permissions = parsed.permissions.joinToString(",")
                )
            )
            step(1f, "Installed!")
            Result.success(installedPkg)
        } catch (c: CancellationException) {
            log("Cancelled")
            throw c
        } catch (t: Throwable) {
            log("FAILED: $t")
            Result.failure(
                if (t is OutOfMemoryError) IOException("Not enough memory to install this app.") else t
            )
        }
    }

    /**
     * The engine's install call can't be interrupted, so wait for it in a detached
     * coroutine and give up after 5 minutes instead of hanging forever.
     */
    private suspend fun installWithTimeout(apk: File): String {
        val deferred = CoroutineScope(Dispatchers.IO).async { sandbox.installApk(apk) }
        val result = withTimeoutOrNull(5 * 60 * 1000L) { deferred.await() }
            ?: throw IOException("The sandbox engine took more than 5 minutes, so the install was stopped.")
        return result.getOrThrow()
    }

    private fun saveTempIcon(pi: PackageInfo, name: String): String? {
        return try {
            val drawable = pi.applicationInfo?.loadIcon(context.packageManager) ?: return null
            val tmpName = "tmp_icon_${name}_${System.currentTimeMillis()}"
            sandbox.saveIcon(drawable, tmpName)
            val f = sandbox.iconFileFor(tmpName)
            if (!f.exists()) return null
            val dest = File(inspectDir, "$tmpName.png")
            f.renameTo(dest)
            dest.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    fun clearInspectCache() {
        try {
            inspectDir.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {
        }
    }
}
