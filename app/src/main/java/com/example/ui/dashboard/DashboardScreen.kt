package com.example.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.models.*
import com.example.ui.components.FloatingNavDock
import com.example.ui.download.DownloadScreen
import com.example.ui.drives.DrivesScreen
import com.example.ui.images.ImagesScreen
import com.example.ui.intro.AppIntroSplash
import com.example.ui.logs.LogsScreen
import com.example.ui.onboarding.DynamicTipsOverlay
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.*
import com.example.util.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSelectImageClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val availableDevices by viewModel.availableDevices.collectAsState()

    var showDeviceSheet by remember { mutableStateOf(false) }
    var showPresetSheet by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(40.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.rufus_premium_icon_1786884298007),
                                contentDescription = "Rufus Logo",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Rufus",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "v4.5",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            // Real-time USB OTG Connection Status Indicator
                            val isConnected = availableDevices.isNotEmpty()
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isConnected) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .clickable { viewModel.refreshUsbDevices() }
                                    .testTag("usb_otg_status_indicator")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isConnected) Color(0xFF10B981) else Color(0xFFE11D48))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Usb,
                                        contentDescription = if (isConnected) "USB OTG Connected" else "No USB OTG Device",
                                        tint = if (isConnected) Color(0xFF10B981) else Color(0xFFE11D48),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isConnected) {
                                            if (availableDevices.size == 1) "OTG: ${availableDevices.first().productName.take(13)}" else "${availableDevices.size} OTG Drives"
                                        } else "No OTG Device",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Primary Quick Action: Refresh USB Storage
                    IconButton(
                        onClick = { viewModel.refreshUsbDevices() },
                        modifier = Modifier.bounceClick().testTag("refresh_usb_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh USB")
                    }

                    // Consolidated Tools & Utilities Dropdown Menu
                    Box {
                        IconButton(
                            onClick = { showTopMenu = true },
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options & Tools")
                        }

                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            // Dynamic Tips & Interactive Guide
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Interactive Guide", fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.startDynamicTips()
                                },
                                modifier = Modifier.bounceClick().testTag("tips_guide_button")
                            )

                            // Hash & Checksum Tool
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Checksum & Hash", fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.openChecksumDialog()
                                },
                                modifier = Modifier.bounceClick().testTag("open_checksum_button")
                            )

                            // Language Switcher (38 Languages)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Language", fontWeight = FontWeight.Medium)
                                            Text(uiState.currentLanguage.nativeName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.openLanguageDialog()
                                },
                                modifier = Modifier.bounceClick().testTag("language_selector_button")
                            )

                            // UEFI Runtime Validation Tool
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("UEFI Media Validation", fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.runUefiMediaValidation()
                                },
                                modifier = Modifier.bounceClick().testTag("uefi_validation_button")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            AnimatedContent(
                targetState = uiState.selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { if (forward) it / 5 else -it / 5 }
                    ) + fadeIn(animationSpec = tween(220)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                                targetOffsetX = { if (forward) -it / 5 else it / 5 }
                            ) + fadeOut(animationSpec = tween(160))
                        )
                },
                label = "tabTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetTab ->
                when (targetTab) {
                    RufusTab.FLASH -> {
                        FlashTabContent(
                            uiState = uiState,
                            onDeviceClick = { showDeviceSheet = true },
                            onSelectImageClick = onSelectImageClick,
                            onPresetsClick = { showPresetSheet = true },
                            onOpenChecksumClick = { viewModel.openChecksumDialog() },
                            onBootSelectionTypeChange = { viewModel.setBootSelectionType(it) },
                            onWindowsOptionsClick = { viewModel.openWindowsOptionsDialog() },
                            onLinuxPersistenceChange = { viewModel.updateLinuxPersistence(it) },
                            onVolumeLabelChange = { viewModel.setVolumeLabel(it) },
                            onPartitionSchemeChange = { viewModel.setPartitionScheme(it) },
                            onTargetSystemChange = { viewModel.setTargetSystem(it) },
                            onFileSystemChange = { viewModel.setFileSystem(it) },
                            onClusterSizeChange = { viewModel.setClusterSize(it) },
                            onQuickFormatToggle = { viewModel.toggleQuickFormat(it) },
                            onBadBlocksToggle = { viewModel.toggleCheckBadBlocks(it) },
                            onBadBlockPassesChange = { viewModel.setBadBlockPasses(it) },
                            onFakeDriveToggle = { viewModel.toggleFakeFlashDriveDetection(it) },
                            onVerifyWrittenDataToggle = { viewModel.toggleVerifyWrittenData(it) },
                            onStartClick = { viewModel.onStartClicked() },
                            onCancelClick = { viewModel.cancelWriting() }
                        )
                    }
                    RufusTab.DRIVES -> DrivesScreen(viewModel = viewModel)
                    RufusTab.IMAGES -> ImagesScreen(viewModel = viewModel, onSelectImageClick = onSelectImageClick)
                    RufusTab.DOWNLOAD -> DownloadScreen(viewModel = viewModel)
                    RufusTab.LOGS -> LogsScreen(viewModel = viewModel)
                    RufusTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }

            // Pure floating dock overlay (no solid space or background bar underneath)
            FloatingNavDock(
                currentTab = uiState.selectedTab,
                onTabSelected = { viewModel.selectTab(it) },
                translations = uiState.strings,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            )
        }
    }

    // Invalid / Untrusted File Extension Dialog
    if (uiState.showInvalidFileDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInvalidFileDialog() },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = "Invalid File",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "UNSUPPORTED DISK IMAGE",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = uiState.invalidFileError,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissInvalidFileDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("OK, GOT IT", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }


    // OTG Missing / Disconnected Alarm Dialog
    if (uiState.showOtgAlarmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissOtgAlarm() },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = "Alarm",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "NO OTG USB DRIVE DETECTED",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.otgAlarmMessage,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                        )
                    ) {
                        Text(
                            text = "Hardware Requirement: OTG USB Adapter + USB 2.0/3.0 Flash Drive or External SSD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.retryOtgScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.bounceClick().testTag("retry_otg_scan_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RETRY OTG SCAN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.dismissOtgAlarm() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.bounceClick().testTag("dismiss_otg_alarm_button")
                ) {
                    Text("DISMISS", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Animated Intro Splash Overlay
    if (uiState.showIntroSplash) {
        AppIntroSplash(
            onIntroComplete = { viewModel.completeIntro() }
        )
    }

    // Dynamic Tips / Guided Tour Walkthrough Overlay
    if (uiState.showDynamicTips) {
        DynamicTipsOverlay(
            currentStep = uiState.currentTipStep,
            onStepChange = { viewModel.setTipStep(it) },
            onDismiss = { viewModel.dismissDynamicTips() }
        )
    }

    // Safety Confirmation Warning Dialog
    if (uiState.showConfirmDialog) {
        val targetDeviceName = uiState.selectedDevice?.displayName ?: "Selected Drive"
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmDialog() },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "WARNING: ALL DATA WILL BE DESTROYED",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "All data on device '$targetDeviceName' will be permanently erased and replaced with the selected bootable disk image.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "To continue with this operation, click OK. To quit click CANCEL.",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAndStartWriting() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_write_button")
                ) {
                    Text("OK (FORMAT & WRITE)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.dismissConfirmDialog() },
                    modifier = Modifier.testTag("cancel_dialog_button")
                ) {
                    Text("CANCEL")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ISO Checksum Calculator & Hash Comparison Dialog
    if (uiState.showChecksumDialog) {
        val img = uiState.selectedImage
        AlertDialog(
            onDismissRequest = { viewModel.dismissChecksumDialog() },
            icon = { Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Image Hashes & Checksum Verification", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Selected: ${img?.fileName ?: "No file loaded"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    // Compute hashes card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TerminalBackground)
                            .padding(12.dp)
                    ) {
                        if (uiState.isCalculatingHash) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.example.ui.components.GlassmorphicPulseLoader(
                                    modifier = Modifier.size(24.dp),
                                    color = TerminalSuccess,
                                    coreColor = Color(0xFF15803d)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Computing MD5, SHA-1, SHA-256...", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                            }
                        } else {
                            val chk = uiState.checksumResult
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Column {
                                    Text("MD5:", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(chk?.md5 ?: img?.hashMd5 ?: "Not calculated", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalSuccess)
                                }
                                Column {
                                    Text("SHA-256:", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(chk?.sha256 ?: img?.hashSha256 ?: "Not calculated", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TerminalSuccess)
                                }
                                Column {
                                    Text("SHA-512:", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(
                                        chk?.sha512?.let { if (it.length > 32) "${it.take(16)}...${it.takeLast(16)}" else it } ?: img?.hashSha512?.take(32) ?: "Not calculated",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TerminalSuccess
                                    )
                                }
                            }
                        }
                    }

                    // Hash comparison input field
                    OutlinedTextField(
                        value = uiState.hashVerifyQuery,
                        onValueChange = { viewModel.verifyHashMatch(it) },
                        label = { Text("Compare Hash (MD5 / SHA-256)") },
                        placeholder = { Text("Paste official ISO hash to verify...") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_hash_verify_input"),
                        trailingIcon = {
                            if (uiState.isHashMatching == true) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Matched", tint = TerminalSuccess)
                            } else if (uiState.isHashMatching == false) {
                                Icon(Icons.Default.Close, contentDescription = "Mismatch", tint = TerminalError)
                            }
                        }
                    )

                    if (uiState.isHashMatching == true) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TerminalSuccess.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("✅", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hash Matches Official Image Signature!", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TerminalSuccess)
                            }
                        }
                    } else if (uiState.isHashMatching == false) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TerminalError.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("⚠️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hash Mismatch! The image may be corrupted.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TerminalError)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissChecksumDialog() }) {
                    Text("DONE")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Windows User Experience Customization Dialog
    if (uiState.showWindowsOptionsDialog) {
        var winConfig by remember { mutableStateOf(uiState.windowsConfig) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissWindowsOptionsDialog() },
            icon = { Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Windows User Experience", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Customize Windows setup options and OOBE parameters:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    CheckboxOption(
                        title = "Remove requirement for 4GB+ RAM, Secure Boot and TPM 2.0",
                        checked = winConfig.bypassTpmSecureBootRam,
                        onCheckedChange = { winConfig = winConfig.copy(bypassTpmSecureBootRam = it) }
                    )

                    CheckboxOption(
                        title = "Remove requirement for an online Microsoft account",
                        checked = winConfig.bypassOnlineAccount,
                        onCheckedChange = { winConfig = winConfig.copy(bypassOnlineAccount = it) }
                    )

                    CheckboxOption(
                        title = "Create a local account with username",
                        checked = winConfig.createLocalAccount,
                        onCheckedChange = { winConfig = winConfig.copy(createLocalAccount = it) }
                    )

                    if (winConfig.createLocalAccount) {
                        OutlinedTextField(
                            value = winConfig.localUsername,
                            onValueChange = { winConfig = winConfig.copy(localUsername = it) },
                            label = { Text("Local username") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(start = 28.dp)
                        )
                    }

                    CheckboxOption(
                        title = "Set regional options using the same values as this user's",
                        checked = winConfig.setRegionalOptions,
                        onCheckedChange = { winConfig = winConfig.copy(setRegionalOptions = it) }
                    )

                    CheckboxOption(
                        title = "Disable data collection (Skip privacy questions)",
                        checked = winConfig.disableDataCollection,
                        onCheckedChange = { winConfig = winConfig.copy(disableDataCollection = it) }
                    )

                    CheckboxOption(
                        title = "Disable BitLocker automatic device encryption",
                        checked = winConfig.disableBitLocker,
                        onCheckedChange = { winConfig = winConfig.copy(disableBitLocker = it) }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateWindowsConfig(winConfig)
                    viewModel.dismissWindowsOptionsDialog()
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissWindowsOptionsDialog() }) {
                    Text("CANCEL")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // UEFI Runtime Validation Result Dialog
    if (uiState.showUefiValidationDialog) {
        val result = uiState.uefiValidationResult
        AlertDialog(
            onDismissRequest = { viewModel.dismissUefiValidationDialog() },
            icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("UEFI Boot Media Validation", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                if (uiState.isUefiValidating || result == null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        com.example.ui.components.GlassmorphicPulseLoader(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Analyzing UEFI boot binaries and certificates...", fontSize = 12.sp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✅", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("UEFI Bootable & Secure Boot Ready", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Partition: ${result.partitionScheme} • FS: ${result.fileSystem}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        result.checks.forEach { check ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(if (check.passed) "✓" else "✕", color = if (check.passed) TerminalSuccess else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(check.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(check.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissUefiValidationDialog() }) {
                    Text("CLOSE")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // 38 Native Languages Dialog
    if (uiState.showLanguageDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredLanguages = RufusLanguage.ALL.filter {
            it.nativeName.contains(searchQuery, ignoreCase = true) || it.englishName.contains(searchQuery, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { viewModel.dismissLanguageDialog() },
            title = { Text("Select Language (38 Supported)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.height(340.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search language...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filteredLanguages) { lang ->
                            val isSelected = uiState.currentLanguage.code == lang.code
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectLanguage(lang) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(lang.nativeName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(lang.englishName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissLanguageDialog() }) {
                    Text("DONE")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Device Selection Sheet
    if (showDeviceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDeviceSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT TARGET DRIVE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = { viewModel.refreshUsbDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (availableDevices.isEmpty()) {
                    Text(
                        text = "No USB devices detected. Connect a physical USB OTG drive or flash memory card.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availableDevices) { device ->
                            val isSelected = uiState.selectedDevice?.deviceName == device.deviceName
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectDevice(device)
                                        showDeviceSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(device.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            "${device.formattedCapacity} • ${device.deviceType.label} • ${device.deviceName}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Presets Selection Sheet
    if (showPresetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPresetSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "SELECT OS IMAGE PRESET",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ImageFile.PRESETS) { preset ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectPresetImage(preset)
                                    showPresetSheet = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(preset.osDetection ?: preset.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${preset.fileName} • ${preset.sizeFormatted}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckboxOption(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
fun FlashTabContent(
    uiState: DashboardUiState,
    onDeviceClick: () -> Unit,
    onSelectImageClick: () -> Unit,
    onPresetsClick: () -> Unit,
    onOpenChecksumClick: () -> Unit,
    onBootSelectionTypeChange: (BootSelectionType) -> Unit,
    onWindowsOptionsClick: () -> Unit,
    onLinuxPersistenceChange: (LinuxPersistenceConfig) -> Unit,
    onVolumeLabelChange: (String) -> Unit,
    onPartitionSchemeChange: (PartitionScheme) -> Unit,
    onTargetSystemChange: (TargetSystem) -> Unit,
    onFileSystemChange: (FileSystem) -> Unit,
    onClusterSizeChange: (Int) -> Unit,
    onQuickFormatToggle: (Boolean) -> Unit,
    onBadBlocksToggle: (Boolean) -> Unit,
    onBadBlockPassesChange: (Int) -> Unit,
    onFakeDriveToggle: (Boolean) -> Unit,
    onVerifyWrittenDataToggle: (Boolean) -> Unit = {},
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    val isWindows = uiState.selectedImage?.isWindows == true || uiState.bootSelectionType == BootSelectionType.WINDOWS_TO_GO
    val isLinux = uiState.selectedImage?.isLinux == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Device Card
        InteractiveDeviceCard(
            device = uiState.selectedDevice,
            onClick = onDeviceClick,
            cardTitle = uiState.strings.deviceCardTitle,
            noDeviceText = uiState.strings.noDeviceSelected,
            promptText = uiState.strings.attachOtgPrompt
        )

        // Boot Selection Card
        InteractiveBootSelectionCard(
            bootSelectionType = uiState.bootSelectionType,
            image = uiState.selectedImage,
            partitionScheme = uiState.partitionScheme,
            targetSystem = uiState.targetSystem,
            onBootTypeChange = onBootSelectionTypeChange,
            onSelectClick = onSelectImageClick,
            onPresetsClick = onPresetsClick,
            onOpenChecksumClick = onOpenChecksumClick,
            onPartitionSchemeChange = onPartitionSchemeChange,
            onTargetSystemChange = onTargetSystemChange
        )

        // Windows User Experience Customization Button if Windows ISO
        if (isWindows) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onWindowsOptionsClick() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Windows User Experience", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("TPM 2.0 / Secure Boot bypass, local account, privacy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("EDIT >", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Linux Persistent Partition Card if Linux ISO
        if (isLinux) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("LINUX PERSISTENT PARTITION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text(
                            if (uiState.linuxPersistence.enabled) "${String.format("%.1f", uiState.linuxPersistence.sizeGb)} GB" else "0 GB (No persistence)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = uiState.linuxPersistence.sizeGb,
                        onValueChange = {
                            onLinuxPersistenceChange(LinuxPersistenceConfig(enabled = it > 0.1f, sizeGb = it))
                        },
                        valueRange = 0f..16f,
                        steps = 15
                    )
                    Text("Persistent storage keeps settings and files across live USB sessions.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Format Options Card
        InteractiveFormatOptionsCard(
            volumeLabel = uiState.volumeLabel,
            onVolumeLabelChange = onVolumeLabelChange,
            fileSystem = uiState.fileSystem,
            onFileSystemChange = onFileSystemChange,
            clusterSize = uiState.clusterSize,
            onClusterSizeChange = onClusterSizeChange,
            quickFormat = uiState.quickFormat,
            onQuickFormatToggle = onQuickFormatToggle,
            checkBadBlocks = uiState.checkBadBlocks,
            onBadBlocksToggle = onBadBlocksToggle,
            badBlockPasses = uiState.badBlockPasses,
            onBadBlockPassesChange = onBadBlockPassesChange,
            detectFakeFlashDrives = uiState.detectFakeFlashDrives,
            onFakeDriveToggle = onFakeDriveToggle,
            verifyWrittenData = uiState.verifyWrittenData,
            onVerifyWrittenDataToggle = onVerifyWrittenDataToggle
        )

        // Isolated Active Flashing Progress & Action Section (optimized to skip static form recompositions)
        ActiveFlashingProgressSection(
            writeProgress = uiState.writeProgress,
            canStart = uiState.selectedDevice != null && (uiState.bootSelectionType != BootSelectionType.ISO_IMAGE || uiState.selectedImage != null),
            onStartClick = onStartClick,
            onCancelClick = onCancelClick,
            onSelectIsoClick = onSelectImageClick,
            strings = uiState.strings
        )

        Spacer(modifier = Modifier.height(88.dp))
    }
}

@Composable
fun ActiveFlashingProgressSection(
    writeProgress: WriteProgress,
    canStart: Boolean,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSelectIsoClick: () -> Unit,
    strings: AppTranslations
) {
    val isWriting = writeProgress !is WriteProgress.Idle &&
                    writeProgress !is WriteProgress.Completed &&
                    writeProgress !is WriteProgress.Error

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Live Circular & Status Progress Gauge Card
        CircularAndLiveStatusCard(
            progress = writeProgress
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Action Buttons
        ActionButtonsRow(
            isWriting = isWriting,
            isCompleted = writeProgress is WriteProgress.Completed,
            canStart = canStart,
            onStartClick = onStartClick,
            onCancelClick = onCancelClick,
            onSelectIsoClick = onSelectIsoClick,
            startButtonText = strings.startButton,
            flashingButtonText = strings.flashingButton,
            startAgainButtonText = strings.startAgainButton
        )
    }
}


@Composable
fun InteractiveDeviceCard(
    device: UsbDeviceDomainModel?,
    onClick: () -> Unit,
    cardTitle: String = "DEVICE",
    noDeviceText: String = "No USB Device Selected",
    promptText: String = "Connect USB OTG or pick test drive"
) {
    val isConnected = device != null

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("device_selection_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cardTitle,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (device != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "HARDWARE OTG",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        color = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (isConnected) "CONNECTED ▼" else "NO DEVICE ▼",
                            color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        device?.deviceType?.badge ?: "USB",
                        color = if (isConnected) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device?.productName ?: noDeviceText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isConnected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (device != null) "${device.formattedCapacity} — ${device.deviceName}" else promptText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Dropdown Selector Composable for choosing partition schemes like MBR or GPT
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartitionSchemeDropdown(
    selectedScheme: PartitionScheme,
    onSchemeSelected: (PartitionScheme) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .testTag("partition_scheme_dropdown")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = selectedScheme.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (selectedScheme) {
                            PartitionScheme.GPT -> "UEFI (non CSM)"
                            PartitionScheme.MBR -> "BIOS or UEFI"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("▼", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            PartitionScheme.values().forEach { scheme ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = "${scheme.name} (${scheme.label})",
                                fontWeight = if (scheme == selectedScheme) FontWeight.Bold else FontWeight.Normal,
                                color = if (scheme == selectedScheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (scheme) {
                                    PartitionScheme.GPT -> "Modern UEFI standard (Supports >2TB drives)"
                                    PartitionScheme.MBR -> "Legacy BIOS & CSM compatible"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSchemeSelected(scheme)
                        expanded = false
                    },
                    modifier = Modifier.testTag("scheme_option_${scheme.name}")
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveBootSelectionCard(
    bootSelectionType: BootSelectionType,
    image: ImageFile?,
    partitionScheme: PartitionScheme,
    targetSystem: TargetSystem,
    onBootTypeChange: (BootSelectionType) -> Unit,
    onSelectClick: () -> Unit,
    onPresetsClick: () -> Unit,
    onOpenChecksumClick: () -> Unit,
    onPartitionSchemeChange: (PartitionScheme) -> Unit,
    onTargetSystemChange: (TargetSystem) -> Unit
) {
    var bootTypeExpanded by remember { mutableStateOf(false) }
    var showImageOptionsMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BOOT SELECTION",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (bootSelectionType == BootSelectionType.ISO_IMAGE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Primary Action: Select Image
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onSelectClick() }
                                .testTag("select_image_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SELECT",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Secondary / Tertiary Tools Overflow Menu (Hash Checksum & Download Presets)
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.clickable { showImageOptionsMenu = true }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.MoreHoriz,
                                        contentDescription = "Image Options",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showImageOptionsMenu,
                                onDismissRequest = { showImageOptionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Tag,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text("Checksum / Hash", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Verify MD5, SHA-1, SHA-256", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    onClick = {
                                        showImageOptionsMenu = false
                                        onOpenChecksumClick()
                                    },
                                    modifier = Modifier.testTag("hash_button")
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Download,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text("ISO Presets Catalog", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Ubuntu, Windows 11, Fedora...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    onClick = {
                                        showImageOptionsMenu = false
                                        onPresetsClick()
                                    },
                                    modifier = Modifier.testTag("presets_button")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Boot Selection Dropdown (FreeDOS, MS-DOS, UEFI Shell, Windows To Go, ISO)
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { bootTypeExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(bootSelectionType.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(bootSelectionType.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("▼", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                DropdownMenu(
                    expanded = bootTypeExpanded,
                    onDismissRequest = { bootTypeExpanded = false }
                ) {
                    BootSelectionType.values().forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(type.label, fontWeight = FontWeight.Bold)
                                    Text(type.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                onBootTypeChange(type)
                                bootTypeExpanded = false
                            }
                        )
                    }
                }
            }

            // ISO File Pill if ISO is selected
            if (bootSelectionType == BootSelectionType.ISO_IMAGE) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .clickable { onSelectClick() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "ISO",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = image?.fileName ?: "Select disk image (ISO/IMG)...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (image?.osDetection != null) {
                            Text(
                                text = "${image.osDetection} • ${image.sizeFormatted}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Partition Scheme & Target System Pickers with the Dropdown selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Partition Scheme Dropdown
                Column(modifier = Modifier.weight(1.1f)) {
                    Text(
                        "PARTITION SCHEME",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PartitionSchemeDropdown(
                        selectedScheme = partitionScheme,
                        onSchemeSelected = onPartitionSchemeChange
                    )
                }

                // Target System
                Column(modifier = Modifier.weight(0.9f)) {
                    Text(
                        "TARGET SYSTEM",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                            Text(
                                text = targetSystem.name.replace("_", " "),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = targetSystem.label,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveFormatOptionsCard(
    volumeLabel: String,
    onVolumeLabelChange: (String) -> Unit,
    fileSystem: FileSystem,
    onFileSystemChange: (FileSystem) -> Unit,
    clusterSize: Int,
    onClusterSizeChange: (Int) -> Unit,
    quickFormat: Boolean,
    onQuickFormatToggle: (Boolean) -> Unit,
    checkBadBlocks: Boolean,
    onBadBlocksToggle: (Boolean) -> Unit,
    badBlockPasses: Int,
    onBadBlockPassesChange: (Int) -> Unit,
    detectFakeFlashDrives: Boolean,
    onFakeDriveToggle: (Boolean) -> Unit,
    verifyWrittenData: Boolean = true,
    onVerifyWrittenDataToggle: (Boolean) -> Unit = {}
) {
    var fsExpanded by remember { mutableStateOf(false) }
    var clusterExpanded by remember { mutableStateOf(false) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "FORMAT OPTIONS",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Primary Format Controls: Volume Label Input
            OutlinedTextField(
                value = volumeLabel,
                onValueChange = onVolumeLabelChange,
                label = { Text("Volume label", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("volume_label_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Format Controls: File System Dropdown (FAT, FAT32, NTFS, UDF, exFAT, ReFS, ext2, ext3, ext4)
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { fsExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("File system", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fileSystem.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("▼", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                DropdownMenu(
                    expanded = fsExpanded,
                    onDismissRequest = { fsExpanded = false }
                ) {
                    FileSystem.values().forEach { fs ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    fs.label,
                                    fontWeight = if (fs == fileSystem) FontWeight.Bold else FontWeight.Normal,
                                    color = if (fs == fileSystem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onFileSystemChange(fs)
                                fsExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progressive Disclosure: Advanced Format Options Accordion
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { isAdvancedExpanded = !isAdvancedExpanded }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Advanced Format Options",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isAdvancedExpanded) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${clusterSize / 1024} KB • ${if (quickFormat) "Quick" else "Full"}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Icon(
                                if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isAdvancedExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isAdvancedExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(10.dp))

                            // Cluster Size Dropdown
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { clusterExpanded = true }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Cluster size", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("Allocation unit size", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${clusterSize / 1024} KB (Default) ▼", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }

                                DropdownMenu(
                                    expanded = clusterExpanded,
                                    onDismissRequest = { clusterExpanded = false }
                                ) {
                                    listOf(4096, 8192, 16384, 32768, 65536).forEach { size ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${size} bytes (${size / 1024} KB)",
                                                    fontWeight = if (clusterSize == size) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (clusterSize == size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                onClusterSizeChange(size)
                                                clusterExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                            // Quick Format Checkbox
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onQuickFormatToggle(!quickFormat) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = quickFormat,
                                    onCheckedChange = { onQuickFormatToggle(it) },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Quick format", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("Skip zero-filling the entire partition table", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                            // Verify written data Checkbox
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onVerifyWrittenDataToggle(!verifyWrittenData) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = verifyWrittenData,
                                    onCheckedChange = { onVerifyWrittenDataToggle(it) },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Verify written data", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("Run bit-for-bit SHA-256 integrity verification after burn", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                            // Check bad blocks
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onBadBlocksToggle(!checkBadBlocks) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checkBadBlocks,
                                    onCheckedChange = { onBadBlocksToggle(it) },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Check device for bad blocks", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("Surface pattern test (${badBlockPasses} pass${if (badBlockPasses > 1) "es" else ""})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Bad block passes & Fake flash drive detection if bad blocks is on
                            if (checkBadBlocks) {
                                Column(modifier = Modifier.padding(start = 34.dp, top = 4.dp, bottom = 4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(1, 2, 3, 4).forEach { pass ->
                                            FilterChip(
                                                selected = badBlockPasses == pass,
                                                onClick = { onBadBlockPassesChange(pass) },
                                                label = { Text("$pass pass${if (pass > 1) "es" else ""}", fontSize = 11.sp) },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onFakeDriveToggle(!detectFakeFlashDrives) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = detectFakeFlashDrives,
                                            onCheckedChange = onFakeDriveToggle,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Detect 'fake' flash drives (True capacity test)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Circular Progress Indicator Component displaying real-time feedback during formatting and image writing
 */
@Composable
fun CircularWriteProgressGauge(
    percentage: Int,
    speedMbPerSec: Double,
    phaseText: String,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = percentage / 100f,
        animationSpec = tween(durationMillis = 120),
        label = "circularProgress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(100.dp)
    ) {
        // Background track
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF1E293B),
            strokeWidth = 8.dp,
            strokeCap = StrokeCap.Round
        )

        // Active Animated Progress
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = TerminalHeader,
            strokeWidth = 8.dp,
            strokeCap = StrokeCap.Round
        )

        // Centered Percentage and Speed
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$percentage%",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
            if (speedMbPerSec > 0.0) {
                Text(
                    text = "${String.format("%.1f", speedMbPerSec)} MB/s",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalSuccess,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = phaseText,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CircularAndLiveStatusCard(progress: WriteProgress) {
    val isWritingOrFormatting = progress is WriteProgress.Writing ||
                               progress is WriteProgress.Formatting ||
                               progress is WriteProgress.Partitioning ||
                               progress is WriteProgress.InstallingBootloader ||
                               progress is WriteProgress.Verifying

    val percentage = when (progress) {
        is WriteProgress.Writing -> progress.percentage
        is WriteProgress.Formatting -> progress.percentage
        is WriteProgress.Partitioning -> progress.percentage
        is WriteProgress.Verifying -> progress.percentage
        is WriteProgress.Completed -> 100
        else -> 0
    }

    val speed = when (progress) {
        is WriteProgress.Writing -> progress.speedMbPerSec
        is WriteProgress.Completed -> progress.averageSpeedMbPerSec
        else -> 0.0
    }

    val phaseName = when (progress) {
        is WriteProgress.Idle -> "READY"
        is WriteProgress.Analyzing -> "ANALYZING"
        is WriteProgress.Partitioning -> "PARTITIONING"
        is WriteProgress.Formatting -> "FORMATTING"
        is WriteProgress.Writing -> "WRITING"
        is WriteProgress.InstallingBootloader -> "BOOTLOADER"
        is WriteProgress.Verifying -> "VERIFYING"
        is WriteProgress.Completed -> "DONE"
        is WriteProgress.Error -> "ERROR"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TerminalBackground)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LIVE OPERATION STATUS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TerminalHeader)
                
                val statusColor = when (progress) {
                    is WriteProgress.Completed -> TerminalSuccess
                    is WriteProgress.Error -> TerminalError
                    is WriteProgress.Writing -> TerminalHeader
                    else -> Color.White
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = phaseName,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time Circular Progress Gauge during write/format operations
            if (isWritingOrFormatting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CircularWriteProgressGauge(
                        percentage = percentage,
                        speedMbPerSec = speed,
                        phaseText = phaseName,
                        modifier = Modifier.padding(end = 12.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val remainingSec = if (progress is WriteProgress.Writing) progress.remainingTimeSec else 0
                        val currentFile = if (progress is WriteProgress.Writing) progress.currentFile else ""

                        Text(
                            text = when (progress) {
                                is WriteProgress.Writing -> "Flashing image payload..."
                                is WriteProgress.Formatting -> "Creating filesystem structures..."
                                is WriteProgress.Partitioning -> "Writing partition tables..."
                                is WriteProgress.InstallingBootloader -> "Installing ${progress.bootloaderType}..."
                                is WriteProgress.Verifying -> "Verifying SHA-256 Checksum..."
                                else -> "Processing..."
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )

                        if (progress is WriteProgress.Writing) {
                            val remainingSec = progress.remainingTimeSec
                            val formattedTime = if (remainingSec >= 60) {
                                val mins = remainingSec / 60
                                val secs = remainingSec % 60
                                "${mins}m ${secs}s"
                            } else {
                                "${remainingSec}s"
                            }

                            val writtenMb = progress.bytesWritten / (1024 * 1024)
                            val totalMb = progress.totalBytes / (1024 * 1024)
                            val remainingMb = ((progress.totalBytes - progress.bytesWritten).coerceAtLeast(0L)) / (1024 * 1024)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = TerminalHeader.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "ETA: $formattedTime",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = TerminalHeader,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "$remainingMb MB remaining",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (currentFile.isNotEmpty()) {
                            Text(
                                text = "File: $currentFile",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            val logMessage = when (progress) {
                is WriteProgress.Idle -> "Ready to format and create bootable media."
                is WriteProgress.Analyzing -> progress.message
                is WriteProgress.Partitioning -> progress.message
                is WriteProgress.Formatting -> progress.message
                is WriteProgress.Writing -> {
                    val etaFormatted = if (progress.remainingTimeSec >= 60) "${progress.remainingTimeSec / 60}m ${progress.remainingTimeSec % 60}s" else "${progress.remainingTimeSec}s"
                    "Writing payload at ${String.format("%.1f", progress.speedMbPerSec)} MB/s • ETA: $etaFormatted"
                }
                is WriteProgress.InstallingBootloader -> "Installing ${progress.bootloaderType}..."
                is WriteProgress.Verifying -> progress.message
                is WriteProgress.Completed -> "SUCCESS: Bootable media created in ${progress.totalTimeSec}s (${String.format("%.1f", progress.averageSpeedMbPerSec)} MB/s)!"
                is WriteProgress.Error -> "Error: ${progress.message}"
            }

            Text(
                text = logMessage,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalText,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun ActionButtonsRow(
    isWriting: Boolean,
    isCompleted: Boolean,
    canStart: Boolean,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSelectIsoClick: () -> Unit = {},
    startButtonText: String = "START",
    flashingButtonText: String = "FLASHING...",
    startAgainButtonText: String = "START AGAIN"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dedicated ISO File Picker Button in the Main Action Area
        OutlinedButton(
            onClick = onSelectIsoClick,
            enabled = !isWriting,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .testTag("pick_iso_button"),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                color = if (!isWriting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = "Pick .ISO Disk Image",
                    tint = if (!isWriting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SELECT .ISO",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!isWriting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Primary START / Flash Button
        Button(
            onClick = onStartClick,
            enabled = !isWriting && canStart,
            modifier = Modifier
                .weight(1.2f)
                .height(56.dp)
                .testTag("start_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (isWriting) flashingButtonText else if (isCompleted) startAgainButtonText else startButtonText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Cancel Button during active flashing
        if (isWriting) {
            OutlinedButton(
                onClick = onCancelClick,
                enabled = isWriting,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("cancel_button"),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("✕", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RufusBottomNavigationBar(
    selectedTab: RufusTab,
    onTabSelected: (RufusTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RufusTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val icon = when (tab) {
                    RufusTab.FLASH -> Icons.Default.PlayArrow
                    RufusTab.DRIVES -> Icons.Default.Storage
                    RufusTab.IMAGES -> Icons.Default.Image
                    RufusTab.DOWNLOAD -> Icons.Default.CloudDownload
                    RufusTab.LOGS -> Icons.Default.Terminal
                    RufusTab.SETTINGS -> Icons.Default.Settings
                }
                RufusBottomNavItem(
                    label = tab.title,
                    icon = icon,
                    selected = isSelected,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
fun RufusBottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("tab_${label.lowercase()}")
    ) {
        val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        val iconColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

        Box(
            modifier = Modifier
                .background(backgroundColor, CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
