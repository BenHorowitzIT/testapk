package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SandboxedAppEntity
import com.example.data.model.FileItem
import com.example.data.model.ParsedPackage
import com.example.data.model.SandboxStorageInfo
import com.example.engine.FileCategoryFilter
import com.example.engine.FileManager
import com.example.engine.FileSortOption
import com.example.engine.HomeShortcuts
import com.example.engine.LogSaver
import com.example.engine.SandboxManager
import com.example.engine.XapkInstallerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class VelvetTab {
    SANDBOX,
    INSTALLER
}

class VelvetBoxViewModel(application: Application) : AndroidViewModel(application) {

    val sandboxManager = SandboxManager(application)
    val xapkEngine = XapkInstallerEngine(application, sandboxManager)
    val fileManager = FileManager(application)

    private val importDir: File
        get() = File(getApplication<Application>().cacheDir, "imports").apply { if (!exists()) mkdirs() }

    private val _sandboxedApps = MutableStateFlow<List<SandboxedAppEntity>>(emptyList())
    val sandboxedApps: StateFlow<List<SandboxedAppEntity>> = _sandboxedApps.asStateFlow()

    private val _currentTab = MutableStateFlow(VelvetTab.SANDBOX)
    val currentTab: StateFlow<VelvetTab> = _currentTab.asStateFlow()

    private val _storageInfo = MutableStateFlow(SandboxStorageInfo())
    val storageInfo: StateFlow<SandboxStorageInfo> = _storageInfo.asStateFlow()

    private val _currentDirectory = MutableStateFlow(fileManager.rootStorageDirectory)
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _currentFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val currentFiles: StateFlow<List<FileItem>> = _currentFiles.asStateFlow()

    /** APK/XAPK files found in Downloads and other common folders (Installer tab). */
    private val _detectedPackages = MutableStateFlow<List<FileItem>>(emptyList())
    val detectedPackages: StateFlow<List<FileItem>> = _detectedPackages.asStateFlow()

    private val _categoryFilter = MutableStateFlow(FileCategoryFilter.ALL)
    val categoryFilter: StateFlow<FileCategoryFilter> = _categoryFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(FileSortOption.NAME_ASC)
    val sortOption: StateFlow<FileSortOption> = _sortOption.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    private val _inspectedPackage = MutableStateFlow<ParsedPackage?>(null)
    val inspectedPackage: StateFlow<ParsedPackage?> = _inspectedPackage.asStateFlow()

    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _installProgress = MutableStateFlow<Pair<Float, String>?>(null)
    val installProgress: StateFlow<Pair<Float, String>?> = _installProgress.asStateFlow()

    private val _selectedSandboxedApp = MutableStateFlow<SandboxedAppEntity?>(null)
    val selectedSandboxedApp: StateFlow<SandboxedAppEntity?> = _selectedSandboxedApp.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshApps()
        scanDetectedPackages()
    }

    /** Called once storage permission is granted, so the browser can show real storage. */
    fun onStoragePermissionGranted() {
        scanDetectedPackages()
    }

    fun setTab(tab: VelvetTab) {
        _currentTab.value = tab
        when (tab) {
            VelvetTab.SANDBOX -> refreshApps()
            VelvetTab.INSTALLER -> scanDetectedPackages()
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            val apps = sandboxManager.listApps()
            _sandboxedApps.value = apps
            _storageInfo.value = sandboxManager.getStorageInfo(apps)
            // keep an open detail sheet in sync
            _selectedSandboxedApp.value?.let { sel ->
                _selectedSandboxedApp.value = apps.firstOrNull { it.packageName == sel.packageName }
            }
        }
    }

    fun refreshStorage() = refreshApps()

    fun scanDetectedPackages() {
        viewModelScope.launch {
            _detectedPackages.value = fileManager.scanCategory(FileCategoryFilter.PACKAGES, FileSortOption.DATE_DESC)
        }
    }

    fun loadDirectory(directory: File) {
        viewModelScope.launch {
            _isLoadingFiles.value = true
            _currentDirectory.value = directory
            val files = fileManager.listDirectory(directory, _sortOption.value)
            _currentFiles.value = files
            _isLoadingFiles.value = false
        }
    }

    fun navigateToDirectory(directory: File) {
        _categoryFilter.value = FileCategoryFilter.ALL
        loadDirectory(directory)
    }

    fun navigateUp(): Boolean {
        when (_categoryFilter.value) {
            FileCategoryFilter.ALL -> {}
            FileCategoryFilter.SANDBOX -> {
                _categoryFilter.value = FileCategoryFilter.ALL
                loadDirectory(fileManager.rootStorageDirectory)
                return true
            }
            else -> {
                setCategoryFilter(FileCategoryFilter.ALL)
                return true
            }
        }
        val current = _currentDirectory.value
        val parent = current.parentFile
        if (parent != null && parent.canRead() && parent.listFiles() != null) {
            loadDirectory(parent)
            return true
        }
        return false
    }

    fun setCategoryFilter(filter: FileCategoryFilter) {
        _categoryFilter.value = filter
        viewModelScope.launch {
            _isLoadingFiles.value = true
            when (filter) {
                FileCategoryFilter.ALL -> loadDirectory(_currentDirectory.value)
                FileCategoryFilter.SANDBOX -> loadDirectory(fileManager.sandboxDirectory)
                else -> {
                    _currentFiles.value = fileManager.scanCategory(filter, _sortOption.value)
                    _isLoadingFiles.value = false
                }
            }
        }
    }

    fun setSortOption(sort: FileSortOption) {
        _sortOption.value = sort
        if (_categoryFilter.value == FileCategoryFilter.ALL || _categoryFilter.value == FileCategoryFilter.SANDBOX) {
            loadDirectory(_currentDirectory.value)
        } else {
            setCategoryFilter(_categoryFilter.value)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun inspectPackage(file: File) {
        viewModelScope.launch {
            _isInspecting.value = true
            val result = xapkEngine.inspectPackage(file)
            result.onSuccess { pkg ->
                _inspectedPackage.value = pkg
            }.onFailure { err ->
                _statusMessage.value = "Can't read this file: ${err.localizedMessage}"
                cleanupImport(file)
            }
            _isInspecting.value = false
        }
    }

    /** A file picked with the system picker, shared to VelvetBox, or opened with VelvetBox. */
    fun importUri(uri: Uri) {
        viewModelScope.launch {
            _isInspecting.value = true
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    // A plain file path we can read directly
                    if (uri.scheme == "file") {
                        uri.path?.let { File(it) }?.takeIf { it.canRead() }?.let { return@runCatching it }
                    }
                    val resolver = getApplication<Application>().contentResolver
                    var name: String? = null
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) name = c.getString(0)
                    }
                    val safeName = (name ?: uri.lastPathSegment ?: "package.apk")
                        .substringAfterLast('/')
                        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
                    val dest = File(importDir, safeName)
                    resolver.openInputStream(uri)!!.use { input ->
                        dest.outputStream().use { out -> input.copyTo(out, 256 * 1024) }
                    }
                    dest
                }
            }
            _isInspecting.value = false
            file.onSuccess { inspectPackage(it) }
                .onFailure { _statusMessage.value = "Couldn't open that file: ${it.localizedMessage}" }
        }
    }

    private fun cleanupImport(file: File) {
        if (file.parentFile?.absolutePath == importDir.absolutePath) file.delete()
    }

    fun closeInspect() {
        _inspectedPackage.value?.let { cleanupImport(it.sourceFile) }
        _inspectedPackage.value = null
        xapkEngine.clearInspectCache()
    }

    private var installJob: Job? = null

    fun installInspectedPackage() {
        val pkg = _inspectedPackage.value ?: return
        if (installJob?.isActive == true) return
        installJob = viewModelScope.launch {
            _installProgress.value = 0.02f to "Preparing sandbox..."
            val result = xapkEngine.installIntoSandbox(pkg) { progress, status ->
                _installProgress.value = progress to status
            }
            _installProgress.value = null
            result.onSuccess {
                _statusMessage.value = "'${pkg.appName}' is installed in Ben's Sandbox"
                closeInspect()
                refreshApps()
                _currentTab.value = VelvetTab.SANDBOX
            }.onFailure { err ->
                val reason = err.localizedMessage ?: err.javaClass.simpleName
                val saved = withContext(Dispatchers.IO) {
                    LogSaver.save(getApplication<Application>(), "Install of ${pkg.packageName} failed: $reason")
                }
                _statusMessage.value = "Install failed: $reason" +
                    (saved.getOrNull()?.let { "\nLog saved to Download/Ben's Sandbox/${it.name}" } ?: "")
            }
        }
    }

    /** Cancel button in the install dialog. */
    fun cancelInstall() {
        installJob?.cancel()
        installJob = null
        _installProgress.value = null
        _statusMessage.value = "Install cancelled"
    }

    /** "Save log" button. */
    fun saveLog() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                LogSaver.save(getApplication<Application>(), "Saved from the Save log button")
            }
            _statusMessage.value = saved.fold(
                onSuccess = { "Log saved to Download/Ben's Sandbox/${it.name}" },
                onFailure = { "Couldn't save log: ${it.localizedMessage}" }
            )
        }
    }

    fun selectSandboxedApp(app: SandboxedAppEntity?) {
        _selectedSandboxedApp.value = app
    }

    fun launchApp(app: SandboxedAppEntity) {
        viewModelScope.launch {
            _statusMessage.value = "Opening ${app.appName}..."
            sandboxManager.launch(app)
                .onSuccess { refreshApps() }
                .onFailure { _statusMessage.value = "Couldn't open ${app.appName}: ${it.localizedMessage}" }
        }
    }

    fun clearAppData(app: SandboxedAppEntity) {
        viewModelScope.launch {
            sandboxManager.clearAppData(app)
                .onSuccess { _statusMessage.value = "Data cleared for ${app.appName}" }
                .onFailure { _statusMessage.value = "Couldn't clear data: ${it.localizedMessage}" }
            refreshApps()
        }
    }

    fun uninstallApp(app: SandboxedAppEntity) {
        viewModelScope.launch {
            sandboxManager.uninstallApp(app)
                .onSuccess {
                    _selectedSandboxedApp.value = null
                    _statusMessage.value = "${app.appName} removed from the sandbox"
                }
                .onFailure { _statusMessage.value = "Couldn't uninstall: ${it.localizedMessage}" }
            refreshApps()
        }
    }

    fun toggleAppStatus(app: SandboxedAppEntity) {
        viewModelScope.launch {
            val newStatus = sandboxManager.toggleStatus(app).getOrNull()
            if (newStatus != null) {
                _statusMessage.value = "${app.appName} is now $newStatus"
            }
            refreshApps()
        }
    }

    fun deleteFile(item: FileItem) {
        viewModelScope.launch {
            fileManager.deleteFile(item.file)
            _statusMessage.value = "Deleted '${item.name}'"
            if (_categoryFilter.value == FileCategoryFilter.ALL || _categoryFilter.value == FileCategoryFilter.SANDBOX) {
                loadDirectory(_currentDirectory.value)
            } else {
                setCategoryFilter(_categoryFilter.value)
            }
            scanDetectedPackages()
        }
    }

    /** Puts a shortcut for a sandboxed app on the home screen. */
    fun addHomeScreenShortcut(app: SandboxedAppEntity) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                HomeShortcuts.add(getApplication<Application>(), app)
            }
            _statusMessage.value = if (ok) {
                "Added ${app.appName} to the home screen"
            } else {
                "Your home screen app doesn't allow adding shortcuts"
            }
        }
    }

    fun dismissStatus() {
        _statusMessage.value = null
    }
}
