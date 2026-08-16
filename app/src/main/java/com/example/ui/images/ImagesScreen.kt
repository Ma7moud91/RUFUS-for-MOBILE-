package com.example.ui.images

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.models.ImageFile
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.dashboard.RufusTab
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalError
import com.example.ui.theme.TerminalSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesScreen(
    viewModel: DashboardViewModel,
    onSelectImageClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header & Browse button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DISK IMAGES & CHECKSUMS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "MD5, SHA-1, SHA-256, SHA-512 and ISO catalog",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { viewModel.selectTab(RufusTab.DOWNLOAD) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FETCH ISO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSelectImageClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("browse_images_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BROWSE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Active Selection Card with Hashes
        val currentImage = uiState.selectedImage
        if (currentImage != null) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURRENTLY LOADED IMAGE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentImage.osDetection ?: "Custom Disk Image",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${currentImage.fileName} (${currentImage.sizeFormatted})",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Checksums Grid
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(TerminalBackground)
                            .padding(12.dp)
                    ) {
                        if (uiState.isCalculatingHash) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = TerminalSuccess)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Computing checksum digests (MD5, SHA-1, SHA-256, SHA-512)...", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                            }
                        } else {
                            val chk = uiState.checksumResult
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                HashRow(label = "MD5", value = chk?.md5?.ifEmpty { currentImage.hashMd5 } ?: "Not computed")
                                HashRow(label = "SHA-1", value = chk?.sha1?.ifEmpty { currentImage.hashSha1 } ?: "Not computed")
                                HashRow(label = "SHA-256", value = chk?.sha256?.ifEmpty { currentImage.hashSha256 } ?: "Not computed")
                                HashRow(label = "SHA-512", value = chk?.sha512?.ifEmpty { currentImage.hashSha512 } ?: "Not computed")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Hash Verifier Input
                    OutlinedTextField(
                        value = uiState.hashVerifyQuery,
                        onValueChange = { viewModel.verifyHashMatch(it) },
                        placeholder = { Text("Paste expected hash to verify match...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("hash_verify_input"),
                        trailingIcon = {
                            if (uiState.isHashMatching == true) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Matched", tint = TerminalSuccess)
                            } else if (uiState.isHashMatching == false) {
                                Icon(Icons.Default.Done, contentDescription = "Mismatch", tint = TerminalError)
                            }
                        }
                    )

                    if (uiState.isHashMatching == true) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "✅ Verified: Hash matches official image checksum!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalSuccess
                        )
                    } else if (uiState.isHashMatching == false) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "❌ Checksum mismatch: Please verify image source integrity.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalError
                        )
                    }
                }
            }
        }

        Text(
            text = "POPULAR OS IMAGES & PRESETS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )

        // Preset List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {

            items(ImageFile.PRESETS) { preset ->
                val isSelected = uiState.selectedImage?.fileName == preset.fileName
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectPresetImage(preset) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.osDetection ?: preset.fileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${preset.fileName} • ${preset.sizeFormatted} • ${preset.architecture}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HashRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (value.length > 28) "${value.take(14)}...${value.takeLast(10)}" else value,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = TerminalSuccess,
            maxLines = 1
        )
    }
}
