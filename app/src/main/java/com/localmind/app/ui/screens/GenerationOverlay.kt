package com.localmind.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.localmind.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GenerationOverlay(
    isVisible: Boolean,
    tokensPerSecond: Float = 0f,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeonBackground.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                // Background animated grid or particles could go here

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Central HUD Visualizer
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(240.dp)
                    ) {
                        HudSpinner()
                        BrainCorePulse()
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        "NEURAL GENERATION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = NeonPrimary,
                        letterSpacing = 3.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Metrics Display
                    if (tokensPerSecond > 0) {
                        MetricBadge(tokensPerSecond)
                    } else {
                        Text(
                            "INITIALIZING ENGINE...",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonTextSecondary,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(64.dp))

                    // Background Run Button
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonTextSecondary,
                            containerColor = NeonElevated.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("RUN IN BACKGROUND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HudSpinner() {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_spinner")
    val primaryColor = NeonPrimary
    val accentColor = NeonAccent

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 4.dp.toPx()

        // Outer ring segments
        drawArc(
            color = primaryColor,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            alpha = 0.5f
        )

        drawArc(
            color = primaryColor,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            alpha = 0.5f
        )
    }

    Canvas(modifier = Modifier.fillMaxSize().rotate(-rotation * 1.5f)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.minDimension / 2) * 0.85f

        drawCircle(
            color = accentColor.copy(alpha = 0.1f),
            radius = radius,
            style = Stroke(width = 1.dp.toPx())
        )

        // Inner tick marks
        for (i in 0 until 12) {
            val angle = (i * 30f) * (Math.PI / 180f)
            val startX = center.x + cos(angle).toFloat() * (radius - 10f)
            val startY = center.y + sin(angle).toFloat() * (radius - 10f)
            val endX = center.x + cos(angle).toFloat() * radius
            val endY = center.y + sin(angle).toFloat() * radius
            drawLine(
                color = accentColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2.dp.toPx(),
                alpha = 0.6f
            )
        }
    }
}

@Composable
private fun BrainCorePulse() {
    val infiniteTransition = rememberInfiniteTransition(label = "brain_pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp)
            .scale(scale)
    ) {
        // Glow layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha * 0.5f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NeonPrimary, Color.Transparent),
                        center = Offset.Unspecified,
                        radius = 150f
                    )
                )
        )

        // Icon
        Text(
            text = "🧠",
            fontSize = 48.sp,
            modifier = Modifier.alpha(alpha)
        )
    }
}

@Composable
private fun MetricBadge(tps: Float) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NeonPrimary.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPrimary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format("%.1f", tps),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = NeonText,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "T/S",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = NeonPrimary
            )
        }
    }
}
