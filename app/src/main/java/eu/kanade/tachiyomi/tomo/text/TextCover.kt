package eu.kanade.tachiyomi.tomo.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.io.ByteArrayOutputStream

private const val WIDTH = 600
private const val HEIGHT = 900

/** A stable hue for a title, so a work keeps its colour everywhere and between installs. */
internal fun coverHue(title: String): Float = Math.floorMod(title.lowercase().hashCode(), 360).toFloat()

/**
 * Book cover for text works, which have none: a gradient in the work's own colour, a spine shadow, the title and the
 * author. Encoded as PNG to be stored as the series' custom cover.
 */
fun renderTextCover(title: String, author: String?): ByteArray {
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val hue = coverHue(title)
    val top = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.70f))
    val bottom = Color.HSVToColor(floatArrayOf(hue, 0.65f, 0.22f))
    canvas.drawRect(
        0f,
        0f,
        WIDTH.toFloat(),
        HEIGHT.toFloat(),
        Paint().apply {
            shader =
                LinearGradient(0f, 0f, WIDTH * 0.6f, HEIGHT.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        },
    )
    canvas.drawRect(0f, 0f, 30f, HEIGHT.toFloat(), Paint().apply { color = Color.argb(64, 0, 0, 0) })

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 58f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    val textWidth = WIDTH - 110
    StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, textWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.1f)
        .setMaxLines(7)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
        .apply {
            canvas.save()
            canvas.translate(70f, 80f)
            draw(canvas)
            canvas.restore()
        }
    if (!author.isNullOrBlank()) {
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 235, 228, 250)
            textSize = 32f
        }
        val line = TextUtils.ellipsize(author, authorPaint, textWidth.toFloat(), TextUtils.TruncateAt.END)
        canvas.drawText(line, 0, line.length, 70f, HEIGHT - 70f, authorPaint)
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        out.toByteArray()
    }
}
