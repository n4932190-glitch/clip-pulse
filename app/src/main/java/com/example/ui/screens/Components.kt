package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.SubtitleCue
import com.example.domain.model.SubtitlePosition
import com.example.domain.model.SubtitleSize
import com.example.domain.model.SubtitleStyle
import com.example.domain.model.VideoSourcePlatform
import com.example.ui.ExportPipelineState
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BebasNeueFontFamily
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MontserratFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.SubtitleDarkScrim
import com.example.ui.theme.SubtitleHighlightYellow
import com.example.ui.theme.SubtitleTerminalGreen

@Composable
fun ViralityScoreBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = "Virality score",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$score% Viral",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun PlatformTagPill(
    platform: VideoSourcePlatform,
    modifier: Modifier = Modifier
) {
    val containerColor = when (platform) {
        VideoSourcePlatform.YOUTUBE -> MaterialTheme.colorScheme.primaryContainer
        VideoSourcePlatform.INSTAGRAM -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (platform) {
        VideoSourcePlatform.YOUTUBE -> MaterialTheme.colorScheme.onPrimaryContainer
        VideoSourcePlatform.INSTAGRAM -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = when (platform) {
                    VideoSourcePlatform.DEVICE_VIDEO -> Icons.Filled.Smartphone
                    else -> Icons.Filled.VideoLibrary
                },
                contentDescription = platform.label,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = platform.label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

fun subtitleFontFamily(style: SubtitleStyle): FontFamily = when (style) {
    SubtitleStyle.HORMOZI_POP -> MontserratFontFamily
    SubtitleStyle.NEON_PULSE -> SpaceGroteskFontFamily
    SubtitleStyle.CLASSIC_PILL -> PlusJakartaSansFontFamily
    SubtitleStyle.CINEMA_MINIMAL -> MontserratFontFamily
    SubtitleStyle.TYPEWRITER_MONO -> JetBrainsMonoFontFamily
    SubtitleStyle.IMPACT_BOX -> BebasNeueFontFamily
}

@Composable
fun StyledSubtitleOverlay(
    cue: SubtitleCue?,
    style: SubtitleStyle,
    position: SubtitlePosition,
    size: SubtitleSize,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 20.dp)
    ) {
        val alignment = when (position) {
            SubtitlePosition.TOP -> Alignment.TopCenter
            SubtitlePosition.MIDDLE -> Alignment.Center
            SubtitlePosition.BOTTOM -> Alignment.BottomCenter
        }

        AnimatedContent(
            targetState = cue,
            transitionSpec = {
                (fadeIn() + scaleIn(
                    initialScale = 0.90f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )) togetherWith fadeOut()
            },
            modifier = Modifier
                .align(alignment)
                .padding(
                    top = if (position == SubtitlePosition.TOP) 24.dp else 0.dp,
                    bottom = if (position == SubtitlePosition.BOTTOM) 34.dp else 0.dp
                ),
            label = "SubtitleAnimation"
        ) { activeCue ->
            if (activeCue != null) {
                SubtitleTextBubble(
                    cue = activeCue,
                    style = style,
                    size = size
                )
            }
        }
    }
}

@Composable
fun SubtitleTextBubble(
    cue: SubtitleCue,
    style: SubtitleStyle,
    size: SubtitleSize,
    previewFontScale: Float = 1.0f
) {
    val rawText = if (style.uppercase) cue.text.uppercase() else cue.text
    val highlight = if (style.uppercase) cue.highlightWord.uppercase() else cue.highlightWord
    val fontFamily = subtitleFontFamily(style)
    val fontSizeSp = (18f * size.scaleFactor * previewFontScale).sp

    val baseColor = when (style) {
        SubtitleStyle.CLASSIC_PILL -> Color(0xFF141222)
        SubtitleStyle.TYPEWRITER_MONO -> Color(0xFFECFDF5)
        else -> Color.White
    }
    val accentColor = when (style) {
        SubtitleStyle.HORMOZI_POP -> SubtitleHighlightYellow
        SubtitleStyle.NEON_PULSE -> NeonCyan
        SubtitleStyle.CLASSIC_PILL -> MaterialTheme.colorScheme.primary
        SubtitleStyle.CINEMA_MINIMAL -> AmberGold
        SubtitleStyle.TYPEWRITER_MONO -> SubtitleTerminalGreen
        SubtitleStyle.IMPACT_BOX -> SubtitleHighlightYellow
    }

    val annotatedText = buildAnnotatedString {
        if (highlight.isNotBlank()) {
            val idx = rawText.indexOf(highlight, ignoreCase = true)
            if (idx >= 0) {
                append(rawText.substring(0, idx))
                withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.ExtraBold)) {
                    append(rawText.substring(idx, (idx + highlight.length).coerceAtMost(rawText.length)))
                }
                append(rawText.substring((idx + highlight.length).coerceAtMost(rawText.length)))
            } else {
                append(rawText)
            }
        } else {
            append(rawText)
        }
    }

    val textShadow = when (style) {
        SubtitleStyle.HORMOZI_POP -> Shadow(color = Color.Black, offset = Offset(0f, 4f), blurRadius = 8f)
        SubtitleStyle.CINEMA_MINIMAL, SubtitleStyle.IMPACT_BOX -> Shadow(
            color = Color.Black.copy(alpha = 0.75f),
            offset = Offset(0f, 3f),
            blurRadius = 6f
        )
        else -> null
    }

    val containerModifier = when (style) {
        SubtitleStyle.HORMOZI_POP -> Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        SubtitleStyle.NEON_PULSE -> Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SubtitleDarkScrim)
            .border(1.5.dp, NeonCyan, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
        SubtitleStyle.CLASSIC_PILL -> Modifier
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .padding(horizontal = 14.dp, vertical = 7.dp)
        SubtitleStyle.CINEMA_MINIMAL -> Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.50f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
        SubtitleStyle.TYPEWRITER_MONO -> Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xEB0C101A))
            .border(1.dp, SubtitleTerminalGreen, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
        SubtitleStyle.IMPACT_BOX -> Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.94f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    }

    Box(modifier = containerModifier, contentAlignment = Alignment.Center) {
        Text(
            text = annotatedText,
            style = TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSizeSp,
                lineHeight = (fontSizeSp.value * 1.18f).sp,
                color = baseColor,
                textAlign = TextAlign.Center,
                shadow = textShadow
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportProgressSheet(
    exportState: ExportPipelineState,
    onDismiss: () -> Unit,
    onShareTikTok: (Uri) -> Unit,
    onShareShorts: (Uri) -> Unit,
    onShareReels: (Uri) -> Unit,
    onShareMore: (Uri) -> Unit
) {
    if (exportState is ExportPipelineState.Idle) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (exportState) {
                is ExportPipelineState.Exporting -> {
                    val animatedProgress by animateFloatAsState(
                        targetValue = exportState.progressPercent / 100f,
                        label = "ExportProgress"
                    )
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedProgress.coerceIn(0.05f, 1f) },
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 5.dp
                        )
                        Text(
                            text = "${exportState.progressPercent}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.saving_to_device),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Cropping to 9:16 portrait and burning stylized subtitles into your MP4…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress.coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_export_button")
                    ) {
                        Text("Cancel Export")
                    }
                }

                is ExportPipelineState.Saved -> {
                    val outcome = exportState.outcome
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(EmeraldSuccess.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Saved successfully",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.saved_to_device_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Movies/ClipPulse/${outcome.displayFileName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (outcome.usedFullVideoStream) {
                                    "9:16 Full Motion Video + Burned-in Subtitles"
                                } else {
                                    "9:16 Cover Reel + Burned-in Subtitles (Stream was restricted by source platform)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Ready to post directly",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onShareTikTok(outcome.mediaStoreUri) },
                            modifier = Modifier.testTag("share_tiktok_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.share_tiktok))
                        }
                        FilledTonalButton(
                            onClick = { onShareShorts(outcome.mediaStoreUri) },
                            modifier = Modifier.testTag("share_shorts_button")
                        ) {
                            Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.share_shorts))
                        }
                        FilledTonalButton(
                            onClick = { onShareReels(outcome.mediaStoreUri) },
                            modifier = Modifier.testTag("share_reels_button")
                        ) {
                            Text(stringResource(R.string.share_reels))
                        }
                        OutlinedButton(
                            onClick = { onShareMore(outcome.mediaStoreUri) },
                            modifier = Modifier.testTag("share_more_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.share_more))
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_export_sheet_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                is ExportPipelineState.Error -> {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = "Export error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Export Failed",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = exportState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dismiss_export_error_button")
                    ) {
                        Text("Close")
                    }
                }

                ExportPipelineState.Idle -> Unit
            }
        }
    }
}
