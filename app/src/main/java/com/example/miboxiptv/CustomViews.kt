package com.example.miboxiptv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class Channel(val name: String, val url: String, val category: String, val tvgId: String)
data class PlaylistEntry(val title: String, val source: String, val isLocal: Boolean)
data class EpgProgram(val title: String, val desc: String, val startMs: Long, val stopMs: Long)

data class AppThemeStyle(
    val id: Int, val name: String,
    val panelBgColor: Int, val cardBgColor: Int, val accentColor: Int, val strokeColor: Int,
    val textPrimaryColor: Int, val textOnAccent: Int, val cornerRadiusDp: Float,
    val normalFont: Typeface, val boldFont: Typeface,
    val isCarouselMenu: Boolean = false
)

class GraphicOrbSpan(private val orbType: Int) : ReplacementSpan() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        return if (start == 0 || orbType >= 10) 76 else 44
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, textPaint: Paint) {
        val cy = (top + bottom) / 2f
        if (orbType == 10 || orbType == 11) {
            val isOn = (orbType == 10)
            val trackW = 66f; val trackH = 32f
            val left = x + 4f; val topY = cy - trackH / 2f
            rect.set(left, topY, left + trackW, topY + trackH)
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = if (isOn) Color.parseColor("#00C853") else Color.parseColor("#455A64")
            canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f
            paint.color = if (isOn) Color.parseColor("#B9F6CA") else Color.parseColor("#90A4AE")
            canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint)
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            val knobX = if (isOn) (left + trackW - trackH / 2f) else (left + trackH / 2f)
            canvas.drawCircle(knobX, cy, trackH * 0.39f, paint)
            return
        }

        val cx = if (start == 0) (x + 37f) else (x + 20f)
        val r = 14.5f
        val (lightHex, mainHex, darkHex) = when (orbType) {
            1 -> Triple("#80FFC8", "#00E676", "#007E3A")
            2 -> Triple("#FF9E9E", "#FF5252", "#9E1212")
            4 -> Triple("#9BE7FF", "#29B6F6", "#02578A")
            else -> Triple("#FFF099", "#FFD740", "#9E7800")
        }
        val mainColor = Color.parseColor(mainHex)

        // 1) Мягкое внешнее свечение (ореол)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(65, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor))
        canvas.drawCircle(cx, cy, r * 1.28f, paint)

        // 2) Объёмная 3D-сфера через плавный RadialGradient (без грубого белого шарика!)
        paint.shader = RadialGradient(
            cx - r * 0.32f,
            cy - r * 0.35f,
            r * 1.38f,
            intArrayOf(Color.parseColor(lightHex), mainColor, Color.parseColor(darkHex)),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        // 3) Тонкий стеклянный блик-полумесяц по верхнему ободку сферы
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.color = Color.argb(135, 255, 255, 255)
        rect.set(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f)
        canvas.drawArc(rect, 200f, 140f, false, paint)

        // 4) Чёткий символ внутри сферы
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.1f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.parseColor("#0D131A")
        when (orbType) {
            1 -> {
                canvas.drawLine(cx - 5.2f, cy + 0.4f, cx - 1.0f, cy + 4.8f, paint)
                canvas.drawLine(cx - 1.0f, cy + 4.8f, cx + 6.0f, cy - 4.4f, paint)
            }
            2 -> {
                canvas.drawLine(cx - 4.6f, cy - 4.6f, cx + 4.6f, cy + 4.6f, paint)
                canvas.drawLine(cx + 4.6f, cy - 4.6f, cx - 4.6f, cy + 4.6f, paint)
            }
            4 -> {
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx - 3.5f, cy - 5.5f)
                path.lineTo(cx + 5.5f, cy)
                path.lineTo(cx - 3.5f, cy + 5.5f)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }
}

class MonogramBadgeSpan(
    private val letters: String,
    private val bgColor: Int
) : ReplacementSpan() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val boxH = (paint.textSize * 1.05f).toInt().coerceAtLeast(22)
        val boxW = (boxH * 1.15f).toInt().coerceAtLeast(24)
        return boxW + (paint.textSize * 0.45f).toInt().coerceAtLeast(10)
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
        val boxH = (textPaint.textSize * 1.05f).coerceAtLeast(22f)
        val boxW = (boxH * 1.15f).coerceAtLeast(24f)
        val centerY = (top + bottom) / 2f
        val boxTop = centerY - boxH / 2f
        val boxBottom = centerY + boxH / 2f
        rect.set(x + 2f, boxTop, x + 2f + boxW, boxBottom)
        val corner = boxH * 0.28f

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = bgColor
        canvas.drawRoundRect(rect, corner, corner, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = Color.argb(95, 255, 255, 255)
        canvas.drawRoundRect(rect, corner, corner, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = textPaint.textSize * 0.56f
        paint.typeface = Typeface.DEFAULT_BOLD
        val fm = paint.fontMetrics
        val textY = centerY - (fm.ascent + fm.descent) / 2f
        canvas.drawText(letters.take(2), rect.centerX(), textY, paint)
    }
}

typealias StatusOrbSpan = GraphicOrbSpan

fun colorizeStatusText(raw: String): CharSequence {
    if (!raw.contains("[")) return raw
    val ss = SpannableString(raw)
    var i = 0
    val palette = intArrayOf(
        Color.parseColor("#E53935"), Color.parseColor("#8E24AA"), Color.parseColor("#3949AB"),
        Color.parseColor("#039BE5"), Color.parseColor("#00897B"), Color.parseColor("#43A047"),
        Color.parseColor("#FB8C00"), Color.parseColor("#D81B60"), Color.parseColor("#5E35B1"),
        Color.parseColor("#00ACC1")
    )
    while (i <= raw.length - 3) {
        when {
            i <= raw.length - 6 && raw.startsWith("[B:", i) && raw[i + 5] == ']' -> {
                val mono = raw.substring(i + 3, i + 5)
                val cIdx = (raw.hashCode() and 0x7FFFFFFF) % palette.size
                ss.setSpan(MonogramBadgeSpan(mono, palette[cIdx]), i, i + 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i += 6
            }
            i <= raw.length - 4 && raw.startsWith("[ON]", i) -> { ss.setSpan(GraphicOrbSpan(10), i, i + 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 4 }
            i <= raw.length - 5 && raw.startsWith("[OFF]", i) -> { ss.setSpan(GraphicOrbSpan(11), i, i + 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 5 }
            raw.startsWith("[+]", i) -> { ss.setSpan(GraphicOrbSpan(1), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 3 }
            raw.startsWith("[-]", i) -> { ss.setSpan(GraphicOrbSpan(2), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 3 }
            raw.startsWith("[?]", i) -> { ss.setSpan(GraphicOrbSpan(3), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 3 }
            raw.startsWith("[>]", i) -> { ss.setSpan(GraphicOrbSpan(4), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 3 }
            else -> i++
        }
    }
    return ss
}

class CrtTurnOffView(context: Context) : View(context) {
    private var progress: Float = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beamRect = RectF()

    init {
        visibility = GONE
        isClickable = true
        isFocusable = true
    }

    fun playCrtAnimation(onFinished: () -> Unit) {
        visibility = VISIBLE
        bringToFront()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 460L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                progress = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onFinished()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f

        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        if (progress < 0.48f) {
            val p1 = (progress / 0.48f).coerceIn(0f, 1f)
            val openHalfH = (h * 0.5f * (1f - p1)).coerceAtLeast(3.5f)
            val openHalfW = w * (0.5f - 0.08f * p1)
            canvas.drawRect(0f, 0f, w, cy - openHalfH, paint)
            canvas.drawRect(0f, cy + openHalfH, w, h, paint)
            canvas.drawRect(0f, cy - openHalfH, cx - openHalfW, cy + openHalfH, paint)
            canvas.drawRect(cx + openHalfW, cy - openHalfH, w, cy + openHalfH, paint)

            paint.color = Color.argb((180 * p1).toInt(), 210, 240, 255)
            beamRect.set(cx - openHalfW, cy - openHalfH, cx + openHalfW, cy + openHalfH)
            canvas.drawRoundRect(beamRect, 8f, 8f, paint)
        } else if (progress < 0.82f) {
            canvas.drawRect(0f, 0f, w, h, paint)
            val p2 = ((progress - 0.48f) / 0.34f).coerceIn(0f, 1f)
            val halfW = (w * 0.42f * (1f - p2)).coerceAtLeast(6f)
            val halfH = (4.5f * (1f - 0.4f * p2)).coerceAtLeast(2.5f)

            paint.color = Color.argb(110, 100, 220, 255)
            beamRect.set(cx - halfW * 1.15f, cy - halfH * 2.6f, cx + halfW * 1.15f, cy + halfH * 2.6f)
            canvas.drawRoundRect(beamRect, 12f, 12f, paint)

            paint.color = Color.WHITE
            beamRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
            canvas.drawRoundRect(beamRect, 6f, 6f, paint)
        } else {
            canvas.drawRect(0f, 0f, w, h, paint)
            val p3 = ((progress - 0.82f) / 0.18f).coerceIn(0f, 1f)
            val dotR = 14f * (1f - p3)
            val alpha = (255 * (1f - p3)).toInt().coerceIn(0, 255)
            paint.color = Color.argb((alpha * 0.5f).toInt(), 120, 225, 255)
            canvas.drawCircle(cx, cy, dotR * 2.1f, paint)
            paint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, dotR, paint)
        }
    }
}

class RedPowerIconButton(context: Context, onClickAction: () -> Unit) : View(context) {
    private var isBtnFocused = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcRect = RectF()

    init {
        isFocusable = true; isFocusableInTouchMode = false; isClickable = true
        setOnFocusChangeListener { v, hasFocus ->
            isBtnFocused = hasFocus
            v.animate().scaleX(if (hasFocus) 1.15f else 1.0f).scaleY(if (hasFocus) 1.15f else 1.0f).setDuration(180L).start()
            invalidate()
        }
        setOnClickListener { onClickAction() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val r = min(cx, cy) - 4f
        paint.style = Paint.Style.FILL
        paint.color = if (isBtnFocused) Color.parseColor("#FF1744") else Color.parseColor("#C62828")
        canvas.drawCircle(cx, cy, r, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isBtnFocused) 3.5f else 1.8f
        paint.color = if (isBtnFocused) Color.WHITE else Color.parseColor("#FF8A80")
        canvas.drawCircle(cx, cy, r, paint)

        val ir = r * 0.48f
        paint.strokeWidth = 3.5f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Color.WHITE
        arcRect.set(cx - ir, cy - ir + 1.5f, cx + ir, cy + ir + 1.5f)
        canvas.drawArc(arcRect, -55f, 290f, false, paint)
        canvas.drawLine(cx, cy - ir - 2.5f, cx, cy - 1f, paint)
    }
}

class LiveEqualizerView(context: Context) : View(context) {
    var barColor: Int = Color.parseColor("#00E676")
        set(value) {
            if (field != value) {
                field = value
                if (isShown) invalidate()
            }
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val phases = floatArrayOf(0.0f, 1.3f, 2.7f, 4.1f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isShown) return
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = System.currentTimeMillis() / 130.0
        val barW = ((w - 10.5f) / 4f).coerceAtLeast(3f)
        paint.color = barColor
        for (i in 0 until 4) {
            val wave = (sin(t + phases[i]).toFloat() + 1f) / 2f
            val barH = (h * (0.22f + 0.75f * wave)).coerceIn(4f, h)
            val left = i * (barW + 3.5f)
            rect.set(left, h - barH, left + barW, h)
            canvas.drawRoundRect(rect, 3f, 3f, paint)
        }
        if (isShown) postInvalidateDelayed(120L)
    }
}

class MarqueeRowTextView(context: Context) : TextView(context) {
    var forceMarqueeActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            isSelected = value
            setSingleLine(true)
            maxLines = 1
            if (value) {
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                setHorizontallyScrolling(true)
            } else {
                ellipsize = TextUtils.TruncateAt.END
            }
        }

    init {
        setSingleLine(true)
        maxLines = 1
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
    }

    fun setTextWithSmoothToggle(newText: CharSequence, animateToggle: Boolean) {
        val oldStr = text?.toString() ?: ""
        val newStr = newText.toString()
        text = newText
        if (animateToggle && oldStr.isNotEmpty() && oldStr != newStr) {
            alpha = 0.65f
            animate().alpha(1f).setDuration(150L).start()
        }
    }

    override fun isFocused(): Boolean = forceMarqueeActive || super.isFocused()
}

class ChannelRowView(context: Context) : FrameLayout(context) {
    val badgeView = TextView(context).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        setSingleLine(true)
        layoutParams = LayoutParams(44, 34, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = 12 }
    }
    val titleView = MarqueeRowTextView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL).apply {
            leftMargin = 68; rightMargin = 48
        }
    }
    val eqView = LiveEqualizerView(context).apply {
        visibility = View.GONE
        layoutParams = LayoutParams(28, 18, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = 12 }
    }
    init {
        addView(badgeView)
        addView(titleView)
        addView(eqView)
    }
}

class RealisticEyeSplashView(context: Context) : View(context) {
    var eyeOpenFraction: Float = 0.02f
        set(value) { field = value.coerceIn(0.02f, 1.0f); invalidate() }
    var lookOffsetX: Float = 0f
        set(value) { field = value.coerceIn(-1f, 1f); invalidate() }
    var pupilScale: Float = 1f
        set(value) { field = value.coerceIn(0.75f, 1.25f); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePath = Path(); private val outerEyePath = Path(); private val innerEyePath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        canvas.drawColor(Color.parseColor("#0B1026"))
        val waveStripes = listOf(
            Triple(0.16f, -0.04f, "#142850"),
            Triple(0.38f, 0.12f, "#006978"),
            Triple(0.64f, 0.30f, "#00A896"),
            Triple(0.86f, 0.50f, "#4A148C"),
            Triple(1.08f, 0.70f, "#AD1457")
        )
        paint.style = Paint.Style.FILL
        for ((lY, rY, hex) in waveStripes) {
            wavePath.reset(); wavePath.moveTo(0f, h * lY)
            wavePath.cubicTo(w * 0.35f, h * (lY - 0.12f), w * 0.68f, h * (rY + 0.08f), w, h * rY)
            wavePath.lineTo(w, h); wavePath.lineTo(0f, h); wavePath.close()
            paint.color = Color.parseColor(hex); canvas.drawPath(wavePath, paint)
        }
        val unit = min(w, h); val rx = unit * 0.36f; val maxRy = unit * 0.21f; val ry = maxRy * eyeOpenFraction
        outerEyePath.reset(); outerEyePath.moveTo(cx - rx * 1.06f, cy)
        outerEyePath.cubicTo(cx - rx * 0.55f, cy - ry * 1.82f, cx + rx * 0.55f, cy - ry * 1.82f, cx + rx * 1.06f, cy)
        outerEyePath.cubicTo(cx + rx * 0.55f, cy + ry * 1.82f, cx - rx * 0.55f, cy + ry * 1.82f, cx - rx * 1.06f, cy)
        outerEyePath.close(); paint.color = Color.parseColor("#090C15"); canvas.drawPath(outerEyePath, paint)

        innerEyePath.reset(); innerEyePath.moveTo(cx - rx * 0.92f, cy)
        innerEyePath.cubicTo(cx - rx * 0.48f, cy - ry * 1.54f, cx + rx * 0.48f, cy - ry * 1.54f, cx + rx * 0.92f, cy)
        innerEyePath.cubicTo(cx + rx * 0.48f, cy + ry * 1.54f, cx - rx * 0.48f, cy + ry * 1.54f, cx - rx * 0.92f, cy)
        innerEyePath.close()
        canvas.save(); canvas.clipPath(innerEyePath)
        paint.color = Color.parseColor("#E8F1F5"); canvas.drawPath(innerEyePath, paint)
        val eyeCenterX = cx + lookOffsetX * (rx * 0.18f); val irisRadius = maxRy * 0.96f
        val irisColors = intArrayOf(
            Color.parseColor("#00E5FF"),
            Color.parseColor("#00BFA5"),
            Color.parseColor("#FFD54F"),
            Color.parseColor("#7C4DFF"),
            Color.parseColor("#00E5FF")
        )
        paint.shader = SweepGradient(eyeCenterX, cy, irisColors, null)
        canvas.drawCircle(eyeCenterX, cy, irisRadius, paint); paint.shader = null
        val pupilR = irisRadius * 0.50f * pupilScale
        paint.color = Color.BLACK; canvas.drawCircle(eyeCenterX, cy, pupilR, paint)
        paint.color = Color.WHITE; canvas.drawCircle(eyeCenterX + pupilR * 0.5f, cy - pupilR * 0.4f, pupilR * 0.25f, paint)
        canvas.restore()
    }
}

class ThemedTileView(
    context: Context, private val iconType: String,
    private val baseColorHex: String, private val accentColorHex: String
) : View(context) {
    var isTileFocused: Boolean = false
        set(value) { field = value; invalidate() }
    var themeStyleId: Int = 0
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val minDim = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        paint.shader = null
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        when (themeStyleId) {
            0 -> drawMaterialIptvIcon(canvas, cx, cy, minDim)
            1 -> drawNeonIptvIcon(canvas, cx, cy, minDim)
            2 -> drawAmoledIptvIcon(canvas, cx, cy, minDim)
            3 -> drawMonochromeIptvIcon(canvas, cx, cy, minDim)
            4 -> drawRetroIptvIcon(canvas, cx, cy, minDim)
            else -> drawCarousel3DIptvIcon(canvas, cx, cy, minDim)
        }
        paint.shader = null
    }

    private fun drawMaterialIptvIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val r = minDim * 0.36f
        if (isTileFocused) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#3360A5FA")
            canvas.drawCircle(cx, cy, minDim * 0.45f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f
            paint.color = Color.parseColor("#60A5FA")
            canvas.drawCircle(cx, cy, minDim * 0.45f, paint)
        }
        paint.style = Paint.Style.FILL
        when (iconType) {
            "PLAYLISTS" -> {
                paint.color = Color.parseColor("#D97706")
                path.reset()
                path.moveTo(cx - r * 0.85f, cy - r * 0.58f)
                path.lineTo(cx - r * 0.22f, cy - r * 0.58f)
                path.lineTo(cx - r * 0.02f, cy - r * 0.34f)
                path.lineTo(cx + r * 0.85f, cy - r * 0.34f)
                path.lineTo(cx + r * 0.85f, cy + r * 0.62f)
                path.lineTo(cx - r * 0.85f, cy + r * 0.62f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = Color.parseColor("#FBBF24")
                rect.set(cx - r * 0.85f, cy - r * 0.26f, cx + r * 0.85f, cy + r * 0.62f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                paint.color = Color.parseColor("#FEF3C7")
                for (i in 0..1) {
                    val ly = cy + i * r * 0.28f
                    rect.set(cx - r * 0.58f, ly - 4f, cx + r * 0.05f, ly + 4f)
                    canvas.drawRoundRect(rect, 4f, 4f, paint)
                }
                paint.color = Color.parseColor("#10B981")
                canvas.drawCircle(cx + r * 0.46f, cy + r * 0.24f, r * 0.34f, paint)
                paint.color = Color.WHITE
                path.reset()
                path.moveTo(cx + r * 0.38f, cy + r * 0.09f)
                path.lineTo(cx + r * 0.60f, cy + r * 0.24f)
                path.lineTo(cx + r * 0.38f, cy + r * 0.39f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "EDITOR" -> {
                paint.color = Color.parseColor("#3B82F6")
                rect.set(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.42f, cy + r * 0.72f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.color = Color.parseColor("#DBEAFE")
                for (i in -1..1) {
                    val ly = cy + i * r * 0.36f
                    rect.set(cx - r * 0.48f, ly - 4f, cx + r * 0.14f, ly + 4f)
                    canvas.drawRoundRect(rect, 4f, 4f, paint)
                }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 12f
                paint.color = Color.parseColor("#F59E0B")
                canvas.drawLine(cx + r * 0.06f, cy + r * 0.48f, cx + r * 0.72f, cy - r * 0.34f, paint)
                paint.color = Color.parseColor("#EF4444")
                canvas.drawLine(cx + r * 0.60f, cy - r * 0.20f, cx + r * 0.76f, cy - r * 0.40f, paint)
            }
            "ANALYZER" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#0284C7")
                canvas.drawCircle(cx, cy, r * 0.80f, paint)
                paint.color = Color.parseColor("#38BDF8")
                rect.set(cx - r * 0.80f, cy - r * 0.80f, cx + r * 0.80f, cy + r * 0.80f)
                canvas.drawArc(rect, -90f, 180f, true, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5.5f; paint.color = Color.WHITE
                path.reset()
                path.moveTo(cx - r * 0.56f, cy + r * 0.06f)
                path.lineTo(cx - r * 0.24f, cy + r * 0.06f)
                path.lineTo(cx - r * 0.08f, cy - r * 0.36f)
                path.lineTo(cx + r * 0.12f, cy + r * 0.36f)
                path.lineTo(cx + r * 0.28f, cy - r * 0.08f)
                path.lineTo(cx + r * 0.56f, cy - r * 0.08f)
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#10B981")
                canvas.drawCircle(cx + r * 0.48f, cy + r * 0.48f, r * 0.28f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.5f; paint.color = Color.WHITE
                canvas.drawLine(cx + r * 0.36f, cy + r * 0.48f, cx + r * 0.45f, cy + r * 0.58f, paint)
                canvas.drawLine(cx + r * 0.45f, cy + r * 0.58f, cx + r * 0.62f, cy + r * 0.38f, paint)
            }
            "THEMES" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#F59E0B")
                canvas.drawCircle(cx, cy, r * 0.82f, paint)
                paint.color = Color.parseColor("#242C3D")
                canvas.drawCircle(cx + r * 0.34f, cy + r * 0.34f, r * 0.22f, paint)
                paint.color = Color.parseColor("#EF4444"); canvas.drawCircle(cx - r * 0.36f, cy - r * 0.18f, r * 0.15f, paint)
                paint.color = Color.parseColor("#8B5CF6"); canvas.drawCircle(cx - r * 0.08f, cy - r * 0.44f, r * 0.15f, paint)
                paint.color = Color.parseColor("#3B82F6"); canvas.drawCircle(cx + r * 0.32f, cy - r * 0.24f, r * 0.15f, paint)
                paint.color = Color.parseColor("#10B981"); canvas.drawCircle(cx - r * 0.32f, cy + r * 0.28f, r * 0.15f, paint)
            }
            "BUFFER" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#0D9488")
                canvas.drawCircle(cx, cy, r * 0.80f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 7f
                paint.color = Color.parseColor("#5EEAD4")
                rect.set(cx - r * 0.58f, cy - r * 0.58f, cx + r * 0.58f, cy + r * 0.58f)
                canvas.drawArc(rect, 140f, 260f, false, paint)
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#FDE047")
                path.reset()
                path.moveTo(cx + r * 0.08f, cy - r * 0.42f)
                path.lineTo(cx - r * 0.26f, cy + r * 0.04f)
                path.lineTo(cx + r * 0.02f, cy + r * 0.04f)
                path.lineTo(cx - r * 0.08f, cy + r * 0.44f)
                path.lineTo(cx + r * 0.28f, cy - r * 0.04f)
                path.lineTo(cx + r * 0.02f, cy - r * 0.04f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "DECODER" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
                paint.color = Color.parseColor("#94A3B8")
                for (k in -1..1) {
                    val off = k * r * 0.32f
                    canvas.drawLine(cx + off, cy - r * 0.84f, cx + off, cy + r * 0.84f, paint)
                    canvas.drawLine(cx - r * 0.84f, cy + off, cx + r * 0.84f, cy + off, paint)
                }
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#475569")
                rect.set(cx - r * 0.62f, cy - r * 0.62f, cx + r * 0.62f, cy + r * 0.62f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                paint.color = Color.parseColor("#0EA5E9")
                rect.set(cx - r * 0.36f, cy - r * 0.36f, cx + r * 0.36f, cy + r * 0.36f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
            }
            "EPG_GRID" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#E2E8F0")
                rect.set(cx - r * 0.74f, cy - r * 0.66f, cx + r * 0.68f, cy + r * 0.66f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.color = Color.parseColor("#E11D48")
                rect.set(cx - r * 0.74f, cy - r * 0.66f, cx + r * 0.68f, cy - r * 0.22f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.color = Color.parseColor("#64748B")
                for (i in 0..1) {
                    val ly = cy + i * r * 0.26f
                    rect.set(cx - r * 0.52f, ly - 4f, cx + r * 0.16f, ly + 4f)
                    canvas.drawRoundRect(rect, 4f, 4f, paint)
                }
                paint.color = Color.parseColor("#0284C7")
                canvas.drawCircle(cx + r * 0.46f, cy + r * 0.42f, r * 0.34f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.8f; paint.color = Color.WHITE
                canvas.drawLine(cx + r * 0.46f, cy + r * 0.42f, cx + r * 0.46f, cy + r * 0.24f, paint)
                canvas.drawLine(cx + r * 0.46f, cy + r * 0.42f, cx + r * 0.60f, cy + r * 0.42f, paint)
            }
            "TOP_CHANNELS" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#4F46E5")
                canvas.drawCircle(cx, cy, r * 0.80f, paint)
                paint.color = Color.parseColor("#FBBF24")
                path.reset()
                path.moveTo(cx - r * 0.52f, cy + r * 0.36f)
                path.lineTo(cx - r * 0.58f, cy - r * 0.24f)
                path.lineTo(cx - r * 0.20f, cy + r * 0.06f)
                path.lineTo(cx, cy - r * 0.42f)
                path.lineTo(cx + r * 0.20f, cy + r * 0.06f)
                path.lineTo(cx + r * 0.58f, cy - r * 0.24f)
                path.lineTo(cx + r * 0.52f, cy + r * 0.36f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "FAVORITES" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#DB2777")
                canvas.drawCircle(cx, cy, r * 0.80f, paint)
                paint.color = Color.parseColor("#FDE047")
                path.reset()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val rad = if (i % 2 == 0) r * 0.56f else r * 0.24f
                    val sx = cx + (rad * cos(a)).toFloat(); val sy = cy + (rad * sin(a)).toFloat()
                    if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "AUDIO" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#4338CA")
                rect.set(cx - r * 0.78f, cy - r * 0.68f, cx + r * 0.78f, cy + r * 0.68f)
                canvas.drawRoundRect(rect, 16f, 16f, paint)
                val xs = listOf(-0.45f, -0.15f, 0.15f, 0.45f)
                val hs = listOf(0.28f, 0.48f, 0.38f, 0.22f)
                val cols = listOf("#38BDF8", "#34D399", "#FBBF24", "#F472B6")
                for (i in xs.indices) {
                    paint.color = Color.parseColor(cols[i])
                    rect.set(cx + xs[i] * r - 7f, cy - hs[i] * r, cx + xs[i] * r + 7f, cy + hs[i] * r)
                    canvas.drawRoundRect(rect, 5f, 5f, paint)
                }
            }
            "CONTROL_MODE" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#334155")
                rect.set(cx - r * 0.48f, cy - r * 0.82f, cx + r * 0.48f, cy + r * 0.82f)
                canvas.drawRoundRect(rect, 18f, 18f, paint)
                paint.color = Color.parseColor("#EF4444")
                canvas.drawCircle(cx, cy - r * 0.54f, r * 0.11f, paint)
                paint.color = Color.parseColor("#38BDF8")
                canvas.drawCircle(cx, cy - r * 0.08f, r * 0.26f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy - r * 0.08f, r * 0.10f, paint)
                paint.color = Color.parseColor("#94A3B8")
                for (row in 0..1) {
                    for (col in -1..1) {
                        canvas.drawCircle(cx + col * r * 0.22f, cy + r * 0.36f + row * r * 0.22f, r * 0.06f, paint)
                    }
                }
            }
            "SETTINGS_GEAR" -> {
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#0284C7")
                canvas.drawCircle(cx, cy, r * 0.72f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, r * 0.32f, paint)
            }
            "SWAP_GROUPS" -> {
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#EA580C")
                canvas.drawCircle(cx, cy, r * 0.72f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = Color.WHITE
                canvas.drawLine(cx - r * 0.38f, cy - r * 0.16f, cx + r * 0.38f, cy - r * 0.16f, paint)
                canvas.drawLine(cx - r * 0.38f, cy + r * 0.16f, cx + r * 0.38f, cy + r * 0.16f, paint)
            }
            else -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#7C3AED")
                rect.set(cx - r * 0.82f, cy - r * 0.56f, cx + r * 0.82f, cy + r * 0.44f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.color = Color.parseColor("#38BDF8")
                rect.set(cx - r * 0.68f, cy - r * 0.42f, cx + r * 0.68f, cy + r * 0.30f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy - r * 0.06f, r * 0.18f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.color = Color.parseColor("#94A3B8")
                canvas.drawLine(cx - r * 0.36f, cy + r * 0.66f, cx + r * 0.36f, cy + r * 0.66f, paint)
            }
        }
    }

    private fun drawNeonIptvIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val r = minDim * 0.35f
        val grad = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            intArrayOf(Color.parseColor("#FF7597"), Color.parseColor("#FFE082"), Color.parseColor("#64FFDA"), Color.parseColor("#00E5FF")),
            null, Shader.TileMode.CLAMP
        )
        if (isTileFocused) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.8f; paint.shader = grad
            canvas.drawCircle(cx, cy, minDim * 0.45f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4.5f
        paint.shader = grad

        when (iconType) {
            "PLAYLISTS" -> {
                path.reset()
                path.moveTo(cx - r * 0.78f, cy - r * 0.52f)
                path.lineTo(cx - r * 0.18f, cy - r * 0.52f)
                path.lineTo(cx + r * 0.02f, cy - r * 0.30f)
                path.lineTo(cx + r * 0.78f, cy - r * 0.30f)
                path.lineTo(cx + r * 0.78f, cy + r * 0.58f)
                path.lineTo(cx - r * 0.78f, cy + r * 0.58f)
                path.close()
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(cx - r * 0.16f, cy - r * 0.08f)
                path.lineTo(cx + r * 0.24f, cy + r * 0.15f)
                path.lineTo(cx - r * 0.16f, cy + r * 0.38f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "EDITOR" -> {
                for (i in -1..1) {
                    val y = cy + i * r * 0.38f
                    canvas.drawLine(cx - r * 0.68f, y, cx + r * 0.20f, y, paint)
                }
                path.reset()
                path.moveTo(cx + r * 0.38f, cy - r * 0.60f)
                path.lineTo(cx + r * 0.62f, cy - r * 0.42f)
                path.lineTo(cx + r * 0.12f, cy + r * 0.52f)
                path.lineTo(cx - r * 0.10f, cy + r * 0.60f)
                path.lineTo(cx - r * 0.08f, cy + r * 0.36f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "ANALYZER" -> {
                for (ring in 1..3) {
                    val rad = r * (0.28f * ring)
                    rect.set(cx - rad, cy - rad + r * 0.15f, cx + rad, cy + rad + r * 0.15f)
                    canvas.drawArc(rect, 210f, 120f, false, paint)
                }
                canvas.drawLine(cx - r * 0.20f, cy + r * 0.34f, cx - r * 0.04f, cy + r * 0.50f, paint)
                canvas.drawLine(cx - r * 0.04f, cy + r * 0.50f, cx + r * 0.28f, cy + r * 0.18f, paint)
            }
            "THEMES" -> {
                canvas.drawCircle(cx, cy, r * 0.74f, paint)
                canvas.drawCircle(cx - r * 0.28f, cy - r * 0.18f, r * 0.12f, paint)
                canvas.drawCircle(cx + r * 0.22f, cy - r * 0.24f, r * 0.12f, paint)
                canvas.drawCircle(cx + r * 0.20f, cy + r * 0.22f, r * 0.14f, paint)
            }
            "BUFFER" -> {
                rect.set(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f)
                canvas.drawArc(rect, 140f, 260f, false, paint)
                path.reset()
                path.moveTo(cx + r * 0.08f, cy - r * 0.42f)
                path.lineTo(cx - r * 0.24f, cy + r * 0.04f)
                path.lineTo(cx + r * 0.22f, cy + r * 0.04f)
                path.lineTo(cx - r * 0.08f, cy + r * 0.44f)
                canvas.drawPath(path, paint)
            }
            "DECODER" -> {
                rect.set(cx - r * 0.52f, cy - r * 0.52f, cx + r * 0.52f, cy + r * 0.52f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                rect.set(cx - r * 0.22f, cy - r * 0.22f, cx + r * 0.22f, cy + r * 0.22f)
                canvas.drawRect(rect, paint)
                for (k in -1..1) {
                    val off = k * r * 0.28f
                    canvas.drawLine(cx + off, cy - r * 0.76f, cx + off, cy - r * 0.52f, paint)
                    canvas.drawLine(cx + off, cy + r * 0.52f, cx + off, cy + r * 0.76f, paint)
                    canvas.drawLine(cx - r * 0.76f, cy + off, cx - r * 0.52f, cy + off, paint)
                    canvas.drawLine(cx + r * 0.52f, cy + off, cx + r * 0.76f, cy + off, paint)
                }
            }
            "EPG_GRID" -> {
                rect.set(cx - r * 0.70f, cy - r * 0.60f, cx + r * 0.70f, cy + r * 0.60f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                canvas.drawLine(cx - r * 0.70f, cy - r * 0.20f, cx + r * 0.70f, cy - r * 0.20f, paint)
                canvas.drawLine(cx - r * 0.44f, cy + r * 0.08f, cx + r * 0.44f, cy + r * 0.08f, paint)
                canvas.drawLine(cx - r * 0.44f, cy + r * 0.34f, cx + r * 0.16f, cy + r * 0.34f, paint)
            }
            "TOP_CHANNELS" -> {
                path.reset()
                path.moveTo(cx - r * 0.62f, cy + r * 0.44f)
                path.lineTo(cx - r * 0.70f, cy - r * 0.30f)
                path.lineTo(cx - r * 0.24f, cy + r * 0.08f)
                path.lineTo(cx, cy - r * 0.50f)
                path.lineTo(cx + r * 0.24f, cy + r * 0.08f)
                path.lineTo(cx + r * 0.70f, cy - r * 0.30f)
                path.lineTo(cx + r * 0.62f, cy + r * 0.44f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "FAVORITES" -> {
                path.reset()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val rad = if (i % 2 == 0) r * 0.74f else r * 0.32f
                    val sx = cx + (rad * cos(a)).toFloat(); val sy = cy + (rad * sin(a)).toFloat()
                    if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "AUDIO" -> {
                canvas.drawCircle(cx - r * 0.22f, cy + r * 0.36f, r * 0.28f, paint)
                canvas.drawLine(cx + r * 0.06f, cy + r * 0.36f, cx + r * 0.06f, cy - r * 0.62f, paint)
                path.reset()
                path.moveTo(cx + r * 0.06f, cy - r * 0.62f)
                path.quadTo(cx + r * 0.58f, cy - r * 0.38f, cx + r * 0.48f, cy - r * 0.05f)
                canvas.drawPath(path, paint)
            }
            "CONTROL_MODE" -> {
                rect.set(cx - r * 0.44f, cy - r * 0.74f, cx + r * 0.44f, cy + r * 0.74f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                canvas.drawCircle(cx, cy - r * 0.18f, r * 0.20f, paint)
                canvas.drawLine(cx - r * 0.16f, cy + r * 0.34f, cx + r * 0.16f, cy + r * 0.34f, paint)
            }
            "SETTINGS_GEAR" -> {
                canvas.drawCircle(cx, cy, r * 0.48f, paint)
                for (deg in 0 until 360 step 45) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawLine(cx + (r * 0.48f * cos(rad)).toFloat(), cy + (r * 0.48f * sin(rad)).toFloat(), cx + (r * 0.72f * cos(rad)).toFloat(), cy + (r * 0.72f * sin(rad)).toFloat(), paint)
                }
            }
            "SWAP_GROUPS" -> {
                canvas.drawLine(cx - r * 0.55f, cy - r * 0.22f, cx + r * 0.55f, cy - r * 0.22f, paint)
                canvas.drawLine(cx - r * 0.55f, cy + r * 0.22f, cx + r * 0.55f, cy + r * 0.22f, paint)
            }
            else -> {
                rect.set(cx - r * 0.74f, cy - r * 0.52f, cx + r * 0.74f, cy + r * 0.38f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                canvas.drawCircle(cx, cy - r * 0.07f, r * 0.20f, paint)
                canvas.drawLine(cx - r * 0.36f, cy + r * 0.62f, cx + r * 0.36f, cy + r * 0.62f, paint)
            }
        }
    }

    private fun drawAmoledIptvIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val r = minDim * 0.36f
        if (isTileFocused) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f; paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, minDim * 0.45f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = Color.WHITE

        when (iconType) {
            "PLAYLISTS" -> {
                rect.set(cx - r * 0.74f, cy - r * 0.52f, cx + r * 0.74f, cy + r * 0.58f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawLine(cx - r * 0.74f, cy - r * 0.24f, cx + r * 0.74f, cy - r * 0.24f, paint)
                path.reset()
                path.moveTo(cx - r * 0.18f, cy - r * 0.05f)
                path.lineTo(cx + r * 0.26f, cy + r * 0.18f)
                path.lineTo(cx - r * 0.18f, cy + r * 0.41f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "EDITOR" -> {
                val s = r * 0.56f
                rect.set(cx - r * 0.70f, cy - r * 0.70f, cx - r * 0.70f + s, cy - r * 0.70f + s)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                canvas.drawLine(rect.centerX() - 8f, rect.centerY(), rect.centerX() - 2f, rect.centerY() + 7f, paint)
                canvas.drawLine(rect.centerX() - 2f, rect.centerY() + 7f, rect.centerX() + 9f, rect.centerY() - 6f, paint)
                canvas.drawLine(cx + r * 0.04f, cy - r * 0.42f, cx + r * 0.70f, cy - r * 0.42f, paint)

                rect.set(cx - r * 0.70f, cy + r * 0.12f, cx - r * 0.70f + s, cy + r * 0.12f + s)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                canvas.drawLine(cx + r * 0.04f, cy + r * 0.40f, cx + r * 0.70f, cy + r * 0.40f, paint)
            }
            "ANALYZER" -> {
                canvas.drawCircle(cx, cy, r * 0.74f, paint)
                canvas.drawCircle(cx, cy, r * 0.38f, paint)
                canvas.drawLine(cx - r * 0.74f, cy, cx + r * 0.74f, cy, paint)
                canvas.drawLine(cx, cy - r * 0.74f, cx, cy + r * 0.74f, paint)
                canvas.drawCircle(cx + r * 0.26f, cy - r * 0.24f, r * 0.09f, paint)
            }
            "THEMES" -> {
                canvas.drawCircle(cx, cy, r * 0.72f, paint)
                canvas.drawCircle(cx, cy, r * 0.24f, paint)
                for (deg in 0 until 360 step 60) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawLine(
                        cx + (r * 0.24f * cos(rad)).toFloat(), cy + (r * 0.24f * sin(rad)).toFloat(),
                        cx + (r * 0.72f * cos(rad)).toFloat(), cy + (r * 0.72f * sin(rad)).toFloat(), paint
                    )
                }
            }
            "BUFFER" -> {
                rect.set(cx - r * 0.74f, cy - r * 0.64f, cx + r * 0.74f, cy + r * 0.84f)
                canvas.drawArc(rect, 160f, 220f, false, paint)
                canvas.drawCircle(cx, cy + r * 0.16f, r * 0.12f, paint)
                canvas.drawLine(cx, cy + r * 0.16f, cx + r * 0.42f, cy - r * 0.28f, paint)
            }
            "DECODER" -> {
                rect.set(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                rect.set(cx - r * 0.38f, cy - r * 0.38f, cx + r * 0.38f, cy + r * 0.38f)
                canvas.drawRect(rect, paint)
                for (k in -1..1) {
                    val off = k * r * 0.24f
                    canvas.drawCircle(cx + off, cy - r * 0.55f, r * 0.06f, paint)
                    canvas.drawCircle(cx + off, cy + r * 0.55f, r * 0.06f, paint)
                    canvas.drawCircle(cx - r * 0.55f, cy + off, r * 0.06f, paint)
                    canvas.drawCircle(cx + r * 0.55f, cy + off, r * 0.06f, paint)
                }
            }
            "EPG_GRID" -> {
                rect.set(cx - r * 0.72f, cy - r * 0.66f, cx + r * 0.72f, cy + r * 0.66f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                canvas.drawLine(cx - r * 0.72f, cy - r * 0.22f, cx + r * 0.72f, cy - r * 0.22f, paint)
                canvas.drawLine(cx - r * 0.24f, cy - r * 0.22f, cx - r * 0.24f, cy + r * 0.66f, paint)
                canvas.drawLine(cx - r * 0.72f, cy + r * 0.22f, cx + r * 0.72f, cy + r * 0.22f, paint)
            }
            "TOP_CHANNELS" -> {
                path.reset()
                path.moveTo(cx - r * 0.58f, cy + r * 0.42f)
                path.lineTo(cx - r * 0.64f, cy - r * 0.22f)
                path.lineTo(cx - r * 0.22f, cy + r * 0.08f)
                path.lineTo(cx, cy - r * 0.42f)
                path.lineTo(cx + r * 0.22f, cy + r * 0.08f)
                path.lineTo(cx + r * 0.64f, cy - r * 0.22f)
                path.lineTo(cx + r * 0.58f, cy + r * 0.42f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawCircle(cx, cy - r * 0.52f, r * 0.07f, paint)
            }
            "FAVORITES" -> {
                rect.set(cx - r * 0.74f, cy - r * 0.58f, cx + r * 0.74f, cy + r * 0.58f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                path.reset()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val rad = if (i % 2 == 0) r * 0.42f else r * 0.18f
                    val sx = cx + (rad * cos(a)).toFloat(); val sy = cy + (rad * sin(a)).toFloat()
                    if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "AUDIO" -> {
                val xs = listOf(-0.60f, -0.36f, -0.12f, 0.12f, 0.36f, 0.60f)
                val hs = listOf(0.20f, 0.48f, 0.74f, 0.34f, 0.58f, 0.22f)
                for (i in xs.indices) {
                    canvas.drawLine(cx + xs[i] * r, cy - hs[i] * r, cx + xs[i] * r, cy + hs[i] * r, paint)
                }
            }
            "CONTROL_MODE" -> {
                rect.set(cx - r * 0.76f, cy - r * 0.46f, cx + r * 0.76f, cy + r * 0.46f)
                canvas.drawRoundRect(rect, 24f, 24f, paint)
                canvas.drawCircle(cx - r * 0.36f, cy, r * 0.22f, paint)
                canvas.drawLine(cx - r * 0.36f - 8f, cy, cx - r * 0.36f + 8f, cy, paint)
                canvas.drawLine(cx - r * 0.36f, cy - 8f, cx - r * 0.36f, cy + 8f, paint)
                canvas.drawCircle(cx + r * 0.36f, cy - r * 0.10f, r * 0.07f, paint)
                canvas.drawCircle(cx + r * 0.24f, cy + r * 0.10f, r * 0.07f, paint)
            }
            "SETTINGS_GEAR" -> {
                canvas.drawCircle(cx, cy, r * 0.46f, paint)
                canvas.drawCircle(cx, cy, r * 0.20f, paint)
            }
            "SWAP_GROUPS" -> {
                canvas.drawLine(cx - r * 0.52f, cy - r * 0.20f, cx + r * 0.52f, cy - r * 0.20f, paint)
                canvas.drawLine(cx - r * 0.52f, cy + r * 0.20f, cx + r * 0.52f, cy + r * 0.20f, paint)
            }
            else -> {
                rect.set(cx - r * 0.76f, cy - r * 0.52f, cx + r * 0.76f, cy + r * 0.40f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawCircle(cx, cy - r * 0.06f, r * 0.28f, paint)
                canvas.drawLine(cx - r * 0.34f, cy + r * 0.62f, cx + r * 0.34f, cy + r * 0.62f, paint)
            }
        }
    }

    private fun drawMonochromeIptvIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val pad = minDim * 0.08f
        val r = (minDim - pad * 2) * 0.35f
        rect.set(pad, pad, width - pad, height - pad)
        paint.style = Paint.Style.FILL
        paint.color = if (isTileFocused) Color.parseColor("#E4E4E7") else Color.parseColor("#F8FAFC")
        canvas.drawRect(rect, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isTileFocused) 3.5f else 1.5f
        paint.color = if (isTileFocused) Color.parseColor("#3F3F46") else Color.parseColor("#D4D4D8")
        canvas.drawRect(rect, paint)

        val gray = Color.parseColor("#52525B")
        paint.color = gray

        when (iconType) {
            "PLAYLISTS" -> {
                paint.style = Paint.Style.FILL
                for (i in -1..1) {
                    val y = cy + i * r * 0.42f
                    canvas.drawCircle(cx - r * 0.52f, y, r * 0.11f, paint)
                    rect.set(cx - r * 0.26f, y - r * 0.09f, cx + r * 0.58f, y + r * 0.09f)
                    canvas.drawRect(rect, paint)
                }
            }
            "EDITOR" -> {
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx + r * 0.35f, cy - r * 0.55f)
                path.lineTo(cx + r * 0.55f, cy - r * 0.35f)
                path.lineTo(cx - r * 0.25f, cy + r * 0.45f)
                path.lineTo(cx - r * 0.55f, cy + r * 0.55f)
                path.lineTo(cx - r * 0.45f, cy + r * 0.25f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "ANALYZER" -> {
                paint.style = Paint.Style.FILL
                for (i in 0..3) {
                    val left = cx - r * 0.58f + i * r * 0.32f
                    val top = cy + r * 0.52f - (i + 1) * r * 0.26f
                    rect.set(left, top, left + r * 0.20f, cy + r * 0.52f)
                    canvas.drawRect(rect, paint)
                }
            }
            "THEMES" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f
                canvas.drawCircle(cx, cy, r * 0.54f, paint)
                paint.style = Paint.Style.FILL
                rect.set(cx - r * 0.54f, cy - r * 0.54f, cx + r * 0.54f, cy + r * 0.54f)
                canvas.drawArc(rect, 90f, 180f, true, paint)
            }
            "BUFFER" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                rect.set(cx - r * 0.56f, cy - r * 0.56f, cx + r * 0.56f, cy + r * 0.56f)
                canvas.drawArc(rect, -150f, 130f, false, paint)
                canvas.drawArc(rect, 30f, 130f, false, paint)
            }
            "DECODER" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f
                rect.set(cx - r * 0.48f, cy - r * 0.48f, cx + r * 0.48f, cy + r * 0.48f)
                canvas.drawRect(rect, paint)
                for (k in -1..1) {
                    val off = k * r * 0.26f
                    canvas.drawLine(cx + off, cy - r * 0.70f, cx + off, cy - r * 0.48f, paint)
                    canvas.drawLine(cx + off, cy + r * 0.48f, cx + off, cy + r * 0.70f, paint)
                    canvas.drawLine(cx - r * 0.70f, cy + off, cx - r * 0.48f, cy + off, paint)
                    canvas.drawLine(cx + r * 0.48f, cy + off, cx + r * 0.70f, cy + off, paint)
                }
            }
            "EPG_GRID" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                rect.set(cx - r * 0.60f, cy - r * 0.56f, cx + r * 0.60f, cy + r * 0.60f)
                canvas.drawRect(rect, paint)
                canvas.drawLine(cx - r * 0.60f, cy - r * 0.22f, cx + r * 0.60f, cy - r * 0.22f, paint)
                paint.style = Paint.Style.FILL
                for (rx in -1..1) {
                    for (ry in 0..1) {
                        canvas.drawCircle(cx + rx * r * 0.30f, cy + r * 0.08f + ry * r * 0.26f, r * 0.07f, paint)
                    }
                }
            }
            "TOP_CHANNELS" -> {
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx - r * 0.56f, cy + r * 0.42f)
                path.lineTo(cx - r * 0.62f, cy - r * 0.22f)
                path.lineTo(cx - r * 0.22f, cy + r * 0.06f)
                path.lineTo(cx, cy - r * 0.44f)
                path.lineTo(cx + r * 0.22f, cy + r * 0.06f)
                path.lineTo(cx + r * 0.62f, cy - r * 0.22f)
                path.lineTo(cx + r * 0.56f, cy + r * 0.42f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "FAVORITES" -> {
                paint.style = Paint.Style.FILL
                path.reset()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val rad = if (i % 2 == 0) r * 0.68f else r * 0.28f
                    val sx = cx + (rad * cos(a)).toFloat(); val sy = cy + (rad * sin(a)).toFloat()
                    if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "AUDIO" -> {
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx - r * 0.58f, cy - r * 0.20f)
                path.lineTo(cx - r * 0.28f, cy - r * 0.20f)
                path.lineTo(cx + r * 0.06f, cy - r * 0.52f)
                path.lineTo(cx + r * 0.06f, cy + r * 0.52f)
                path.lineTo(cx - r * 0.28f, cy + r * 0.20f)
                path.lineTo(cx - r * 0.58f, cy + r * 0.20f)
                path.close()
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                rect.set(cx - r * 0.10f, cy - r * 0.36f, cx + r * 0.38f, cy + r * 0.36f)
                canvas.drawArc(rect, -55f, 110f, false, paint)
            }
            "CONTROL_MODE" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f
                rect.set(cx - r * 0.36f, cy - r * 0.64f, cx + r * 0.36f, cy + r * 0.64f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy - r * 0.12f, r * 0.14f, paint)
            }
            else -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f
                rect.set(cx - r * 0.64f, cy - r * 0.44f, cx + r * 0.64f, cy + r * 0.32f)
                canvas.drawRect(rect, paint)
                canvas.drawLine(cx - r * 0.32f, cy + r * 0.52f, cx + r * 0.32f, cy + r * 0.52f, paint)
            }
        }
    }

    private fun drawRetroIptvIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val pad = minDim * 0.08f
        val r = (minDim - pad * 2) * 0.36f
        val cardColor = when (iconType) {
            "PLAYLISTS", "FAVORITES", "EDITOR", "TOP_CHANNELS", "MORE" -> Color.parseColor("#2A7B69")
            "ANALYZER", "BUFFER", "AUDIO" -> Color.parseColor("#9B533F")
            "THEMES", "EPG_GRID" -> Color.parseColor("#D68928")
            else -> Color.parseColor("#544F4E")
        }
        val corner = minDim * 0.15f
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#880A0807")
        rect.set(pad + 3f, pad + 7f, width - pad + 3f, height - pad + 7f)
        canvas.drawRoundRect(rect, corner, corner, paint)

        paint.color = cardColor
        rect.set(pad, pad, width - pad, height - pad)
        canvas.drawRoundRect(rect, corner, corner, paint)
        if (isTileFocused) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
            paint.color = Color.parseColor("#F5E6CC")
            canvas.drawRoundRect(rect, corner, corner, paint)
        }

        val cream = Color.parseColor("#F3E5CA")
        paint.color = cream

        when (iconType) {
            "PLAYLISTS" -> {
                paint.style = Paint.Style.FILL; paint.color = cream
                rect.set(cx - r * 0.72f, cy - r * 0.52f, cx + r * 0.72f, cy + r * 0.56f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                paint.color = Color.parseColor("#2A7B69")
                canvas.drawCircle(cx - r * 0.30f, cy + r * 0.04f, r * 0.18f, paint)
                canvas.drawCircle(cx + r * 0.30f, cy + r * 0.04f, r * 0.18f, paint)
            }
            "EDITOR" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f; paint.color = cream
                path.reset()
                path.moveTo(cx - r * 0.55f, cy - r * 0.55f)
                path.lineTo(cx - r * 0.55f, cy + r * 0.55f)
                path.lineTo(cx + r * 0.55f, cy + r * 0.55f)
                canvas.drawPath(path, paint)
                canvas.drawLine(cx - r * 0.15f, cy + r * 0.25f, cx + r * 0.55f, cy - r * 0.45f, paint)
            }
            "ANALYZER" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#E63926")
                canvas.drawCircle(cx, cy, r * 0.74f, paint)
                paint.color = cream
                canvas.drawCircle(cx, cy, r * 0.50f, paint)
                paint.color = Color.parseColor("#E63926")
                canvas.drawCircle(cx, cy, r * 0.28f, paint)
                paint.color = cream
                canvas.drawCircle(cx, cy, r * 0.12f, paint)
            }
            "THEMES" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = cream
                rect.set(cx - r * 0.64f, cy - r * 0.64f, cx + r * 0.64f, cy + r * 0.64f)
                canvas.drawRect(rect, paint)
                canvas.drawLine(cx - r * 0.64f, cy, cx + r * 0.64f, cy, paint)
                canvas.drawLine(cx, cy - r * 0.64f, cx, cy + r * 0.64f, paint)
            }
            "BUFFER" -> {
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#FDE047")
                canvas.drawCircle(cx, cy + r * 0.06f, r * 0.66f, paint)
                paint.color = Color.parseColor("#9B533F")
                canvas.drawCircle(cx, cy + r * 0.06f, r * 0.40f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.color = cream
                canvas.drawLine(cx, cy + r * 0.06f, cx + r * 0.28f, cy - r * 0.18f, paint)
                canvas.drawLine(cx, cy - r * 0.60f, cx, cy - r * 0.78f, paint)
            }
            "DECODER" -> {
                paint.style = Paint.Style.FILL; paint.color = cream
                rect.set(cx - r * 0.62f, cy - r * 0.32f, cx + r * 0.62f, cy + r * 0.60f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.color = cream
                canvas.drawLine(cx - r * 0.20f, cy - r * 0.32f, cx - r * 0.45f, cy - r * 0.70f, paint)
                canvas.drawLine(cx + r * 0.20f, cy - r * 0.32f, cx + r * 0.45f, cy - r * 0.70f, paint)
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#3B2F63")
                rect.set(cx - r * 0.44f, cy - r * 0.12f, cx + r * 0.44f, cy + r * 0.38f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
            }
            "EPG_GRID" -> {
                paint.style = Paint.Style.FILL; paint.color = cream
                rect.set(cx - r * 0.64f, cy - r * 0.62f, cx + r * 0.64f, cy + r * 0.62f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.color = Color.parseColor("#544F4E")
                for (i in -1..1) {
                    val y = cy + i * r * 0.32f
                    rect.set(cx - r * 0.44f, y - 5f, cx + r * 0.44f, y + 5f)
                    canvas.drawRect(rect, paint)
                }
            }
            "TOP_CHANNELS" -> {
                paint.style = Paint.Style.FILL; paint.color = cream
                path.reset()
                path.moveTo(cx, cy - r * 0.10f)
                path.lineTo(cx - r * 0.72f, cy + r * 0.56f)
                path.lineTo(cx + r * 0.72f, cy + r * 0.56f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawCircle(cx, cy - r * 0.44f, r * 0.18f, paint)
            }
            "FAVORITES" -> {
                paint.style = Paint.Style.FILL; paint.color = cream
                canvas.drawCircle(cx, cy + r * 0.15f, r * 0.34f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f
                for (deg in listOf(-150, -120, -90, -60, -30)) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawLine(
                        cx + (r * 0.46f * cos(rad)).toFloat(), cy + r * 0.15f + (r * 0.46f * sin(rad)).toFloat(),
                        cx + (r * 0.78f * cos(rad)).toFloat(), cy + r * 0.15f + (r * 0.78f * sin(rad)).toFloat(), paint
                    )
                }
            }
            "AUDIO" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 7f; paint.color = cream
                canvas.drawCircle(cx, cy, r * 0.62f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, r * 0.24f, paint)
            }
            "CONTROL_MODE" -> {
                paint.style = Paint.Style.FILL; paint.color = cream
                rect.set(cx - r * 0.62f, cy + r * 0.18f, cx + r * 0.62f, cy + r * 0.54f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f
                canvas.drawLine(cx, cy + r * 0.18f, cx + r * 0.24f, cy - r * 0.42f, paint)
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#E63926")
                canvas.drawCircle(cx + r * 0.24f, cy - r * 0.46f, r * 0.20f, paint)
            }
            else -> {
                paint.style = Paint.Style.FILL; paint.color = cream
                rect.set(cx - r * 0.70f, cy - r * 0.52f, cx + r * 0.70f, cy + r * 0.52f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.color = Color.parseColor("#2A7B69")
                rect.set(cx - r * 0.52f, cy - r * 0.36f, cx + r * 0.28f, cy + r * 0.36f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawCircle(cx + r * 0.48f, cy - r * 0.14f, r * 0.08f, paint)
                canvas.drawCircle(cx + r * 0.48f, cy + r * 0.14f, r * 0.08f, paint)
            }
        }
    }

    private fun drawCarousel3DIptvIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val discR = minDim * 0.38f
        val topCy = cy - minDim * 0.035f
        val r = discR * 0.64f

        val (topHex, edgeHex) = when (iconType) {
            "PLAYLISTS" -> Pair("#EF4444", "#991B1B")
            "EDITOR" -> Pair("#65A30D", "#3F6212")
            "ANALYZER" -> Pair("#0284C7", "#075985")
            "THEMES" -> Pair("#9333EA", "#6B21A8")
            "BUFFER" -> Pair("#EA580C", "#9A3412")
            "DECODER" -> Pair("#0D9488", "#115E59")
            "EPG_GRID" -> Pair("#E11D48", "#881337")
            "TOP_CHANNELS" -> Pair("#2563EB", "#1E3A8A")
            "FAVORITES" -> Pair("#D97706", "#78350F")
            "AUDIO" -> Pair("#0EA5E9", "#0369A1")
            "CONTROL_MODE" -> Pair("#4F46E5", "#312E81")
            else -> Pair("#0284C7", "#075985")
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#55000000")
        canvas.drawCircle(cx, topCy + minDim * 0.105f, discR, paint)
        paint.color = Color.parseColor(edgeHex)
        canvas.drawCircle(cx, topCy + minDim * 0.075f, discR, paint)
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawCircle(cx, topCy + minDim * 0.038f, discR, paint)
        if (iconType == "THEMES") {
            paint.shader = LinearGradient(
                cx - discR, topCy - discR, cx + discR, topCy + discR,
                intArrayOf(Color.parseColor("#8B5CF6"), Color.parseColor("#EC4899"), Color.parseColor("#F59E0B")),
                null, Shader.TileMode.CLAMP
            )
        } else {
            paint.color = Color.parseColor(topHex)
        }
        canvas.drawCircle(cx, topCy, discR, paint)
        paint.shader = null

        if (isTileFocused) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f; paint.color = Color.WHITE
            canvas.drawCircle(cx, topCy, discR, paint)
        }

        when (iconType) {
            "PLAYLISTS" -> {
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                rect.set(cx - r * 0.62f, topCy - r * 0.44f, cx + r * 0.62f, topCy + r * 0.44f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                paint.color = Color.parseColor("#EF4444")
                path.reset()
                path.moveTo(cx - r * 0.18f, topCy - r * 0.24f)
                path.lineTo(cx + r * 0.26f, topCy)
                path.lineTo(cx - r * 0.18f, topCy + r * 0.24f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "EDITOR" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.5f; paint.color = Color.WHITE
                canvas.drawLine(cx - r * 0.56f, topCy - r * 0.30f, cx + r * 0.12f, topCy - r * 0.30f, paint)
                canvas.drawLine(cx - r * 0.56f, topCy, cx + r * 0.02f, topCy, paint)
                canvas.drawLine(cx - r * 0.56f, topCy + r * 0.30f, cx - r * 0.12f, topCy + r * 0.30f, paint)
                paint.strokeWidth = 7f; paint.color = Color.parseColor("#FDE047")
                canvas.drawLine(cx + r * 0.08f, topCy + r * 0.38f, cx + r * 0.54f, topCy - r * 0.28f, paint)
            }
            "ANALYZER" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = Color.WHITE
                for (ring in 1..2) {
                    val rad = r * (0.34f * ring)
                    rect.set(cx - rad, topCy - rad + r * 0.10f, cx + rad, topCy + rad + r * 0.10f)
                    canvas.drawArc(rect, 205f, 130f, false, paint)
                }
                paint.color = Color.parseColor("#4ADE80"); paint.strokeWidth = 5f
                canvas.drawLine(cx - r * 0.22f, topCy + r * 0.24f, cx - r * 0.04f, topCy + r * 0.42f, paint)
                canvas.drawLine(cx - r * 0.04f, topCy + r * 0.42f, cx + r * 0.28f, topCy + r * 0.08f, paint)
            }
            "THEMES" -> {
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                canvas.drawCircle(cx, topCy, r * 0.58f, paint)
                paint.color = Color.parseColor("#EF4444"); canvas.drawCircle(cx - r * 0.22f, topCy - r * 0.14f, r * 0.11f, paint)
                paint.color = Color.parseColor("#3B82F6"); canvas.drawCircle(cx + r * 0.02f, topCy - r * 0.28f, r * 0.11f, paint)
                paint.color = Color.parseColor("#10B981"); canvas.drawCircle(cx + r * 0.24f, topCy - r * 0.08f, r * 0.11f, paint)
                paint.color = Color.parseColor("#9333EA"); canvas.drawCircle(cx + r * 0.18f, topCy + r * 0.22f, r * 0.14f, paint)
            }
            "BUFFER" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = Color.WHITE
                rect.set(cx - r * 0.56f, topCy - r * 0.56f, cx + r * 0.56f, topCy + r * 0.56f)
                canvas.drawArc(rect, 140f, 260f, false, paint)
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#FDE047")
                path.reset()
                path.moveTo(cx + r * 0.06f, topCy - r * 0.36f)
                path.lineTo(cx - r * 0.22f, topCy + r * 0.04f)
                path.lineTo(cx + r * 0.02f, topCy + r * 0.04f)
                path.lineTo(cx - r * 0.06f, topCy + r * 0.38f)
                path.lineTo(cx + r * 0.24f, topCy - r * 0.04f)
                path.lineTo(cx + r * 0.02f, topCy - r * 0.04f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "DECODER" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.8f; paint.color = Color.WHITE
                rect.set(cx - r * 0.46f, topCy - r * 0.46f, cx + r * 0.46f, topCy + r * 0.46f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                for (k in -1..1) {
                    val off = k * r * 0.24f
                    canvas.drawLine(cx + off, topCy - r * 0.68f, cx + off, topCy - r * 0.46f, paint)
                    canvas.drawLine(cx + off, topCy + r * 0.46f, cx + off, topCy + r * 0.68f, paint)
                    canvas.drawLine(cx - r * 0.68f, topCy + off, cx - r * 0.46f, topCy + off, paint)
                    canvas.drawLine(cx + r * 0.46f, topCy + off, cx + r * 0.68f, topCy + off, paint)
                }
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#FDE047")
                rect.set(cx - r * 0.20f, topCy - r * 0.20f, cx + r * 0.20f, topCy + r * 0.20f)
                canvas.drawRoundRect(rect, 4f, 4f, paint)
            }
            "EPG_GRID" -> {
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                rect.set(cx - r * 0.56f, topCy - r * 0.48f, cx + r * 0.56f, topCy + r * 0.48f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                paint.color = Color.parseColor("#E11D48")
                rect.set(cx - r * 0.40f, topCy - r * 0.28f, cx + r * 0.40f, topCy - r * 0.14f)
                canvas.drawRect(rect, paint)
                rect.set(cx - r * 0.40f, topCy - r * 0.02f, cx + r * 0.24f, topCy + r * 0.12f)
                canvas.drawRect(rect, paint)
                rect.set(cx - r * 0.40f, topCy + r * 0.22f, cx + r * 0.32f, topCy + r * 0.34f)
                canvas.drawRect(rect, paint)
            }
            "TOP_CHANNELS" -> {
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#FDE047")
                path.reset()
                path.moveTo(cx - r * 0.52f, topCy + r * 0.36f)
                path.lineTo(cx - r * 0.58f, topCy - r * 0.22f)
                path.lineTo(cx - r * 0.20f, topCy + r * 0.06f)
                path.lineTo(cx, topCy - r * 0.42f)
                path.lineTo(cx + r * 0.20f, topCy + r * 0.06f)
                path.lineTo(cx + r * 0.58f, topCy - r * 0.22f)
                path.lineTo(cx + r * 0.52f, topCy + r * 0.36f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "FAVORITES" -> {
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                path.reset()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val rad = if (i % 2 == 0) r * 0.58f else r * 0.25f
                    val sx = cx + (rad * cos(a)).toFloat(); val sy = topCy + (rad * sin(a)).toFloat()
                    if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "AUDIO" -> {
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                canvas.drawCircle(cx - r * 0.26f, topCy + r * 0.30f, r * 0.17f, paint)
                canvas.drawCircle(cx + r * 0.22f, topCy + r * 0.22f, r * 0.17f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.5f
                canvas.drawLine(cx - r * 0.10f, topCy + r * 0.30f, cx - r * 0.10f, topCy - r * 0.34f, paint)
                canvas.drawLine(cx + r * 0.38f, topCy + r * 0.22f, cx + r * 0.38f, topCy - r * 0.42f, paint)
                canvas.drawLine(cx - r * 0.10f, topCy - r * 0.34f, cx + r * 0.38f, topCy - r * 0.42f, paint)
            }
            "CONTROL_MODE" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.8f; paint.color = Color.WHITE
                rect.set(cx - r * 0.38f, topCy - r * 0.58f, cx + r * 0.38f, topCy + r * 0.58f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#FDE047")
                canvas.drawCircle(cx, topCy - r * 0.12f, r * 0.16f, paint)
            }
            else -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = Color.WHITE
                rect.set(cx - r * 0.58f, topCy - r * 0.42f, cx + r * 0.58f, topCy + r * 0.30f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawLine(cx - r * 0.30f, topCy + r * 0.48f, cx + r * 0.30f, topCy + r * 0.48f, paint)
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#FDE047")
                canvas.drawCircle(cx, topCy - r * 0.06f, r * 0.16f, paint)
            }
        }
    }
}
