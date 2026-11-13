package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.ShortcutLaunchActivity
import com.example.engine.AppMetaStore
import top.niunaijun.blackbox.core.env.BEnvironment
import java.io.File

/**
 * 1x1 home-screen widget for one sandboxed app: shows its icon and name,
 * tap opens it. Shows "Removed" once the app is uninstalled from the sandbox.
 */
class SandboxAppWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(context, manager, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = prefs(context).edit()
        for (id in ids) edit.remove(key(id))
        edit.apply()
    }

    companion object {
        const val EXTRA_PACKAGE = "sandbox_package"

        private fun prefs(context: Context) =
            context.getSharedPreferences("sandbox_widgets", Context.MODE_PRIVATE)

        private fun key(id: Int) = "widget_$id"

        fun setApp(context: Context, widgetId: Int, packageName: String) {
            prefs(context).edit().putString(key(widgetId), packageName).apply()
        }

        /** Refresh every placed widget (after installs, uninstalls, enable/disable). */
        fun updateAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, SandboxAppWidget::class.java))
                for (id in ids) update(context, manager, id)
            } catch (_: Throwable) {
            }
        }

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_sandbox_app)
            val pkg = prefs(context).getString(key(widgetId), null)

            if (pkg == null) {
                // Not set up yet (some home screens skip the setup screen): tap to choose an app
                views.setImageViewResource(R.id.widget_icon, R.mipmap.ic_launcher)
                views.setTextViewText(R.id.widget_label, "Choose app")
                val setup = Intent(context, WidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context, widgetId, setup,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                manager.updateAppWidget(widgetId, views)
                return
            }

            val installed = try {
                BEnvironment.getBaseApkDir(pkg).exists()
            } catch (_: Throwable) {
                false
            }

            if (!installed) {
                views.setImageViewResource(R.id.widget_icon, R.mipmap.ic_launcher)
                views.setInt(R.id.widget_icon, "setImageAlpha", 90)
                views.setTextViewText(R.id.widget_label, "Removed")
                views.setOnClickPendingIntent(R.id.widget_root, null)
                manager.updateAppWidget(widgetId, views)
                return
            }

            val meta = AppMetaStore(context).get(pkg)
            views.setInt(R.id.widget_icon, "setImageAlpha", if (meta?.status == "Disabled") 120 else 255)
            val icon = loadIcon(File(File(context.filesDir, "velvet_icons"), "$pkg.png"))
            if (icon != null) {
                views.setImageViewBitmap(R.id.widget_icon, icon)
            } else {
                views.setImageViewResource(R.id.widget_icon, R.mipmap.ic_launcher)
            }
            views.setTextViewText(R.id.widget_label, meta?.appName ?: pkg)
            views.setViewVisibility(R.id.widget_label, View.VISIBLE)

            val open = Intent(context, ShortcutLaunchActivity::class.java)
                .setAction("com.example.LAUNCH_SANDBOX_APP")
                .putExtra(EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, widgetId, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            manager.updateAppWidget(widgetId, views)
        }

        private fun loadIcon(file: File): Bitmap? = try {
            if (!file.exists()) null else BitmapFactory.decodeFile(file.absolutePath)?.let { src ->
                val max = 144
                if (src.width <= max && src.height <= max) src
                else {
                    val r = max.toFloat() / maxOf(src.width, src.height)
                    Bitmap.createScaledBitmap(
                        src,
                        (src.width * r).toInt().coerceAtLeast(1),
                        (src.height * r).toInt().coerceAtLeast(1),
                        true
                    )
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
