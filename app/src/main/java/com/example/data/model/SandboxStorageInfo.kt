package com.example.data.model

data class SandboxStorageInfo(
    val totalDeviceBytes: Long = 0L,
    val freeDeviceBytes: Long = 0L,
    val sandboxTotalBytes: Long = 0L,
    val sandboxAppsBytes: Long = 0L,
    val sandboxObbBytes: Long = 0L,
    val installedAppsCount: Int = 0
) {
    val usedDeviceBytes: Long
        get() = (totalDeviceBytes - freeDeviceBytes).coerceAtLeast(0L)

    val formattedSandboxSize: String
        get() = FileItem.formatBytes(sandboxTotalBytes)

    val formattedTotalDevice: String
        get() = FileItem.formatBytes(totalDeviceBytes)

    val formattedFreeDevice: String
        get() = FileItem.formatBytes(freeDeviceBytes)

    val sandboxUsageRatio: Float
        get() = if (totalDeviceBytes > 0) {
            (sandboxTotalBytes.toFloat() / totalDeviceBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
}
