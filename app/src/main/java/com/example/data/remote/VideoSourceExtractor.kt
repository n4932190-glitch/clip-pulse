package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.domain.model.ResolvedVideoSource
import com.example.domain.model.TranscriptChunk
import com.example.domain.model.VideoSourcePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private const val TAG = "VideoSourceExtractor"

class VideoSourceExtractor(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun detectPlatform(rawInput: String): VideoSourcePlatform {
        val lower = rawInput.trim().lowercase()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> VideoSourcePlatform.YOUTUBE
            lower.contains("instagram.com") || lower.contains("instagr.am") -> VideoSourcePlatform.INSTAGRAM
            lower.startsWith("content://") || lower.startsWith("file://") -> VideoSourcePlatform.DEVICE_VIDEO
            else -> VideoSourcePlatform.DIRECT_LINK
        }
    }

    suspend fun resolveUrl(rawUrl: String): ResolvedVideoSource = withContext(Dispatchers.IO) {
        val cleanedUrl = extractFirstUrl(rawUrl)
        require(cleanedUrl.isNotBlank()) { "Please enter a valid YouTube, Instagram, or video URL." }

        return@withContext when (detectPlatform(cleanedUrl)) {
            VideoSourcePlatform.YOUTUBE -> resolveYouTube(cleanedUrl)
            VideoSourcePlatform.INSTAGRAM -> resolveInstagram(cleanedUrl)
            VideoSourcePlatform.DEVICE_VIDEO -> resolveDeviceVideo(Uri.parse(cleanedUrl))
            VideoSourcePlatform.DIRECT_LINK -> resolveDirectVideoUrl(cleanedUrl)
        }
    }

    suspend fun resolveDeviceVideo(uri: Uri): ResolvedVideoSource = withContext(Dispatchers.IO) {
        var displayName = "Device Video"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        displayName = cursor.getString(idx)?.substringBeforeLast('.') ?: displayName
                    }
                }
            }

        var durationMs = 60_000L
        var width = 0
        var height = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull()?.coerceAtLeast(3_000L) ?: 60_000L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val titleMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!titleMeta.isNullOrBlank()) {
                displayName = titleMeta
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting local video", e)
        } finally {
            runCatching { retriever.release() }
        }

        val resolutionDesc = if (width > 0 && height > 0) "${width}x${height}" else "Local MP4"
        ResolvedVideoSource(
            inputUrl = uri.toString(),
            platform = VideoSourcePlatform.DEVICE_VIDEO,
            title = displayName,
            author = "Device Library",
            description = "Local video ($resolutionDesc, ${durationMs / 1000}s)",
            thumbnailUrl = null,
            playableUri = uri.toString(),
            durationMs = durationMs,
            transcriptCues = emptyList(),
            isStreamPlayable = true
        )
    }

    suspend fun extractKeyframesForGemini(
        uriString: String,
        durationMs: Long,
        count: Int = 5
    ): List<Pair<Long, Bitmap>> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<Pair<Long, Bitmap>>()
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "http" || uri.scheme == "https") {
                retriever.setDataSource(uriString, emptyMap())
            } else {
                retriever.setDataSource(context, uri)
            }
            val safeDuration = durationMs.coerceAtLeast(5_000L)
            val step = safeDuration / (count + 1)
            for (i in 1..count) {
                val timeMs = (step * i).coerceAtMost(safeDuration - 500L)
                val rawBitmap = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (rawBitmap != null) {
                    val targetWidth = 360
                    val ratio = targetWidth.toFloat() / rawBitmap.width.coerceAtLeast(1)
                    val targetHeight = (rawBitmap.height * ratio).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
                    if (scaled !== rawBitmap) rawBitmap.recycle()
                    frames.add(timeMs to scaled)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract video keyframes: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }
        frames
    }

    private fun resolveYouTube(url: String): ResolvedVideoSource {
        val videoId = extractYouTubeVideoId(url)
            ?: throw IllegalArgumentException("Could not recognize a valid YouTube video ID from this link.")

        val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
        var title = "YouTube Video ($videoId)"
        var author = "YouTube Creator"
        var thumbnailUrl: String? = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        var durationMs = 180_000L
        var description = ""
        var playableUri: String? = null
        val transcriptCues = mutableListOf<TranscriptChunk>()

        // 1. oEmbed for guaranteed title/author/thumbnail
        runCatching {
            val encoded = URLEncoder.encode(canonicalUrl, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .header("User-Agent", DESKTOP_UA)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        title = json.optString("title", title)
                        author = json.optString("author_name", author)
                        thumbnailUrl = json.optString("thumbnail_url", thumbnailUrl ?: "")
                            .ifBlank { thumbnailUrl }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "YouTube oEmbed lookup failed: ${it.message}") }

        // 2. Watch page ytInitialPlayerResponse for duration, description, captions & open streams
        runCatching {
            val req = Request.Builder()
                .url("$canonicalUrl&hl=en&bpctr=9999999999")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string().orEmpty()
                    val playerJson = extractBalancedJson(html, "ytInitialPlayerResponse = ")
                    if (playerJson != null) {
                        val root = JSONObject(playerJson)
                        val details = root.optJSONObject("videoDetails")
                        if (details != null) {
                            title = details.optString("title", title).ifBlank { title }
                            author = details.optString("author", author).ifBlank { author }
                            description = details.optString("shortDescription", "")
                            val lenSec = details.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
                            if (lenSec > 0L) durationMs = lenSec * 1000L
                        }

                        // Check progressive formats with direct non-ciphered URLs
                        val streamingData = root.optJSONObject("streamingData")
                        val formats = streamingData?.optJSONArray("formats")
                        if (formats != null) {
                            playableUri = pickBestDirectFormatUrl(formats)
                        }

                        // Extract timed caption tracks
                        val captionTracks = root.optJSONObject("captions")
                            ?.optJSONObject("playerCaptionsTracklistRenderer")
                            ?.optJSONArray("captionTracks")
                        val captionUrl = pickBestCaptionTrackUrl(captionTracks)
                        if (!captionUrl.isNullOrBlank()) {
                            transcriptCues.addAll(fetchYouTubeCaptions(captionUrl))
                        }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "YouTube watch page extraction failed: ${it.message}") }

        return ResolvedVideoSource(
            inputUrl = canonicalUrl,
            platform = VideoSourcePlatform.YOUTUBE,
            title = title,
            author = author,
            description = description,
            thumbnailUrl = thumbnailUrl,
            playableUri = playableUri,
            durationMs = durationMs,
            transcriptCues = transcriptCues,
            isStreamPlayable = !playableUri.isNullOrBlank()
        )
    }

    private fun resolveInstagram(url: String): ResolvedVideoSource {
        val cleanUrl = url.substringBefore("?").trimEnd('/') + "/"
        var title = "Instagram Reel"
        var author = "Instagram Creator"
        var description = ""
        var thumbnailUrl: String? = null
        var playableUri: String? = null

        val shortcodeMatch = Pattern.compile("instagram\\.com/(?:reel|p|tv)/([A-Za-z0-9_-]+)").matcher(cleanUrl)
        val shortcode = if (shortcodeMatch.find()) shortcodeMatch.group(1) else null

        // 1. Try Instagram embed captioned page first (often exposes public og/video_url cleanly)
        val candidateUrls = buildList {
            if (!shortcode.isNullOrBlank()) {
                add("https://www.instagram.com/reel/$shortcode/embed/captioned/")
            }
            add(cleanUrl)
        }

        for (targetUrl in candidateUrls) {
            runCatching {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", MOBILE_UA)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string().orEmpty()
                        if (thumbnailUrl.isNullOrBlank()) {
                            thumbnailUrl = extractMetaTag(html, "og:image")
                        }
                        val ogTitle = extractMetaTag(html, "og:title")
                        if (!ogTitle.isNullOrBlank() && title == "Instagram Reel") {
                            title = decodeHtmlEntities(ogTitle).take(90)
                        }
                        val ogDesc = extractMetaTag(html, "og:description")
                        if (!ogDesc.isNullOrBlank() && description.isBlank()) {
                            description = decodeHtmlEntities(ogDesc)
                        }
                        if (playableUri.isNullOrBlank()) {
                            playableUri = extractMetaTag(html, "og:video:secure_url")
                                ?: extractMetaTag(html, "og:video")
                                ?: extractJsonStringField(html, "video_url")
                        }
                        if (author == "Instagram Creator") {
                            val handleMatch = Pattern.compile("@([A-Za-z0-9_.]+)").matcher(title + " " + description)
                            if (handleMatch.find()) {
                                author = "@${handleMatch.group(1)}"
                            }
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "Instagram scrape attempt failed for $targetUrl: ${it.message}") }

            if (!playableUri.isNullOrBlank() && !thumbnailUrl.isNullOrBlank()) break
        }

        // Inspect stream duration if playableUri resolved
        var durationMs = 90_000L
        if (!playableUri.isNullOrBlank()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(playableUri, emptyMap())
                val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                if (dur != null && dur > 2000L) durationMs = dur
            } catch (e: Exception) {
                Log.w(TAG, "Could not inspect Instagram video duration: ${e.message}")
            } finally {
                runCatching { retriever.release() }
            }
        }

        return ResolvedVideoSource(
            inputUrl = cleanUrl,
            platform = VideoSourcePlatform.INSTAGRAM,
            title = title,
            author = author,
            description = description,
            thumbnailUrl = thumbnailUrl,
            playableUri = playableUri,
            durationMs = durationMs,
            transcriptCues = emptyList(),
            isStreamPlayable = !playableUri.isNullOrBlank()
        )
    }

    private fun resolveDirectVideoUrl(url: String): ResolvedVideoSource {
        var durationMs = 90_000L
        var isPlayable = false
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(url, emptyMap())
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (dur != null && dur > 1000L) {
                durationMs = dur
                isPlayable = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct link metadata check failed: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }

        val fileName = Uri.parse(url).lastPathSegment?.substringBeforeLast('.')?.ifBlank { null } ?: "Online Video"
        return ResolvedVideoSource(
            inputUrl = url,
            platform = VideoSourcePlatform.DIRECT_LINK,
            title = fileName,
            author = Uri.parse(url).host ?: "Web Video",
            description = "Direct video URL",
            thumbnailUrl = null,
            playableUri = if (isPlayable) url else null,
            durationMs = durationMs,
            transcriptCues = emptyList(),
            isStreamPlayable = isPlayable
        )
    }

    private fun pickBestDirectFormatUrl(formats: JSONArray): String? {
        var bestUrl: String? = null
        var bestHeight = 0
        for (i in 0 until formats.length()) {
            val obj = formats.optJSONObject(i) ?: continue
            val directUrl = obj.optString("url", "")
            val mime = obj.optString("mimeType", "")
            val height = obj.optInt("height", 0)
            if (directUrl.startsWith("http") && mime.contains("video/mp4") && height >= bestHeight) {
                bestHeight = height
                bestUrl = directUrl
            }
        }
        return bestUrl
    }

    private fun pickBestCaptionTrackUrl(tracks: JSONArray?): String? {
        if (tracks == null || tracks.length() == 0) return null
        var fallbackUrl: String? = null
        for (i in 0 until tracks.length()) {
            val track = tracks.optJSONObject(i) ?: continue
            val baseUrl = track.optString("baseUrl", "")
            val lang = track.optString("languageCode", "")
            if (baseUrl.isNotBlank()) {
                if (lang.startsWith("en")) return baseUrl
                if (fallbackUrl == null) fallbackUrl = baseUrl
            }
        }
        return fallbackUrl
    }

    private fun fetchYouTubeCaptions(baseUrl: String): List<TranscriptChunk> {
        val chunks = mutableListOf<TranscriptChunk>()
        runCatching {
            val req = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", DESKTOP_UA)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val xml = resp.body?.string().orEmpty()
                if (xml.isBlank()) return@use

                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(StringReader(xml))
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "text") {
                        val startSec = parser.getAttributeValue(null, "start")?.toDoubleOrNull() ?: 0.0
                        val durSec = parser.getAttributeValue(null, "dur")?.toDoubleOrNull() ?: 2.5
                        val rawText = parser.nextText()
                        val cleanText = decodeHtmlEntities(rawText).replace("\n", " ").trim()
                        if (cleanText.isNotBlank()) {
                            val startMs = (startSec * 1000).toLong()
                            val endMs = startMs + (durSec * 1000).toLong().coerceAtLeast(800L)
                            chunks.add(TranscriptChunk(startMs, endMs, cleanText))
                        }
                    }
                    eventType = parser.next()
                }
            }
        }.onFailure { Log.w(TAG, "Failed parsing YouTube captions: ${it.message}") }
        return chunks
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val patterns = listOf(
            "(?:v=|/v/|youtu\\.be/|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})"
        )
        for (p in patterns) {
            val m = Pattern.compile(p).matcher(url)
            if (m.find()) return m.group(1)
        }
        return null
    }

    private fun extractFirstUrl(text: String): String {
        val trimmed = text.trim()
        val m = Pattern.compile("(https?://\\S+|content://\\S+)").matcher(trimmed)
        return if (m.find()) m.group(1).orEmpty() else trimmed
    }

    private fun extractBalancedJson(html: String, prefix: String): String? {
        val startIdx = html.indexOf(prefix)
        if (startIdx < 0) return null
        val jsonStart = html.indexOf('{', startIdx + prefix.length)
        if (jsonStart < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in jsonStart until html.length) {
            val c = html[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\' && inString) {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        return html.substring(jsonStart, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun extractMetaTag(html: String, property: String): String? {
        val regex = Pattern.compile(
            "<meta[^>]+(?:property|name)=[\"']${Pattern.quote(property)}[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        )
        val m = regex.matcher(html)
        if (m.find()) return m.group(1)?.replace("&amp;", "&")
        val revRegex = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"']${Pattern.quote(property)}[\"']",
            Pattern.CASE_INSENSITIVE
        )
        val m2 = revRegex.matcher(html)
        return if (m2.find()) m2.group(1)?.replace("&amp;", "&") else null
    }

    private fun extractJsonStringField(html: String, key: String): String? {
        val m = Pattern.compile("\"${Pattern.quote(key)}\"\\s*:\\s*\"([^\"]+)\"").matcher(html)
        if (!m.find()) return null
        return m.group(1)
            ?.replace("\\u0026", "&")
            ?.replace("\\/", "/")
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x27;", "'")
            .replace("&#x2F;", "/")
    }

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
