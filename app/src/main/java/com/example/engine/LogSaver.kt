package com.example.engine

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a troubleshooting log to Download/VelvetBox: VelvetBox's own log output
 * (all its processes, including the sandbox engine) plus the last XAPK merge log.
 */
object LogSaver {

    fun save(context: Context, reason: String): Result<File> = runCatching {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "VelvetBox"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Can't create Download/VelvetBox (storage permission?)")
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "velvetbox-log-$stamp.txt")

        file.bufferedWriter().use { out ->
            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (_: Throwable) {
                "?"
            }
            out.write("VelvetBox log\n")
            out.write("Reason: $reason\n")
            out.write("Time: $stamp\n")
            out.write("VelvetBox version: $version\n")
            out.write("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            out.write("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            out.write("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}\n\n")

            val mergeLog = MergeRunner.logFile(context)
            if (mergeLog.exists()) {
                out.write("===== Last XAPK merge log =====\n")
                out.write(mergeLog.readText().takeLast(200_000))
                out.write("\n\n")
            }

            out.write("===== logcat =====\n")
            out.flush()
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        out.write(line)
                        out.write("\n")
                    }
                }
            } catch (t: Throwable) {
                out.write("Couldn't read logcat: $t\n")
            }
        }
        file
    }
}
