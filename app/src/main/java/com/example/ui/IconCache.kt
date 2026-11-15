package com.example.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps app icons in memory at list size, so scrolling never re-reads icon files.
 */
object IconCache {
    private val cache = LruCache<String, ImageBitmap>(100)

    fun peek(path: String): ImageBitmap? = cache.get(path)

    /** Loads a small version of the icon (call off the main thread). */
    fun load(path: String, sizePx: Int = 128): ImageBitmap? {
        cache.get(path)?.let { return it }
        return try {
            if (!File(path).exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) {
                sample *= 2
            }
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            bitmap.prepareToDraw()
            bitmap.asImageBitmap().also { cache.put(path, it) }
        } catch (_: Throwable) {
            null
        }
    }

    /** Forget cached icons (after an install replaced an icon file). */
    fun clear() = cache.evictAll()
}

/** Icon for a list row: instant if cached, otherwise loaded in the background. */
@Composable
fun rememberIcon(path: String?): ImageBitmap? {
    val initial = remember(path) { path?.let { IconCache.peek(it) } }
    val state = produceState(initialValue = initial, path) {
        if (value == null && path != null) {
            value = withContext(Dispatchers.IO) { IconCache.load(path) }
        }
    }
    return state.value
}
