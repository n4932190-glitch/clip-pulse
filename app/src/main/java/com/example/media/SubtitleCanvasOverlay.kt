package com.example.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import com.example.domain.model.SubtitleCue
import com.example.domain.model.SubtitlePosition
import com.example.domain.model.SubtitleSize
import com.example.domain.model.SubtitleStyle
import kotlin.math.roundToInt

@UnstableApi
class SubtitleCanvasOverlay(
    context: Context,
    private val cues: List<SubtitleCue>,
    private val style: SubtitleStyle,
    private val position: SubtitlePosition,
    private val size: SubtitleSize
) : CanvasOverlay(true) {

    private val typeface: Typeface = runCatching {
        ResourcesCompat.getFont(context, style.fontResId)
    }.getOrNull() ?: Typeface.DEFAULT_BOLD

    private val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(this@SubtitleCanvasOverlay.typeface, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(this@SubtitleCanvasOverlay.typeface, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        textAlign = Paint.Align.LEFT
    }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val timeMs = presentationTimeUs / 1000L
        val activeCue = cues.firstOrNull { timeMs in it.startOffsetMs..it.endOffsetMs } ?: return

        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        if (width <= 0f || height <= 0f) return

        val rawText = if (style.uppercase) activeCue.text.uppercase() else activeCue.text
        val highlight = if (style.uppercase) activeCue.highlightWord.uppercase() else activeCue.highlightWord

        val baseTextSize = (width * 0.058f) * size.scaleFactor
        fillPaint.textSize = baseTextSize
        strokePaint.textSize = baseTextSize
        strokePaint.strokeWidth = baseTextSize * 0.16f

        val maxTextWidth = (width * 0.84f).roundToInt().coerceAtLeast(100)
        val layoutText = buildHighlightedText(rawText, highlight)

        val fillLayout = StaticLayout.Builder
            .obtain(layoutText, 0, layoutText.length, fillPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.12f)
            .setIncludePad(false)
            .build()

        var maxLineWidth = 0f
        for (i in 0 until fillLayout.lineCount) {
            maxLineWidth = maxOf(maxLineWidth, fillLayout.getLineWidth(i))
        }
        val boxWidth = maxLineWidth.coerceAtLeast(120f)
        val textHeight = fillLayout.height.toFloat()

        val centerY = height * position.verticalBias
        val topY = (centerY - textHeight / 2f).coerceIn(height * 0.08f, height - textHeight - height * 0.08f)
        val leftX = (width - maxTextWidth) / 2f
        val pillPadX = width * 0.045f
        val pillPadY = width * 0.025f
        val pillRect = RectF(
            (width - boxWidth) / 2f - pillPadX,
            topY - pillPadY,
            (width + boxWidth) / 2f + pillPadX,
            topY + textHeight + pillPadY
        )

        // Draw style-specific backdrop
        when (style) {
            SubtitleStyle.HORMOZI_POP -> {
                // Strong dark shadow / outline, no heavy box
                strokePaint.color = Color.BLACK
                fillPaint.setShadowLayer(10f, 0f, 5f, Color.argb(210, 0, 0, 0))
            }
            SubtitleStyle.NEON_PULSE -> {
                boxPaint.color = Color.argb(215, 11, 9, 20)
                val radius = width * 0.032f
                canvas.drawRoundRect(pillRect, radius, radius, boxPaint)
                borderPaint.color = Color.rgb(0, 229, 255)
                borderPaint.strokeWidth = 4f
                canvas.drawRoundRect(pillRect, radius, radius, borderPaint)
                fillPaint.clearShadowLayer()
            }
            SubtitleStyle.CLASSIC_PILL -> {
                boxPaint.color = Color.argb(245, 255, 255, 255)
                val radius = width * 0.032f
                canvas.drawRoundRect(pillRect, radius, radius, boxPaint)
                fillPaint.clearShadowLayer()
            }
            SubtitleStyle.CINEMA_MINIMAL -> {
                boxPaint.color = Color.argb(125, 0, 0, 0)
                val radius = width * 0.022f
                canvas.drawRoundRect(pillRect, radius, radius, boxPaint)
                fillPaint.setShadowLayer(6f, 0f, 3f, Color.argb(180, 0, 0, 0))
            }
            SubtitleStyle.TYPEWRITER_MONO -> {
                boxPaint.color = Color.argb(235, 12, 16, 26)
                val radius = width * 0.018f
                canvas.drawRoundRect(pillRect, radius, radius, boxPaint)
                borderPaint.color = Color.rgb(0, 255, 156)
                borderPaint.strokeWidth = 3f
                canvas.drawRoundRect(pillRect, radius, radius, borderPaint)
                fillPaint.clearShadowLayer()
            }
            SubtitleStyle.IMPACT_BOX -> {
                boxPaint.color = Color.argb(238, 255, 77, 109)
                val radius = width * 0.02f
                canvas.drawRoundRect(pillRect, radius, radius, boxPaint)
                fillPaint.setShadowLayer(6f, 0f, 3f, Color.argb(160, 0, 0, 0))
            }
        }

        canvas.save()
        canvas.translate(leftX, topY)

        if (style == SubtitleStyle.HORMOZI_POP || style == SubtitleStyle.IMPACT_BOX) {
            val strokeLayout = StaticLayout.Builder
                .obtain(rawText, 0, rawText.length, strokePaint, maxTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.12f)
                .setIncludePad(false)
                .build()
            strokePaint.color = if (style == SubtitleStyle.IMPACT_BOX) Color.argb(190, 15, 10, 25) else Color.BLACK
            strokeLayout.draw(canvas)
        }

        fillLayout.draw(canvas)
        canvas.restore()
    }

    private fun buildHighlightedText(text: String, highlightWord: String): CharSequence {
        val baseColor = when (style) {
            SubtitleStyle.CLASSIC_PILL -> Color.rgb(20, 18, 34)
            SubtitleStyle.TYPEWRITER_MONO -> Color.rgb(236, 253, 245)
            else -> Color.WHITE
        }
        val accentColor = when (style) {
            SubtitleStyle.HORMOZI_POP -> Color.rgb(255, 230, 0)
            SubtitleStyle.NEON_PULSE -> Color.rgb(0, 229, 255)
            SubtitleStyle.CLASSIC_PILL -> Color.rgb(255, 45, 85)
            SubtitleStyle.CINEMA_MINIMAL -> Color.rgb(255, 214, 102)
            SubtitleStyle.TYPEWRITER_MONO -> Color.rgb(0, 255, 156)
            SubtitleStyle.IMPACT_BOX -> Color.rgb(255, 230, 0)
        }

        fillPaint.color = baseColor
        val ssb = SpannableStringBuilder(text)
        if (highlightWord.isNotBlank()) {
            val idx = text.indexOf(highlightWord, ignoreCase = true)
            if (idx >= 0) {
                ssb.setSpan(
                    ForegroundColorSpan(accentColor),
                    idx,
                    (idx + highlightWord.length).coerceAtMost(text.length),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return ssb
    }
}
