package com.example.engine

import android.content.Context
import android.os.Environment
import com.example.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.core.env.BEnvironment
import java.io.File
import java.util.Locale

enum class FileCategoryFilter {
    ALL,
    PACKAGES, // APK files
    OBB,      // OBB expansion files
    SANDBOX   // The sandbox's own virtual storage (what sandboxed apps see as /sdcard)
}

enum class FileSortOption {
    NAME_ASC,
    DATE_DESC,
    SIZE_DESC
}

class FileManager(private val context: Context) {

    val rootStorageDirectory: File
        get() {
            val ext = Environment.getExternalStorageDirectory()
            return if (ext != null && ext.exists() && ext.canRead()) ext else context.filesDir
        }

    val sandboxDirectory: File
        get() = try {
            BEnvironment.getExternalUserDir(SandboxManager.USER_ID).apply { if (!exists()) mkdirs() }
        } catch (_: Throwable) {
            File(context.filesDir, "sandbox").apply { if (!exists()) mkdirs() }
        }

    suspend fun listDirectory(
        directory: File,
        sortOption: FileSortOption = FileSortOption.NAME_ASC
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val files = directory.listFiles() ?: return@withContext emptyList()
        val items = files.map { FileItem.fromFile(it) }
        sortItems(items, sortOption)
    }

    suspend fun scanCategory(
        category: FileCategoryFilter,
        sortOption: FileSortOption = FileSortOption.DATE_DESC
    ): List<FileItem> = withContext(Dispatchers.IO) {
        when (category) {
            FileCategoryFilter.ALL -> emptyList()
            FileCategoryFilter.SANDBOX -> {
                listDirectory(sandboxDirectory, sortOption)
            }
            FileCategoryFilter.PACKAGES -> {
                scanForExtensions(listOf("apk"), sortOption)
            }
            FileCategoryFilter.OBB -> {
                scanForExtensions(listOf("obb"), sortOption)
            }
        }
    }

    private fun scanForExtensions(
        extensions: List<String>,
        sortOption: FileSortOption
    ): List<FileItem> {
        val results = mutableListOf<FileItem>()
        val extLower = extensions.map { it.lowercase(Locale.ROOT) }

        // Folders to scan thoroughly: Downloads, Documents, Bluetooth, App External, and Root
        val searchDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "bluetooth"),
            File(Environment.getExternalStorageDirectory(), "Android/obb"),
            File(Environment.getExternalStorageDirectory(), "APKPure"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Documents")
        ).distinct().filter { it.exists() && it.canRead() }

        for (dir in searchDirs) {
            try {
                dir.walkTopDown()
                    .maxDepth(4)
                    .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in extLower }
                    .take(200)
                    .forEach { file ->
                        results.add(FileItem.fromFile(file))
                    }
            } catch (_: Exception) {}
        }

        return sortItems(results.distinctBy { it.path }, sortOption)
    }

    private fun sortItems(items: List<FileItem>, sortOption: FileSortOption): List<FileItem> {
        return when (sortOption) {
            FileSortOption.NAME_ASC -> items.sortedWith(
                compareBy<FileItem> { !it.isDirectory }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            FileSortOption.DATE_DESC -> items.sortedWith(
                compareBy<FileItem> { !it.isDirectory }
                    .thenByDescending { it.lastModified }
            )
            FileSortOption.SIZE_DESC -> items.sortedWith(
                compareBy<FileItem> { !it.isDirectory }
                    .thenByDescending { it.size }
            )
        }
    }

    suspend fun deleteFile(file: File): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }

    suspend fun renameFile(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dest = File(file.parentFile, newName)
            if (dest.exists()) throw IllegalArgumentException("A file named '$newName' already exists.")
            if (file.renameTo(dest)) dest else throw IllegalStateException("Failed to rename file.")
        }
    }
}
