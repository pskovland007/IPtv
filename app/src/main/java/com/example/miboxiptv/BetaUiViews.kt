package com.example.miboxiptv

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class SunbeamEyeSplashView(context: Context) : View(context) {
    var eyeOpenFraction: Float = 0.05f
        set(value) {
            field = value.coerceIn(0.02f, 1f)
            invalidate()
        }
    var lookOffsetX: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rayPath = Path()
    private val eyePath = Path()
    private val lashPath = Path()
    private val irisRect = RectF()

    private val colNavy = Color.parseColor("#0B2742")
    private val colCobalt = Color.parseColor("#2B6ED2")
    private val colCoral = Color.parseColor("#EF7540")
    private val colBerry = Color.parseColor("#B82A6B")
    private val colEmerald = Color.parseColor("#18A086")
    private val colGold = Color.parseColor("#E5BC62")
    private val colOutline = Color.parseColor("#0B1320")
    private val colSclera = Color.parseColor("#F7F9FC")

    private val rayColors = intArrayOf(
        colCobalt, colNavy, colCoral, colNavy,
        colCobalt, colNavy, colBerry, colNavy,
        colEmerald, colNavy, colGold, colNavy,
        colCoral, colNavy, colBerry, colNavy
    )
    private val irisStarts = floatArrayOf(200f, 270f, 330f, 35f, 90f)
    private val irisSweeps = floatArrayOf(70f, 60f, 65f, 55f, 110f)
    private val irisColors = intArrayOf(
        Color.parseColor("#1E88E5"),
        Color.parseColor("#00B8D4"),
        Color.parseColor("#7C4DFF"),
        Color.parseColor("#FFD54F"),
        Color.parseColor("#00BFA5")
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * 0.5f
        val cy = h * 0.55f
        val maxR = Math.hypot(w.toDouble(), h.toDouble()).toFloat()

        canvas.drawColor(colNavy)
        val stepDeg = 360f / rayColors.size
        val startOffset = -stepDeg * 0.5f
        for (i in rayColors.indices) {
            val a1 = Math.toRadians((startOffset + i * stepDeg).toDouble())
            val a2 = Math.toRadians((startOffset + (i + 1) * stepDeg).toDouble())
            rayPath.reset()
            rayPath.moveTo(cx, cy)
            rayPath.lineTo(cx + (maxR * Math.cos(a1)).toFloat(), cy + (maxR * Math.sin(a1)).toFloat())
            rayPath.lineTo(cx + (maxR * Math.cos(a2)).toFloat(), cy + (maxR * Math.sin(a2)).toFloat())
            rayPath.close()
            fillPaint.color = rayColors[i]
            canvas.drawPath(rayPath, fillPaint)
        }

        // Larger, commanding eye proportion (~52% of 16:9 width) with safe headroom for lashes
        val fullEyeRy = minOf(w * 0.165f, h * 0.305f)
        val eyeRx = fullEyeRy * 1.60f
        val curEyeRy = fullEyeRy * eyeOpenFraction

        val outlineW = fullEyeRy * 0.165f
        val lashW = fullEyeRy * 0.095f

        strokePaint.color = colOutline
        strokePaint.strokeWidth = lashW
        val lashLift = 0.35f + 0.65f * eyeOpenFraction

        val l1x = cx + eyeRx * 0.10f
        val l1y = cy - curEyeRy * 0.72f
        lashPath.reset()
        lashPath.moveTo(l1x, l1y)
        lashPath.cubicTo(
            l1x + eyeRx * 0.08f, l1y - fullEyeRy * 0.42f * lashLift,
            l1x + eyeRx * 0.02f, l1y - fullEyeRy * 0.72f * lashLift,
            l1x - eyeRx * 0.10f, l1y - fullEyeRy * 0.60f * lashLift
        )
        canvas.drawPath(lashPath, strokePaint)

        val l2x = cx + eyeRx * 0.34f
        val l2y = cy - curEyeRy * 0.60f
        lashPath.reset()
        lashPath.moveTo(l2x, l2y)
        lashPath.cubicTo(
            l2x + eyeRx * 0.14f, l2y - fullEyeRy * 0.36f * lashLift,
            l2x + eyeRx * 0.14f, l2y - fullEyeRy * 0.66f * lashLift,
            l2x + eyeRx * 0.01f, l2y - fullEyeRy * 0.58f * lashLift
        )
        canvas.drawPath(lashPath, strokePaint)

        val l3x = cx + eyeRx * 0.56f
        val l3y = cy - curEyeRy * 0.42f
        lashPath.reset()
        lashPath.moveTo(l3x, l3y)
        lashPath.cubicTo(
            l3x + eyeRx * 0.18f, l3y - fullEyeRy * 0.26f * lashLift,
            l3x + eyeRx * 0.22f, l3y - fullEyeRy * 0.52f * lashLift,
            l3x + eyeRx * 0.10f, l3y - fullEyeRy * 0.46f * lashLift
        )
        canvas.drawPath(lashPath, strokePaint)

        eyePath.reset()
        eyePath.moveTo(cx - eyeRx, cy)
        eyePath.quadTo(cx, cy - curEyeRy * 1.48f, cx + eyeRx, cy)
        eyePath.quadTo(cx, cy + curEyeRy * 1.48f, cx - eyeRx, cy)
        eyePath.close()

        canvas.save()
        canvas.clipPath(eyePath)
        fillPaint.color = colSclera
        canvas.drawPath(eyePath, fillPaint)

        val irisCx = cx + lookOffsetX * eyeRx * 0.28f
        val irisCy = cy
        val irisR = fullEyeRy * 0.72f
        val pupilR = irisR * 0.48f
        val hlR = pupilR * 0.34f

        irisRect.set(irisCx - irisR, irisCy - irisR, irisCx + irisR, irisCy + irisR)
        for (i in irisColors.indices) {
            fillPaint.color = irisColors[i]
            canvas.drawArc(irisRect, irisStarts[i], irisSweeps[i], true, fillPaint)
        }
        strokePaint.color = colOutline
        strokePaint.strokeWidth = fullEyeRy * 0.085f
        canvas.drawCircle(irisCx, irisCy, irisR, strokePaint)

        fillPaint.color = colOutline
        canvas.drawCircle(irisCx, irisCy, pupilR, fillPaint)

        fillPaint.color = Color.WHITE
        canvas.drawCircle(irisCx + pupilR * 0.36f, irisCy - pupilR * 0.34f, hlR, fillPaint)
        canvas.restore()

        strokePaint.color = colOutline
        strokePaint.strokeWidth = outlineW
        canvas.drawPath(eyePath, strokePaint)
    }
}

class DeviceGlyphIconView(context: Context, private val isTv: Boolean) : View(context) {
    var glyphColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = minOf(w, h)
        val sw = (s * 0.068f).coerceAtLeast(3f)
        strokePaint.color = glyphColor
        strokePaint.strokeWidth = sw
        fillPaint.color = glyphColor

        if (isTv) {
            val padX = w * 0.08f
            val topY = h * 0.16f
            val botY = h * 0.72f
            rect.set(padX, topY, w - padX, botY)
            canvas.drawRoundRect(rect, s * 0.10f, s * 0.10f, strokePaint)
            canvas.drawLine(w * 0.5f, botY, w * 0.5f, h * 0.85f, strokePaint)
            canvas.drawLine(w * 0.28f, h * 0.86f, w * 0.72f, h * 0.86f, strokePaint)
        } else {
            val bodyW = s * 0.54f
            val bodyH = s * 0.84f
            val left = (w - bodyW) / 2f
            val top = (h - bodyH) / 2f
            rect.set(left, top, left + bodyW, top + bodyH)
            canvas.drawRoundRect(rect, s * 0.11f, s * 0.11f, strokePaint)
            canvas.drawLine(w * 0.43f, top + bodyH * 0.11f, w * 0.57f, top + bodyH * 0.11f, strokePaint)
            canvas.drawCircle(w * 0.5f, top + bodyH * 0.87f, sw * 0.65f, fillPaint)
        }
    }
}

class ThemeColorDotView(context: Context, private val themeStyle: AppThemeStyle) : View(context) {
    var showCheck: Boolean = false
        set(value) { field = value; invalidate() }
    var isOnAccentBg: Boolean = false
        set(value) { field = value; invalidate() }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val checkPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w * 0.5f
        val cy = h * 0.5f
        val r = minOf(w, h) * 0.44f

        if (showCheck && isOnAccentBg) {
            strokePaint.color = themeStyle.textOnAccent
            strokePaint.strokeWidth = (r * 0.24f).coerceAtLeast(2.6f)
            checkPath.reset()
            checkPath.moveTo(cx - r * 0.55f, cy + r * 0.02f)
            checkPath.lineTo(cx - r * 0.12f, cy + r * 0.46f)
            checkPath.lineTo(cx + r * 0.62f, cy - r * 0.40f)
            canvas.drawPath(checkPath, strokePaint)
            return
        }

        val bgCol = Color.rgb(
            Color.red(themeStyle.panelBgColor),
            Color.green(themeStyle.panelBgColor),
            Color.blue(themeStyle.panelBgColor)
        )
        rect.set(cx - r, cy - r, cx + r, cy + r)
        fillPaint.color = bgCol
        canvas.drawArc(rect, 90f, 180f, true, fillPaint)
        fillPaint.color = themeStyle.accentColor
        canvas.drawArc(rect, -90f, 180f, true, fillPaint)

        strokePaint.color = if (isOnAccentBg) Color.argb(220, 255, 255, 255) else Color.argb(140, 255, 255, 255)
        strokePaint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, r, strokePaint)

        if (showCheck) {
            strokePaint.color = Color.WHITE
            strokePaint.strokeWidth = (r * 0.22f).coerceAtLeast(2.2f)
            checkPath.reset()
            checkPath.moveTo(cx - r * 0.42f, cy + r * 0.04f)
            checkPath.lineTo(cx - r * 0.08f, cy + r * 0.36f)
            checkPath.lineTo(cx + r * 0.46f, cy - r * 0.30f)
            canvas.drawPath(checkPath, strokePaint)
        }
    }
}

class ThemeLivePreviewView(context: Context, initialTheme: AppThemeStyle) : View(context) {
    var themeStyle: AppThemeStyle = initialTheme
        set(value) {
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val clipPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pad = 18f
        val availW = (w - pad * 2f).coerceAtLeast(60f)
        val availH = (h - pad * 2f).coerceAtLeast(40f)
        val targetAspect = 16f / 9f
        val frameW: Float
        val frameH: Float
        if (availW / availH > targetAspect) {
            frameH = availH
            frameW = frameH * targetAspect
        } else {
            frameW = availW
            frameH = frameW / targetAspect
        }
        val left = (w - frameW) * 0.5f
        val top = (h - frameH) * 0.5f
        val right = left + frameW
        val bottom = top + frameH
        val frameR = (frameH * 0.075f).coerceIn(12f, 24f)

        val accent = themeStyle.accentColor
        val panelRgb = Color.rgb(
            Color.red(themeStyle.panelBgColor),
            Color.green(themeStyle.panelBgColor),
            Color.blue(themeStyle.panelBgColor)
        )
        val cardRgb = Color.rgb(
            Color.red(themeStyle.cardBgColor),
            Color.green(themeStyle.cardBgColor),
            Color.blue(themeStyle.cardBgColor)
        )
        val textCol = themeStyle.textPrimaryColor

        // 1. Soft evenly-fading Ambilight outer glow around miniature TV frame
        val glowSteps = intArrayOf(12, 24, 42, 68, 104)
        for (i in glowSteps.indices) {
            val spread = (glowSteps.size - i) * 3f
            strokePaint.color = Color.argb(glowSteps[i], Color.red(accent), Color.green(accent), Color.blue(accent))
            strokePaint.strokeWidth = 3.5f
            rect.set(left - spread, top - spread, right + spread, bottom + spread)
            canvas.drawRoundRect(rect, frameR + spread * 0.6f, frameR + spread * 0.6f, strokePaint)
        }

        // 2. Clipped TV screen interior
        clipPath.reset()
        rect.set(left, top, right, bottom)
        clipPath.addRoundRect(rect, frameR, frameR, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // Screen backdrop simulating video ambiance matched to theme
        val screenTopCol = when (themeStyle.id) {
            3 -> Color.parseColor("#D4D4D8")
            2 -> Color.parseColor("#050507")
            1 -> Color.parseColor("#110D1F")
            4 -> Color.parseColor("#2A221A")
            else -> Color.parseColor("#121926")
        }
        val screenBotCol = when (themeStyle.id) {
            3 -> Color.parseColor("#A1A1AA")
            2 -> Color.parseColor("#0E0E12")
            1 -> Color.parseColor("#1B1433")
            4 -> Color.parseColor("#4A3620")
            else -> Color.parseColor("#1E2C42")
        }
        fillPaint.shader = LinearGradient(left, top, right, bottom, screenTopCol, screenBotCol, Shader.TileMode.CLAMP)
        canvas.drawRect(left, top, right, bottom, fillPaint)
        fillPaint.shader = null

        // Subtle soft cinema glow on video right side
        fillPaint.shader = RadialGradient(
            left + frameW * 0.72f, top + frameH * 0.45f, frameW * 0.42f,
            Color.argb(72, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(left, top, right, bottom, fillPaint)
        fillPaint.shader = null

        // 3. Top-right mini signal bars & top-left mini clock pill
        fillPaint.color = Color.argb(140, Color.red(panelRgb), Color.green(panelRgb), Color.blue(panelRgb))
        canvas.drawRoundRect(RectF(left + frameW * 0.04f, top + frameH * 0.05f, left + frameW * 0.16f, top + frameH * 0.13f), 6f, 6f, fillPaint)
        fillPaint.color = Color.argb(190, Color.red(textCol), Color.green(textCol), Color.blue(textCol))
        canvas.drawRoundRect(RectF(left + frameW * 0.06f, top + frameH * 0.08f, left + frameW * 0.14f, top + frameH * 0.10f), 2f, 2f, fillPaint)

        for (b in 0..3) {
            val bx = right - frameW * 0.09f + b * (frameW * 0.014f)
            val bh = frameH * (0.025f + b * 0.012f)
            fillPaint.color = Color.parseColor("#4CAF50")
            canvas.drawRoundRect(RectF(bx, top + frameH * 0.11f - bh, bx + frameW * 0.009f, top + frameH * 0.11f), 2f, 2f, fillPaint)
        }

        val miniR = (themeStyle.cornerRadiusDp * 0.35f).coerceIn(5f, 12f)

        if (themeStyle.isCarouselMenu) {
            // Carousel theme preview: horizontal sliding cards centered
            val cardY1 = top + frameH * 0.24f
            val cardY2 = top + frameH * 0.72f
            fillPaint.color = Color.argb(175, Color.red(cardRgb), Color.green(cardRgb), Color.blue(cardRgb))
            canvas.drawRoundRect(RectF(left + frameW * 0.07f, cardY1 + 10f, left + frameW * 0.29f, cardY2 - 10f), miniR, miniR, fillPaint)
            canvas.drawRoundRect(RectF(left + frameW * 0.71f, cardY1 + 10f, left + frameW * 0.93f, cardY2 - 10f), miniR, miniR, fillPaint)
            // Center highlighted carousel tile
            val cRect = RectF(left + frameW * 0.33f, cardY1, left + frameW * 0.67f, cardY2)
            fillPaint.color = Color.argb(235, Color.red(panelRgb), Color.green(panelRgb), Color.blue(panelRgb))
            canvas.drawRoundRect(cRect, miniR + 2f, miniR + 2f, fillPaint)
            strokePaint.color = accent
            strokePaint.strokeWidth = 2.6f
            canvas.drawRoundRect(cRect, miniR + 2f, miniR + 2f, strokePaint)
            fillPaint.color = accent
            canvas.drawRoundRect(RectF(left + frameW * 0.40f, top + frameH * 0.56f, left + frameW * 0.60f, top + frameH * 0.62f), 4f, 4f, fillPaint)
        } else {
            // Standard themes preview: mini sidebar + active pill + mini OSD bar
            val sbLeft = left + frameW * 0.04f
            val sbTop = top + frameH * 0.17f
            val sbRight = left + frameW * 0.39f
            val sbBot = bottom - frameH * 0.19f
            fillPaint.color = Color.argb(232, Color.red(panelRgb), Color.green(panelRgb), Color.blue(panelRgb))
            canvas.drawRoundRect(RectF(sbLeft, sbTop, sbRight, sbBot), miniR, miniR, fillPaint)
            strokePaint.color = Color.argb(145, Color.red(accent), Color.green(accent), Color.blue(accent))
            strokePaint.strokeWidth = 1.8f
            canvas.drawRoundRect(RectF(sbLeft, sbTop, sbRight, sbBot), miniR, miniR, strokePaint)

            // 4 channel rows inside mini sidebar (2nd row active)
            val sbInnerH = sbBot - sbTop - 16f
            val rowStep = sbInnerH / 4f
            for (r in 0 until 4) {
                val ry1 = sbTop + 8f + r * rowStep + 2f
                val ry2 = ry1 + rowStep - 5f
                if (r == 1) {
                    fillPaint.color = accent
                    canvas.drawRoundRect(RectF(sbLeft + 7f, ry1, sbRight - 7f, ry2), 6f, 6f, fillPaint)
                    fillPaint.color = themeStyle.textOnAccent
                    canvas.drawRoundRect(RectF(sbLeft + 14f, (ry1 + ry2) * 0.5f - 2.5f, sbRight - 22f, (ry1 + ry2) * 0.5f + 2.5f), 2f, 2f, fillPaint)
                } else {
                    fillPaint.color = Color.argb(120, Color.red(textCol), Color.green(textCol), Color.blue(textCol))
                    val barEnd = sbRight - if (r == 2) 30f else 18f
                    canvas.drawRoundRect(RectF(sbLeft + 14f, (ry1 + ry2) * 0.5f - 2f, barEnd, (ry1 + ry2) * 0.5f + 2f), 2f, 2f, fillPaint)
                }
            }
        }

        // 4. Miniature bottom OSD bar with EPG progress
        val osdLeft = left + frameW * 0.08f
        val osdRight = right - frameW * 0.08f
        val osdTop = bottom - frameH * 0.14f
        val osdBot = bottom - frameH * 0.045f
        fillPaint.color = Color.argb(220, Color.red(panelRgb), Color.green(panelRgb), Color.blue(panelRgb))
        canvas.drawRoundRect(RectF(osdLeft, osdTop, osdRight, osdBot), miniR * 0.7f, miniR * 0.7f, fillPaint)
        val barY = (osdTop + osdBot) * 0.5f
        val barL = osdLeft + frameW * 0.06f
        val barR = osdRight - frameW * 0.06f
        fillPaint.color = Color.argb(80, Color.red(textCol), Color.green(textCol), Color.blue(textCol))
        canvas.drawRoundRect(RectF(barL, barY - 2.5f, barR, barY + 2.5f), 2.5f, 2.5f, fillPaint)
        fillPaint.color = accent
        canvas.drawRoundRect(RectF(barL, barY - 2.5f, barL + (barR - barL) * 0.58f, barY + 2.5f), 2.5f, 2.5f, fillPaint)
        canvas.drawCircle(barL + (barR - barL) * 0.58f, barY, 4.5f, fillPaint)

        canvas.restore()

        // 5. Crisp TV frame border
        strokePaint.color = Color.argb(200, Color.red(accent), Color.green(accent), Color.blue(accent))
        strokePaint.strokeWidth = 2.4f
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, frameR, frameR, strokePaint)
    }
}

data class StatsModalRowData(
    val isAnalyzerMode: Boolean,
    val isEnabled: Boolean,
    val title: String,
    val primaryCount: Int,
    val secondaryCount: Int,
    val totalCount: Int
)

class StatsStatusIconView(context: Context) : View(context) {
    var isAnalyzerMode: Boolean = false
        set(value) { field = value; invalidate() }
    var isEnabledState: Boolean = true
        set(value) { field = value; invalidate() }
    var isHighlighted: Boolean = false
        set(value) { field = value; invalidate() }
    var iconTintOnAccent: Int = Color.BLACK
        set(value) { field = value; invalidate() }
    var iconTintNormal: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w * 0.5f
        val cy = h * 0.5f
        val r = minOf(w, h) * 0.42f

        if (isAnalyzerMode) {
            fillPaint.color = if (isHighlighted) iconTintOnAccent else Color.argb(185, Color.red(iconTintNormal), Color.green(iconTintNormal), Color.blue(iconTintNormal))
            path.reset()
            path.moveTo(cx - r * 0.42f, cy - r * 0.58f)
            path.lineTo(cx + r * 0.62f, cy)
            path.lineTo(cx - r * 0.42f, cy + r * 0.58f)
            path.close()
            canvas.drawPath(path, fillPaint)
        } else {
            val badgeCol = if (isEnabledState) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
            fillPaint.color = badgeCol
            canvas.drawCircle(cx, cy, r, fillPaint)

            strokePaint.color = Color.WHITE
            strokePaint.strokeWidth = (r * 0.24f).coerceAtLeast(2.2f)
            if (isEnabledState) {
                path.reset()
                path.moveTo(cx - r * 0.44f, cy + r * 0.04f)
                path.lineTo(cx - r * 0.10f, cy + r * 0.38f)
                path.lineTo(cx + r * 0.48f, cy - r * 0.34f)
                canvas.drawPath(path, strokePaint)
            } else {
                val d = r * 0.34f
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, strokePaint)
                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, strokePaint)
            }
        }
    }
}

class StatsProgressBarView(context: Context) : View(context) {
    var greenFraction: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var redFraction: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var trackColor: Int = Color.parseColor("#2B313C")
        set(value) { field = value; invalidate() }
    var greenColor: Int = Color.parseColor("#22C55E")
        set(value) { field = value; invalidate() }
    var redColor: Int = Color.parseColor("#EF4444")
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val clipPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val barH = (h * 0.46f).coerceIn(7f, 13f)
        val top = (h - barH) * 0.5f
        val bot = top + barH
        val rad = barH * 0.5f

        rect.set(0f, top, w, bot)
        paint.color = trackColor
        canvas.drawRoundRect(rect, rad, rad, paint)

        if (greenFraction <= 0f && redFraction <= 0f) return

        clipPath.reset()
        clipPath.addRoundRect(rect, rad, rad, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        val greenW = (w * greenFraction).coerceIn(0f, w)
        if (greenW > 0f) {
            val drawW = greenW.coerceAtLeast(barH)
            paint.color = greenColor
            canvas.drawRoundRect(RectF(0f, top, drawW.coerceAtMost(w), bot), rad, rad, paint)
        }
        val redW = (w * redFraction).coerceIn(0f, (w - greenW).coerceAtLeast(0f))
        if (redW > 0f) {
            val startX = greenW
            val endX = (startX + redW).coerceAtMost(w)
            paint.color = redColor
            canvas.drawRect(startX, top, endX, bot, paint)
        }
        canvas.restore()
    }
}

class StructuredStatsRowView(context: Context) : LinearLayout(context) {
    private val statusIcon = StatsStatusIconView(context)
    private val titleTv = TextView(context)
    private val progressBar = StatsProgressBarView(context)
    private val rightBox = LinearLayout(context)
    private val rightPrimaryTv = TextView(context)
    private val rightSecondaryTv = TextView(context)
    private var currentData: StatsModalRowData? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        weightSum = 1f
        setPadding(18, 0, 18, 0)

        val leftZone = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 0.40f)
        }
        statusIcon.layoutParams = LayoutParams(26, 26).apply { rightMargin = 14 }
        titleTv.apply {
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        leftZone.addView(statusIcon)
        leftZone.addView(titleTv)

        val midZone = FrameLayout(context).apply {
            setPadding(14, 0, 14, 0)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 0.30f)
        }
        progressBar.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            24,
            Gravity.CENTER
        )
        midZone.addView(progressBar)

        rightBox.apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 0.30f)
        }
        rightPrimaryTv.apply {
            isSingleLine = true
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                fontFeatureSettings = "tnum"
            }
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        rightSecondaryTv.apply {
            isSingleLine = true
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                fontFeatureSettings = "tnum"
            }
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8 }
        }
        rightBox.addView(rightPrimaryTv)
        rightBox.addView(rightSecondaryTv)

        addView(leftZone)
        addView(midZone)
        addView(rightBox)
    }

    private fun isDarkColor(col: Int): Boolean {
        val lum = (Color.red(col) * 299 + Color.green(col) * 587 + Color.blue(col) * 114) / 1000
        return lum < 145
    }

    fun bindData(data: StatsModalRowData, theme: AppThemeStyle, scale: Float, highlighted: Boolean) {
        currentData = data
        statusIcon.isAnalyzerMode = data.isAnalyzerMode
        statusIcon.isEnabledState = data.isEnabled
        titleTv.text = data.title

        val iconPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f * scale, resources.displayMetrics).toInt().coerceIn(24, 54)
        val iconLp = statusIcon.layoutParams as LayoutParams
        if (iconLp.width != iconPx) {
            iconLp.width = iconPx
            iconLp.height = iconPx
            iconLp.rightMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f * scale, resources.displayMetrics).toInt().coerceAtLeast(10)
            statusIcon.layoutParams = iconLp
        }

        val total = data.totalCount.coerceAtLeast(0)
        if (data.isAnalyzerMode) {
            rightSecondaryTv.visibility = VISIBLE
            rightPrimaryTv.text = "✓ ${data.primaryCount}"
            rightSecondaryTv.text = "✕ ${data.secondaryCount}"
            if (total > 0) {
                progressBar.greenFraction = data.primaryCount.toFloat() / total.toFloat()
                progressBar.redFraction = data.secondaryCount.toFloat() / total.toFloat()
            } else {
                progressBar.greenFraction = 0f
                progressBar.redFraction = 0f
            }
        } else {
            rightSecondaryTv.visibility = VISIBLE
            rightPrimaryTv.text = "${data.primaryCount}/${data.secondaryCount}"
            rightSecondaryTv.text = if (data.isEnabled) "ВКЛ" else "ВЫКЛ"
            progressBar.greenFraction = if (total > 0 && data.isEnabled) {
                data.primaryCount.toFloat() / total.toFloat()
            } else {
                0f
            }
            progressBar.redFraction = 0f
        }

        applyThemeAndHighlight(theme, scale, highlighted)
    }

    fun applyThemeAndHighlight(theme: AppThemeStyle, scale: Float, highlighted: Boolean) {
        val data = currentData
        val textSp = 15.2f * scale
        val numSp = 14.6f * scale

        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        rightPrimaryTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, numSp)
        rightSecondaryTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, numSp)

        titleTv.typeface = if (highlighted) theme.boldFont else theme.normalFont
        rightPrimaryTv.typeface = if (highlighted) theme.boldFont else theme.normalFont
        rightSecondaryTv.typeface = if (highlighted) theme.boldFont else theme.normalFont

        statusIcon.isHighlighted = highlighted
        statusIcon.iconTintOnAccent = theme.textOnAccent
        statusIcon.iconTintNormal = theme.textPrimaryColor

        val padH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f * scale, resources.displayMetrics).toInt().coerceAtLeast(16)
        setPadding(padH, 0, padH, 0)

        if (highlighted) {
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(theme.accentColor)
                cornerRadius = 16f * scale
                setStroke(2, Color.WHITE)
            })
            titleTv.setTextColor(theme.textOnAccent)
            val accentIsLight = !isDarkColor(theme.accentColor)
            progressBar.trackColor = if (accentIsLight) Color.parseColor("#382B1E") else Color.argb(95, 0, 0, 0)
            progressBar.greenColor = Color.parseColor("#22C55E")
            progressBar.redColor = Color.parseColor("#EF4444")

            if (data?.isAnalyzerMode == true) {
                rightSecondaryTv.setBackgroundDrawable(null)
                rightSecondaryTv.setPadding(0, 0, 0, 0)
                rightPrimaryTv.setTextColor(if (accentIsLight) Color.parseColor("#0B5D2E") else Color.parseColor("#86EFAC"))
                rightSecondaryTv.setTextColor(if (accentIsLight) Color.parseColor("#991B1B") else Color.parseColor("#FCA5A5"))
            } else {
                rightPrimaryTv.setTextColor(theme.textOnAccent)
                val enabled = data?.isEnabled == true
                rightSecondaryTv.gravity = Gravity.CENTER
                rightSecondaryTv.setPadding((10 * scale).toInt(), (3 * scale).toInt(), (10 * scale).toInt(), (3 * scale).toInt())
                val onBg = when (theme.id) {
                    1 -> Color.parseColor("#00F5A0")
                    2 -> Color.WHITE
                    3 -> Color.parseColor("#27272A")
                    4 -> Color.parseColor("#2E8B6E")
                    else -> Color.parseColor("#22C55E")
                }
                val onTxt = when (theme.id) {
                    1 -> Color.parseColor("#041F17")
                    2 -> Color.BLACK
                    3 -> Color.WHITE
                    4 -> Color.parseColor("#FFF8EB")
                    else -> Color.parseColor("#052E16")
                }
                rightSecondaryTv.setBackgroundDrawable(GradientDrawable().apply {
                    setColor(if (enabled) onBg else Color.argb(120, 15, 23, 42))
                    cornerRadius = if (theme.id == 3) 12f else 999f
                })
                rightSecondaryTv.setTextColor(if (enabled) onTxt else Color.WHITE)
            }
        } else {
            val darkSurface = isDarkColor(theme.panelBgColor)
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(if (darkSurface) Color.argb(68, 0, 0, 0) else Color.argb(24, 0, 0, 0))
                cornerRadius = 14f * scale
                setStroke(1, if (darkSurface) Color.argb(36, 255, 255, 255) else Color.argb(40, 0, 0, 0))
            })
            titleTv.setTextColor(theme.textPrimaryColor)
            progressBar.trackColor = if (darkSurface) Color.argb(52, 255, 255, 255) else Color.parseColor("#D4D4D8")
            progressBar.greenColor = Color.parseColor("#22C55E")
            progressBar.redColor = Color.parseColor("#EF4444")

            if (data?.isAnalyzerMode == true) {
                rightSecondaryTv.setBackgroundDrawable(null)
                rightSecondaryTv.setPadding(0, 0, 0, 0)
                rightPrimaryTv.setTextColor(if (darkSurface) Color.parseColor("#4ADE80") else Color.parseColor("#15803D"))
                rightSecondaryTv.setTextColor(if (darkSurface) Color.parseColor("#F87171") else Color.parseColor("#DC2626"))
            } else {
                rightPrimaryTv.setTextColor(theme.textPrimaryColor)
                val enabled = data?.isEnabled == true
                rightSecondaryTv.gravity = Gravity.CENTER
                rightSecondaryTv.setPadding((10 * scale).toInt(), (3 * scale).toInt(), (10 * scale).toInt(), (3 * scale).toInt())
                val onBg = when (theme.id) {
                    1 -> Color.parseColor("#00F5A0")
                    2 -> Color.WHITE
                    3 -> Color.parseColor("#27272A")
                    4 -> Color.parseColor("#2E8B6E")
                    else -> Color.parseColor("#22C55E")
                }
                val onTxt = when (theme.id) {
                    1 -> Color.parseColor("#041F17")
                    2 -> Color.BLACK
                    3 -> Color.WHITE
                    4 -> Color.parseColor("#FFF8EB")
                    else -> Color.parseColor("#052E16")
                }
                rightSecondaryTv.setBackgroundDrawable(GradientDrawable().apply {
                    setColor(if (enabled) onBg else if (darkSurface) Color.argb(72, 148, 163, 184) else Color.argb(65, 100, 116, 139))
                    cornerRadius = if (theme.id == 3) 12f else 999f
                })
                rightSecondaryTv.setTextColor(if (enabled) onTxt else if (darkSurface) Color.parseColor("#E2E8F0") else Color.parseColor("#334155"))
            }
        }
    }
}
