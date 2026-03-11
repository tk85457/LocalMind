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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                title = { Text("Performance Benchmark", color = NeonText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NeonText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonSurface)
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
            // Realtime Hardware Dashboard — always visible
            HardwareDashboard(stats = hardwareStats, activeModel = activeModel)

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = benchmarkState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "BenchmarkStateTransition"
            ) { state ->
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    when (state) {
                        is BenchmarkState.Idle -> IdleView(onStart = { viewModel.startBenchmark() })
                        is BenchmarkState.Running -> RunningView(
                            state = state,
                            activeModel = activeModel,
                            onStop = { viewModel.resetBenchmark() }
                        )
                        is BenchmarkState.Complete -> CompleteView(
                            result = state.result,
                            onReset = { viewModel.resetBenchmark() }
                        )
                        is BenchmarkState.Error -> ErrorView(
                            message = state.message,
                            onReset = { viewModel.resetBenchmark() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Idle Screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun IdleView(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "scale"
        )
        Surface(
            shape = CircleShape,
            color = NeonElevated,
            modifier = Modifier.size(110.dp).graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Speed, null, modifier = Modifier.size(55.dp), tint = NeonPrimary)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text("AI Benchmark", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold, color = NeonText)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Measures real inference speed (tokens/sec),\nTime-to-First-Token, and memory usage.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "⚡ Uses chat template — same path as actual chat",
            style = MaterialTheme.typography.bodySmall,
            color = NeonPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary, contentColor = NeonBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Bolt, null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("START BENCHMARK", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Running Screen — REALTIME live stats
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RunningView(
    state: BenchmarkState.Running,
    activeModel: com.localmind.app.domain.model.Model?,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Live TPS meter
        Box(contentAlignment = Alignment.Center) {
            AnimatedCircularProgress(progress = state.progress)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    String.format("%.1f", state.currentTps),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = NeonPrimary
                )
                Text("t/s", style = MaterialTheme.typography.labelSmall, color = NeonTextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "BENCHMARKING...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = NeonText,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Realtime live stats row
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NeonElevated)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LiveStatItem(label = "Tokens", value = "${state.tokensGenerated}")
                VerticalDivider(modifier = Modifier.height(36.dp), color = NeonTextExtraMuted.copy(alpha = 0.3f))
                LiveStatItem(
                    label = "Elapsed",
                    value = if (state.elapsedMs > 0) "${state.elapsedMs / 1000}.${(state.elapsedMs % 1000) / 100}s" else "0.0s"
                )
                VerticalDivider(modifier = Modifier.height(36.dp), color = NeonTextExtraMuted.copy(alpha = 0.3f))
                LiveStatItem(
                    label = "Speed",
                    value = String.format("%.1f t/s", state.currentTps)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            activeModel?.let { "Model: ${it.name} • ${it.quantization}" } ?: "Loading model...",
            style = MaterialTheme.typography.bodySmall,
            color = NeonTextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onStop,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonError),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonError.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("STOP", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun LiveStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black, color = NeonText)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = NeonTextSecondary, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Complete Screen — Detailed + Useful Results
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CompleteView(result: BenchmarkResult, onReset: () -> Unit) {

    val ratingColor = when (result.performanceColor) {
        "excellent" -> Color(0xFF00E676)
        "good"      -> Color(0xFF69F0AE)
        "ok"        -> Color(0xFFFFD740)
        "slow"      -> Color(0xFFFF6D00)
        else        -> Color(0xFFFF1744)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Performance Rating Badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ratingColor.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, ratingColor.copy(alpha = 0.5f))
        ) {
            Text(
                text = result.performanceRating,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ratingColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main TPS — hero metric
        Text(
            String.format("%.2f", result.tokensPerSecond),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = NeonPrimary
        )
        Text(
            "tokens / second",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonTextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Section 1: Speed Metrics ──
        BenchmarkCard(title = "⚡ Speed Metrics") {
            ResultRow(
                label = "Inference Speed",
                value = String.format("%.2f t/s", result.tokensPerSecond),
                icon = Icons.Default.Bolt,
                color = NeonPrimary
            )
            BenchmarkDivider()
            ResultRow(
                label = "Time to First Token",
                value = if (result.timeToFirstTokenMs > 0)
                    "${result.timeToFirstTokenMs} ms"
                else "< 1 ms",
                icon = Icons.Default.Timer,
                color = Color(0xFF40C4FF),
                subtitle = "Lower = more responsive"
            )
            BenchmarkDivider()
            ResultRow(
                label = "Total Generation",
                value = formatDuration(result.generationTimeMs.toLong()),
                icon = Icons.Default.Schedule,
                color = Color(0xFF69F0AE)
            )
            BenchmarkDivider()
            ResultRow(
                label = "Tokens Generated",
                value = "${result.totalTokens} tokens",
                icon = Icons.Default.TextFields,
                color = Color(0xFFFFD740)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Section 2: Memory ──
        BenchmarkCard(title = "💾 Memory Usage") {
            ResultRow(
                label = "RAM Used by App",
                value = "${result.peakMemoryMB} MB",
                icon = Icons.Default.DataUsage,
                color = Color(0xFFE040FB)
            )
            if (result.memoryUsedMB > 0) {
                BenchmarkDivider()
                ResultRow(
                    label = "Delta During Bench",
                    value = "+${result.memoryUsedMB} MB",
                    icon = Icons.Default.TrendingUp,
                    color = Color(0xFFFF6D00)
                )
            }
            BenchmarkDivider()
            ResultRow(
                label = "Device RAM",
                value = String.format("%.1f GB total", result.totalRamGb),
                icon = Icons.Default.Memory,
                color = NeonPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Section 3: Config ──
        BenchmarkCard(title = "⚙️ Run Configuration") {
            // 2-column config row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ConfigItem(label = "Context", value = "${result.contextSize} tokens")
                ConfigItem(label = "CPU Threads", value = "${result.threadCount} / ${result.cpuCores}")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ConfigItem(
                    label = "GPU Layers",
                    value = if (result.gpuLayers > 0) "${result.gpuLayers} layers" else "CPU only"
                )
                ConfigItem(
                    label = "Prompt Eval",
                    value = if (result.promptEvalTimeMs > 0)
                        "${result.promptEvalTimeMs.toLong()} ms"
                    else "--"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── What does this mean? ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NeonPrimary.copy(alpha = 0.07f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPrimary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📊 What does this mean?",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    when {
                        result.tokensPerSecond >= 25f ->
                            "Excellent! This device handles AI inference very well. Real-time conversations will feel smooth."
                        result.tokensPerSecond >= 15f ->
                            "Good performance. Responses generate quickly. Suitable for daily use."
                        result.tokensPerSecond >= 8f  ->
                            "Acceptable. Responses may take a few seconds. Works well for most use cases."
                        result.tokensPerSecond >= 3f  ->
                            "Slow. Consider using a smaller/more quantized model (Q4_K_M instead of Q8_0)."
                        else ->
                            "Very slow. This model may be too large for this device. Try Llama-3.2-1B or SmolLM2-1.7B."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPrimary),
            border = androidx.compose.foundation.BorderStroke(2.dp, NeonPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("RUN AGAIN", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BenchmarkCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NeonElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = NeonTextSecondary, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun BenchmarkDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = NeonTextExtraMuted.copy(alpha = 0.15f)
    )
}

@Composable
private fun ConfigItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NeonTextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = NeonText,
            fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error Screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ErrorView(message: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.ReportProblem, null, modifier = Modifier.size(72.dp), tint = NeonError)
        Spacer(modifier = Modifier.height(20.dp))
        Text("Benchmark Failed", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = NeonError)
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NeonError.copy(alpha = 0.08f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonError.copy(alpha = 0.3f))
        ) {
            Text(
                message,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary, contentColor = NeonBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("TRY AGAIN", fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hardware Dashboard — Realtime System Monitor
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HardwareDashboard(
    stats: com.localmind.app.core.utils.HardwareStats?,
    activeModel: com.localmind.app.domain.model.Model?
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NeonElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with live indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Monitor, null, tint = NeonPrimary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LIVE SYSTEM MONITOR", style = MaterialTheme.typography.labelSmall,
                        color = NeonPrimary, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                // Blinking live dot
                val blinkAlpha by rememberInfiniteTransition(label = "blink").animateFloat(
                    initialValue = 0.2f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "blink"
                )
                Surface(shape = CircleShape, color = NeonSuccess.copy(alpha = blinkAlpha),
                    modifier = Modifier.size(8.dp)) {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CPU + RAM gauges
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                HardwareGauge(
                    label = "CPU",
                    value = stats?.cpuUsage ?: 0f,
                    displayValue = "${((stats?.cpuUsage ?: 0f) * 100).toInt()}%",
                    color = NeonPrimary
                )
                HardwareGauge(
                    label = "RAM",
                    value = stats?.ramUsagePercent ?: 0f,
                    displayValue = String.format("%.1f GB", stats?.usedRamGb ?: 0.0),
                    color = Color.Magenta
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = NeonTextExtraMuted.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            // Device + Model info in a 2-column grid
            Row(modifier = Modifier.fillMaxWidth()) {
                // Left: Active Model
                Column(modifier = Modifier.weight(1f)) {
                    DashLabel("ACTIVE MODEL")
                    Text(activeModel?.name ?: "None",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonText, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        activeModel?.let { m ->
                            buildString {
                                append(m.quantization)
                                val gb = m.sizeBytes / (1024f * 1024f * 1024f)
                                if (gb > 0) append(" • ${String.format("%.1f", gb)} GB")
                            }
                        } ?: "--",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonTextExtraMuted
                    )
                }
                // Right: Device
                Column(horizontalAlignment = Alignment.End) {
                    DashLabel("DEVICE")
                    Text(android.os.Build.MODEL,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonText, fontWeight = FontWeight.Bold)
                    Text(
                        stats?.let { "%.1f GB • %d cores".format(it.totalRamGb,
                            Runtime.getRuntime().availableProcessors()) } ?: "--",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonTextExtraMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Left: Quantization + Context
                Column(modifier = Modifier.weight(1f)) {
                    DashLabel("QUANTIZATION")
                    Text(activeModel?.quantization ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonText, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    DashLabel("ANDROID VERSION")
                    Text("Android ${android.os.Build.VERSION.RELEASE}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonText, fontWeight = FontWeight.SemiBold)
                }
                // Right: Storage + RAM free
                Column(horizontalAlignment = Alignment.End) {
                    DashLabel("FREE STORAGE")
                    Text(stats?.availableStorage ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonText, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    DashLabel("FREE RAM")
                    Text(
                        stats?.let { "%.1f GB".format(it.totalRamGb - it.usedRamGb) } ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonText, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DashLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall,
        color = NeonTextSecondary, fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp)
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Components
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HardwareGauge(label: String, value: Float, displayValue: String, color: Color) {
    val trackColor = NeonTextExtraMuted.copy(alpha = 0.1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            val animatedValue by animateFloatAsState(
                targetValue = value,
                animationSpec = tween(800, easing = FastOutSlowInEasing),
                label = "gauge"
            )
            Canvas(modifier = Modifier.size(80.dp)) {
                val strokeWidth = 8.dp.toPx()
                drawCircle(color = trackColor, style = Stroke(width = strokeWidth))
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = animatedValue * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(displayValue, style = MaterialTheme.typography.bodyMedium,
                color = NeonText, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = NeonTextSecondary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnimatedCircularProgress(progress: Float) {
    val primary = NeonPrimary
    val trackColor = NeonTextExtraMuted.copy(alpha = 0.1f)
    val rotation by rememberInfiniteTransition(label = "rot").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rot"
    )
    Canvas(modifier = Modifier.size(150.dp)) {
        val sw = 10.dp.toPx()
        drawCircle(color = trackColor, style = Stroke(width = sw))
        drawArc(
            brush = Brush.sweepGradient(
                listOf(primary.copy(alpha = 0.1f), primary, primary.copy(alpha = 0.1f)),
                center = Offset(size.width / 2, size.height / 2)
            ),
            startAngle = rotation, sweepAngle = 110f, useCenter = false,
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        drawArc(
            color = primary, startAngle = -90f, sweepAngle = progress * 360f,
            useCenter = false, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    color = NeonTextSecondary, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = NeonTextExtraMuted)
                }
            }
        }
        Text(value, style = MaterialTheme.typography.titleSmall,
            color = NeonText, fontWeight = FontWeight.Black)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
private fun formatDuration(ms: Long): String = when {
    ms <= 0   -> "--"
    ms < 1000 -> "${ms}ms"
    else      -> "${ms / 1000}.${(ms % 1000) / 100}s"
}
