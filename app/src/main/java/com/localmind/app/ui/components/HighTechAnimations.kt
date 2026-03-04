package com.localmind.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmind.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * A reusable pulsing core animation effectively simulating a Lottie-style "breathing" effect.
 */
@Composable
fun PulsingCore(
    modifier: Modifier = Modifier,
    color: Color = NeonPrimary,
    glowColor: Color = NeonAccent,
    initialScale: Float = 0.8f,
    targetScale: Float = 1.2f,
    durationMillis: Int = 1500
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_core")

    val scale by infiniteTransition.animateFloat(
        initialValue = initialScale,
        targetValue = targetScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_alpha"
    )

    Box(
        modifier = modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Glow layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha * 0.6f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = Offset.Unspecified,
                        radius = 300f
                    )
                )
        )

        // Inner Core
        Box(
            modifier = Modifier
                .fillMaxSize(0.6f)
                .alpha(alpha)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * A high-tech rotating scanner animation.
 */
@Composable
fun RotatingScanner(
    modifier: Modifier = Modifier,
    color: Color = NeonPrimary,
    durationMillis: Int = 4000
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.rotate(rotation)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 4.dp.toPx()

        // Outer arcs
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            alpha = 0.8f
        )

        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            alpha = 0.8f
        )
    }
}

/**
 * Typewriter effect for text.
 */
@Composable
fun TypingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = NeonText,
    cursorColor: Color = NeonPrimary,
    typingSpeedMillis: Long = 50
) {
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        displayedText = ""
        text.forEach { char ->
            displayedText += char
            delay(typingSpeedMillis)
        }
    }

    val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = displayedText, style = style, color = color)
        if (displayedText.length < text.length || true) { // Always show cursor blinking
             Box(
                modifier = Modifier
                    .width(style.fontSize.value.dp / 4) // Approximate cursor width
                    .height(style.fontSize.value.dp)
                    .alpha(cursorAlpha)
                    .background(cursorColor)
            )
        }
    }
}

/**
 * A loading indicator with orbiting dots.
 */
@Composable
fun OrbitingLoader(
    modifier: Modifier = Modifier,
    color: Color = NeonPrimary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbit")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "orbit_rotation"
    )

    Box(modifier = modifier.rotate(rotation)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 3

            drawCircle(
                color = color.copy(alpha = 0.2f),
                radius = radius,
                style = Stroke(width = 1.dp.toPx())
            )

            val satelliteCount = 3
            for (i in 0 until satelliteCount) {
                val angle = (i * 360f / satelliteCount) * (Math.PI / 180f)
                val x = center.x + cos(angle).toFloat() * radius
                val y = center.y + sin(angle).toFloat() * radius

                drawCircle(
                    color = color,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}
