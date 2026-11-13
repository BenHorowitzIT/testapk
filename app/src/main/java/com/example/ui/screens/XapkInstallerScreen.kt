package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FileItem
import com.example.ui.components.RomanticHeader
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.RoseGlowEnd
import com.example.ui.theme.RoseGlowStart
import com.example.ui.theme.VelvetBackground
import com.example.ui.theme.VelvetPrimary
import com.example.ui.theme.VelvetSurface
import com.example.ui.theme.VelvetSurfaceVariant
import java.io.File

@Composable
fun XapkInstallerScreen(
    scannedPackages: List<FileItem>,
    onInspectFile: (File) -> Unit,
    onImportUri: (Uri) -> Unit,
    onNavigateToFileBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isGuideExpanded by remember { mutableStateOf(false) }

    // Launcher for picking APK/XAPK from Android Document Provider
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(onImportUri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VelvetBackground)
    ) {
        RomanticHeader(
            title = "XAPK & APK Installer",
            subtitle = "Native split-APK & OBB unpacker for Android 9"
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Dropzone Card
            item {
                InstallerDropzoneCard(
                    onBrowseSystem = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onBrowseInternal = onNavigateToFileBrowser
                )
            }

            // Android 9 XAPK Info Guide
            item {
                XapkAndroid9GuideCard(
                    isExpanded = isGuideExpanded,
                    onToggle = { isGuideExpanded = !isGuideExpanded }
                )
            }

            // Detected APK/XAPKs section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Detected Packages (${scannedPackages.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Ready to sandbox",
                        style = MaterialTheme.typography.labelSmall,
                        color = VelvetPrimary
                    )
                }
            }

            if (scannedPackages.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VelvetSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = VelvetPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No APK or XAPK files found in common folders.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Use 'Browse All Files' or pick a file to install.",
                                style = MaterialTheme.typography.labelSmall,
                                color = VelvetPrimary
                            )
                        }
                    }
                }
            } else {
                items(scannedPackages, key = { it.path }) { pkgFile ->
                    PackageScanItem(
                        item = pkgFile,
                        onInspect = { onInspectFile(pkgFile.file) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun InstallerDropzoneCard(
    onBrowseSystem: () -> Unit,
    onBrowseInternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VelvetSurface),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, RoseGlowStart.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(RoseGlowStart, RoseGlowEnd))
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Install APK or XAPK File",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Decompresses multi-part split APKs and places OBB expansion archives automatically into isolated space.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onBrowseInternal,
                    colors = ButtonDefaults.buttonColors(containerColor = VelvetPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("browse_internal_files_button")
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "File Explorer",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Button(
                    onClick = onBrowseSystem,
                    colors = ButtonDefaults.buttonColors(containerColor = VelvetSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("browse_system_picker_button")
                ) {
                    Text(
                        text = "Select Document",
                        color = VelvetPrimary,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun XapkAndroid9GuideCard(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VelvetSurfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = VelvetPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How VelvetBox Solves Android 9 XAPK",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = VelvetPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "• Native Android 9 has no built-in XAPK handler: Standard package installers reject .xapk files because they are zip containers containing split APKs and OBB data.\n" +
                            "• VelvetBox decomposes the XAPK container natively without external tools:\n" +
                            "  1. Reads manifest.json and unpacks the base + split APKs.\n" +
                            "  2. Installs the base APK and adds only the splits your phone needs (CPU, screen, language).\n" +
                            "  3. Copies OBB game data into the app's own Android/obb folder inside the sandbox.\n" +
                            "  4. The app runs inside VelvetBox, so the phone never installs it or sees its package name.\n" +
                            "• Some apps still won't run in any sandbox: ones that need Google sign-in / Play Services, banking apps, games with anti-cheat, and 32-bit-only apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun PackageScanItem(
    item: FileItem,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VelvetSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect)
            .border(1.dp, RoseGlowStart.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .testTag("package_item_${item.name}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (item.isXapk) EmeraldSafe.copy(alpha = 0.2f) else VelvetPrimary.copy(alpha = 0.2f)
                    )
            ) {
                Icon(
                    imageVector = if (item.isXapk) Icons.Default.FolderZip else Icons.Default.Android,
                    contentDescription = null,
                    tint = if (item.isXapk) EmeraldSafe else VelvetPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.formattedSize} • ${item.formattedDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VelvetSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Inspect",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = VelvetPrimary
                )
            }
        }
    }
}
