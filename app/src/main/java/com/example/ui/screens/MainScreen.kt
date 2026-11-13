package com.example.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.material3.SnackbarDuration
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
    val detectedPackages by viewModel.detectedPackages.collectAsStateWithLifecycle()
    val isInspecting by viewModel.isInspecting.collectAsStateWithLifecycle()

    val inspectedPackage by viewModel.inspectedPackage.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    val selectedSandboxedApp by viewModel.selectedSandboxedApp.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val inspectorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Back from Installer goes to the Sandbox tab
    BackHandler(enabled = currentTab == VelvetTab.INSTALLER) {
        viewModel.setTab(VelvetTab.SANDBOX)
    }

    // Compact header that slides away when scrolling down and returns when scrolling up
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Status snackbar triggers
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            viewModel.dismissStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = VelvetPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentTab == VelvetTab.SANDBOX) "Ben's Sandbox" else "Install APK",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VelvetBackground,
                    scrolledContainerColor = VelvetSurface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                scrollBehavior = scrollBehavior
            )
        },
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

            }
        },
        containerColor = VelvetBackground,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
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
                        onNavigateToInstaller = { viewModel.setTab(VelvetTab.INSTALLER) },
                        onSaveLog = { viewModel.saveLog() },
                        onAddShortcut = { app -> viewModel.addHomeScreenShortcut(app) }
                    )
                }

                VelvetTab.INSTALLER -> {
                    XapkInstallerScreen(
                        scannedPackages = detectedPackages,
                        onInspectFile = { file -> viewModel.inspectPackage(file) },
                        onImportUri = { uri -> viewModel.importUri(uri) }
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
            onToggleStatus = { viewModel.toggleAppStatus(app) },
            onAddShortcut = { viewModel.addHomeScreenShortcut(app) }
        )
    }

    // Active Install Progress Dialog
    installProgress?.let { (progress, status) ->
        InstallProgressDialog(
            progress = progress,
            status = status,
            onCancel = { viewModel.cancelInstall() }
        )
    }

    // Reading a package before the inspector opens
    if (isInspecting && installProgress == null) {
        InstallProgressDialog(
            progress = 0.1f,
            status = "Reading package...",
            title = "Opening package"
        )
    }
}
