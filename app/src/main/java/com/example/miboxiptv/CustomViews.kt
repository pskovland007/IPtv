package com.example.miboxiptv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MonogramBadgeSpan(private val letters: String, private val bgColor: Int) : ReplacementSpan() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = 68

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        textPaint: Paint
    ) {
        val cy = (top + bottom) / 2f
        val w = 50f
        val h = 34f
        rect.set(x + 2f, cy - h / 2f, x + 2f + w, cy + h / 2f)

        paint.style = Paint.Style.FILL
        paint.color = bgColor
        canvas.drawRoundRect(rect, 9f, 9f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = Color.argb(190, 255, 255, 255)
        canvas.drawRoundRect(rect, 9f, 9f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = if (letters.length >= 2) 18f else 20f
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val textY = cy - (fm.descent + fm.ascent) / 2f
        canvas.drawText(letters, rect.centerX(), textY, paint)
    }
}

private val badgePaletteColors = intArrayOf(
    Color.parseColor("#E53935"),
    Color.parseColor("#8E24AA"),
    Color.parseColor("#3949AB"),
    Color.parseColor("#039BE5"),
    Color.parseColor("#00897B"),
    Color.parseColor("#43A047"),
    Color.parseColor("#FB8C00"),
    Color.parseColor("#D81B60"),
    Color.parseColor("#5E35B1"),
    Color.parseColor("#00ACC1")
)

class StatusOrbSpan(private val statusType: Int) : ReplacementSpan() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        return if (statusType == 3 || statusType == 4) 64 else 38
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        textPaint: Paint
    ) {
        val cy = (top + bottom) / 2f
        if (statusType == 3 || statusType == 4) {
            val isOn = (statusType == 3)
            val trackW = 48f
            val trackH = 24f
            val left = x + 4f
            rect.set(left, cy - trackH / 2f, left + trackW, cy + trackH / 2f)

            paint.style = Paint.Style.FILL
            paint.color = if (isOn) Color.parseColor("#00C853") else Color.parseColor("#475569")
            canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = Color.argb(110, 255, 255, 255)
            canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint)

            paint.style = Paint.Style.FILL
            val knobR = 9.5f
            val knobCx = if (isOn) left + trackW - knobR - 3f else left + knobR + 3f
            paint.color = Color.WHITE
            canvas.drawCircle(knobCx, cy, knobR, paint)
            return
        }

        val cx = x + 16f
        val r = 10.5f
        val (cLight, cDark) = when (statusType) {
            1 -> Pair(Color.parseColor("#69F0AE"), Color.parseColor("#00C853"))
            0 -> Pair(Color.parseColor("#FF8A80"), Color.parseColor("#D50000"))
            else -> Pair(Color.parseColor("#80D8FF"), Color.parseColor("#0091EA"))
        }

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.35f, cLight, cDark, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        paint.color = Color.argb(170, 255, 255, 255)
        canvas.drawCircle(cx - r * 0.32f, cy - r * 0.32f, r * 0.28f, paint)
    }
}

fun colorizeStatusText(raw: String): CharSequence {
    if (!raw.contains("[")) return raw
    val ss = SpannableString(raw)
    var i = 0
    while (i < raw.length - 2) {
        when {
            i <= raw.length - 6 && raw.startsWith("[B:", i) && raw[i + 5] == ']' -> {
                val mono = raw.substring(i + 3, i + 5)
                val cIdx = (raw.hashCode() and 0x7FFFFFFF) % badgePaletteColors.size
                ss.setSpan(MonogramBadgeSpan(mono, badgePaletteColors[cIdx]), i, i + 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i += 6
            }
            i <= raw.length - 4 && raw.startsWith("[ON]", i) -> {
                ss.setSpan(StatusOrbSpan(3), i, i + 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i += 4
            }
            i <= raw.length - 5 && raw.startsWith("[OFF]", i) -> {
                ss.setSpan(StatusOrbSpan(4), i, i + 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i += 5
            }
            raw.startsWith("[+]", i) -> {
                ss.setSpan(StatusOrbSpan(1), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i += 3
            }
            raw.startsWith("[-]", i) -> {
                ss.setSpan(StatusOrbSpan(0), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i += 3
            }
            raw.startsWith("[?]", i) || raw.startsWith("[>]", i) -> {
                ss.setSpan(StatusOrbSpan(2), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i += 3
            }
            else -> i++
        }
    }
    return ss
}

class MarqueeRowTextView(context: Context) : TextView(context) {
    var forceMarqueeActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            isSelected = value
            ellipsize = if (value) TextUtils.TruncateAt.MARQUEE else TextUtils.TruncateAt.END
        }

    init {
        isSingleLine = true
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        marqueeRepeatLimit = -1
        isHorizontalFadingEdgeEnabled = false
        includeFontPadding = false
    }

    fun setTextWithSmoothToggle(newText: CharSequence, animateToggle: Boolean) {
        if (!animateToggle || text.isNullOrEmpty() || text.toString() == newText.toString()) {
            text = newText
            alpha = 1f
            return
        }
        animate().cancel()
        alpha = 0.55f
        text = newText
        animate().alpha(1f).setDuration(130L).start()
    }
}

class LiveEqualizerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    var barColor: Int = Color.parseColor("#00E676")
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.color = barColor
        val t = System.currentTimeMillis() / 160.0
        val barWidth = w / 5.2f
        val gap = (w - barWidth * 3f) / 2f

        for (i in 0..2) {
            val phase = t + i * 1.35
            val ratio = (0.28f + 0.68f * ((sin(phase) + 1.0) / 2.0)).toFloat()
            val bh = (h * ratio).coerceAtLeast(4f)
            val left = i * (barWidth + gap)
            rect.set(left, h - bh, left + barWidth, h)
            canvas.drawRoundRect(rect, 3f, 3f, paint)
        }
        postInvalidateDelayed(160L)
    }
}

class ChannelRowView(context: Context) : FrameLayout(context) {
    val badgeView: TextView
    val titleView: MarqueeRowTextView
    val eqView: LiveEqualizerView

    init {
        clipChildren = true
        clipToPadding = true

        badgeView = TextView(context).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Color.WHITE)
            isSingleLine = true
            includeFontPadding = false
            isFocusable = false
            isClickable = false
        }

        titleView = MarqueeRowTextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            isFocusable = false
            isClickable = false
        }

        eqView = LiveEqualizerView(context).apply {
            visibility = View.GONE
            isFocusable = false
            isClickable = false
            layoutParams = LayoutParams(24, 20, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                rightMargin = 10
            }
        }

        addView(badgeView)
        addView(titleView)
        addView(eqView)
    }
}

class RedPowerIconButton(context: Context, private val onClickAction: () -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcRect = RectF()
    private var isBtnFocused = false

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        isClickable = true
        setOnFocusChangeListener { v, hasFocus ->
            isBtnFocused = hasFocus
            v.animate().scaleX(if (hasFocus) 1.15f else 1.0f).scaleY(if (hasFocus) 1.15f else 1.0f).setDuration(150L).start()
            invalidate()
        }
        setOnClickListener { onClickAction() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 4f

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - r * 0.25f, cy - r * 0.25f, r * 1.3f,
            if (isBtnFocused) Color.parseColor("#FF5252") else Color.parseColor("#E53935"),
            Color.parseColor("#8E0000"),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isBtnFocused) 3.2f else 1.8f
        paint.color = if (isBtnFocused) Color.WHITE else Color.argb(140, 255, 255, 255)
        canvas.drawCircle(cx, cy, r, paint)

        paint.color = Color.WHITE
        paint.strokeWidth = 3.2f
        paint.strokeCap = Paint.Cap.ROUND
        val ir = r * 0.46f
        arcRect.set(cx - ir, cy - ir, cx + ir, cy + ir)
        canvas.drawArc(arcRect, -60f, 300f, false, paint)
        canvas.drawLine(cx, cy - ir * 1.15f, cx, cy - ir * 0.15f, paint)
    }
}

class CrtTurnOffView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f

    init {
        visibility = GONE
    }

    fun playCrtAnimation(onComplete: () -> Unit) {
        visibility = VISIBLE
        bringToFront()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                progress = anim.animatedValue as Float
                invalidate()
                if (progress >= 1f) onComplete()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.BLACK)

        if (progress < 0.55f) {
            val p = progress / 0.55f
            val lineH = (h * (1f - p)).coerceAtLeast(4f)
            paint.color = Color.WHITE
            canvas.drawRect(0f, cy(h) - lineH / 2f, w, cy(h) + lineH / 2f, paint)
        } else if (progress < 0.92f) {
            val p = (progress - 0.55f) / 0.37f
            val lineW = (w * (1f - p)).coerceAtLeast(6f)
            paint.color = Color.WHITE
            canvas.drawRect(cx(w) - lineW / 2f, cy(h) - 2f, cx(w) + lineW / 2f, cy(h) + 2f, paint)
        }
    }

    private fun cx(w: Float) = w / 2f
    private fun cy(h: Float) = h / 2f
}

class RealisticEyeSplashView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyePath = Path()
    private val wavePath = Path()

    var eyeOpenFraction: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.02f, 1.0f)
            invalidate()
        }

    var lookOffsetX: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawColor(Color.parseColor("#0B1026"))

        val waveColors = intArrayOf(
            Color.parseColor("#142850"),
            Color.parseColor("#006978"),
            Color.parseColor("#00A896"),
            Color.parseColor("#4A148C"),
            Color.parseColor("#AD1457")
        )
        for (i in waveColors.indices) {
            val yBase = h * (0.18f + i * 0.17f)
            wavePath.reset()
            wavePath.moveTo(0f, yBase)
            wavePath.cubicTo(w * 0.35f, yBase - h * 0.12f, w * 0.68f, yBase + h * 0.08f, w, yBase - h * 0.10f)
            wavePath.lineTo(w, h)
            wavePath.lineTo(0f, h)
            wavePath.close()
            paint.style = Paint.Style.FILL
            paint.color = waveColors[i]
            canvas.drawPath(wavePath, paint)
        }

        val cx = w / 2f
        val cy = h / 2f
        val rx = min(w, h) * 0.34f
        val ry = min(w, h) * 0.20f * eyeOpenFraction

        eyePath.reset()
        eyePath.moveTo(cx - rx, cy)
        eyePath.cubicTo(cx - rx * 0.45f, cy - ry * 1.55f, cx + rx * 0.45f, cy - ry * 1.55f, cx + rx, cy)
        eyePath.cubicTo(cx + rx * 0.45f, cy + ry * 1.55f, cx - rx * 0.45f, cy + ry * 1.55f, cx - rx, cy)
        eyePath.close()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 22f
        paint.color = Color.parseColor("#090C15")
        canvas.drawPath(eyePath, paint)

        canvas.save()
        canvas.clipPath(eyePath)
        canvas.drawColor(Color.parseColor("#E8F1F5"))

        val irisR = min(w, h) * 0.125f
        val irisCx = cx + lookOffsetX * (rx * 0.28f)

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            irisCx, cy, irisR,
            intArrayOf(Color.parseColor("#00BFA5"), Color.parseColor("#00E5FF"), Color.parseColor("#090C15")),
            floatArrayOf(0.2f, 0.78f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(irisCx, cy, irisR, paint)
        paint.shader = null

        paint.color = Color.parseColor("#05070B")
        canvas.drawCircle(irisCx, cy, irisR * 0.45f, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(irisCx + irisR * 0.24f, cy - irisR * 0.24f, irisR * 0.14f, paint)
        canvas.restore()
    }
}

class ThemedTileView(
    context: Context,
    private val iconType: String,
    baseHex: String,
    accentHex: String
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val baseColor = Color.parseColor(baseHex)
    private val accentColor = Color.parseColor(accentHex)

    var themeStyleId: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var isTileFocused: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.43f

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, w, h, baseColor, accentColor, Shader.TileMode.CLAMP)
        if (themeStyleId == 5) {
            canvas.drawCircle(cx, cy, r, paint)
        } else {
            rect.set(w * 0.08f, h * 0.08f, w * 0.92f, h * 0.92f)
            canvas.drawRoundRect(rect, 28f, 28f, paint)
        }
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isTileFocused) 4.5f else 2f
        paint.color = if (isTileFocused) Color.WHITE else Color.argb(120, 255, 255, 255)
        if (themeStyleId == 5) {
            canvas.drawCircle(cx, cy, r, paint)
        } else {
            canvas.drawRoundRect(rect, 28f, 28f, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 4.2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        val s = min(w, h) * 0.21f
        when (iconType) {
            "PLAYLISTS" -> {
                for (i in -1..1) {
                    val y = cy + i * s * 0.65f
                    canvas.drawLine(cx - s * 0.8f, y, cx + s * 0.8f, y, paint)
                }
            }
            "EDITOR" -> {
                canvas.drawLine(cx - s * 0.7f, cy + s * 0.7f, cx + s * 0.7f, cy - s * 0.7f, paint)
                canvas.drawCircle(cx + s * 0.5f, cy - s * 0.5f, s * 0.22f, paint)
            }
            "ANALYZER" -> {
                val path = Path().apply {
                    moveTo(cx - s, cy)
                    lineTo(cx - s * 0.4f, cy)
                    lineTo(cx - s * 0.1f, cy - s * 0.8f)
                    lineTo(cx + s * 0.2f, cy + s * 0.8f)
                    lineTo(cx + s * 0.5f, cy)
                    lineTo(cx + s, cy)
                }
                canvas.drawPath(path, paint)
            }
            "THEMES" -> {
                canvas.drawCircle(cx, cy, s * 0.85f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx - s * 0.3f, cy - s * 0.2f, s * 0.16f, paint)
                canvas.drawCircle(cx + s * 0.3f, cy - s * 0.2f, s * 0.16f, paint)
                canvas.drawCircle(cx, cy + s * 0.35f, s * 0.16f, paint)
            }
            "BUFFER" -> {
                rect.set(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f)
                canvas.drawArc(rect, 135f, 270f, false, paint)
                canvas.drawLine(cx, cy, cx + s * 0.55f, cy - s * 0.35f, paint)
            }
            "DECODER" -> {
                rect.set(cx - s * 0.65f, cy - s * 0.65f, cx + s * 0.65f, cy + s * 0.65f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
            }
            "EPG_GRID" -> {
                rect.set(cx - s * 0.8f, cy - s * 0.65f, cx + s * 0.8f, cy + s * 0.65f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
                canvas.drawLine(cx - s * 0.8f, cy - s * 0.15f, cx + s * 0.8f, cy - s * 0.15f, paint)
            }
            "TOP_CHANNELS" -> {
                val p = Path().apply {
                    moveTo(cx, cy - s * 0.85f)
                    lineTo(cx - s * 0.75f, cy + s * 0.65f)
                    lineTo(cx + s * 0.75f, cy + s * 0.65f)
                    close()
                }
                canvas.drawPath(p, paint)
            }
            "FAVORITES" -> {
                canvas.drawCircle(cx, cy, s * 0.75f, paint)
                canvas.drawLine(cx, cy - s * 0.45f, cx, cy + s * 0.45f, paint)
                canvas.drawLine(cx - s * 0.45f, cy, cx + s * 0.45f, cy, paint)
            }
            "AUDIO" -> {
                canvas.drawLine(cx - s * 0.6f, cy + s * 0.3f, cx - s * 0.6f, cy - s * 0.3f, paint)
                canvas.drawLine(cx, cy + s * 0.7f, cx, cy - s * 0.7f, paint)
                canvas.drawLine(cx + s * 0.6f, cy + s * 0.4f, cx + s * 0.6f, cy - s * 0.4f, paint)
            }
            "CONTROL_MODE" -> {
                rect.set(cx - s * 0.5f, cy - s * 0.85f, cx + s * 0.5f, cy + s * 0.85f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
            }
            else -> {
                canvas.drawCircle(cx, cy, s * 0.45f, paint)
                for (a in 0 until 360 step 60) {
                    val rad = Math.toRadians(a.toDouble())
                    canvas.drawLine(
                        (cx + cos(rad) * s * 0.55f).toFloat(),
                        (cy + sin(rad) * s * 0.55f).toFloat(),
                        (cx + cos(rad) * s * 0.88f).toFloat(),
                        (cy + sin(rad) * s * 0.88f).toFloat(),
                        paint
                    )
                }
            }
        }
    }
}
