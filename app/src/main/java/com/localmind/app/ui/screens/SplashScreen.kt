package com.localmind.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmind.app.R
import com.localmind.app.ui.theme.NeonPrimary
import com.localmind.app.ui.theme.NeonPrimaryVariant
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {

    var phase by remember { mutableStateOf(0) }

    // === Phase-based animations ===

    // Icon scale: bounces in from 0
    val iconScale by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconScale"
    )

    // Icon alpha: fades in
    val iconAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(500),
        label = "iconAlpha"
    )

    // Outer ring scale & alpha
    val ring1Scale by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0.1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "ring1"
    )
    val ring1Alpha by animateFloatAsState(
        targetValue = if (phase >= 1) 0.5f else 0f,
        animationSpec = tween(500),
        label = "ring1a"
    )

    // Inner sweep ring
    val ring2Scale by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0.1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "ring2"
    )
    val ring2Alpha by animateFloatAsState(
        targetValue = if (phase >= 1) 0.25f else 0f,
        animationSpec = tween(700, delayMillis = 100),
        label = "ring2a"
    )

    // Particle dots alpha
    val particlesAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 0.7f else 0f,
        animationSpec = tween(800, delayMillis = 200),
        label = "particlesAlpha"
    )

    // Tagline
    val taglineAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(600, delayMillis = 300),
        label = "taglineAlpha"
    )
    val taglineOffsetY by animateFloatAsState(
        targetValue = if (phase >= 2) 0f else 24f,
        animationSpec = tween(600, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "taglineY"
    )

    // Screen fade out
    val screenAlpha by animateFloatAsState(
        targetValue = if (phase >= 4) 0f else 1f,
        animationSpec = tween(400),
        label = "screenAlpha"
    )

    // === Infinite transition animations ===
    val inf = rememberInfiniteTransition(label = "inf")

    // Glow pulse for the icon
    val glowPulse by inf.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Ring rotation
    val ringRotation by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(6000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "ringRot"
    )

    // Particle orbit rotation
    val particleRotation by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "particleRot"
    )

    // Secondary particle orbit (slower, opposite)
    val particleRotation2 by inf.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(7000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "particleRot2"
    )

    // Shimmer effect on icon
    val shimmerOffset by inf.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            tween(2500, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val shimmerBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            neonPrimary.copy(alpha = 0f),
            neonPrimary.copy(alpha = 0.25f * glowPulse),
            neonPrimary.copy(alpha = 0f)
        ),
        start = androidx.compose.ui.geometry.Offset(shimmerOffset * 300f, 0f),
        end = androidx.compose.ui.geometry.Offset(shimmerOffset * 300f + 200f, 200f)
    )

    // Phase sequencing
    LaunchedEffect(Unit) {
        delay(80)
        phase = 1   // Icon + rings appear
        delay(400)
        phase = 2   // Particles + tagline appear
        delay(1400)
        phase = 4   // Fade out
        delay(430)
        onSplashFinished()
    }

    // Capture Color values OUTSIDE Canvas lambdas
    val neonPrimary = NeonPrimary
    val neonPrimaryVariant = NeonPrimaryVariant
    val isDark = isSystemInDarkTheme()
    val appNameColor = if (isDark) Color.White else Color(0xFF0F172A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha),
        // NO background - fully transparent!
        contentAlignment = Alignment.Center
    ) {

        // === Outer rotating dashed ring ===
        Canvas(
            modifier = Modifier
                .size(280.dp)
                .scale(ring1Scale)
                .alpha(ring1Alpha)
                .graphicsLayer { rotationZ = ringRotation }
        ) {
            val r = size.minDimension / 2f - 4.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val circumference = 2f * Math.PI.toFloat() * r
            val dashLen = 18f
            val gapLen = 10f
            val count = (circumference / (dashLen + gapLen)).toInt()
            val ringColor = neonPrimary
            repeat(count) { i ->
                val startAngle = i * (360f / count)
                drawArc(
                    color = ringColor,
                    startAngle = startAngle,
                    sweepAngle = dashLen / circumference * 360f,
                    useCenter = false,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2, r * 2)
                )
            }
        }

        // === Mid gradient sweep ring ===
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .scale(ring2Scale)
                .alpha(ring2Alpha)
                .graphicsLayer { rotationZ = -ringRotation * 0.6f }
        ) {
            val r = size.minDimension / 2f - 3.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        neonPrimary.copy(alpha = 0f),
                        neonPrimary.copy(alpha = 0.9f),
                        neonPrimaryVariant.copy(alpha = 0.6f),
                        neonPrimary.copy(alpha = 0f)
                    ),
                    center = Offset(cx, cy)
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2)
            )
        }

        // === Orbiting particle dots (inner orbit) ===
        Canvas(
            modifier = Modifier
                .size(200.dp)
                .alpha(particlesAlpha)
                .graphicsLayer { rotationZ = particleRotation }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val orbitR = size.minDimension / 2f - 8.dp.toPx()
            val particleCount = 8
            repeat(particleCount) { i ->
                val angle = (i * 360f / particleCount) * (Math.PI.toFloat() / 180f)
                val px = cx + orbitR * cos(angle)
                val py = cy + orbitR * sin(angle)
                val particleAlpha = 0.3f + 0.7f * ((i % 3) / 2f)
                val particleSize = if (i % 2 == 0) 3.dp.toPx() else 2.dp.toPx()
                drawCircle(
                    color = neonPrimary.copy(alpha = particleAlpha),
                    radius = particleSize,
                    center = Offset(px, py)
                )
            }
        }

        // === Orbiting particle dots (outer orbit, slower, opposite direction) ===
        Canvas(
            modifier = Modifier
                .size(310.dp)
                .alpha(particlesAlpha * 0.5f)
                .graphicsLayer { rotationZ = particleRotation2 }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val orbitR = size.minDimension / 2f - 6.dp.toPx()
            val particleCount = 12
            repeat(particleCount) { i ->
                val angle = (i * 360f / particleCount) * (Math.PI.toFloat() / 180f)
                val px = cx + orbitR * cos(angle)
                val py = cy + orbitR * sin(angle)
                val particleAlpha = 0.15f + 0.35f * ((i % 4) / 3f)
                drawCircle(
                    color = neonPrimaryVariant.copy(alpha = particleAlpha),
                    radius = 1.5.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // === App Icon (Brain logo) + App Name ===
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(iconScale)
                .alpha(iconAlpha)
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(glowPulse)
                    .graphicsLayer {
                        shadowElevation = if (phase >= 1) 24f else 0f
                        ambientShadowColor = neonPrimary.copy(alpha = 0.4f)
                        spotShadowColor = neonPrimary.copy(alpha = 0.6f)
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "Local Mind Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                // Shimmer overlay on icon
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(brush = shimmerBrush)
                }
            }
            // App name — dark mode: white, light mode: dark
            Text(
                text = "LocalMind",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = appNameColor,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
        }

        // === Tagline at bottom ===
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp)
                .offset(y = taglineOffsetY.dp)
                .alpha(taglineAlpha)
        ) {
            Text(
                text = "Private AI · On-Device",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = neonPrimary.copy(alpha = 0.75f),
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
