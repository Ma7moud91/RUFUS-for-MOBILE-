package com.example.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.bounceClick

data class AppTipItem(
    val step: Int,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val targetTag: String
)

val APP_TIPS = listOf(
    AppTipItem(
        step = 1,
        title = "Target USB Drive Selector",
        description = "Connect your OTG USB Flash Drive or SSD via USB-C. Tap this card to select the target drive for partition and formatting.",
        icon = Icons.Default.Usb,
        targetTag = "device_selection_card"
    ),
    AppTipItem(
        step = 2,
        title = "Boot Selection & Image Picker",
        description = "Select your bootable ISO/IMG file (Windows, Linux, or Rescue Disk), or choose FreeDOS, MS-DOS, or UEFI Shell.",
        icon = Icons.Default.DiscFull,
        targetTag = "boot_selection_card"
    ),
    AppTipItem(
        step = 3,
        title = "Partition Scheme (GPT vs MBR)",
        description = "Choose GPT for modern UEFI PCs (required for Windows 11), or MBR for legacy BIOS / CSM compatibility.",
        icon = Icons.Default.PieChart,
        targetTag = "partition_scheme_dropdown"
    ),
    AppTipItem(
        step = 4,
        title = "Windows 11 Customization",
        description = "When a Windows ISO is verified, configure automatic bypasses for TPM 2.0, Secure Boot, RAM checks, and offline local accounts.",
        icon = Icons.Default.Build,
        targetTag = "windows_experience_card"
    ),
    AppTipItem(
        step = 5,
        title = "Formatting & File System Options",
        description = "Set Volume Label, File System (FAT32/NTFS/exFAT), cluster size, and perform surface bad block verification.",
        icon = Icons.Default.FolderZip,
        targetTag = "format_options_card"
    ),
    AppTipItem(
        step = 6,
        title = "Hash & Integrity Verification",
        description = "Verify SHA-256, SHA-512, and MD5 checksums of your ISO against official release hashes before flashing.",
        icon = Icons.Default.VerifiedUser,
        targetTag = "checksum_verification"
    ),
    AppTipItem(
        step = 7,
        title = "START Button & Floating Dock",
        description = "Tap START to safely partition and flash your bootable USB drive. Use the floating bottom dock to access the ISO Download Hub and Logs!",
        icon = Icons.Default.FlashOn,
        targetTag = "start_button"
    )
)

/**
 * Dynamic Interactive Onboarding & Feature Spotlight Walkthrough.
 */
@Composable
fun DynamicTipsOverlay(
    currentStep: Int,
    onStepChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val tip = APP_TIPS.getOrNull(currentStep - 1) ?: APP_TIPS.first()
    val isLast = currentStep >= APP_TIPS.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume clicks to prevent background clicks */ },
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                // Header Row (Step Badge + Skip)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "STEP ${tip.step} OF ${APP_TIPS.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.bounceClick()
                    ) {
                        Text("SKIP TIPS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icon & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = tip.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = tip.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Interactive Feature Guide",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Description
                Text(
                    text = tip.description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Action Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { onStepChange(currentStep - 1) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BACK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Button(
                        onClick = {
                            if (isLast) {
                                onDismiss()
                            } else {
                                onStepChange(currentStep + 1)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.bounceClick()
                    ) {
                        Text(
                            text = if (isLast) "GOT IT! START APP" else "NEXT TIP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isLast) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
