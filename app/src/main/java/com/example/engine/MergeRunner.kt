package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Starts [MergeService] in its separate process and watches it:
 * passes each log line on as live progress, stops it if it hangs, and
 * kills it if the install is cancelled.
 */
object MergeRunner {
    const val EXTRA_APK_DIR = "apk_dir"
    const val EXTRA_OUT_APK = "out_apk"

    /** Stop if the merger writes nothing new for this long. */
    private const val NO_PROGRESS_TIMEOUT_MS = 3 * 60 * 1000L

    /** Hard limit for one merge. */
    private const val TOTAL_TIMEOUT_MS = 15 * 60 * 1000L

    private fun dir(context: Context) = File(context.cacheDir, "merge").apply { if (!exists()) mkdirs() }
    fun logFile(context: Context) = File(dir(context), "merge.log")
    fun statusFile(context: Context) = File(dir(context), "status")
    fun pidFile(context: Context) = File(dir(context), "pid")

    /** Runs the merge; throws with a readable message if it fails. Cancellable. */
    suspend fun merge(
        context: Context,
        apkDir: File,
        outApk: File,
        onLine: (String) -> Unit
    ) {
        logFile(context).delete()
        statusFile(context).delete()
        pidFile(context).delete()
        outApk.delete()

        val intent = Intent(context, MergeService::class.java)
            .putExtra(EXTRA_APK_DIR, apkDir.absolutePath)
            .putExtra(EXTRA_OUT_APK, outApk.absolutePath)
        context.startService(intent)

        val start = System.currentTimeMillis()
        var lastActivity = start
        var readOffset = 0L
        var finished = false
        var missedChecks = 0
        try {
            while (true) {
                delay(500)
                val now = System.currentTimeMillis()

                // New log lines = progress
                val log = logFile(context)
                if (log.exists() && log.length() > readOffset) {
                    val newText = readFrom(log, readOffset)
                    readOffset = log.length()
                    lastActivity = now
                    newText.lines().lastOrNull { it.isNotBlank() }?.let(onLine)
                }

                val status = statusFile(context)
                if (status.exists()) {
                    val text = status.readText()
                    if (text.startsWith("OK")) {
                        if (!outApk.exists() || outApk.length() == 0L) {
                            throw IOException("The merger finished but produced no APK.")
                        }
                        finished = true
                        return
                    }
                    if (text.startsWith("ERROR")) {
                        finished = true
                        throw IOException("Merging failed: " + text.removePrefix("ERROR").trim())
                    }
                }

                // Process died without writing a status (usually out of memory).
                // Ask Android (not /proc, which Android 9 hides) and require 3 misses in a row.
                val pid = readPid(context)
                if (pid != null && !statusFile(context).exists()) {
                    if (isProcessAlive(context, pid)) {
                        missedChecks = 0
                    } else {
                        missedChecks++
                        if (missedChecks >= 3 && !statusFile(context).exists()) {
                            throw IOException("The merge process crashed (probably out of memory).")
                        }
                    }
                }
                if (pid == null && now - start > 30_000) {
                    throw IOException("The merge process didn't start.")
                }

                if (now - lastActivity > NO_PROGRESS_TIMEOUT_MS) {
                    throw IOException("Merging stopped responding for 3 minutes, so it was stopped.")
                }
                if (now - start > TOTAL_TIMEOUT_MS) {
                    throw IOException("Merging took longer than 15 minutes, so it was stopped.")
                }
            }
        } finally {
            if (!finished) stop(context)
        }
    }

    /** Kills the merge process if it's running. */
    fun stop(context: Context) {
        try {
            context.stopService(Intent(context, MergeService::class.java))
        } catch (_: Throwable) {
        }
        readPid(context)?.let { pid ->
            if (pid != Process.myPid()) {
                try { Process.killProcess(pid) } catch (_: Throwable) {}
            }
        }
    }

    /** True if [pid] is one of VelvetBox's own running processes. */
    private fun isProcessAlive(context: Context, pid: Int): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val procs = am.runningAppProcesses
        // If Android won't tell us, assume it's alive and let the timeouts decide.
        procs == null || procs.any { it.pid == pid }
    } catch (_: Throwable) {
        true
    }

    private fun readPid(context: Context): Int? = try {
        pidFile(context).takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
    } catch (_: Throwable) {
        null
    }

    private fun readFrom(file: File, offset: Long): String = try {
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            val from = maxOf(offset, len - 64 * 1024)
            raf.seek(from)
            val bytes = ByteArray((len - from).coerceAtLeast(0).toInt())
            raf.readFully(bytes)
            String(bytes)
        }
    } catch (_: Throwable) {
        ""
    }
}
