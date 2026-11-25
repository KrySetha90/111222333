package com.example.filemanager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** A frosted "liquid glass" panel: translucent fill, soft top sheen and a bright specular edge. */
class GlassDrawable(
    private val tint: Int,
    private val radius: Float,
    private val dark: Boolean,
    private val strong: Boolean = false
) : Drawable() {
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f }
    private val rect = RectF()
    private var a = 255

    private fun w(alpha: Int) = Color.argb(alpha, 255, 255, 255)

    override fun onBoundsChange(b: Rect) {
        rect.set(b.left + 2f, b.top + 2f, b.right - 2f, b.bottom - 2f)
        sheenP.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            intArrayOf(w(if (dark) 55 else 120), w(0)), floatArrayOf(0f, 0.55f), Shader.TileMode.CLAMP)
        edgeP.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(w(if (dark) 150 else 240), w(30), w(30), w(if (dark) 110 else 180)),
            floatArrayOf(0f, 0.38f, 0.62f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        val base = if (dark) (if (strong) 60 else 26) else (if (strong) 185 else 72)
        fillP.color = Color.WHITE; fillP.alpha = base * a / 255
        c.drawRoundRect(rect, radius, radius, fillP)
        if (tint != Color.WHITE) {
            fillP.color = tint; fillP.alpha = (if (strong) 175 else 125) * a / 255
            c.drawRoundRect(rect, radius, radius, fillP)
        }
        c.drawRoundRect(rect, radius, radius, sheenP)
        c.drawRoundRect(rect, radius, radius, edgeP)
    }

    override fun setAlpha(alpha: Int) { a = alpha; invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getOutline(outline: Outline) { outline.setRoundRect(bounds, radius) }
}

/** Colourful blurred orbs on a soft gradient: what the glass panels sit on. */
class GlassBackground(private val style: String) : Drawable() {
    companion object {
        val styles = linkedMapOf("aurora" to "Aurora", "ocean" to "Ocean", "sunset" to "Sunset", "midnight" to "Midnight (dark)")
        fun isDark(s: String) = s == "midnight"
    }

    private class Spec(val top: Int, val bottom: Int, val orbs: List<FloatArray>, val colors: IntArray)

    private fun spec(): Spec = when (style) {
        "ocean" -> Spec(0xFFCFEFFF.toInt(), 0xFFD8E8FF.toInt(),
            listOf(floatArrayOf(.10f, .10f, .75f), floatArrayOf(.92f, .40f, .70f), floatArrayOf(.20f, .80f, .75f), floatArrayOf(.95f, .96f, .55f)),
            intArrayOf(0xFF2E9BFF.toInt(), 0xFF2DE0C8.toInt(), 0xFF6A7BFF.toInt(), 0xFF8EE7FF.toInt()))
        "sunset" -> Spec(0xFFFFE3CF.toInt(), 0xFFF9D5EC.toInt(),
            listOf(floatArrayOf(.10f, .10f, .75f), floatArrayOf(.92f, .30f, .70f), floatArrayOf(.15f, .75f, .75f), floatArrayOf(.92f, .96f, .60f)),
            intArrayOf(0xFFFF8A5B.toInt(), 0xFFFF5E9C.toInt(), 0xFFB06CFF.toInt(), 0xFFFFC857.toInt()))
        "midnight" -> Spec(0xFF0B1026.toInt(), 0xFF1A0F33.toInt(),
            listOf(floatArrayOf(.10f, .10f, .80f), floatArrayOf(.95f, .35f, .70f), floatArrayOf(.10f, .72f, .70f), floatArrayOf(.92f, .96f, .60f)),
            intArrayOf(0xFF5B3CFF.toInt(), 0xFF0FB8C9.toInt(), 0xFFB02A8F.toInt(), 0xFF2A56D6.toInt()))
        else -> Spec(0xFFD9E4FF.toInt(), 0xFFFBE3F4.toInt(),
            listOf(floatArrayOf(.12f, .08f, .80f), floatArrayOf(.95f, .30f, .70f), floatArrayOf(.10f, .62f, .75f), floatArrayOf(.90f, .92f, .70f)),
            intArrayOf(0xFF8B7BFF.toInt(), 0xFF37D5E8.toInt(), 0xFFFF85C0.toInt(), 0xFFFFC46B.toInt()))
    }

    private val paints = ArrayList<Paint>()
    private val basePaint = Paint()

    override fun onBoundsChange(b: Rect) {
        val s = spec(); val w = b.width().toFloat(); val h = b.height().toFloat()
        basePaint.shader = LinearGradient(0f, 0f, 0f, h, s.top, s.bottom, Shader.TileMode.CLAMP)
        paints.clear()
        for ((i, o) in s.orbs.withIndex()) {
            val col = s.colors[i]
            paints.add(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(o[0] * w, o[1] * h, o[2] * maxOf(w, h) * 0.7f,
                    intArrayOf(Color.argb(210, Color.red(col), Color.green(col), Color.blue(col)), Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))),
                    null, Shader.TileMode.CLAMP)
            })
        }
    }

    override fun draw(c: Canvas) {
        val b = bounds
        c.drawRect(b, basePaint)
        for (p in paints) c.drawRect(b, p)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
