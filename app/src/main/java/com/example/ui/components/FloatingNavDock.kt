package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.RufusTab
import com.example.util.bounceClick

data class DockTabItem(
    val tab: RufusTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val DOCK_TABS = listOf(
    DockTabItem(RufusTab.FLASH, "Flash", Icons.Filled.FlashOn, Icons.Outlined.FlashOn),
    DockTabItem(RufusTab.DOWNLOAD, "Download", Icons.Filled.CloudDownload, Icons.Outlined.CloudDownload),
    DockTabItem(RufusTab.DRIVES, "Drives", Icons.Filled.Storage, Icons.Outlined.Storage),
    DockTabItem(RufusTab.LOGS, "Logs", Icons.Filled.Terminal, Icons.Outlined.Terminal),
    DockTabItem(RufusTab.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

/**
 * Modern Google Material 3 Expressive Floating Navigation Dock.
 * Features glassmorphic tonal blur, spring physics pill transitions, and haptic feedback.
 */
@Composable
fun FloatingNavDock(
    currentTab: RufusTab,
    onTabSelected: (RufusTab) -> Unit,
    modifier: Modifier = Modifier,
    translations: com.example.domain.models.AppTranslations? = null
) {
    val haptic = LocalHapticFeedback.current

    val t = translations ?: com.example.domain.models.RufusStrings.get("en")
    val localizedTabs = remember(t) {
        listOf(
            DockTabItem(RufusTab.FLASH, t.flashTab, Icons.Filled.FlashOn, Icons.Outlined.FlashOn),
            DockTabItem(RufusTab.DOWNLOAD, t.downloadTab, Icons.Filled.CloudDownload, Icons.Outlined.CloudDownload),
            DockTabItem(RufusTab.DRIVES, t.drivesTab, Icons.Filled.Storage, Icons.Outlined.Storage),
            DockTabItem(RufusTab.LOGS, t.logsTab, Icons.Filled.Terminal, Icons.Outlined.Terminal),
            DockTabItem(RufusTab.SETTINGS, t.settingsTab, Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            shadowElevation = 12.dp,
            tonalElevation = 6.dp,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                )
            ),
            modifier = Modifier.wrapContentWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                localizedTabs.forEach { item ->
                    val isSelected = currentTab == item.tab

                    val animatedPillColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "pillColor"
                    )

                    val animatedContentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "contentColor"
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = animatedPillColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .bounceClick(scaleDown = 0.90f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabSelected(item.tab)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = if (isSelected) 14.dp else 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = animatedContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.label,
                                    color = animatedContentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
