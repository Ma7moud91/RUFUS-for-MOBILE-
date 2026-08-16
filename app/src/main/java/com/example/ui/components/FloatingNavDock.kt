package com.example.ui.components

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
 * Modern Glassmorphic Floating Navigation Dock with Dynamic Blur, Specular Highlights,
 * and Silky-Smooth Spring Physics Transitions.
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
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Frosted Glass Outer Wrapper
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(34.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    ambientColor = Color.Black.copy(alpha = 0.28f)
                )
                .clip(RoundedCornerShape(34.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
                        )
                    ),
                    shape = RoundedCornerShape(34.dp)
                )
        ) {
            // Glass Reflection Sheen Highlight (Top half)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(34.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Inner Tabs Row
            Row(
                modifier = Modifier
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                localizedTabs.forEach { item ->
                    val isSelected = currentTab == item.tab

                    // Smooth animated scale for icon
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "iconScale"
                    )

                    // Smooth animated vertical translation offset
                    val iconOffsetY by animateDpAsState(
                        targetValue = if (isSelected) (-1.5).dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "iconOffset"
                    )

                    // Smooth background color transition
                    val animatedPillColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "pillColor"
                    )

                    // Smooth content color transition
                    val animatedContentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "contentColor"
                    )

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = animatedPillColor,
                        shadowElevation = if (isSelected) 4.dp else 0.dp,
                        border = if (isSelected) {
                            CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.6f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    )
                                ),
                                width = 1.dp
                            )
                        } else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .bounceClick(scaleDown = 0.93f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabSelected(item.tab)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                .padding(
                                    horizontal = if (isSelected) 14.dp else 10.dp,
                                    vertical = 10.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = animatedContentColor,
                                modifier = Modifier
                                    .size(21.dp)
                                    .offset(y = iconOffsetY)
                                    .scale(iconScale)
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(animationSpec = tween(180)) + expandHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    expandFrom = Alignment.Start
                                ),
                                exit = fadeOut(animationSpec = tween(120)) + shrinkHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    shrinkTowards = Alignment.Start
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.label,
                                        color = animatedContentColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
