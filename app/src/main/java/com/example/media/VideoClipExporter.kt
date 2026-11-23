package com.example.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.domain.model.FramingCropMode
import com.example.domain.model.ResolvedVideoSource
import com.example.domain.model.SubtitlePosition
import com.example.domain.model.SubtitleSize
import com.example.domain.model.SubtitleStyle
import com.example.domain.model.ViralClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "VideoClipExporter"

data class ExportOutcome(
    val mediaStoreUri: Uri,
    val displayFileName: String,
    val usedFullVideoStream: Boolean
)

class VideoClipExporter(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @OptIn(UnstableApi::class)
    suspend fun exportAndSaveClip(
        source: ResolvedVideoSource,
        clip: ViralClip,
        subtitleStyle: SubtitleStyle,
        subtitlePosition: SubtitlePosition,
        subtitleSize: SubtitleSize,
        cropMode: FramingCropMode,
        onProgress: (Int) -> Unit
    ): ExportOutcome {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempOutputFile = File(exportDir, "temp_clip_${System.currentTimeMillis()}.mp4")
        if (tempOutputFile.exists()) tempOutputFile.delete()

        val hasPlayableStream = source.isStreamPlayable && !source.playableUri.isNullOrBlank()
        val mediaItem: MediaItem
        val isImageMode: Boolean

        if (hasPlayableStream) {
            isImageMode = false
            val safeEndMs = clip.endMs.coerceAtLeast(clip.startMs + 3_000L)
            mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(source.playableUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs.coerceAtLeast(0L))
                        .setEndPositionMs(safeEndMs)
                        .build()
                )
                .build()
        } else {
            isImageMode = true
            val coverUri = prepareCoverImageUri(source.thumbnailUrl, source.title)
            val durationMs = (clip.endMs - clip.startMs).coerceIn(5_000L, 95_000L)
            mediaItem = MediaItem.Builder()
                .setUri(coverUri)
                .setImageDurationMs(durationMs)
                .build()
        }

        val videoEffects = buildList<Effect> {
            when (cropMode) {
                FramingCropMode.LEFT_FOCUS -> add(Crop(-1f, 0.40f, -1f, 1f))
                FramingCropMode.RIGHT_FOCUS -> add(Crop(-0.40f, 1f, -1f, 1f))
                FramingCropMode.CENTER -> Unit
            }
            // Output crisp 9:16 720x1280 portrait Shorts/TikTok frame
            add(Presentation.createForWidthAndHeight(720, 1280, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            val overlay = SubtitleCanvasOverlay(
                context = context,
                cues = clip.subtitles,
                style = subtitleStyle,
                position = subtitlePosition,
                size = subtitleSize
            )
            add(OverlayEffect(listOf(overlay)))
        }

        val editedItemBuilder = EditedMediaItem.Builder(mediaItem)
            .setEffects(androidx.media3.transformer.Effects(emptyList(), videoEffects))
        if (isImageMode) {
            editedItemBuilder.setFrameRate(30)
        }
        val editedMediaItem = editedItemBuilder.build()

        runTransformerOnMainThread(editedMediaItem, tempOutputFile.absolutePath, onProgress)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeTitle = clip.title.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(24)
            .ifBlank { "ViralClip" }
        val fileName = "ClipPulse_${safeTitle}_$timestamp.mp4"

        val savedUri = saveFileToDeviceGallery(tempOutputFile, fileName)
        runCatching { tempOutputFile.delete() }

        return ExportOutcome(
            mediaStoreUri = savedUri,
            displayFileName = fileName,
            usedFullVideoStream = hasPlayableStream
        )
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerOnMainThread(
        editedMediaItem: EditedMediaItem,
        outputPath: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            var polling = true
            val progressHolder = ProgressHolder()

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        polling = false
                        onProgress(100)
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        polling = false
                        Log.e(TAG, "Media3 Transformer export failed", exportException)
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException(
                                    exportException.localizedMessage ?: "Video rendering failed on this device."
                                )
                            )
                        }
                    }
                })
                .build()

            transformer.start(editedMediaItem, outputPath)

            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!polling || !continuation.isActive) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress.coerceIn(0, 99))
                    }
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED && polling) {
                        mainHandler.postDelayed(this, 250L)
                    }
                }
            }
            mainHandler.postDelayed(pollRunnable, 250L)

            continuation.invokeOnCancellation {
                polling = false
                mainHandler.removeCallbacks(pollRunnable)
                transformer.cancel()
            }
        }
    }

    private suspend fun prepareCoverImageUri(thumbnailUrl: String?, title: String): Uri =
        withContext(Dispatchers.IO) {
            val coverFile = File(context.cacheDir, "exports/cover_${System.currentTimeMillis()}.jpg")
            coverFile.parentFile?.mkdirs()

            if (!thumbnailUrl.isNullOrBlank()) {
                val downloaded = runCatching {
                    val req = Request.Builder().url(thumbnailUrl).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bytes = resp.body?.bytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                FileOutputStream(coverFile).use { it.write(bytes) }
                                true
                            } else false
                        } else false
                    }
                }.getOrDefault(false)
                if (downloaded) return@withContext Uri.fromFile(coverFile)
            }

            // Generate a rich 720x1280 gradient backdrop bitmap if no thumbnail is reachable
            val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val gradientPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 720f, 1280f,
                    intArrayOf(
                        Color.rgb(19, 16, 34),
                        Color.rgb(55, 16, 46),
                        Color.rgb(9, 10, 28)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, 720f, 1280f, gradientPaint)
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 255, 255, 255)
                textSize = 34f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(title.take(28), 360f, 240f, titlePaint)
            FileOutputStream(coverFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()
            Uri.fromFile(coverFile)
        }

    private suspend fun saveFileToDeviceGallery(sourceFile: File, displayFileName: String): Uri =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayFileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ClipPulse")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Unable to create MediaStore video entry.")
                try {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Unable to write to MediaStore stream.")
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                    itemUri
                } catch (e: Exception) {
                    resolver.delete(itemUri, null, null)
                    throw e
                }
            } else {
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ClipPulse"
                )
                val destDir = if (moviesDir.exists() || moviesDir.mkdirs()) moviesDir else {
                    File(context.filesDir, "clips").apply { mkdirs() }
                }
                val destFile = File(destDir, displayFileName)
                sourceFile.copyTo(destFile, overwrite = true)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
            }
        }
}

object ShareHelper {
    fun shareClip(context: Context, videoUri: Uri, targetPackage: String? = null, title: String = "Check out this clip") {
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, videoUri)
            putExtra(Intent.EXTRA_TEXT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (!targetPackage.isNullOrBlank()) {
            val targeted = Intent(baseIntent).setPackage(targetPackage)
            if (targeted.resolveActivity(context.packageManager) != null) {
                context.startActivity(targeted)
                return
            }
        }

        val chooser = Intent.createChooser(baseIntent, "Share 9:16 Clip").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    val TIKTOK_PACKAGES = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    const val INSTAGRAM_PACKAGE = "com.instagram.android"

    fun findInstalledTikTokPackage(context: Context): String? {
        return TIKTOK_PACKAGES.firstOrNull { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }
    }
}
