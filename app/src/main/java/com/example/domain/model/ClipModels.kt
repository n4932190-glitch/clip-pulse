package com.example.domain.model

import com.example.R

enum class ClipDurationOption(val seconds: Int, val label: String) {
    THIRTY(30, "30s"),
    SIXTY(60, "60s"),
    NINETY(90, "90s")
}

enum class FramingCropMode(val label: String) {
    CENTER("Center 9:16"),
    LEFT_FOCUS("Left Focus"),
    RIGHT_FOCUS("Right Focus")
}

enum class SubtitleStyle(
    val displayName: String,
    val tagline: String,
    val fontResId: Int,
    val uppercase: Boolean
) {
    HORMOZI_POP(
        displayName = "Hormozi Pop",
        tagline = "Bold yellow punch + outline",
        fontResId = R.font.montserrat,
        uppercase = true
    ),
    NEON_PULSE(
        displayName = "Neon Pulse",
        tagline = "Cyan glow glass pill",
        fontResId = R.font.space_grotesk,
        uppercase = false
    ),
    CLASSIC_PILL(
        displayName = "Classic Pill",
        tagline = "Crisp white Reels card",
        fontResId = R.font.plus_jakarta_sans,
        uppercase = false
    ),
    CINEMA_MINIMAL(
        displayName = "Cinema",
        tagline = "Clean editorial caption",
        fontResId = R.font.montserrat,
        uppercase = false
    ),
    TYPEWRITER_MONO(
        displayName = "Mono Terminal",
        tagline = "Tech monospace dark box",
        fontResId = R.font.jetbrains_mono,
        uppercase = false
    ),
    IMPACT_BOX(
        displayName = "Impact Box",
        tagline = "Condensed coral highlight",
        fontResId = R.font.bebas_neue,
        uppercase = true
    )
}

enum class SubtitlePosition(val label: String, val verticalBias: Float) {
    TOP("Top", 0.18f),
    MIDDLE("Middle", 0.50f),
    BOTTOM("Lower", 0.78f)
}

enum class SubtitleSize(val label: String, val scaleFactor: Float) {
    SMALL("Small", 0.85f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.22f)
}

enum class VideoSourcePlatform(val label: String) {
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    DEVICE_VIDEO("Device Video"),
    DIRECT_LINK("Direct Video")
}

data class TranscriptChunk(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class ResolvedVideoSource(
    val inputUrl: String,
    val platform: VideoSourcePlatform,
    val title: String,
    val author: String,
    val description: String,
    val thumbnailUrl: String?,
    val playableUri: String?,
    val durationMs: Long,
    val transcriptCues: List<TranscriptChunk>,
    val isStreamPlayable: Boolean
)

data class SubtitleCue(
    val id: String,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val text: String,
    val highlightWord: String
)

data class ViralClip(
    val id: String,
    val title: String,
    val viralityScore: Int,
    val category: String,
    val hookReason: String,
    val startMs: Long,
    val endMs: Long,
    val subtitles: List<SubtitleCue>
) {
    val durationSeconds: Int
        get() = (((endMs - startMs).coerceAtLeast(1000L)) / 1000L).toInt()
}
