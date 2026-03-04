package com.localmind.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmind.app.ui.theme.*

@Composable
fun ModelListItem(
    name: String,
    sizeText: String,
    isActive: Boolean = false,
    isLocal: Boolean = true,
    isPending: Boolean = false,
    isWorking: Boolean = false,
    progress: Float? = null,
    downloadedBytes: Long? = null,
    totalBytes: Long? = null,
    downloadSpeed: String? = null,
    eta: String? = null,
    supportsVision: Boolean = false,
    onActivate: () -> Unit = {},
    onOffload: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSettings: () -> Unit = {},
    onDetails: () -> Unit = {},
    onDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = NeonSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Icon, Name, Size, Status Dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (supportsVision) Icons.Default.Visibility else Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = NeonPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = name,
                    color = NeonText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = sizeText,
                    color = NeonTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) NeonPrimary
                            else if (isLocal) NeonTextExtraMuted.copy(alpha = 0.6f)
                            else NeonTextExtraMuted.copy(alpha = 0.2f)
                        )
                )
            }

            // Action Row: Primary Button + Utility Icons
            if (isPending || progress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { progress?.coerceIn(0f, 1f) ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = NeonPrimary,
                        trackColor = NeonElevated
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = progress?.let { "${(it * 100f).toInt().coerceIn(0, 100)}%" } ?: "Queued...",
                                color = NeonPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (progress != null && progress > 0f) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (downloadedBytes != null && totalBytes != null) {
                                        Text(
                                            text = "${DownloadMetricsFormatter.formatBytes(downloadedBytes)} / ${DownloadMetricsFormatter.formatBytes(totalBytes)}",
                                            color = NeonPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = downloadSpeed ?: "",
                                        color = NeonTextExtraMuted,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = eta ?: "",
                                        color = NeonTextExtraMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        TextButton(onClick = onCancelDownload, contentPadding = PaddingValues(0.dp)) {
                            Text("Cancel", color = NeonError, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Centered Primary Action Button
                    Button(
                        onClick = {
                            if (isLocal) {
                                if (isActive) onOffload() else onActivate()
                            } else {
                                onDownload()
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = when {
                            isActive -> ButtonDefaults.buttonColors(
                                containerColor = NeonPrimary.copy(alpha = 0.15f),
                                contentColor = NeonPrimary
                            )
                            isLocal -> ButtonDefaults.buttonColors(
                                containerColor = NeonPrimary.copy(alpha = 0.25f),
                                contentColor = NeonPrimary
                            )
                            else -> ButtonDefaults.buttonColors(
                                containerColor = NeonPrimary.copy(alpha = 0.1f),
                                contentColor = NeonPrimary
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = NeonPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = when {
                                    isActive -> Icons.Default.Bolt
                                    isLocal -> Icons.Default.PlayArrow
                                    else -> Icons.Default.CloudDownload
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when {
                                isActive -> "Offload"
                                isLocal -> "Load"
                                else -> "Download"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Utility Icons
                    IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NeonTextSecondary, modifier = Modifier.size(22.dp))
                    }

                    if (isLocal) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = NeonError.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                        }
                    }

                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Quick Details",
                            tint = NeonTextSecondary,
                            modifier = Modifier.size(22.dp).rotate(rotation)
                        )
                    }
                }
            }

            // Inline Detail Preview (matches image 4 expandable areas)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                DetailPreview(onDetails)
            }
        }
    }
}

@Composable
private fun DetailPreview(onViewFullProfile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(color = NeonElevated, thickness = 1.dp)

        // Detailed Info Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Estimated memory: ~378.6 MB", color = NeonTextSecondary, fontSize = 13.sp)
                Text("Vision capabilities: Supported", color = NeonTextSecondary, fontSize = 13.sp)
            }
            TextButton(onClick = onViewFullProfile) {
                Text("Details >", color = NeonPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Pilled stats grid
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Arch", "Gemma")
            Pill("Params", "38M")
            Pill("Quant", "Q8_0")
        }
    }
}

@Composable
private fun Pill(label: String, value: String) {
    Surface(
        color = NeonElevated,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = NeonTextExtraMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(value, color = NeonText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
