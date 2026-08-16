package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels {
        val appContainer = (application as RufusApplication).container
        DashboardViewModel.provideFactory(
            usbRepository = appContainer.usbRepository,
            writeEngine = appContainer.writeEngine,
            logRepository = appContainer.logRepository,
            isoDownloadEngine = appContainer.isoDownloadEngine,
            notificationManager = appContainer.notificationManager,
            feedbackManager = appContainer.feedbackManager
        )
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Some file providers do not support persistable permissions, continue with temporary grant
            }

            val docFile = DocumentFile.fromSingleUri(this, selectedUri)
            val name = docFile?.name ?: "custom-disk-image.iso"
            val size = docFile?.length() ?: 0L
            val ext = name.substringAfterLast('.', "").lowercase()

            if (ext.isEmpty() || !DashboardViewModel.ALLOWED_FLASH_EXTENSIONS.contains(ext)) {
                dashboardViewModel.reportInvalidFile(name, ext)
                Toast.makeText(this, "Invalid File: '$name' is not a supported bootable disk image (.iso, .img, .raw, etc.)", Toast.LENGTH_LONG).show()
                return@let
            }

            dashboardViewModel.selectImage(selectedUri, name, size, this)
            Toast.makeText(this, "Selected: $name", Toast.LENGTH_SHORT).show()
        }
    }


    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        if (!notifGranted) {
            Toast.makeText(this, "Notifications disabled: Live flashing status won't be shown in status bar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestRequiredPermissions()

        val prefs = getSharedPreferences("rufus_app_prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            prefs.edit().putBoolean("is_first_launch", false).apply()
            dashboardViewModel.startIntro()
            dashboardViewModel.startDynamicTips()
        }

        setContent {
            val uiState by dashboardViewModel.uiState.collectAsState()

            com.example.ui.components.ThemeCircularReveal(isDarkMode = uiState.isDarkMode) { darkTheme ->
                MyApplicationTheme(
                    darkTheme = darkTheme,
                    accentColorOverride = uiState.accentColorOverride?.let { androidx.compose.ui.graphics.Color(it) }
                ) {
                    DashboardScreen(
                        viewModel = dashboardViewModel,
                        onSelectImageClick = {
                        try {
                            pickImageLauncher.launch(arrayOf(
                                "application/x-iso9660-image",
                                "application/octet-stream",
                                "application/zip",
                                "application/x-raw-disk-image",
                                "*/*"
                            ))
                        } catch (e: Exception) {
                            Toast.makeText(this, "Could not open file picker: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dashboardViewModel.refreshUsbDevices()
    }
}
