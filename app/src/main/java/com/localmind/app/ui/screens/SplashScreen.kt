package com.localmind.app.ui.screens

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import com.localmind.app.ui.theme.NeonText
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {

    var phase by remember { mutableStateOf(0) }

    // Phase-based animations
    val iconScale by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "iconScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(500),
        label = "iconAlpha"
    )
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
    val particlesAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 0.7f else 0f,
        animationSpec = tween(800, delayMillis = 200),
        label = "particlesAlpha"
    )
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
    val screenAlpha by animateFloatAsState(
        targetValue = if (phase >= 4) 0f else 1f,
        animationSpec = tween(400),
        label = "screenAlpha"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(600, delayMillis = 150),
        label = "titleAlpha"
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (phase >= 2) 0f else 16f,
        animationSpec = tween(600, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "titleY"
    )

    // Infinite animations
    val inf = rememberInfiniteTransition(label = "inf")

    val glowPulse by inf.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val ringRotation by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "ringRot"
    )
    val particleRotation by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "particleRot"
    )
    val particleRotation2 by inf.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "particleRot2"
    )
    val shimmerOffset by inf.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    // 3D rotation — subtle slow Y-axis wobble for depth
    val rotY by inf.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotY"
    )
    val rotX by inf.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotX"
    )

    // Phase sequencing
    LaunchedEffect(Unit) {
        delay(80)
        phase = 1
        delay(400)
        phase = 2
        delay(1400)
        phase = 4
        delay(430)
        onSplashFinished()
    }

    val neonPrimary = NeonPrimary
    val neonPrimaryVariant = NeonPrimaryVariant

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            neonPrimary.copy(alpha = 0f),
            neonPrimary.copy(alpha = 0.18f * glowPulse),
            neonPrimary.copy(alpha = 0f)
        ),
        start = Offset(shimmerOffset * 300f, 0f),
        end = Offset(shimmerOffset * 300f + 200f, 200f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha),
        contentAlignment = Alignment.Center
    ) {

        // Outer rotating dashed ring
        Canvas(
            modifier = Modifier
                .size(300.dp)
                .graphicsLayer {
                    scaleX = ring1Scale; scaleY = ring1Scale
                    alpha = ring1Alpha
                    rotationZ = ringRotation
                }
        ) {
            val r = size.minDimension / 2f - 4.dp.toPx()
            val cx = size.width / 2f; val cy = size.height / 2f
            val circumference = 2f * Math.PI.toFloat() * r
            val dashLen = 18f; val gapLen = 10f
            val count = (circumference / (dashLen + gapLen)).toInt()
            repeat(count) { i ->
                val startAngle = i * (360f / count)
                drawArc(
                    color = neonPrimary,
                    startAngle = startAngle,
                    sweepAngle = dashLen / circumference * 360f,
                    useCenter = false,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2, r * 2)
                )
            }
        }

        // Mid gradient sweep ring
        Canvas(
            modifier = Modifier
                .size(260.dp)
                .graphicsLayer {
                    scaleX = ring2Scale; scaleY = ring2Scale
                    alpha = ring2Alpha
                    rotationZ = -ringRotation * 0.6f
                }
        ) {
            val r = size.minDimension / 2f - 3.dp.toPx()
            val cx = size.width / 2f; val cy = size.height / 2f
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
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2)
            )
        }

        // Inner orbit particles
        Canvas(
            modifier = Modifier
                .size(210.dp)
                .alpha(particlesAlpha)
                .graphicsLayer { rotationZ = particleRotation }
        ) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val orbitR = size.minDimension / 2f - 8.dp.toPx()
            repeat(8) { i ->
                val angle = (i * 45f) * (Math.PI.toFloat() / 180f)
                val px = cx + orbitR * cos(angle); val py = cy + orbitR * sin(angle)
                drawCircle(
                    color = neonPrimary.copy(alpha = 0.3f + 0.7f * ((i % 3) / 2f)),
                    radius = if (i % 2 == 0) 3.dp.toPx() else 2.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // Outer orbit particles
        Canvas(
            modifier = Modifier
                .size(330.dp)
                .alpha(particlesAlpha * 0.5f)
                .graphicsLayer { rotationZ = particleRotation2 }
        ) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val orbitR = size.minDimension / 2f - 6.dp.toPx()
            repeat(12) { i ->
                val angle = (i * 30f) * (Math.PI.toFloat() / 180f)
                val px = cx + orbitR * cos(angle); val py = cy + orbitR * sin(angle)
                drawCircle(
                    color = neonPrimaryVariant.copy(alpha = 0.15f + 0.35f * ((i % 4) / 3f)),
                    radius = 1.5.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // Brain icon + title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .graphicsLayer {
                    scaleX = iconScale; scaleY = iconScale
                    alpha = iconAlpha
                }
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer {
                        // 3D rotation for depth effect
                        rotationY = rotY * iconScale
                        rotationX = rotX * iconScale
                        scaleX = glowPulse
                        scaleY = glowPulse
                        cameraDistance = 10f * density
                        shadowElevation = 28f
                        spotShadowColor = neonPrimary
                        ambientShadowColor = neonPrimaryVariant
                    }
            ) {
                // BlendMode.Screen + Offscreen compositing:
                // Makes white/grey pixels transparent on dark background.
                // Brain's vibrant blue/purple stays visible — halo disappears.
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "LocalMind Brain Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawIntoCanvas { canvas ->
                                val paint = Paint().apply {
                                    blendMode = BlendMode.Screen
                                }
                                canvas.saveLayer(
                                    androidx.compose.ui.geometry.Rect(
                                        0f, 0f, size.width, size.height
                                    ),
                                    paint
                                )
                                drawContent()
                                canvas.restore()
                            }
                        },
                    contentScale = ContentScale.Fit
                )
                // Shimmer overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(brush = shimmerBrush)
                }
            }

            // App title — slides up and fades in after icon
            Text(
                text = "LocalMind",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = NeonText,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .offset(y = titleOffsetY.dp)
            )
        }

        // Tagline at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp)
                .offset(y = taglineOffsetY.dp)
                .alpha(taglineAlpha)
        ) {
            Text(
                text = "Private AI  ·  On-Device",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = neonPrimary.copy(alpha = 0.75f),
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
