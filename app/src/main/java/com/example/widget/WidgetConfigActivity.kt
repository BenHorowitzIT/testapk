package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SandboxedAppEntity
import com.example.engine.SandboxManager
import com.example.ui.theme.VelvetBackground
import com.example.ui.theme.VelvetBoxTheme
import com.example.ui.theme.VelvetPrimary
import com.example.ui.theme.VelvetSurface

/** Opens when a "Sandbox app" widget is placed: pick which sandboxed app it opens. */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // If the user backs out, the home screen drops the widget
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val sandbox = SandboxManager(applicationContext)
        setContent {
            VelvetBoxTheme {
                var apps by remember { mutableStateOf<List<SandboxedAppEntity>?>(null) }
                LaunchedEffect(Unit) { apps = sandbox.listApps() }
                AppPicker(apps = apps, onPick = { pick(it) })
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    private fun pick(app: SandboxedAppEntity) {
        SandboxAppWidget.setApp(this, widgetId, app.packageName)
        SandboxAppWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }
}

@Composable
private fun AppPicker(apps: List<SandboxedAppEntity>?, onPick: (SandboxedAppEntity) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VelvetBackground)
            .padding(16.dp)
    ) {
        Text(
            text = "Choose an app for this widget",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        when {
            apps == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VelvetPrimary)
            }
            apps.isEmpty() -> Text(
                text = "No apps in Ben's Sandbox yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(VelvetSurface)
                            .clickable { onPick(app) }
                            .padding(12.dp)
                    ) {
                        val bitmap = remember(app.iconPath) {
                            app.iconPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = VelvetPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
