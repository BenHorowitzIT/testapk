package com.example.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.reandroid.apk.APKLogger
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Merges split APKs in its own process (":merge", see AndroidManifest).
 * If a huge XAPK runs it out of memory, only this process dies, not VelvetBox.
 * It talks to the app through small files (see [MergeRunner]).
 */
class MergeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apkDir = intent?.getStringExtra(MergeRunner.EXTRA_APK_DIR)
        val outApk = intent?.getStringExtra(MergeRunner.EXTRA_OUT_APK)
        if (apkDir == null || outApk == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Thread({
            val logFile = MergeRunner.logFile(this)
            val statusFile = MergeRunner.statusFile(this)
            MergeRunner.pidFile(this).writeText(Process.myPid().toString())

            FileWriter(logFile, true).use { writer ->
                fun line(text: String) {
                    try {
                        writer.write(text.replace('\n', ' '))
                        writer.write("\n")
                        writer.flush()
                    } catch (_: Throwable) {
                    }
                }

                val logger = object : APKLogger {
                    override fun logMessage(msg: String?) {
                        line(msg ?: "")
                    }

                    override fun logError(msg: String?, tr: Throwable?) {
                        line("ERROR: ${msg ?: ""} ${tr ?: ""}")
                    }

                    override fun logVerbose(msg: String?) {
                        line(msg ?: "")
                    }
                }

                try {
                    val maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                    line("Merge process started (memory limit ${maxMb} MB)")
                    ApkMerger.merge(File(apkDir), File(outApk), logger)
                    statusFile.writeText("OK")
                } catch (oom: OutOfMemoryError) {
                    line("Out of memory")
                    statusFile.writeText("ERROR\nYour phone ran out of memory while merging this XAPK.")
                } catch (t: Throwable) {
                    val sw = StringWriter()
                    t.printStackTrace(PrintWriter(sw))
                    line(sw.toString().take(4000))
                    statusFile.writeText("ERROR\n${t.javaClass.simpleName}: ${t.message ?: ""}")
                }
            }
            stopSelf(startId)
            // Free the memory right away
            Process.killProcess(Process.myPid())
        }, "velvet-merge").start()

        return START_NOT_STICKY
    }
}
