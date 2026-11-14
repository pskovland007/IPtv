package com.example.miboxiptv

import android.content.Context
import android.graphics.*
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        return if (orbType >= 10) 68 else 34
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, textPaint: Paint) {
        val cy = (top + bottom) / 2f
        if (orbType == 10 || orbType == 11) {
            val isOn = (orbType == 10)
            val trackW = 54f; val trackH = 26f
            val left = x + 4f; val topY = cy - trackH / 2f
            rect.set(left, topY, left + trackW, topY + trackH)
            paint.style = Paint.Style.FILL
            paint.color = if (isOn) Color.parseColor("#00C853") else Color.parseColor("#455A64")
            canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f
            paint.color = if (isOn) Color.parseColor("#B9F6CA") else Color.parseColor("#90A4AE")
            canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint)
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            val knobX = if (isOn) (left + trackW - trackH / 2f) else (left + trackH / 2f)
            canvas.drawCircle(knobX, cy, trackH * 0.38f, paint)
            return
        }
        val cx = x + 15f; val r = 10.5f
        val mainColor = when (orbType) {
            1 -> Color.parseColor("#00E676")
            2 -> Color.parseColor("#FF5252")
            else -> Color.parseColor("#FFD740")
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(65, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor))
        canvas.drawCircle(cx, cy, r * 1.32f, paint)
        paint.color = mainColor; canvas.drawCircle(cx, cy, r, paint)
        paint.color = Color.argb(130, 255, 255, 255)
        canvas.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.32f, paint)

        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.4f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.parseColor("#101418")
        if (orbType == 1) {
            canvas.drawLine(cx - 4.2f, cy + 0.5f, cx - 1.0f, cy + 3.8f, paint)
            canvas.drawLine(cx - 1.0f, cy + 3.8f, cx + 4.8f, cy - 3.5f, paint)
        } else if (orbType == 2) {
            canvas.drawLine(cx - 3.6f, cy - 3.6f, cx + 3.6f, cy + 3.6f, paint)
            canvas.drawLine(cx + 3.6f, cy - 3.6f, cx - 3.6f, cy + 3.6f, paint)
        }
    }
}

fun colorizeStatusText(raw: String): CharSequence {
    if (!raw.contains("[")) return raw
    val ss = SpannableString(raw)
    var i = 0
    while (i <= raw.length - 3) {
        when {
            i <= raw.length - 4 && raw.startsWith("[ON]", i) -> { ss.setSpan(GraphicOrbSpan(10), i, i + 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 4 }
            i <= raw.length - 5 && raw.startsWith("[OFF]", i) -> { ss.setSpan(GraphicOrbSpan(11), i, i + 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 5 }
            raw.startsWith("[+]", i) -> { ss.setSpan(GraphicOrbSpan(1), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 3 }
            raw.startsWith("[-]", i) -> { ss.setSpan(GraphicOrbSpan(2), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 3 }
            raw.startsWith("[?]", i) -> { ss.setSpan(GraphicOrbSpan(3), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i += 3 }
            else -> i++
        }
    }
    return ss
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
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val phases = floatArrayOf(0.0f, 1.3f, 2.7f, 4.1f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return
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
        postInvalidateDelayed(85L)
    }
}

class MarqueeRowTextView(context: Context) : TextView(context) {
    init {
        setSingleLine(true)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    var forceMarqueeActive: Boolean = false
        set(value) {
            field = value
            setSingleLine(true)
            maxLines = 1
            if (value) {
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                setHorizontallyScrolling(true)
                isSelected = true
            } else {
                isSelected = false
                ellipsize = TextUtils.TruncateAt.END
            }
        }

    override fun isFocused(): Boolean = forceMarqueeActive || super.isFocused()
}

class ChannelRowView(context: Context) : FrameLayout(context) {
    val badgeView = TextView(context).apply {
        gravity = Gravity.CENTER; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        setSingleLine(true); maxLines = 1
        layoutParams = LayoutParams(44, 44, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = 14 }
    }
    val titleView = MarqueeRowTextView(context).apply {
        setSingleLine(true); maxLines = 1
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL).apply {
            leftMargin = 70; rightMargin = 54
        }
    }
    val eqView = LiveEqualizerView(context).apply {
        visibility = View.GONE
        layoutParams = LayoutParams(34, 24, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = 14 }
    }
    init {
        setPadding(4, 10, 4, 10)
        addView(badgeView); addView(titleView); addView(eqView)
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
        val w = width.toFloat(); val h = height.toFloat()
        val minDim = min(w, h)
        val pad = if (isTileFocused) minDim * 0.04f else minDim * 0.08f

        val mainColor = when (themeStyleId) {
            0 -> Color.parseColor(if (isTileFocused) "#1E3A8A" else "#2B2D30")
            1 -> Color.parseColor("#100D22")
            2 -> Color.parseColor("#050505")
            3 -> Color.parseColor("#242933")
            4 -> Color.parseColor("#2D1A12")
            5 -> Color.parseColor(if (isTileFocused) "#3F3F46" else "#18181B")
            6 -> Color.parseColor(if (isTileFocused) "#DBEAFE" else "#F1F5F9")
            7 -> Color.parseColor("#1E052E")
            8 -> Color.parseColor("#021202")
            9 -> Color.parseColor("#1A1308")
            else -> Color.parseColor(baseColorHex)
        }
        val badgeColor = when (themeStyleId) {
            0 -> Color.parseColor("#A8C7FA")
            1 -> Color.parseColor("#00E5FF")
            2 -> Color.parseColor("#00E676")
            3 -> Color.parseColor("#88C0D0")
            4 -> Color.parseColor("#FFB300")
            5 -> Color.parseColor("#E4E4E7")
            6 -> Color.parseColor("#2563EB")
            7 -> Color.parseColor("#FF2A85")
            8 -> Color.parseColor("#00FF66")
            9 -> Color.parseColor("#FFD700")
            else -> Color.parseColor(accentColorHex)
        }

        val cx = w / 2f; val cy = h / 2f
        val r = (minDim - pad * 2) * 0.34f
        val corner = when (themeStyleId) {
            0 -> minDim * 0.34f
            2, 5 -> minDim * 0.12f
            8 -> 4f
            else -> minDim * 0.22f
        }

        paint.style = Paint.Style.FILL; paint.color = mainColor
        rect.set(pad, pad, w - pad, h - pad)
        canvas.drawRoundRect(rect, corner, corner, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isTileFocused) 4.5f else 2f
        paint.color = if (isTileFocused) (if (themeStyleId == 6) Color.parseColor("#1D4ED8") else Color.WHITE) else badgeColor
        rect.set(pad, pad, w - pad, h - pad)
        canvas.drawRoundRect(rect, corner, corner, paint)

        val fgColor = when (themeStyleId) {
            5 -> Color.parseColor("#F4F4F5")
            6 -> Color.parseColor("#0F172A")
            8 -> Color.parseColor("#00FF66")
            9 -> Color.parseColor("#FFF8DC")
            else -> Color.WHITE
        }

        val isWireframeTheme = themeStyleId in listOf(1, 2, 8)
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND

        when (iconType) {
            "PLAYLISTS" -> {
                for (i in -1..1) {
                    val ly = cy + i * (r * 0.52f)
                    paint.color = if (i == 0) badgeColor else fgColor
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx - r * 0.56f, ly, r * 0.13f, paint)
                    paint.style = if (isWireframeTheme) Paint.Style.STROKE else Paint.Style.FILL
                    paint.strokeWidth = 2.5f
                    rect.set(cx - r * 0.26f, ly - r * 0.11f, cx + r * 0.68f, ly + r * 0.11f)
                    canvas.drawRoundRect(rect, 5f, 5f, paint)
                }
            }
            "EDITOR" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f; paint.color = fgColor
                canvas.drawLine(cx - r * 0.62f, cy - r * 0.42f, cx + r * 0.10f, cy - r * 0.42f, paint)
                canvas.drawLine(cx - r * 0.62f, cy, cx - r * 0.08f, cy, paint)
                canvas.drawLine(cx - r * 0.62f, cy + r * 0.42f, cx - r * 0.22f, cy + r * 0.42f, paint)
                paint.color = badgeColor
                canvas.drawLine(cx + r * 0.05f, cy + r * 0.45f, cx + r * 0.62f, cy - r * 0.35f, paint)
            }
            "ANALYZER" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.2f
                for (ring in 1..3) {
                    val rad = r * (0.32f * ring)
                    paint.color = if (ring == 3) badgeColor else fgColor
                    rect.set(cx - rad, cy - rad + r * 0.2f, cx + rad, cy + rad + r * 0.2f)
                    canvas.drawArc(rect, 210f, 120f, false, paint)
                }
                paint.style = Paint.Style.FILL; paint.color = badgeColor
                canvas.drawCircle(cx, cy + r * 0.34f, r * 0.16f, paint)
            }
            "THEMES" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f; paint.color = fgColor
                canvas.drawCircle(cx, cy, r * 0.78f, paint)
                paint.style = Paint.Style.FILL
                paint.color = badgeColor; canvas.drawCircle(cx - r * 0.30f, cy - r * 0.22f, r * 0.17f, paint)
                paint.color = Color.parseColor("#00E5FF"); canvas.drawCircle(cx + r * 0.26f, cy - r * 0.26f, r * 0.17f, paint)
                paint.color = Color.parseColor("#FFD740"); canvas.drawCircle(cx + r * 0.30f, cy + r * 0.24f, r * 0.17f, paint)
            }
            "BUFFER" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = fgColor
                rect.set(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f)
                canvas.drawArc(rect, 150f, 240f, false, paint)
                paint.style = if (isWireframeTheme) Paint.Style.STROKE else Paint.Style.FILL; paint.color = badgeColor
                path.reset()
                path.moveTo(cx + r * 0.10f, cy - r * 0.56f); path.lineTo(cx - r * 0.34f, cy + r * 0.04f)
                path.lineTo(cx + r * 0.02f, cy + r * 0.04f); path.lineTo(cx - r * 0.12f, cy + r * 0.56f)
                path.lineTo(cx + r * 0.36f, cy - r * 0.06f); path.lineTo(cx + r * 0.02f, cy - r * 0.06f); path.close()
                canvas.drawPath(path, paint)
            }
            "DECODER" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = fgColor
                rect.set(cx - r * 0.52f, cy - r * 0.52f, cx + r * 0.52f, cy + r * 0.52f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.style = if (isWireframeTheme) Paint.Style.STROKE else Paint.Style.FILL; paint.color = badgeColor
                rect.set(cx - r * 0.24f, cy - r * 0.24f, cx + r * 0.24f, cy + r * 0.24f)
                canvas.drawRoundRect(rect, 4f, 4f, paint)
            }
            "EPG_GRID" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = fgColor
                rect.set(cx - r * 0.72f, cy - r * 0.62f, cx + r * 0.72f, cy + r * 0.68f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawLine(cx - r * 0.72f, cy - r * 0.18f, cx + r * 0.72f, cy - r * 0.18f, paint)
                paint.style = Paint.Style.FILL; paint.color = badgeColor
                canvas.drawCircle(cx - r * 0.32f, cy + r * 0.18f, r * 0.13f, paint)
                canvas.drawCircle(cx, cy + r * 0.18f, r * 0.13f, paint)
                canvas.drawCircle(cx + r * 0.32f, cy + r * 0.18f, r * 0.13f, paint)
            }
            "TOP_CHANNELS" -> {
                paint.style = if (isWireframeTheme) Paint.Style.STROKE else Paint.Style.FILL
                paint.strokeWidth = 3.2f; paint.color = badgeColor
                path.reset()
                path.moveTo(cx - r * 0.66f, cy + r * 0.48f); path.lineTo(cx - r * 0.74f, cy - r * 0.36f)
                path.lineTo(cx - r * 0.26f, cy + r * 0.06f); path.lineTo(cx, cy - r * 0.56f)
                path.lineTo(cx + r * 0.26f, cy + r * 0.06f); path.lineTo(cx + r * 0.74f, cy - r * 0.36f)
                path.lineTo(cx + r * 0.66f, cy + r * 0.48f); path.close()
                canvas.drawPath(path, paint)
            }
            "FAVORITES" -> {
                paint.style = if (isWireframeTheme) Paint.Style.STROKE else Paint.Style.FILL
                paint.strokeWidth = 3f; paint.color = badgeColor
                path.reset()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val rad = if (i % 2 == 0) r * 0.78f else r * 0.34f
                    val sx = cx + (rad * cos(a)).toFloat(); val sy = cy + (rad * sin(a)).toFloat()
                    if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                path.close(); canvas.drawPath(path, paint)
            }
            "AUDIO" -> {
                val pos = listOf(-0.52f, 0f, 0.52f); val hts = listOf(0.45f, 0.76f, 0.58f)
                paint.style = Paint.Style.FILL
                for (i in pos.indices) {
                    paint.color = if (i == 1) badgeColor else fgColor
                    rect.set(cx + pos[i] * r - 5.5f, cy - hts[i] * r, cx + pos[i] * r + 5.5f, cy + hts[i] * r)
                    canvas.drawRoundRect(rect, 5f, 5f, paint)
                }
            }
            "CONTROL_MODE" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.2f; paint.color = fgColor
                rect.set(cx - r * 0.48f, cy - r * 0.80f, cx + r * 0.48f, cy + r * 0.80f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                canvas.drawCircle(cx, cy - r * 0.22f, r * 0.24f, paint)
                paint.style = Paint.Style.FILL; paint.color = badgeColor
                canvas.drawCircle(cx, cy - r * 0.22f, r * 0.10f, paint)
            }
            "SETTINGS_GEAR" -> {
                paint.style = Paint.Style.FILL; paint.color = fgColor
                for (deg in 0 until 360 step 45) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawCircle(cx + (r * 0.70f * cos(rad)).toFloat(), cy + (r * 0.70f * sin(rad)).toFloat(), r * 0.18f, paint)
                }
                canvas.drawCircle(cx, cy, r * 0.64f, paint)
                paint.color = mainColor; canvas.drawCircle(cx, cy, r * 0.28f, paint)
            }
            "SWAP_GROUPS" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.8f; paint.color = fgColor
                canvas.drawLine(cx - r * 0.60f, cy - r * 0.26f, cx + r * 0.60f, cy - r * 0.26f, paint)
                canvas.drawLine(cx + r * 0.24f, cy - r * 0.54f, cx + r * 0.60f, cy - r * 0.26f, paint)
                paint.color = badgeColor
                canvas.drawLine(cx - r * 0.60f, cy + r * 0.26f, cx + r * 0.60f, cy + r * 0.26f, paint)
                canvas.drawLine(cx - r * 0.24f, cy + r * 0.54f, cx - r * 0.60f, cy + r * 0.26f, paint)
            }
            else -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.2f; paint.color = fgColor
                rect.set(cx - r * 0.76f, cy - r * 0.56f, cx + r * 0.76f, cy + r * 0.42f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawLine(cx - r * 0.36f, cy + r * 0.68f, cx + r * 0.36f, cy + r * 0.68f, paint)
                paint.style = Paint.Style.FILL; paint.color = badgeColor
                canvas.drawCircle(cx, cy - r * 0.08f, r * 0.24f, paint)
            }
        }
    }
}
