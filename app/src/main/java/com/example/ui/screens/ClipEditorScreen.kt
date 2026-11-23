package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.ClipDurationOption
import com.example.domain.model.FramingCropMode
import com.example.domain.model.ResolvedVideoSource
import com.example.domain.model.SubtitleCue
import com.example.domain.model.SubtitlePosition
import com.example.domain.model.SubtitleSize
import com.example.domain.model.SubtitleStyle
import com.example.domain.model.ViralClip
import com.example.ui.ClipPulseUiState
import com.example.ui.StudioPipelineState
import kotlinx.coroutines.delay

@Composable
fun ClipEditorScreen(
    uiState: ClipPulseUiState,
    onSelectClip: (Int) -> Unit,
    onSelectDuration: (ClipDurationOption) -> Unit,
    onSelectFraming: (FramingCropMode) -> Unit,
    onSelectSubtitleStyle: (SubtitleStyle) -> Unit,
    onSelectSubtitlePosition: (SubtitlePosition) -> Unit,
    onSelectSubtitleSize: (SubtitleSize) -> Unit,
    onEditSubtitleCue: (cueId: String, newText: String, newHighlight: String) -> Unit,
    onAttachLocalVideo: (Uri) -> Unit,
    onSaveToDevice: () -> Unit,
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = uiState.resolvedSource
    val activeClip = uiState.activeClip

    if (source == null || activeClip == null) {
        EmptyEditorView(onGoToStudio = onGoToStudio, modifier = modifier)
        return
    }

    var editingCue by remember { mutableStateOf<SubtitleCue?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 720.dp

        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(330.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PreviewStageAndSaveCard(
                        source = source,
                        clip = activeClip,
                        uiState = uiState,
                        onAttachLocalVideo = onAttachLocalVideo,
                        onSaveToDevice = onSaveToDevice
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    EditorControlsPanel(
                        source = source,
                        uiState = uiState,
                        activeClip = activeClip,
                        onSelectClip = onSelectClip,
                        onSelectDuration = onSelectDuration,
                        onSelectFraming = onSelectFraming,
                        onSelectSubtitleStyle = onSelectSubtitleStyle,
                        onSelectSubtitlePosition = onSelectSubtitlePosition,
                        onSelectSubtitleSize = onSelectSubtitleSize,
                        onOpenEditCue = { editingCue = it }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    PreviewStageAndSaveCard(
                        source = source,
                        clip = activeClip,
                        uiState = uiState,
                        onAttachLocalVideo = onAttachLocalVideo,
                        onSaveToDevice = onSaveToDevice
                    )

                    EditorControlsPanel(
                        source = source,
                        uiState = uiState,
                        activeClip = activeClip,
                        onSelectClip = onSelectClip,
                        onSelectDuration = onSelectDuration,
                        onSelectFraming = onSelectFraming,
                        onSelectSubtitleStyle = onSelectSubtitleStyle,
                        onSelectSubtitlePosition = onSelectSubtitlePosition,
                        onSelectSubtitleSize = onSelectSubtitleSize,
                        onOpenEditCue = { editingCue = it }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    editingCue?.let { cue ->
        EditSubtitleCueDialog(
            cue = cue,
            onDismiss = { editingCue = null },
            onSave = { newText, newHighlight ->
                onEditSubtitleCue(cue.id, newText, newHighlight)
                editingCue = null
            }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewStageAndSaveCard(
    source: ResolvedVideoSource,
    clip: ViralClip,
    uiState: ClipPulseUiState,
    onAttachLocalVideo: (Uri) -> Unit,
    onSaveToDevice: () -> Unit
) {
    val context = LocalContext.current
    val attachVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onAttachLocalVideo) }
    )

    val clipDurationMs = (clip.endMs - clip.startMs).coerceAtLeast(3_000L)
    var positionOffsetMs by remember(clip.id) { mutableLongStateOf(0L) }
    var isPlaying by remember(clip.id) { mutableStateOf(true) }

    val exoPlayer = remember(source.playableUri, clip.id) {
        if (source.isStreamPlayable && !source.playableUri.isNullOrBlank()) {
            ExoPlayer.Builder(context).build().apply {
                val item = MediaItem.Builder()
                    .setUri(Uri.parse(source.playableUri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startMs.coerceAtLeast(0L))
                            .setEndPositionMs(clip.endMs.coerceAtLeast(clip.startMs + 3_000L))
                            .build()
                    )
                    .build()
                setMediaItem(item)
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                prepare()
            }
        } else null
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    // Sync playhead position for real-time Compose subtitle preview
    LaunchedEffect(exoPlayer, clip.id, isPlaying) {
        while (true) {
            if (exoPlayer != null) {
                isPlaying = exoPlayer.isPlaying
                positionOffsetMs = exoPlayer.currentPosition.coerceIn(0L, clipDurationMs)
            } else if (isPlaying) {
                positionOffsetMs = (positionOffsetMs + 100L) % clipDurationMs
            }
            delay(100L)
        }
    }

    val activeCue = remember(clip.subtitles, positionOffsetMs) {
        clip.subtitles.firstOrNull { positionOffsetMs in it.startOffsetMs..it.endOffsetMs }
    }

    // Header bar above preview
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlatformTagPill(platform = source.platform)
        ViralityScoreBadge(score = clip.viralityScore)
    }

    // 9:16 Vertical Preview Frame
    Box(
        modifier = Modifier
            .widthIn(max = 278.dp)
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            if (!source.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = source.thumbnailUrl,
                    contentDescription = source.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
        }

        // Top 9:16 badge overlay
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.58f)
        ) {
            Text(
                text = "9:16 • ${clip.durationSeconds}s • ${uiState.framingCropMode.label}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Real-time Animated Styled Subtitle Overlay
        StyledSubtitleOverlay(
            cue = activeCue,
            style = uiState.selectedSubtitleStyle,
            position = uiState.subtitlePosition,
            size = uiState.subtitleSize
        )
    }

    // Playback Scrubber Controls
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        if (exoPlayer != null) {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            isPlaying = exoPlayer.isPlaying
                        } else {
                            isPlaying = !isPlaying
                        }
                    },
                    modifier = Modifier.testTag("preview_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = (positionOffsetMs.toFloat() / clipDurationMs.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        val newMs = (fraction * clipDurationMs).toLong()
                        positionOffsetMs = newMs
                        exoPlayer?.seekTo(newMs)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preview_scrubber"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Text(
                    text = "${formatMs(positionOffsetMs)}/${formatMs(clipDurationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (!source.isStreamPlayable) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Platform restricted direct stream",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Save exports a 9:16 cover reel with captions, or attach a video file for full motion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        attachVideoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    modifier = Modifier.testTag("attach_local_video_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Attach", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // Immediate Save to Device CTA
    Button(
        onClick = onSaveToDevice,
        enabled = uiState.pipelineState !is StudioPipelineState.Processing,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("save_to_device_button"),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.save_to_device),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun EditorControlsPanel(
    source: ResolvedVideoSource,
    uiState: ClipPulseUiState,
    activeClip: ViralClip,
    onSelectClip: (Int) -> Unit,
    onSelectDuration: (ClipDurationOption) -> Unit,
    onSelectFraming: (FramingCropMode) -> Unit,
    onSelectSubtitleStyle: (SubtitleStyle) -> Unit,
    onSelectSubtitlePosition: (SubtitlePosition) -> Unit,
    onSelectSubtitleSize: (SubtitleSize) -> Unit,
    onOpenEditCue: (SubtitleCue) -> Unit
) {
    // 1. AI Viral Moments Selector
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.viral_moments_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = source.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(uiState.viralClips) { index, clip ->
                val selected = index == uiState.selectedClipIndex
                Card(
                    onClick = { onSelectClip(index) },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .width(235.dp)
                        .testTag("viral_clip_card_$index")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ViralityScoreBadge(score = clip.viralityScore)
                            Text(
                                text = "${formatMs(clip.startMs)}–${formatMs(clip.endMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = clip.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = clip.hookReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // 2. Duration (30s / 60s / 90s) & 9:16 Framing Controls
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Length & 9:16 Framing",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClipDurationOption.entries.forEach { option ->
                FilterChip(
                    selected = uiState.selectedDuration == option,
                    onClick = { onSelectDuration(option) },
                    label = { Text(option.label) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("editor_duration_${option.label}")
                )
            }
            Spacer(Modifier.weight(1f))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FramingCropMode.entries) { mode ->
                FilterChip(
                    selected = uiState.framingCropMode == mode,
                    onClick = { onSelectFraming(mode) },
                    label = { Text(mode.label) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("framing_${mode.name.lowercase()}")
                )
            }
        }
    }

    // 3. Subtitle Styles Carousel (6 Styles)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.Subtitles,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = stringResource(R.string.subtitle_styles_title),
                style = MaterialTheme.typography.titleLarge
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(SubtitleStyle.entries) { style ->
                val selected = uiState.selectedSubtitleStyle == style
                Card(
                    onClick = { onSelectSubtitleStyle(style) },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .width(168.dp)
                        .testTag("subtitle_style_${style.name.lowercase()}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center
                        ) {
                            SubtitleTextBubble(
                                cue = SubtitleCue(
                                    id = "sample",
                                    startOffsetMs = 0L,
                                    endOffsetMs = 1000L,
                                    text = "Wait for THIS",
                                    highlightWord = "THIS"
                                ),
                                style = style,
                                size = SubtitleSize.SMALL,
                                previewFontScale = 0.72f
                            )
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = style.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1
                            )
                            Text(
                                text = style.tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Position & Size selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubtitlePosition.entries.forEach { pos ->
                FilterChip(
                    selected = uiState.subtitlePosition == pos,
                    onClick = { onSelectSubtitlePosition(pos) },
                    label = { Text(pos.label) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("subtitle_pos_${pos.name.lowercase()}")
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubtitleSize.entries.forEach { sz ->
                FilterChip(
                    selected = uiState.subtitleSize == sz,
                    onClick = { onSelectSubtitleSize(sz) },
                    label = { Text(sz.label) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("subtitle_size_${sz.name.lowercase()}")
                )
            }
        }
    }

    // 4. Editable Subtitle Cues List
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.captions_timeline_title),
            style = MaterialTheme.typography.titleMedium
        )
        activeClip.subtitles.forEach { cue ->
            Card(
                onClick = { onOpenEditCue(cue) },
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subtitle_cue_${cue.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatMs(cue.startOffsetMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cue.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (cue.highlightWord.isNotBlank()) {
                            Text(
                                text = "Highlight: ${cue.highlightWord}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit subtitle cue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditSubtitleCueDialog(
    cue: SubtitleCue,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var text by remember(cue) { mutableStateOf(cue.text) }
    var highlight by remember(cue) { mutableStateOf(cue.highlightWord) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Subtitle Line", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Caption text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_cue_text_input")
                )
                OutlinedTextField(
                    value = highlight,
                    onValueChange = { highlight = it },
                    label = { Text("Highlight keyword") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_cue_highlight_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text.trim(), highlight.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("confirm_edit_cue_button")
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptyEditorView(
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MovieCreation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = stringResource(R.string.empty_editor_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.empty_editor_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onGoToStudio,
                modifier = Modifier
                    .height(50.dp)
                    .testTag("empty_editor_go_studio_button")
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Link Studio")
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}
