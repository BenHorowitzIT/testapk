package com.example.engine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.R
import com.example.ShortcutLaunchActivity
import com.example.data.local.SandboxedAppEntity

/** Home-screen shortcuts that open a sandboxed app directly. */
object HomeShortcuts {
    const val ACTION = "com.example.LAUNCH_SANDBOX_APP"
    const val EXTRA_PACKAGE = "sandbox_package"

    /** Asks the home screen to add a shortcut. Returns false if it can't. */
    fun add(context: Context, app: SandboxedAppEntity): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false

        val intent = Intent(ACTION)
            .setClass(context, ShortcutLaunchActivity::class.java)
            .putExtra(EXTRA_PACKAGE, app.packageName)

        val bitmap = app.iconPath?.let { path ->
            try {
                BitmapFactory.decodeFile(path)?.let { scaled(it) }
            } catch (_: Throwable) {
                null
            }
        }
        val icon = if (bitmap != null) {
            IconCompat.createWithBitmap(bitmap)
        } else {
            IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        }

        val info = ShortcutInfoCompat.Builder(context, "sandbox_${app.packageName}")
            .setShortLabel(app.appName.take(25))
            .setLongLabel(app.appName)
            .setIcon(icon)
            .setIntent(intent)
            .build()

        return try {
            ShortcutManagerCompat.requestPinShortcut(context, info, null)
        } catch (_: Throwable) {
            false
        }
    }

    /** Keeps the icon small enough for the home screen. */
    private fun scaled(src: Bitmap): Bitmap {
        val max = 192
        if (src.width <= max && src.height <= max) return src
        val ratio = max.toFloat() / maxOf(src.width, src.height)
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
