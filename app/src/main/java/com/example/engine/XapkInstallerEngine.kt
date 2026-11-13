package com.example.engine

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.example.data.model.ParsedPackage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import top.niunaijun.blackbox.core.env.BEnvironment
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Reads APK / XAPK / APKS files and installs them into the VelvetBox sandbox.
 *
 * XAPK install steps:
 *  1. pick only the splits this phone needs (its CPU, screen density, language)
 *  2. install the base APK into the sandbox as it is
 *  3. put the other splits next to it (the patched engine tells Android about them)
 *     and unpack the CPU split's native libraries into the app's library folder
 *  4. copy OBB game data into the sandboxed app's own Android/obb folder
 */
class XapkInstallerEngine(
    private val context: Context,
    private val sandbox: SandboxManager
) {

    companion object {
        val PACKAGE_EXTENSIONS = setOf("apk", "xapk", "apks", "apkm")

        /** Steps of the last install, included in saved logs. */
        fun installLogFile(context: Context) = File(context.cacheDir, "install.log")
    }

    private val inspectDir: File
        get() = File(context.cacheDir, "pkg_inspect").apply { if (!exists()) mkdirs() }

    private val workRoot: File
        get() = File(context.cacheDir, "pkg_work").apply { if (!exists()) mkdirs() }

    suspend fun inspectPackage(file: File): Result<ParsedPackage> = withContext(Dispatchers.IO) {
        runCatching {
            when (file.extension.lowercase()) {
                "apk" -> inspectApk(file)
                "xapk", "apks", "apkm", "zip" -> inspectBundle(file)
                else -> {
                    // Unknown name: sniff the contents
                    if (looksLikeBundle(file)) inspectBundle(file) else inspectApk(file)
                }
            }
        }
    }

    private fun looksLikeBundle(file: File): Boolean = try {
        ZipFile(file).use { zip ->
            zip.entries().asSequence().any { it.name.endsWith(".apk", ignoreCase = true) }
        }
    } catch (_: Exception) {
        false
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

    private data class BundleLayout(
        val apkEntries: List<String>,
        val obbEntries: List<String>,
        val baseEntry: String?,
        val manifest: JSONObject?
    )

    private fun readLayout(zip: ZipFile): BundleLayout {
        val apks = mutableListOf<String>()
        val obbs = mutableListOf<String>()
        var manifest: JSONObject? = null
        for (entry in zip.entries().toList()) {
            if (entry.isDirectory) continue
            val name = entry.name
            when {
                name == "manifest.json" || name == "info.json" -> {
                    if (manifest == null) {
                        manifest = try {
                            JSONObject(zip.getInputStream(entry).bufferedReader().readText())
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                name.endsWith(".apk", ignoreCase = true) -> apks.add(name)
                name.endsWith(".obb", ignoreCase = true) -> obbs.add(name)
            }
        }
        if (apks.isEmpty()) {
            throw IllegalStateException("No APK found inside this file. Encrypted APKM files are not supported.")
        }
        return BundleLayout(apks, obbs, pickBase(apks, manifest), manifest)
    }

    private fun pickBase(apks: List<String>, manifest: JSONObject?): String? {
        if (apks.size == 1) return apks.first()
        // XAPK manifest lists which file is the base
        val splits = manifest?.optJSONArray("split_apks")
        if (splits != null) {
            for (i in 0 until splits.length()) {
                val o = splits.optJSONObject(i) ?: continue
                if (o.optString("id") != "base") continue
                val f = o.optString("file")
                for (name in apks) {
                    if (name == f || File(name).name == f) return name
                }
            }
        }
        for (name in apks) {
            if (File(name).name.equals("base.apk", ignoreCase = true)) return name
        }
        val pkgName = manifest?.optString("package_name")?.takeIf { it.isNotEmpty() }
            ?: manifest?.optString("pname")?.takeIf { it.isNotEmpty() }
        if (pkgName != null) {
            for (name in apks) {
                if (File(name).name == "$pkgName.apk") return name
            }
        }
        for (name in apks) {
            val n = File(name).name
            if (!n.startsWith("config.") && !n.startsWith("split_")) return name
        }
        return apks.first()
    }

    private fun inspectBundle(file: File): ParsedPackage {
        ZipFile(file).use { zip ->
            val layout = readLayout(zip)
            val manifest = layout.manifest

            var appName = manifest?.optString("name")?.takeIf { it.isNotEmpty() }
                ?: manifest?.optString("app_name")?.takeIf { it.isNotEmpty() }
                ?: file.nameWithoutExtension
            var packageName = manifest?.optString("package_name")?.takeIf { it.isNotEmpty() }
                ?: manifest?.optString("pname")?.takeIf { it.isNotEmpty() }
                ?: "unknown.package"
            var versionName = manifest?.optString("version_name")?.takeIf { it.isNotEmpty() } ?: "1.0"
            var versionCode = manifest?.optLong("version_code", 1L) ?: 1L
            var minSdk = manifest?.optInt("min_sdk_version", 21) ?: 21
            var targetSdk = manifest?.optInt("target_sdk_version", 28) ?: 28
            var permissions = emptyList<String>()
            var activities = emptyList<String>()
            var iconPath: String? = null

            val baseName = layout.baseEntry
            if (baseName != null) {
                val tempBase = File(inspectDir, "base_${System.currentTimeMillis()}.apk")
                try {
                    zip.getInputStream(zip.getEntry(baseName)).use { input ->
                        tempBase.outputStream().use { out -> input.copyTo(out) }
                    }
                    archiveInfo(tempBase)?.let { pi ->
                        val pm = context.packageManager
                        packageName = pi.packageName ?: packageName
                        versionName = pi.versionName ?: versionName
                        versionCode = pi.longVersionCode
                        pi.applicationInfo?.let { ai ->
                            appName = ai.loadLabel(pm)?.toString() ?: appName
                            minSdk = ai.minSdkVersion
                            targetSdk = ai.targetSdkVersion
                        }
                        permissions = pi.requestedPermissions?.toList() ?: emptyList()
                        activities = pi.activities?.map { it.name } ?: emptyList()
                        iconPath = saveTempIcon(pi, packageName)
                    }
                } catch (_: Exception) {
                } finally {
                    tempBase.delete()
                }
            }

            // XAPKs usually ship an icon.png too
            if (iconPath == null) {
                zip.getEntry("icon.png")?.let { entry ->
                    val dest = File(inspectDir, "icon_${packageName}_${System.currentTimeMillis()}.png")
                    try {
                        zip.getInputStream(entry).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                        iconPath = dest.absolutePath
                    } catch (_: Exception) {
                    }
                }
            }

            return ParsedPackage(
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                isXapk = true,
                splitApks = layout.apkEntries,
                obbFiles = layout.obbEntries,
                permissions = permissions,
                activities = activities,
                sourceFile = file,
                fileSize = file.length(),
                iconPath = iconPath
            )
        }
    }

    /** Installs into the sandbox. Returns the installed package name. */
    suspend fun installIntoSandbox(
        parsed: ParsedPackage,
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val job = coroutineContext[Job]
        val work = File(workRoot, "job_${System.currentTimeMillis()}")
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
            var hasObb = false

            val installedPkg: String = if (!parsed.isXapk) {
                step(0.3f, "Installing into the sandbox...")
                installWithTimeout(parsed.sourceFile)
            } else {
                work.mkdirs()
                ZipFile(parsed.sourceFile).use { zip ->
                    val layout = readLayout(zip)

                    // 1. pick only the splits this phone needs
                    val chosen = SplitPicker.pick(layout.apkEntries, layout.baseEntry)
                    val skipped = layout.apkEntries.size - chosen.size
                    step(
                        0.04f,
                        "Using ${chosen.size} of ${layout.apkEntries.size} APKs" +
                            if (skipped > 0) " (skipping $skipped not needed on this phone)" else ""
                    )
                    log("Chosen: ${chosen.joinToString()}")
                    val baseName = layout.baseEntry ?: chosen.first()

                    // 2. install the base APK as it is (original signature kept)
                    step(0.1f, "Unpacking ${File(baseName).name}...")
                    val baseApk = File(work, "base.apk")
                    extract(zip, zip.getEntry(baseName), baseApk, checkCancel = { job?.ensureActive() })
                    job?.ensureActive()
                    step(0.35f, "Installing into the sandbox...")
                    val pkg = installWithTimeout(baseApk)
                    baseApk.delete()

                    // 3. add the splits next to it: resources/features go to <app>/splits
                    //    (the patched engine hands them to Android), CPU splits give native libraries
                    val splits = chosen.filter { it != baseName }
                    if (splits.isNotEmpty()) {
                        val splitDir = File(BEnvironment.getAppDir(pkg), "splits")
                        splitDir.deleteRecursively()
                        splitDir.mkdirs()
                        val libDir = BEnvironment.getAppLibDir(pkg).apply { mkdirs() }
                        val abi = hostAbi()
                        splits.forEachIndexed { i, name ->
                            job?.ensureActive()
                            step(
                                0.45f + 0.2f * i / splits.size,
                                "Adding ${File(name).name} (${i + 1} of ${splits.size})"
                            )
                            val tmp = File(work, "split_$i.apk")
                            extract(zip, zip.getEntry(name), tmp, checkCancel = { job?.ensureActive() })
                            val libs = extractNativeLibs(tmp, abi, libDir)
                            if (libs > 0) log("  $libs native libraries ($abi) from ${File(name).name}")
                            if (SplitPicker.abiOf(name) == null) {
                                val dest = File(splitDir, SplitPicker.splitNameOf(name) + ".apk")
                                if (!tmp.renameTo(dest)) {
                                    tmp.copyTo(dest, overwrite = true)
                                    tmp.delete()
                                }
                                log("  split ${dest.name} added")
                            } else {
                                tmp.delete()
                            }
                        }
                    }

                    // 4. OBB data
                    if (layout.obbEntries.isNotEmpty()) {
                        hasObb = true
                        val obbTargets = obbTargets(layout, pkg)
                        val total = layout.obbEntries.sumOf { zip.getEntry(it).size.coerceAtLeast(1L) }
                        var done = 0L
                        for ((entryName, target) in obbTargets) {
                            job?.ensureActive()
                            val entry = zip.getEntry(entryName)
                            target.parentFile?.mkdirs()
                            step(0.7f + 0.28f * done / total, "Copying game data ${target.name}...")
                            extract(zip, entry, target, { job?.ensureActive() }) { copied ->
                                onProgress(
                                    0.7f + 0.28f * (done + copied) / total,
                                    "Copying game data ${target.name}..."
                                )
                            }
                            done += entry.size.coerceAtLeast(1L)
                        }
                    }
                    pkg
                }
            }

            step(0.98f, "Finishing up...")
            sandbox.copyIcon(parsed.iconPath, installedPkg)
            val previous = sandbox.metaStore.get(installedPkg)
            sandbox.metaStore.put(
                installedPkg,
                AppMeta(
                    appName = parsed.appName,
                    isXapk = parsed.isXapk,
                    hasObb = hasObb || (previous?.hasObb ?: false),
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
                if (t is OutOfMemoryError) IOException("Not enough memory for this XAPK.") else t
            )
        } finally {
            work.deleteRecursively()
        }
    }

    /** CPU type the sandbox runs apps as (VelvetBox's own bitness). */
    private fun hostAbi(): String {
        val abis = if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
        return abis.firstOrNull() ?: Build.SUPPORTED_ABIS.first()
    }

    /** Copies the native libraries (lib/<abi>/...so files) from [apk] into the app's library folder. */
    private fun extractNativeLibs(apk: File, abi: String, libDir: File): Int {
        var count = 0
        ZipFile(apk).use { zip ->
            for (entry in zip.entries().toList()) {
                val name = entry.name
                if (entry.isDirectory || !name.startsWith("lib/$abi/") || !name.endsWith(".so")) continue
                val fileName = name.substringAfterLast('/')
                if (fileName.contains("..")) continue
                extract(zip, entry, File(libDir, fileName))
                count++
            }
        }
        return count
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

    /** Decides where each OBB goes inside the sandbox's virtual storage. */
    private fun obbTargets(layout: BundleLayout, pkg: String): List<Pair<String, File>> {
        val virtualSdcard = sandbox.sandboxStorageRoot()
        val obbDir = sandbox.obbDirFor(pkg)
        val installPaths = mutableMapOf<String, String>()
        layout.manifest?.optJSONArray("expansions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val f = o.optString("file")
                val p = o.optString("install_path")
                if (f.isNotEmpty() && p.isNotEmpty()) installPaths[f] = p
            }
        }
        return layout.obbEntries.map { name ->
            val installPath = installPaths[name]
            val target = when {
                installPath != null && !installPath.contains("..") ->
                    File(virtualSdcard, installPath.trimStart('/'))
                name.startsWith("Android/obb/") && !name.contains("..") ->
                    File(virtualSdcard, name)
                else -> File(obbDir, File(name).name)
            }
            name to target
        }
    }

    private fun extract(
        zip: ZipFile,
        entry: ZipEntry,
        dest: File,
        checkCancel: () -> Unit = {},
        onBytes: ((Long) -> Unit)? = null
    ) {
        zip.getInputStream(entry).use { input ->
            dest.outputStream().use { out ->
                val buffer = ByteArray(256 * 1024)
                var copied = 0L
                var lastReport = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    copied += n
                    if (copied - lastReport > 8L * 1024 * 1024) {
                        lastReport = copied
                        checkCancel()
                        onBytes?.invoke(copied)
                    }
                }
            }
        }
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
