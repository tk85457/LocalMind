package com.localmind.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmind.app.ui.components.*
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.OnboardingViewModel

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onboardingViewModel: OnboardingViewModel = hiltViewModel()
) {
    val currentPage by onboardingViewModel.currentPage.collectAsStateWithLifecycle()
    val isComplete by onboardingViewModel.isComplete.collectAsStateWithLifecycle()
    val dimens = com.localmind.app.ui.theme.LocalDimens.current

    LaunchedEffect(isComplete) {
        if (isComplete) {
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonBackground)
    ) {
        // Decorative background elements
        PulsingCore(
            modifier = Modifier
                .size(600.dp)
                .offset(x = (-150).dp, y = (-150).dp)
                .alpha(0.05f),
            color = NeonPrimary,
            durationMillis = 8000
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.paddingScreenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header (Empty spacer instead of skip)
            Spacer(modifier = Modifier.height(dimens.spacingMedium))

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = currentPage,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(500)) + slideInHorizontally { it }) togetherWith
                                (fadeOut(animationSpec = tween(500)) + slideOutHorizontally { -it })
                        },
                        label = "onboarding_content"
                    ) { page ->
                        when (page) {
                            0 -> OnboardingPage(
                                title = "Privacy-First Intelligence",
                                description = "Your personal AI companion that runs strictly on-device. No cloud, no tracking, just intelligence in your pocket.",
                                icon = Icons.Default.Security,
                                accentColor = NeonPrimary
                            ) {
                                PulsingCore(modifier = Modifier.size(180.dp), color = NeonPrimary)
                            }

                            1 -> OnboardingPage(
                                title = "High-Performance Engine",
                                description = "Leverage your phone's unique hardware to run advanced LLMs. Real-time diagnostics ensure peak efficiency.",
                                icon = Icons.Default.Speed,
                                accentColor = NeonPrimaryLight
                            ) {
                                RotatingScanner(modifier = Modifier.size(180.dp), color = NeonPrimaryLight)
                            }

                            2 -> OnboardingPage(
                                title = "Seamless Experience",
                                description = "Chat, code, and explore models with a premium interface designed for the next generation of mobile computing.",
                                icon = Icons.Default.AutoAwesome,
                                accentColor = NeonAccent
                            ) {
                                OrbitingLoader(modifier = Modifier.size(180.dp), color = NeonAccent)
                            }
                        }
                    }
                }
            }

            // Footer Navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dimens.spacingLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 40.dp)
                ) {
                    repeat(3) { index ->
                        val isSelected = index == currentPage
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 32.dp else 8.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "indicator"
                        )
                        Box(
                            modifier = Modifier
                                .width(width)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) NeonPrimary else NeonElevated)
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
                ) {
                    if (currentPage > 0) {
                        OutlinedButton(
                            onClick = { onboardingViewModel.previousPage() },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                NeonTextExtraMuted.copy(alpha = 0.3f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonText)
                        ) {
                            Text("BACK", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { onboardingViewModel.nextPage() },
                        modifier = Modifier
                            .weight(if (currentPage > 0) 1.5f else 1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPrimary,
                            contentColor = NeonText
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Text(
                            text = if (currentPage == 2) "INITIALIZE SYSTEM" else "CONTINUE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    graphic: @Composable () -> Unit
) {
    val dimens = com.localmind.app.ui.theme.LocalDimens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated Graphic Area
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            graphic()

            // Icon overlay
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = accentColor
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = dimens.textSizeTitle
            ),
            fontWeight = FontWeight.Black,
            color = NeonText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = NeonTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
