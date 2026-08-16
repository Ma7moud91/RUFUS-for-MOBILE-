package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.models.FileSystem
import com.example.domain.models.PartitionScheme
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.theme.AccentPresets

@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = uiState.strings.appSettings,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Language Selector Card (38 Native Languages)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.openLanguageDialog() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(uiState.strings.appLanguage, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "${uiState.currentLanguage.nativeName} (${uiState.currentLanguage.englishName})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text("CHANGE >", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        // General / Appearance
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "APPEARANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(uiState.strings.darkMode, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Switch between dark slate and light theme", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = uiState.isDarkMode,
                        onCheckedChange = { viewModel.toggleDarkMode(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                Text("Accent Color Override", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("Select a custom primary accent color that persists across sessions regardless of system wallpaper.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AccentPresets.forEach { preset ->
                        val isSelected = uiState.accentColorOverride == preset.value
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setAccentColorOverride(preset.value) },
                            label = { Text(preset.name, fontSize = 11.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(preset.color)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        // Flashing Defaults
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "FORMAT & PARTITION PREFERENCES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Default Partition Scheme", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = uiState.partitionScheme == PartitionScheme.GPT,
                        onClick = { viewModel.setPartitionScheme(PartitionScheme.GPT) },
                        label = { Text("GPT (Modern UEFI)") },
                        shape = RoundedCornerShape(10.dp)
                    )
                    FilterChip(
                        selected = uiState.partitionScheme == PartitionScheme.MBR,
                        onClick = { viewModel.setPartitionScheme(PartitionScheme.MBR) },
                        label = { Text("MBR (Legacy BIOS)") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Verification Toggle in Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Verify Written Data After Burn", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Read back written media sectors and calculate SHA-256 hash to ensure 100% image integrity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = uiState.verifyWrittenData,
                        onCheckedChange = { viewModel.toggleVerifyWrittenData(it) }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Text("Supported File Systems (9 Formats)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(FileSystem.FAT, FileSystem.FAT32, FileSystem.NTFS, FileSystem.EXFAT).forEach { fs ->
                        FilterChip(
                            selected = uiState.fileSystem == fs,
                            onClick = { viewModel.setFileSystem(fs) },
                            label = { Text(fs.name, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(FileSystem.UDF, FileSystem.REFS, FileSystem.EXT2, FileSystem.EXT3, FileSystem.EXT4).forEach { fs ->
                        FilterChip(
                            selected = uiState.fileSystem == fs,
                            onClick = { viewModel.setFileSystem(fs) },
                            label = { Text(fs.name, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        // About Rufus
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(64.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.rufus_premium_icon_1786884298007),
                            contentDescription = "Rufus Premium Logo",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "ABOUT ANDROID RUFUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text("Android Rufus v4.5 (Mobile Edition)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Format USB, flash card and virtual drives to FAT/FAT32/NTFS/UDF/exFAT/ReFS/ext2/ext3/ext4. Create FreeDOS, MS-DOS, UEFI Shell, Windows To Go, and bootable Linux/Windows ISO drives with TPM/Secure Boot bypass & OOBE automation.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Portable • Small footprint • Secure Boot compatible • 38 languages", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(88.dp))
    }
}

