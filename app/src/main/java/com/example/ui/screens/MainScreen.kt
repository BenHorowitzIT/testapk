package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.InstallProgressDialog
import com.example.ui.components.PackageInspectorSheet
import com.example.ui.components.SandboxedAppDetailSheet
import com.example.ui.theme.VelvetBackground
import com.example.ui.theme.VelvetPrimary
import com.example.ui.theme.VelvetSurface
import com.example.ui.theme.VelvetSurfaceVariant
import com.example.ui.viewmodel.VelvetBoxViewModel
import com.example.ui.viewmodel.VelvetTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: VelvetBoxViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val sandboxedApps by viewModel.sandboxedApps.collectAsStateWithLifecycle()
    val storageInfo by viewModel.storageInfo.collectAsStateWithLifecycle()
    val currentFiles by viewModel.currentFiles.collectAsStateWithLifecycle()
    val currentDirectory by viewModel.currentDirectory.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoadingFiles by viewModel.isLoadingFiles.collectAsStateWithLifecycle()
    val detectedPackages by viewModel.detectedPackages.collectAsStateWithLifecycle()
    val isInspecting by viewModel.isInspecting.collectAsStateWithLifecycle()

    val inspectedPackage by viewModel.inspectedPackage.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    val selectedSandboxedApp by viewModel.selectedSandboxedApp.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val inspectorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Handle Back Navigation in File Browser
    BackHandler(enabled = currentTab == VelvetTab.FILE_BROWSER) {
        val handled = viewModel.navigateUp()
        if (!handled) {
            viewModel.setTab(VelvetTab.SANDBOX)
        }
    }

    // Status snackbar triggers
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = VelvetSurface,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = currentTab == VelvetTab.SANDBOX,
                    onClick = { viewModel.setTab(VelvetTab.SANDBOX) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Sandbox",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Sandbox",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = VelvetPrimary,
                        indicatorColor = VelvetPrimary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_item_sandbox")
                )

                NavigationBarItem(
                    selected = currentTab == VelvetTab.INSTALLER,
                    onClick = { viewModel.setTab(VelvetTab.INSTALLER) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Installer",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Installer",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = VelvetPrimary,
                        indicatorColor = VelvetPrimary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_item_installer")
                )

                NavigationBarItem(
                    selected = currentTab == VelvetTab.FILE_BROWSER,
                    onClick = { viewModel.setTab(VelvetTab.FILE_BROWSER) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Files",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Explorer",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = VelvetPrimary,
                        indicatorColor = VelvetPrimary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_item_files")
                )
            }
        },
        containerColor = VelvetBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                VelvetTab.SANDBOX -> {
                    SandboxSpaceScreen(
                        apps = sandboxedApps,
                        storageInfo = storageInfo,
                        onAppClick = { app -> viewModel.selectSandboxedApp(app) },
                        onLaunchApp = { app -> viewModel.launchApp(app) },
                        onNavigateToInstaller = { viewModel.setTab(VelvetTab.INSTALLER) }
                    )
                }

                VelvetTab.INSTALLER -> {
                    XapkInstallerScreen(
                        scannedPackages = detectedPackages,
                        onInspectFile = { file -> viewModel.inspectPackage(file) },
                        onImportUri = { uri -> viewModel.importUri(uri) },
                        onNavigateToFileBrowser = { viewModel.setTab(VelvetTab.FILE_BROWSER) }
                    )
                }

                VelvetTab.FILE_BROWSER -> {
                    FileBrowserScreen(
                        currentDirectory = currentDirectory,
                        files = currentFiles,
                        activeFilter = categoryFilter,
                        sortOption = sortOption,
                        searchQuery = searchQuery,
                        isLoading = isLoadingFiles,
                        onNavigateDirectory = { dir -> viewModel.navigateToDirectory(dir) },
                        onNavigateUp = { viewModel.navigateUp() },
                        onFilterChange = { filter -> viewModel.setCategoryFilter(filter) },
                        onSortChange = { sort -> viewModel.setSortOption(sort) },
                        onSearchChange = { query -> viewModel.setSearchQuery(query) },
                        onInspectPackage = { file -> viewModel.inspectPackage(file) },
                        onDeleteFile = { item -> viewModel.deleteFile(item) }
                    )
                }
            }
        }
    }

    // Inspection Modal Sheet
    inspectedPackage?.let { pkg ->
        PackageInspectorSheet(
            packageInfo = pkg,
            sheetState = inspectorSheetState,
            onDismiss = { viewModel.closeInspect() },
            onInstallToSandbox = { viewModel.installInspectedPackage() }
        )
    }

    // Sandboxed App Details Sheet
    selectedSandboxedApp?.let { app ->
        SandboxedAppDetailSheet(
            app = app,
            sheetState = appDetailSheetState,
            onDismiss = { viewModel.selectSandboxedApp(null) },
            onLaunch = {
                viewModel.launchApp(app)
                viewModel.selectSandboxedApp(null)
            },
            onClearData = { viewModel.clearAppData(app) },
            onUninstall = { viewModel.uninstallApp(app) },
            onToggleStatus = { viewModel.toggleAppStatus(app) }
        )
    }

    // Active Install Progress Dialog
    installProgress?.let { (progress, status) ->
        InstallProgressDialog(
            progress = progress,
            status = status
        )
    }

    // Reading a package before the inspector opens
    if (isInspecting && installProgress == null) {
        InstallProgressDialog(
            progress = 0.1f,
            status = "Reading package..."
        )
    }
}
