package com.example.data.model

import java.io.File

data class ParsedPackage(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val isXapk: Boolean,
    val splitApks: List<String> = emptyList(),
    val obbFiles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val sourceFile: File,
    val fileSize: Long,
    val iconPath: String? = null
)
