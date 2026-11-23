package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.ClipDurationOption
import com.example.domain.model.ResolvedVideoSource
import com.example.domain.model.SubtitleCue
import com.example.domain.model.ViralClip
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

private const val TAG = "ViralClipAiRepository"

class ViralClipAiRepository {

    private val clipResponseSchema = Schema.obj(
        properties = mapOf(
            "clips" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Catchy viral hook title for the short clip, max 8 words"),
                        "viralityScore" to Schema.integer(description = "Estimated viral engagement score from 65 to 99"),
                        "category" to Schema.string(description = "2-word hook tag such as Hot Take, Story Hook, Key Insight, or Action Peak"),
                        "hookReason" to Schema.string(description = "One concise sentence explaining why this moment hooks viewers in the first 3 seconds"),
                        "startSeconds" to Schema.integer(description = "Clip start timestamp in seconds within the source video"),
                        "endSeconds" to Schema.integer(description = "Clip end timestamp in seconds within the source video"),
                        "subtitles" to Schema.array(
                            items = Schema.obj(
                                properties = mapOf(
                                    "startOffsetMs" to Schema.integer(description = "Cue start time in milliseconds relative to clip start (0 to clip duration)"),
                                    "endOffsetMs" to Schema.integer(description = "Cue end time in milliseconds relative to clip start"),
                                    "text" to Schema.string(description = "Short punchy subtitle phrase, 3 to 7 words max per line"),
                                    "highlightWord" to Schema.string(description = "Single strongest keyword from this phrase to emphasize visually")
                                )
                            ),
                            description = "Timed subtitle cues covering the entire clip cleanly every 2-3 seconds"
                        )
                    )
                ),
                description = "Exactly 3 ranked viral short-form clip candidates"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = clipResponseSchema
            }
        )
    }

    suspend fun findViralClips(
        source: ResolvedVideoSource,
        targetDuration: ClipDurationOption,
        keyframes: List<Pair<Long, Bitmap>>
    ): List<ViralClip> {
        val totalSeconds = (source.durationMs / 1000L).coerceAtLeast(10L).toInt()
        val desiredClipSeconds = targetDuration.seconds.coerceAtMost(totalSeconds)

        val transcriptSummary = if (source.transcriptCues.isNotEmpty()) {
            source.transcriptCues
                .take(120)
                .joinToString("\n") { chunk ->
                    val sec = chunk.startMs / 1000L
                    "[${sec}s] ${chunk.text}"
                }
        } else {
            "No embedded transcript available."
        }

        val frameTimelineNote = if (keyframes.isNotEmpty()) {
            "Attached ${keyframes.size} video keyframes sampled at: " +
                keyframes.joinToString(", ") { "${it.first / 1000L}s" }
        } else {
            "Analyze using video title, platform, creator, description, and transcript."
        }

        val prompt = """
            You are an expert short-form viral video editor for TikTok, YouTube Shorts, and Instagram Reels.
            Analyze the video below and extract 3 distinct, high-retention 9:16 short clips.

            Source Platform: ${source.platform.label}
            Video Title: ${source.title}
            Creator: ${source.author}
            Total Video Duration: $totalSeconds seconds
            Target Clip Length: $desiredClipSeconds seconds (each clip must be roughly $desiredClipSeconds seconds long, and startSeconds/endSeconds must stay strictly within 0..$totalSeconds)
            Description: ${source.description.take(900)}
            Visual Context: $frameTimelineNote

            Transcript with timestamps:
            $transcriptSummary

            Instructions:
            1. Find the 3 most engaging, viral-worthy moments (strong opening hook, curiosity gap, payoff, or visual peak).
            2. Order them from highest viralityScore to lowest.
            3. Ensure each clip's (endSeconds - startSeconds) matches ~$desiredClipSeconds seconds (clamped to $totalSeconds if the source video is shorter).
            4. Generate crisp, rhythmic, short-form subtitle cues (3-7 words each) starting from startOffsetMs = 0 up to ${(desiredClipSeconds * 1000)} ms.
            5. If a transcript is provided, base subtitles directly on the spoken words in that time window; otherwise craft accurate, engaging narrative/topic captions matching the video content and visual frames.
            6. Choose one standout highlightWord per subtitle cue that appears in that cue's text.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                if (keyframes.isNotEmpty()) {
                    model.generateContent(
                        content {
                            keyframes.forEach { (_, bitmap) -> image(bitmap) }
                            text(prompt)
                        }
                    )
                } else {
                    model.generateContent(prompt)
                }
            }
            val jsonText = response.text ?: throw IllegalStateException("Gemini returned an empty response.")
            parseClipsResponse(jsonText, totalSeconds, desiredClipSeconds)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini viral clip analysis timed out", e)
            throw IllegalStateException("AI analysis timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini viral clip analysis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to analyze video with AI.")
        }
    }

    private fun parseClipsResponse(
        jsonString: String,
        totalSeconds: Int,
        desiredClipSeconds: Int
    ): List<ViralClip> {
        val root = JSONObject(jsonString)
        val clipsArray = root.getJSONArray("clips")
        val clips = mutableListOf<ViralClip>()

        for (i in 0 until clipsArray.length()) {
            val obj = clipsArray.getJSONObject(i)
            val rawStart = obj.optInt("startSeconds", 0).coerceIn(0, (totalSeconds - 5).coerceAtLeast(0))
            val rawEnd = obj.optInt("endSeconds", rawStart + desiredClipSeconds)
                .coerceIn(rawStart + 5, totalSeconds.coerceAtLeast(rawStart + 5))

            val clipDurationMs = ((rawEnd - rawStart) * 1000L).coerceAtLeast(5_000L)
            val subsArray = obj.optJSONArray("subtitles")
            val cues = mutableListOf<SubtitleCue>()

            if (subsArray != null) {
                for (j in 0 until subsArray.length()) {
                    val cueObj = subsArray.getJSONObject(j)
                    val sOffset = cueObj.optLong("startOffsetMs", j * 2500L)
                        .coerceIn(0L, (clipDurationMs - 400L).coerceAtLeast(0L))
                    val eOffset = cueObj.optLong("endOffsetMs", sOffset + 2400L)
                        .coerceIn(sOffset + 400L, clipDurationMs)
                    val text = cueObj.optString("text", "").trim()
                    if (text.isNotBlank()) {
                        cues.add(
                            SubtitleCue(
                                id = UUID.randomUUID().toString(),
                                startOffsetMs = sOffset,
                                endOffsetMs = eOffset,
                                text = text,
                                highlightWord = cueObj.optString("highlightWord", "").trim()
                            )
                        )
                    }
                }
            }

            clips.add(
                ViralClip(
                    id = UUID.randomUUID().toString(),
                    title = obj.optString("title", "Viral Moment #${i + 1}"),
                    viralityScore = obj.optInt("viralityScore", 88).coerceIn(1, 99),
                    category = obj.optString("category", "Viral Hook"),
                    hookReason = obj.optString("hookReason", "High-retention opening hook."),
                    startMs = rawStart * 1000L,
                    endMs = rawEnd * 1000L,
                    subtitles = cues.sortedBy { it.startOffsetMs }
                )
            )
        }

        if (clips.isEmpty()) {
            throw IllegalStateException("AI did not return any viral clip candidates.")
        }
        return clips.sortedByDescending { it.viralityScore }
    }
}
