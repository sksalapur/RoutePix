@file:OptIn(ExperimentalMaterial3Api::class)

package com.routepix.ui.timeline

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Premium colour palettes for charts ──────────────────────────────────────
private val pieColors = listOf(
    Color(0xFF6366F1), // indigo
    Color(0xFFF472B6), // pink
    Color(0xFF34D399), // emerald
    Color(0xFFFBBF24), // amber
    Color(0xFF60A5FA), // blue
    Color(0xFFA78BFA), // violet
    Color(0xFFF87171), // red
    Color(0xFF2DD4BF), // teal
    Color(0xFFE879F9), // fuchsia
    Color(0xFF4ADE80), // green
)

private val barGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF818CF8), Color(0xFF6366F1))
)

@Composable
fun TripAnalyticsScreen(
    viewModel: TimelineViewModel,
    totalPhotoCount: Int,
    onClose: () -> Unit
) {
    val leaderboard by viewModel.leaderboard.collectAsState()
    val sceneBreakdown by viewModel.sceneBreakdown.collectAsState()
    val activity by viewModel.timelineActivity.collectAsState()

    // Entry animation
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Summary card ────────────────────────────────────────────────────
            SummaryCard(
                totalPhotos = totalPhotoCount,
                contributors = leaderboard.size,
                topScene = sceneBreakdown.firstOrNull()?.label ?: "—",
                animProgress = animProgress.value
            )

            // ── Leaderboard bar chart ───────────────────────────────────────────
            if (leaderboard.isNotEmpty()) {
                AnalyticsSectionCard(
                    icon = { Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFBBF24)) },
                    title = "Uploader Leaderboard"
                ) {
                    LeaderboardBarChart(
                        entries = leaderboard,
                        animProgress = animProgress.value
                    )
                }
            }

            // ── Scene pie chart ─────────────────────────────────────────────────
            if (sceneBreakdown.isNotEmpty()) {
                AnalyticsSectionCard(
                    icon = { Icon(Icons.Default.Landscape, null, tint = Color(0xFF34D399)) },
                    title = "Scene Breakdown"
                ) {
                    ScenePieChart(
                        entries = sceneBreakdown,
                        animProgress = animProgress.value
                    )
                }
            }

            // ── Timeline activity ───────────────────────────────────────────────
            if (activity.isNotEmpty()) {
                AnalyticsSectionCard(
                    icon = { Icon(Icons.Default.Timeline, null, tint = Color(0xFF60A5FA)) },
                    title = "Upload Activity",
                    subtitle = "Number of uploads per day"
                ) {
                    ActivityBarChart(
                        entries = activity,
                        animProgress = animProgress.value
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Summary Card ────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    totalPhotos: Int,
    contributors: Int,
    topScene: String,
    animProgress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatPill(
                value = "${(totalPhotos * animProgress).toInt()}",
                label = "Photos",
                color = Color(0xFF6366F1)
            )
            StatPill(
                value = "${(contributors * animProgress).toInt()}",
                label = "Contributors",
                color = Color(0xFFF472B6)
            )
            StatPill(
                value = topScene,
                label = "Top Scene",
                color = Color(0xFF34D399)
            )
        }
    }
}

@Composable
private fun StatPill(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Section Card wrapper ────────────────────────────────────────────────────

@Composable
private fun AnalyticsSectionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon()
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

// ── Leaderboard Bar Chart ───────────────────────────────────────────────────

@Composable
private fun LeaderboardBarChart(
    entries: List<TimelineViewModel.LeaderboardEntry>,
    animProgress: Float
) {
    val maxCount = entries.maxOfOrNull { it.count } ?: 1

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEachIndexed { index, entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Rank badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            when (index) {
                                0 -> Color(0xFFFBBF24)
                                1 -> Color(0xFFC0C0C0)
                                2 -> Color(0xFFCD7F32)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (index < 3) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Name
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(80.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Bar
                Box(modifier = Modifier.weight(1f).height(24.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barWidth = size.width * (entry.count.toFloat() / maxCount) * animProgress
                        drawRoundRect(
                            brush = barGradient,
                            topLeft = Offset.Zero,
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(12.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Count
                Text(
                    text = "${(entry.count * animProgress).toInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Scene Pie Chart ─────────────────────────────────────────────────────────

@Composable
private fun ScenePieChart(
    entries: List<TimelineViewModel.SceneEntry>,
    animProgress: Float
) {
    val total = entries.sumOf { it.count }.toFloat()
    if (total == 0f) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pie canvas
        Canvas(
            modifier = Modifier
                .size(160.dp)
                .padding(8.dp)
        ) {
            var startAngle = -90f
            entries.forEachIndexed { index, entry ->
                val sweep = (entry.count / total) * 360f * animProgress
                drawArc(
                    color = pieColors[index % pieColors.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 32.dp.toPx(), cap = StrokeCap.Butt),
                    topLeft = Offset(16.dp.toPx(), 16.dp.toPx()),
                    size = Size(size.width - 32.dp.toPx(), size.height - 32.dp.toPx())
                )
                startAngle += sweep
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Legend
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            entries.take(8).forEachIndexed { index, entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(pieColors[index % pieColors.size])
                    )
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${entry.count}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Activity Bar Chart (Vertical) ───────────────────────────────────────────

@Composable
private fun ActivityBarChart(
    entries: List<TimelineViewModel.ActivityEntry>,
    animProgress: Float
) {
    val maxCount = entries.maxOfOrNull { it.count } ?: 1
    val displayEntries = entries.takeLast(14) // last 14 days

    val yAxisWidth = 32.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        // Y-axis label
        Box(
            modifier = Modifier
                .width(yAxisWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Uploads ↑",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color.Gray,
                modifier = Modifier
                    .graphicsLayer { rotationZ = -90f }
            )
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val barCount = displayEntries.size
            if (barCount == 0) return@Canvas

            val barSpacing = 4.dp.toPx()
            val totalSpacing = barSpacing * (barCount - 1)
            val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(4.dp.toPx())
            val chartHeight = size.height - 24.dp.toPx()

            displayEntries.forEachIndexed { index, entry ->
                val x = index * (barWidth + barSpacing)
                val barHeight = (entry.count.toFloat() / maxCount) * chartHeight * animProgress

                // Bar
                drawRoundRect(
                    color = Color(0xFF6366F1).copy(alpha = 0.7f + 0.3f * (entry.count.toFloat() / maxCount)),
                    topLeft = Offset(x, chartHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // Date label
                if (barCount <= 7 || index % 2 == 0) {
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 9.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        drawText(
                            entry.date,
                            x + barWidth / 2,
                            size.height,
                            paint
                        )
                    }
                }

                // Count on top of bar
                if (barHeight > 16.dp.toPx()) {
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 10.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                            isAntiAlias = true
                        }
                        drawText(
                            "${entry.count}",
                            x + barWidth / 2,
                            chartHeight - barHeight + 14.dp.toPx(),
                            paint
                        )
                    }
                }
            }
        }
    }

    // X-axis label
    Text(
        text = "Date →",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = Color.Gray,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, top = 2.dp)
    )
}
