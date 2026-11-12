package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FileItem
import com.example.engine.FileCategoryFilter
import com.example.engine.FileSortOption
import com.example.ui.components.RomanticHeader
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.RoseGlowStart
import com.example.ui.theme.VelvetBackground
import com.example.ui.theme.VelvetPrimary
import com.example.ui.theme.VelvetSurface
import com.example.ui.theme.VelvetSurfaceVariant
import java.io.File

@Composable
fun FileBrowserScreen(
    currentDirectory: File,
    files: List<FileItem>,
    activeFilter: FileCategoryFilter,
    sortOption: FileSortOption,
    searchQuery: String,
    isLoading: Boolean,
    onNavigateDirectory: (File) -> Unit,
    onNavigateUp: () -> Boolean,
    onFilterChange: (FileCategoryFilter) -> Unit,
    onSortChange: (FileSortOption) -> Unit,
    onSearchChange: (String) -> Unit,
    onInspectPackage: (File) -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VelvetBackground)
    ) {
        RomanticHeader(
            title = "File Explorer",
            subtitle = "Intuitive package & filesystem navigation",
            badgeText = "${filteredFiles.size} items"
        )

        // Filter Category Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            CategoryChip(
                label = "All Files",
                isSelected = activeFilter == FileCategoryFilter.ALL,
                onClick = { onFilterChange(FileCategoryFilter.ALL) }
            )
            CategoryChip(
                label = "APKs & XAPKs",
                isSelected = activeFilter == FileCategoryFilter.PACKAGES,
                onClick = { onFilterChange(FileCategoryFilter.PACKAGES) }
            )
            CategoryChip(
                label = "OBB Expansion",
                isSelected = activeFilter == FileCategoryFilter.OBB,
                onClick = { onFilterChange(FileCategoryFilter.OBB) }
            )
            CategoryChip(
                label = "Sandbox Root",
                isSelected = activeFilter == FileCategoryFilter.SANDBOX,
                onClick = { onFilterChange(FileCategoryFilter.SANDBOX) }
            )
        }

        // Search Bar & Sort
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        "Search files or packages...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = VelvetPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VelvetPrimary,
                    unfocusedBorderColor = VelvetSurfaceVariant,
                    focusedContainerColor = VelvetSurface,
                    unfocusedContainerColor = VelvetSurface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("file_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Sort Menu Button
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VelvetSurface)
                        .testTag("sort_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort",
                        tint = VelvetPrimary
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(VelvetSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Name (A to Z)", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onSortChange(FileSortOption.NAME_ASC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Date (Newest first)", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onSortChange(FileSortOption.DATE_DESC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Size (Largest first)", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onSortChange(FileSortOption.SIZE_DESC)
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        // Breadcrumb Navigation Path (When viewing folder hierarchy)
        if (activeFilter == FileCategoryFilter.ALL) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = { onNavigateUp() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(VelvetSurfaceVariant)
                        .testTag("navigate_up_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Up",
                        tint = VelvetPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Scrollable Breadcrumbs
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    val segments = getBreadcrumbSegments(currentDirectory)
                    segments.forEachIndexed { index, pair ->
                        Text(
                            text = pair.first,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (index == segments.lastIndex) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (index == segments.lastIndex) VelvetPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { onNavigateDirectory(pair.second) }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        )
                        if (index < segments.lastIndex) {
                            Text(
                                text = " / ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // File List / Loading
        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator(color = VelvetPrimary)
            }
        } else if (filteredFiles.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(32.dp)
            ) {
                Text(
                    text = "No files found in this location.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredFiles, key = { it.path }) { item ->
                    FileItemRow(
                        item = item,
                        onClick = {
                            if (item.isDirectory) {
                                onNavigateDirectory(item.file)
                            } else if (item.isApk || item.isXapk) {
                                onInspectPackage(item.file)
                            }
                        },
                        onInspect = { onInspectPackage(item.file) },
                        onDelete = { onDeleteFile(item) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VelvetPrimary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = VelvetSurfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(10.dp),
        border = null
    )
}

@Composable
private fun FileItemRow(
    item: FileItem,
    onClick: () -> Unit,
    onInspect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VelvetSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                1.dp,
                if (item.isXapk) EmeraldSafe.copy(alpha = 0.25f)
                else if (item.isApk) VelvetPrimary.copy(alpha = 0.25f)
                else RoseGlowStart.copy(alpha = 0.1f),
                RoundedCornerShape(14.dp)
            )
            .testTag("file_item_${item.name}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            // File Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (item.isDirectory) VelvetPrimary.copy(alpha = 0.15f)
                        else if (item.isXapk) EmeraldSafe.copy(alpha = 0.2f)
                        else if (item.isApk) VelvetPrimary.copy(alpha = 0.2f)
                        else VelvetSurfaceVariant
                    )
            ) {
                Icon(
                    imageVector = when {
                        item.isDirectory -> Icons.Default.Folder
                        item.isXapk -> Icons.Default.FolderZip
                        item.isApk -> Icons.Default.Android
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = when {
                        item.isDirectory -> VelvetPrimary
                        item.isXapk -> EmeraldSafe
                        item.isApk -> VelvetPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (item.isDirectory) "${item.childCount} items • ${item.formattedDate}"
                    else "${item.formattedSize} • ${item.formattedDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick action button
            if (item.isApk || item.isXapk) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (item.isXapk) EmeraldSafe.copy(alpha = 0.18f) else VelvetPrimary.copy(alpha = 0.18f))
                        .clickable(onClick = onInspect)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("inspect_file_btn_${item.name}")
                ) {
                    Text(
                        text = if (item.isXapk) "XAPK" else "Install",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (item.isXapk) EmeraldSafe else VelvetPrimary
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun getBreadcrumbSegments(current: File): List<Pair<String, File>> {
    val segments = mutableListOf<Pair<String, File>>()
    var curr: File? = current
    while (curr != null) {
        val name = if (curr.name.isEmpty()) "Root" else curr.name
        segments.add(0, name to curr)
        curr = curr.parentFile
    }
    return segments.takeLast(4)
}
