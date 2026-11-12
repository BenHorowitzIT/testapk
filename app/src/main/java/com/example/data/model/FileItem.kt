package com.example.data.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isApk: Boolean = false,
    val isXapk: Boolean = false,
    val isObb: Boolean = false,
    val childCount: Int = 0
) {
    val extension: String
        get() = if (isDirectory) "" else file.extension.lowercase(Locale.ROOT)

    val formattedSize: String
        get() = formatBytes(size)

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }

        fun fromFile(file: File): FileItem {
            val isDir = file.isDirectory
            val ext = file.extension.lowercase(Locale.ROOT)
            val isApk = !isDir && ext == "apk"
            val isXapk = !isDir && (ext == "xapk" || ext == "apks" || ext == "apkm")
            val isObb = !isDir && ext == "obb"
            val count = if (isDir) {
                file.list()?.size ?: 0
            } else 0

            return FileItem(
                file = file,
                name = file.name,
                path = file.absolutePath,
                isDirectory = isDir,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                isApk = isApk,
                isXapk = isXapk,
                isObb = isObb,
                childCount = count
            )
        }
    }
}
