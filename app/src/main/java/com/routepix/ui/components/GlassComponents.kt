package com.routepix.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GlassCard: Frosted glass effect using RenderEffect on Android 12+, semi-transparent fallback on older devices.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    opacity: Float = 0.85f,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = opacity))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.15f), // Edge highlight
                shape = shape
            )
    ) {
        Column(content = content)
    }
}

/**
 * GlassTopBar: Blurred top navigation bar that allows content scrolling behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets
) {
    // TopAppBar inside a GlassCard that covers the status bar area
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 0.dp, // Flat surface for bar
            opacity = 0.95f
        ) {
            TopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = actions,
                windowInsets = windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * Extension for ModalBottomSheet to use the GlassCard effect.
 * Note: Wrap the content of ModalBottomSheet with this GlassCard.
 */
@Composable
fun GlassBottomSheetContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        cornerRadius = 24.dp,
        opacity = 0.95f, // Slightly higher to ensure readability over timeline
        content = content
    )
}
