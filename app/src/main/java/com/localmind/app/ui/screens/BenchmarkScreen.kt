@file:Suppress("DEPRECATION")

package com.localmind.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.BenchmarkViewModel
import com.localmind.app.ui.viewmodel.BenchmarkState
import com.localmind.app.ui.viewmodel.BenchmarkResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun BenchmarkScreen(
    onNavigateBack: () -> Unit,
    viewModel: BenchmarkViewModel = hiltViewModel()
) {
    val benchmarkState by viewModel.benchmarkState.collectAsStateWithLifecycle()
    val hardwareStats by viewModel.hardwareStats.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Labs", color = NeonText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NeonText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NeonSurface
                )
            )
        },
        containerColor = NeonBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Real-time Hardware Dashboard
            HardwareDashboard(
                stats = hardwareStats,
                activeModel = activeModel
            )

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = benchmarkState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) with fadeOut(animationSpec = tween(500))
                },
                label = "BenchmarkStateTransition"
            ) { state ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    when (state) {
                        is BenchmarkState.Idle -> IdleView(onStart = { viewModel.startBenchmark() })
                        is BenchmarkState.Running -> RunningView(state.progress, state.currentTps)
                        is BenchmarkState.Complete -> CompleteView(state.result, onReset = { viewModel.resetBenchmark() })
                        is BenchmarkState.Error -> ErrorView(state.message, onReset = { viewModel.resetBenchmark() })
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun IdleView(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        Surface(
            shape = CircleShape,
            color = NeonElevated,
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = NeonPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "AI Benchmark Suite",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = NeonText
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Validate inference nodes, memory evaluation, and throughput latency.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonPrimary,
                contentColor = NeonBackground
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Bolt, null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("INITIALIZE PERFORMANCE TEST", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RunningView(progress: Float, currentTps: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedCircularProgress(progress = progress)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    String.format("%.1f", currentTps),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = NeonPrimary
                )
                Text(
                    "t/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "EXECUTING INFERENCE...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = NeonText,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Testing 4-bit quantized GGUF throughput",
            style = MaterialTheme.typography.bodySmall,
            color = NeonTextSecondary
        )
    }
}

@Composable
private fun CompleteView(result: BenchmarkResult, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = NeonSuccess.copy(alpha = 0.1f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = NeonSuccess
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Laboratory Report",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = NeonText
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeonElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Config info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ResultRowItem(
                        label = "Context",
                        value = "${result.contextSize}",
                        icon = Icons.Default.LinearScale,
                        color = Color.Cyan
                    )
                    ResultRowItem(
                        label = "Threads",
                        value = "${result.threadCount}",
                        icon = Icons.Default.Memory,
                        color = NeonPrimary
                    )
                }

                HorizontalDivider(color = NeonTextExtraMuted.copy(alpha = 0.2f))

                ResultRow(
                    label = "Total Throughput",
                    value = String.format("%.2f t/s", result.tokensPerSecond),
                    icon = Icons.Default.Bolt,
                    color = NeonPrimary
                )

                HorizontalDivider(color = NeonTextExtraMuted.copy(alpha = 0.2f))

                ResultRow(
                    label = "Prompt Eval Time",
                    value = String.format("%.1f ms", result.promptEvalTimeMs),
                    icon = Icons.Default.Timer,
                    color = NeonSuccess
                )

                HorizontalDivider(color = NeonTextExtraMuted.copy(alpha = 0.2f))

                ResultRow(
                    label = "Memory Allocation",
                    value = "${result.memoryUsedMB} MB",
                    icon = Icons.Default.DataUsage,
                    color = Color.Magenta
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPrimary),
            border = androidx.compose.foundation.BorderStroke(2.dp, NeonPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("CALIBRATE AGAIN", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorView(message: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.ReportProblem,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = NeonError
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Validation Failed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = NeonError
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary, contentColor = NeonBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("RESTART ENGINE")
        }
    }
}

@Composable
private fun HardwareDashboard(
    stats: com.localmind.app.core.utils.HardwareStats?,
    activeModel: com.localmind.app.domain.model.Model? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = NeonElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Monitor, null, tint = NeonPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "SYSTEM MONITOR",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonPrimary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                val infiniteTransition = rememberInfiniteTransition(label = "blink")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "blinkAlpha"
                )

                Surface(
                    shape = CircleShape,
                    color = NeonSuccess.copy(alpha = alpha),
                    modifier = Modifier.size(8.dp)
                ) {}
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HardwareGauge(
                    label = "CPU LOAD",
                    value = stats?.cpuUsage ?: 0f,
                    displayValue = "${((stats?.cpuUsage ?: 0f) * 100).toInt()}%",
                    color = NeonPrimary
                )
                HardwareGauge(
                    label = "RAM POOL",
                    value = stats?.ramUsagePercent ?: 0f,
                    displayValue = String.format("%.1f GB", stats?.usedRamGb ?: 0.0),
                    color = Color.Magenta
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = NeonTextExtraMuted.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ACTIVE MODEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        activeModel?.name ?: "None",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        activeModel?.let { "${String.format("%.1f", it.sizeBytes / (1024f * 1024f * 1024f))} GB" } ?: "--",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonTextExtraMuted
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "DEVICE",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        android.os.Build.MODEL,
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stats?.let { String.format("%.1f GB RAM | %d Cores", it.totalRamGb, Runtime.getRuntime().availableProcessors()) } ?: "--",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonTextExtraMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareGauge(
    label: String,
    value: Float,
    displayValue: String,
    color: Color
) {
    val trackColor = NeonTextExtraMuted.copy(alpha = 0.1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            val animatedValue by animateFloatAsState(
                targetValue = value,
                animationSpec = tween(1000, easing = FastOutSlowInEasing),
                label = "gauge"
            )

            Canvas(modifier = Modifier.size(90.dp)) {
                val strokeWidth = 8.dp.toPx()
                drawCircle(
                    color = trackColor,
                    style = Stroke(width = strokeWidth)
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = animatedValue * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(
                displayValue,
                style = MaterialTheme.typography.titleMedium,
                color = NeonText,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = NeonTextSecondary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnimatedCircularProgress(progress: Float) {
    val primary = NeonPrimary
    val trackColor = NeonTextExtraMuted.copy(alpha = 0.1f)
    val arcColors = listOf(
        primary.copy(alpha = 0.1f),
        primary,
        primary.copy(alpha = 0.1f)
    )
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = Modifier.size(160.dp)) {
        val strokeWidth = 12.dp.toPx()

        drawCircle(
            color = trackColor,
            style = Stroke(width = strokeWidth)
        )

        drawArc(
            brush = Brush.sweepGradient(
                arcColors,
                center = Offset(size.width / 2, size.height / 2)
            ),
            startAngle = rotation,
            sweepAngle = 120f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        drawArc(
            color = primary,
            startAngle = -90f,
            sweepAngle = progress * 360f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = NeonTextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = NeonText,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun ResultRowItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NeonTextSecondary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = NeonText,
                fontWeight = FontWeight.Black
            )
        }
    }
}
