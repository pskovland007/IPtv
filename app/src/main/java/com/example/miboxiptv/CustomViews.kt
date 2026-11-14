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
        postInvalidateDelayed(110L)
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
        ellipsize = TextUtils.TruncateAt.END
    }

    override fun isFocused(): Boolean = forceMarqueeActive || super.isFocused()
}

class ChannelRowView(context: Context) : FrameLayout(context) {
    val badgeView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        setSingleLine(true)
        layoutParams = LayoutParams(42, 34, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = 14 }
    }
    val titleView = MarqueeRowTextView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL).apply {
            leftMargin = 66; rightMargin = 48
        }
    }
    val eqView = LiveEqualizerView(context: Context).apply {
        visibility = View.GONE
        layoutParams = LayoutParams(30, 20, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = 12 }
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

// 6 ОТДЕЛЬНЫХ НАБОРОВ ИКОНОК ПОД КАЖДУЮ ТЕМУ СО СКРИНШОТОВ
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
            0 -> drawMaterialThemeIcon(canvas, cx, cy, minDim)
            1 -> drawNeonThemeIcon(canvas, cx, cy, minDim)
            2 -> drawAmoledThemeIcon(canvas, cx, cy, minDim)
            3 -> drawMonochromeThemeIcon(canvas, cx, cy, minDim)
            4 -> drawRetroThemeIcon(canvas, cx, cy, minDim)
            else -> drawCarousel3DThemeIcon(canvas, cx, cy, minDim)
        }
        paint.shader = null
    }

    // =========================================================================
    // ТЕМА 0: МАТЕРИАЛ ДИЗАЙН (Плоские разноцветные фигурные иконки со скриншота)
    // =========================================================================
    private fun drawMaterialThemeIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val r = minDim * 0.36f
        if (isTileFocused) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#242563EB")
            canvas.drawCircle(cx, cy, minDim * 0.46f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.5f
            paint.color = Color.parseColor("#2563EB")
            canvas.drawCircle(cx, cy, minDim * 0.46f, paint)
        }
        paint.style = Paint.Style.FILL
        when (iconType) {
            "PLAYLISTS" -> {
                // Жёлтая плоская папка Material
                paint.color = Color.parseColor("#F59E0B")
                path.reset()
                path.moveTo(cx - r * 0.85f, cy - r * 0.55f)
                path.lineTo(cx - r * 0.20f, cy - r * 0.55f)
                path.lineTo(cx, cy - r * 0.32f)
                path.lineTo(cx + r * 0.85f, cy - r * 0.32f)
                path.lineTo(cx + r * 0.85f, cy + r * 0.62f)
                path.lineTo(cx - r * 0.85f, cy + r * 0.62f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = Color.parseColor("#FBBF24")
                rect.set(cx - r * 0.85f, cy - r * 0.25f, cx + r * 0.85f, cy + r * 0.62f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
            }
            "EDITOR" -> {
                // Ножницы с синими кольцами и серебристыми лезвиями
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 7f
                paint.color = Color.parseColor("#94A3B8")
                canvas.drawLine(cx - r * 0.35f, cy + r * 0.35f, cx + r * 0.72f, cy - r * 0.62f, paint)
                canvas.drawLine(cx - r * 0.35f, cy - r * 0.35f, cx + r * 0.72f, cy + r * 0.62f, paint)
                paint.color = Color.parseColor("#1D4ED8")
                paint.strokeWidth = 6.5f
                canvas.drawCircle(cx - r * 0.48f, cy - r * 0.42f, r * 0.30f, paint)
                canvas.drawCircle(cx - r * 0.48f, cy + r * 0.42f, r * 0.30f, paint)
            }
            "ANALYZER" -> {
                // Бирюзовый прицел-мишень с 4 секторами
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#BAE6FD")
                canvas.drawCircle(cx, cy, r * 0.74f, paint)
                paint.color = Color.parseColor("#38BDF8")
                rect.set(cx - r * 0.74f, cy - r * 0.74f, cx + r * 0.74f, cy + r * 0.74f)
                canvas.drawArc(rect, 0f, 90f, true, paint)
                canvas.drawArc(rect, 180f, 90f, true, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f
                paint.color = Color.parseColor("#0284C7")
                canvas.drawCircle(cx, cy, r * 0.74f, paint)
                canvas.drawLine(cx, cy - r * 0.96f, cx, cy - r * 0.56f, paint)
                canvas.drawLine(cx, cy + r * 0.56f, cx, cy + r * 0.96f, paint)
                canvas.drawLine(cx - r * 0.96f, cy, cx - r * 0.56f, cy, paint)
                canvas.drawLine(cx + r * 0.56f, cy, cx + r * 0.96f, cy, paint)
            }
            "THEMES" -> {
                // Оранжевая палитра художника с цветными красками
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#F59E0B")
                canvas.drawCircle(cx, cy, r * 0.82f, paint)
                paint.color = Color.parseColor("#E4E6EE")
                canvas.drawCircle(cx + r * 0.34f, cy + r * 0.34f, r * 0.24f, paint)
                paint.color = Color.parseColor("#EF4444"); canvas.drawCircle(cx - r * 0.36f, cy - r * 0.18f, r * 0.14f, paint)
                paint.color = Color.parseColor("#8B5CF6"); canvas.drawCircle(cx - r * 0.08f, cy - r * 0.44f, r * 0.14f, paint)
                paint.color = Color.parseColor("#3B82F6"); canvas.drawCircle(cx + r * 0.32f, cy - r * 0.24f, r * 0.14f, paint)
                paint.color = Color.parseColor("#10B981"); canvas.drawCircle(cx - r * 0.32f, cy + r * 0.28f, r * 0.14f, paint)
            }
            "BUFFER" -> {
                // Мятно-голубое облако со стрелкой загрузки
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#67E8F9")
                canvas.drawCircle(cx - r * 0.38f, cy + r * 0.08f, r * 0.42f, paint)
                canvas.drawCircle(cx + r * 0.02f, cy - r * 0.16f, r * 0.52f, paint)
                canvas.drawCircle(cx + r * 0.44f, cy + r * 0.12f, r * 0.38f, paint)
                rect.set(cx - r * 0.48f, cy + r * 0.05f, cx + r * 0.50f, cy + r * 0.50f)
                canvas.drawRect(rect, paint)
                paint.color = Color.parseColor("#0D9488")
                rect.set(cx - r * 0.12f, cy - r * 0.25f, cx + r * 0.12f, cy + r * 0.15f)
                canvas.drawRect(rect, paint)
                path.reset()
                path.moveTo(cx - r * 0.32f, cy + r * 0.08f)
                path.lineTo(cx + r * 0.32f, cy + r * 0.08f)
                path.lineTo(cx, cy + r * 0.42f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "DECODER" -> {
                // Тёмная виниловая пластинка с голубым яблоком
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#1E293B")
                canvas.drawCircle(cx, cy, r * 0.84f, paint)
                paint.color = Color.parseColor("#334155")
                rect.set(cx - r * 0.84f, cy - r * 0.84f, cx + r * 0.84f, cy + r * 0.84f)
                canvas.drawArc(rect, -45f, 90f, true, paint)
                paint.color = Color.parseColor("#38BDF8")
                canvas.drawCircle(cx, cy, r * 0.34f, paint)
                paint.color = Color.parseColor("#1E293B")
                canvas.drawCircle(cx, cy, r * 0.10f, paint)
            }
            "EPG_GRID" -> {
                // Отрывной календарь с малиновой шапкой и кружком в углу
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#D6D3D1")
                rect.set(cx - r * 0.72f, cy - r * 0.65f, cx + r * 0.72f, cy + r * 0.68f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.color = Color.parseColor("#BE123C")
                rect.set(cx - r * 0.72f, cy - r * 0.65f, cx + r * 0.72f, cy - r * 0.20f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.color = Color.parseColor("#DC2626")
                canvas.drawCircle(cx + r * 0.42f, cy + r * 0.38f, r * 0.36f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx + r * 0.42f, cy + r * 0.38f, r * 0.18f, paint)
            }
            "TOP_CHANNELS" -> {
                // Красный развивающийся флажок на коричневом древке
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#EF4444")
                rect.set(cx - r * 0.50f, cy - r * 0.68f, cx + r * 0.25f, cy - r * 0.02f)
                canvas.drawRect(rect, paint)
                paint.color = Color.parseColor("#DC2626")
                rect.set(cx + r * 0.10f, cy - r * 0.54f, cx + r * 0.68f, cy + r * 0.10f)
                canvas.drawRect(rect, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 7f
                paint.color = Color.parseColor("#78350F")
                canvas.drawLine(cx - r * 0.50f, cy - r * 0.72f, cx - r * 0.50f, cy + r * 0.75f, paint)
            }
            "FAVORITES" -> {
                // Розовое сердце с белой линией пульса ЭКГ
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#EC4899")
                canvas.drawCircle(cx - r * 0.36f, cy - r * 0.22f, r * 0.44f, paint)
                canvas.drawCircle(cx + r * 0.36f, cy - r * 0.22f, r * 0.44f, paint)
                path.reset()
                path.moveTo(cx - r * 0.77f, cy - r * 0.04f)
                path.lineTo(cx + r * 0.77f, cy - r * 0.04f)
                path.lineTo(cx, cy + r * 0.78f)
                path.close()
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = Color.WHITE
                path.reset()
                path.moveTo(cx - r * 0.65f, cy + r * 0.05f)
                path.lineTo(cx - r * 0.22f, cy + r * 0.05f)
                path.lineTo(cx - r * 0.08f, cy - r * 0.28f)
                path.lineTo(cx + r * 0.12f, cy + r * 0.32f)
                path.lineTo(cx + r * 0.26f, cy + r * 0.05f)
                path.lineTo(cx + r * 0.65f, cy + r * 0.05f)
                canvas.drawPath(path, paint)
            }
            "AUDIO" -> {
                // Сине-фиолетовый квадрат с белой звуковой синусоидой (верхний правый значок со скрина Material)
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#6366F1")
                rect.set(cx - r * 0.76f, cy - r * 0.62f, cx + r * 0.76f, cy + r * 0.62f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 6.5f; paint.color = Color.parseColor("#E0E7FF")
                path.reset()
                path.moveTo(cx - r * 0.62f, cy)
                path.cubicTo(cx - r * 0.30f, cy - r * 0.65f, cx - r * 0.10f, cy + r * 0.65f, cx + r * 0.18f, cy - r * 0.25f)
                path.lineTo(cx + r * 0.62f, cy - r * 0.25f)
                canvas.drawPath(path, paint)
            }
            "CONTROL_MODE" -> {
                // Серо-синий калькулятор/пульт с экраном и сеткой кнопок
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#64748B")
                rect.set(cx - r * 0.62f, cy - r * 0.80f, cx + r * 0.62f, cy + r * 0.80f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.color = Color.parseColor("#CBD5E1")
                rect.set(cx - r * 0.44f, cy - r * 0.62f, cx + r * 0.44f, cy - r * 0.28f)
                canvas.drawRect(rect, paint)
                for (row in 0..2) {
                    for (col in -1..1) {
                        canvas.drawCircle(cx + col * r * 0.28f, cy + row * r * 0.26f, r * 0.08f, paint)
                    }
                }
            }
            else -> {
                // Солнце с лучами за голубым облаком (правый нижний значок со скрина Material)
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#F59E0B")
                canvas.drawCircle(cx + r * 0.22f, cy - r * 0.18f, r * 0.38f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
                for (deg in 0 until 360 step 45) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawLine(
                        cx + r * 0.22f + (r * 0.46f * cos(rad)).toFloat(), cy - r * 0.18f + (r * 0.46f * sin(rad)).toFloat(),
                        cx + r * 0.22f + (r * 0.62f * cos(rad)).toFloat(), cy - r * 0.18f + (r * 0.62f * sin(rad)).toFloat(), paint
                    )
                }
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#60A5FA")
                canvas.drawCircle(cx - r * 0.32f, cy + r * 0.18f, r * 0.36f, paint)
                canvas.drawCircle(cx + r * 0.04f, cy + r * 0.08f, r * 0.42f, paint)
                rect.set(cx - r * 0.35f, cy + r * 0.15f, cx + r * 0.25f, cy + r * 0.54f)
                canvas.drawRect(rect, paint)
            }
        }
    }

    // =========================================================================
    // ТЕМА 1: НЕОН (Радужные контурные неоновые иконки на чёрном со скриншота)
    // =========================================================================
    private fun drawNeonThemeIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val r = minDim * 0.35f
        val grad = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            intArrayOf(Color.parseColor("#FF7597"), Color.parseColor("#FFE082"), Color.parseColor("#64FFDA"), Color.parseColor("#7C4DFF")),
            null, Shader.TileMode.CLAMP
        )
        if (isTileFocused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.shader = grad
            canvas.drawCircle(cx, cy, minDim * 0.46f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4.6f
        paint.shader = grad

        when (iconType) {
            "PLAYLISTS" -> {
                // Треугольник Play с внутренними лучами как Google Play на скрине Неон
                path.reset()
                path.moveTo(cx - r * 0.55f, cy - r * 0.72f)
                path.lineTo(cx + r * 0.68f, cy)
                path.lineTo(cx - r * 0.55f, cy + r * 0.72f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(cx - r * 0.55f, cy - r * 0.72f, cx + r * 0.15f, cy + r * 0.30f, paint)
                canvas.drawLine(cx - r * 0.55f, cy + r * 0.72f, cx + r * 0.15f, cy - r * 0.30f, paint)
            }
            "EDITOR" -> {
                // Горизонтальные строки и поверх диагональный карандаш (нижний ряд по центру на скрине Неон)
                for (i in -1..1) {
                    val y = cy + i * r * 0.38f
                    canvas.drawLine(cx - r * 0.68f, y, cx + r * 0.55f, y, paint)
                }
                path.reset()
                path.moveTo(cx + r * 0.12f, cy - r * 0.68f)
                path.lineTo(cx + r * 0.38f, cy - r * 0.56f)
                path.lineTo(cx - r * 0.08f, cy + r * 0.62f)
                path.lineTo(cx - r * 0.32f, cy + r * 0.72f)
                path.lineTo(cx - r * 0.32f, cy + r * 0.46f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "ANALYZER" -> {
                // Неоновая лупа (левый нижний угол на скрине Неон)
                canvas.drawCircle(cx - r * 0.15f, cy - r * 0.15f, r * 0.52f, paint)
                canvas.drawLine(cx + r * 0.22f, cy + r * 0.22f, cx + r * 0.72f, cy + r * 0.72f, paint)
            }
            "THEMES" -> {
                // 4 скруглённые фигуры 2x2 (квадраты + круг, средний ряд на скрине Неон)
                rect.set(cx - r * 0.66f, cy - r * 0.66f, cx - r * 0.10f, cy - r * 0.10f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                rect.set(cx + r * 0.10f, cy - r * 0.66f, cx + r * 0.66f, cy - r * 0.10f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                rect.set(cx - r * 0.66f, cy + r * 0.10f, cx - r * 0.10f, cy + r * 0.66f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawCircle(cx + r * 0.38f, cy + r * 0.38f, r * 0.28f, paint)
            }
            "BUFFER" -> {
                // Неоновый фонарик (нижний ряд, 2-я иконка на скрине Неон)
                rect.set(cx - r * 0.32f, cy - r * 0.72f, cx + r * 0.32f, cy - r * 0.38f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
                rect.set(cx - r * 0.20f, cy - r * 0.38f, cx + r * 0.20f, cy + r * 0.72f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawCircle(cx, cy + r * 0.10f, r * 0.08f, paint)
            }
            "DECODER" -> {
                // Прямоугольный чип/купюра с кругом внутри (средний ряд справа на скрине Неон)
                rect.set(cx - r * 0.75f, cy - r * 0.46f, cx + r * 0.75f, cy + r * 0.46f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                canvas.drawCircle(cx, cy, r * 0.24f, paint)
                canvas.drawLine(cx - r * 0.75f, cy - r * 0.18f, cx - r * 0.42f, cy - r * 0.18f, paint)
                canvas.drawLine(cx + r * 0.42f, cy + r * 0.18f, cx + r * 0.75f, cy + r * 0.18f, paint)
            }
            "EPG_GRID" -> {
                // Неоновые круглые часы со стрелками (верхний ряд по центру на скрине Неон)
                canvas.drawCircle(cx, cy, r * 0.74f, paint)
                canvas.drawLine(cx, cy - r * 0.46f, cx, cy, paint)
                canvas.drawLine(cx, cy, cx + r * 0.34f, cy + r * 0.26f, paint)
            }
            "TOP_CHANNELS" -> {
                // Ромбовидная стрелка компаса (верхний ряд, 6-я иконка на скрине Неон)
                path.reset()
                path.moveTo(cx + r * 0.62f, cy - r * 0.62f)
                path.lineTo(cx + r * 0.16f, cy + r * 0.16f)
                path.lineTo(cx - r * 0.62f, cy + r * 0.62f)
                path.lineTo(cx - r * 0.16f, cy - r * 0.16f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(cx - r * 0.16f, cy - r * 0.16f, cx + r * 0.16f, cy + r * 0.16f, paint)
            }
            "FAVORITES" -> {
                // Неоновый глобус с меридианами и параллелями (левый верхний угол на скрине Неон)
                canvas.drawCircle(cx, cy, r * 0.74f, paint)
                rect.set(cx - r * 0.36f, cy - r * 0.74f, cx + r * 0.36f, cy + r * 0.74f)
                canvas.drawOval(rect, paint)
                canvas.drawLine(cx - r * 0.70f, cy - r * 0.22f, cx + r * 0.70f, cy - r * 0.22f, paint)
                canvas.drawLine(cx - r * 0.70f, cy + r * 0.22f, cx + r * 0.70f, cy + r * 0.22f, paint)
            }
            "AUDIO" -> {
                // Неоновая музыкальная нота с флажком (верхний ряд, 5-я иконка на скрине Неон)
                canvas.drawCircle(cx - r * 0.18f, cy + r * 0.38f, r * 0.32f, paint)
                canvas.drawLine(cx + r * 0.14f, cy + r * 0.38f, cx + r * 0.14f, cy - r * 0.68f, paint)
                path.reset()
                path.moveTo(cx + r * 0.14f, cy - r * 0.68f)
                path.quadTo(cx + r * 0.68f, cy - r * 0.42f, cx + r * 0.56f, cy - r * 0.08f)
                canvas.drawPath(path, paint)
            }
            "CONTROL_MODE" -> {
                // Неоновая телефонная трубка (средний ряд слева на скрине Неон)
                path.reset()
                path.moveTo(cx - r * 0.45f, cy - r * 0.65f)
                path.quadTo(cx - r * 0.80f, cy + r * 0.15f, cx + r * 0.35f, cy + r * 0.70f)
                path.lineTo(cx + r * 0.62f, cy + r * 0.42f)
                path.lineTo(cx + r * 0.28f, cy + r * 0.15f)
                path.lineTo(cx + r * 0.08f, cy + r * 0.32f)
                path.quadTo(cx - r * 0.32f, cy + r * 0.05f, cx - r * 0.22f, cy - r * 0.22f)
                path.lineTo(cx - r * 0.04f, cy - r * 0.38f)
                path.close()
                canvas.drawPath(path, paint)
            }
            else -> {
                // Неоновая зубчатая шестерёнка (нижний ряд, 5-я иконка на скрине Неон)
                canvas.drawCircle(cx, cy, r * 0.26f, paint)
                canvas.drawCircle(cx, cy, r * 0.58f, paint)
                for (deg in 0 until 360 step 45) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawLine(
                        cx + (r * 0.58f * cos(rad)).toFloat(), cy + (r * 0.58f * sin(rad)).toFloat(),
                        cx + (r * 0.78f * cos(rad)).toFloat(), cy + (r * 0.78f * sin(rad)).toFloat(), paint
                    )
                }
            }
        }
    }

    // =========================================================================
    // ТЕМА 2: АМОЛЕД БЛЭК (Тонкие белые чертежи на чёрном со скриншота)
    // =========================================================================
    private fun drawAmoledThemeIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val r = minDim * 0.36f
        if (isTileFocused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, minDim * 0.46f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = Color.WHITE

        when (iconType) {
            "PLAYLISTS" -> {
                // Двойной контурный Play (нижний док слева на скрине AMOLED)
                path.reset()
                path.moveTo(cx - r * 0.58f, cy - r * 0.65f)
                path.lineTo(cx + r * 0.58f, cy)
                path.lineTo(cx - r * 0.58f, cy + r * 0.65f)
                path.close()
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(cx - r * 0.25f, cy - r * 0.45f)
                path.lineTo(cx + r * 0.58f, cy)
                path.lineTo(cx - r * 0.25f, cy + r * 0.45f)
                canvas.drawPath(path, paint)
            }
            "EDITOR" -> {
                // 4 скруглённых квадрата Calculator с + и = внутри (3-й ряд слева на скрине AMOLED)
                val s = r * 0.58f
                rect.set(cx - r * 0.72f, cy - r * 0.72f, cx - r * 0.72f + s, cy - r * 0.72f + s)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawLine(rect.centerX() - 10f, rect.centerY(), rect.centerX() + 10f, rect.centerY(), paint)
                canvas.drawLine(rect.centerX(), rect.centerY() - 10f, rect.centerX(), rect.centerY() + 10f, paint)

                rect.set(cx + r * 0.14f, cy - r * 0.72f, cx + r * 0.14f + s, cy - r * 0.72f + s)
                canvas.drawRoundRect(rect, 10f, 10f, paint)

                rect.set(cx - r * 0.72f, cy + r * 0.14f, cx - r * 0.72f + s, cy + r * 0.14f + s)
                canvas.drawRoundRect(rect, 10f, 10f, paint)

                rect.set(cx + r * 0.14f, cy + r * 0.14f, cx + r * 0.14f + s, cy + r * 0.14f + s)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawLine(rect.centerX() - 10f, rect.centerY() - 5f, rect.centerX() + 10f, rect.centerY() - 5f, paint)
                canvas.drawLine(rect.centerX() - 10f, rect.centerY() + 5f, rect.centerX() + 10f, rect.centerY() + 5f, paint)
            }
            "ANALYZER" -> {
                // Молекулярное дерево SmartThings (2-й ряд слева на скрине AMOLED)
                canvas.drawCircle(cx, cy, r * 0.16f, paint)
                val angles = listOf(-90.0, -25.0, 45.0, 135.0, 205.0)
                for (deg in angles) {
                    val rad = Math.toRadians(deg)
                    val ex = cx + (r * 0.65f * cos(rad)).toFloat()
                    val ey = cy + (r * 0.65f * sin(rad)).toFloat()
                    canvas.drawLine(cx + (r * 0.16f * cos(rad)).toFloat(), cy + (r * 0.16f * sin(rad)).toFloat(), ex, ey, paint)
                    canvas.drawCircle(ex, ey, r * 0.15f, paint)
                }
            }
            "THEMES" -> {
                // Колесо обозрения Theme Park с кабинками (4-й ряд справа на скрине AMOLED)
                canvas.drawCircle(cx, cy, r * 0.66f, paint)
                canvas.drawCircle(cx, cy, r * 0.22f, paint)
                path.reset()
                path.moveTo(cx - r * 0.36f, cy + r * 0.76f)
                path.lineTo(cx, cy)
                path.lineTo(cx + r * 0.36f, cy + r * 0.76f)
                canvas.drawPath(path, paint)
                rect.set(cx - r * 0.80f, cy - r * 0.36f, cx - r * 0.48f, cy - r * 0.06f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
                rect.set(cx + r * 0.48f, cy - r * 0.56f, cx + r * 0.80f, cy - r * 0.26f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
            }
            "BUFFER" -> {
                // Круг AIDA64 с двойным ободом и числом 64 внутри (6-й ряд на скрине AMOLED)
                canvas.drawCircle(cx, cy, r * 0.76f, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = r * 0.72f
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                canvas.drawText("64", cx, cy + r * 0.26f, paint)
            }
            "DECODER" -> {
                // Процессор CPU-Z с кружками-контактами внутри рамки (1-й ряд, 3-я иконка на скрине AMOLED)
                rect.set(cx - r * 0.74f, cy - r * 0.74f, cx + r * 0.74f, cy + r * 0.74f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                rect.set(cx - r * 0.38f, cy - r * 0.38f, cx + r * 0.38f, cy + r * 0.38f)
                canvas.drawRect(rect, paint)
                for (k in -1..1) {
                    val off = k * r * 0.24f
                    canvas.drawCircle(cx + off, cy - r * 0.56f, r * 0.06f, paint)
                    canvas.drawCircle(cx + off, cy + r * 0.56f, r * 0.06f, paint)
                    canvas.drawCircle(cx - r * 0.56f, cy + off, r * 0.06f, paint)
                    canvas.drawCircle(cx + r * 0.56f, cy + off, r * 0.06f, paint)
                }
            }
            "EPG_GRID" -> {
                // Календарь с числом 31 и двумя колечками сверху (2-й ряд, 2-я иконка на скрине AMOLED)
                rect.set(cx - r * 0.70f, cy - r * 0.68f, cx + r * 0.70f, cy + r * 0.70f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                canvas.drawCircle(cx - r * 0.32f, cy - r * 0.46f, r * 0.07f, paint)
                canvas.drawCircle(cx + r * 0.32f, cy - r * 0.46f, r * 0.07f, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = r * 0.76f
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                canvas.drawText("31", cx, cy + r * 0.36f, paint)
            }
            "TOP_CHANNELS" -> {
                // Буквы Z-A / вершины (4-й ряд, 3-я иконка на скрине AMOLED)
                path.reset()
                path.moveTo(cx - r * 0.65f, cy - r * 0.58f)
                path.lineTo(cx + r * 0.15f, cy - r * 0.58f)
                path.lineTo(cx - r * 0.45f, cy + r * 0.58f)
                path.lineTo(cx + r * 0.65f, cy + r * 0.58f)
                path.lineTo(cx + r * 0.20f, cy - r * 0.58f)
                path.lineTo(cx - r * 0.18f, cy + r * 0.25f)
                canvas.drawPath(path, paint)
            }
            "FAVORITES" -> {
                // Картина с горами и солнцем OneDrive (1-й ряд справа на скрине AMOLED)
                rect.set(cx - r * 0.74f, cy - r * 0.60f, cx + r * 0.74f, cy + r * 0.60f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawCircle(cx + r * 0.34f, cy - r * 0.22f, r * 0.16f, paint)
                path.reset()
                path.moveTo(cx - r * 0.62f, cy + r * 0.48f)
                path.lineTo(cx - r * 0.15f, cy - r * 0.12f)
                path.lineTo(cx + r * 0.25f, cy + r * 0.32f)
                path.lineTo(cx + r * 0.45f, cy + r * 0.12f)
                path.lineTo(cx + r * 0.65f, cy + r * 0.48f)
                canvas.drawPath(path, paint)
            }
            "AUDIO" -> {
                // Вертикальные линии звуковой дорожки Voice Recorder (3-й ряд, 2-я иконка на скрине AMOLED)
                val xs = listOf(-0.60f, -0.36f, -0.12f, 0.12f, 0.36f, 0.60f)
                val hs = listOf(0.18f, 0.46f, 0.74f, 0.32f, 0.56f, 0.20f)
                for (i in xs.indices) {
                    canvas.drawLine(cx + xs[i] * r, cy - hs[i] * r, cx + xs[i] * r, cy + hs[i] * r, paint)
                }
            }
            "CONTROL_MODE" -> {
                // Геймпад Gaming Hub (2-й ряд, 3-я иконка на скрине AMOLED)
                rect.set(cx - r * 0.78f, cy - r * 0.46f, cx + r * 0.78f, cy + r * 0.46f)
                canvas.drawRoundRect(rect, 26f, 26f, paint)
                canvas.drawCircle(cx - r * 0.36f, cy, r * 0.22f, paint)
                canvas.drawCircle(cx + r * 0.36f, cy, r * 0.22f, paint)
                canvas.drawLine(cx - r * 0.36f - 8f, cy, cx - r * 0.36f + 8f, cy, paint)
                canvas.drawLine(cx - r * 0.36f, cy - 8f, cx - r * 0.36f, cy + 8f, paint)
            }
            else -> {
                // Камера Expert RAW с диафрагмой (2-й ряд справа на скрине AMOLED)
                rect.set(cx - r * 0.76f, cy - r * 0.54f, cx + r * 0.76f, cy + r * 0.54f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawCircle(cx, cy, r * 0.34f, paint)
                canvas.drawCircle(cx + r * 0.52f, cy - r * 0.32f, r * 0.08f, paint)
                for (deg in 0 until 360 step 60) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawLine(
                        cx + (r * 0.12f * cos(rad)).toFloat(), cy + (r * 0.12f * sin(rad)).toFloat(),
                        cx + (r * 0.32f * cos(rad + 0.5)).toFloat(), cy + (r * 0.32f * sin(rad + 0.5)).toFloat(), paint
                    )
                }
            }
        }
    }

    // =========================================================================
    // ТЕМА 3: МОНОХРОМ (Белые квадратные карточки с серыми силуэтами со скриншота)
    // =========================================================================
    private fun drawMonochromeThemeIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
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

        val gray = Color.parseColor("#71717A")
        paint.color = gray

        when (iconType) {
            "PLAYLISTS" -> {
                // 3 строгих серых горизонтальных блока с точками (2-й ряд, 7-я иконка на скрине Монохром)
                paint.style = Paint.Style.FILL
                for (i in -1..1) {
                    val y = cy + i * r * 0.42f
                    rect.set(cx - r * 0.55f, y - r * 0.11f, cx + r * 0.55f, y + r * 0.11f)
                    canvas.drawRect(rect, paint)
                }
            }
            "EDITOR" -> {
                // Серый диагональный карандаш (2-й ряд слева на скрине Монохром)
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
                // Иконка волн RSS / Сигнал (4-й ряд справа на скрине Монохром)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx - r * 0.42f, cy + r * 0.42f, r * 0.14f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.5f
                rect.set(cx - r * 0.95f, cy - r * 0.10f, cx + r * 0.10f, cy + r * 0.95f)
                canvas.drawArc(rect, -90f, 90f, false, paint)
                rect.set(cx - r * 1.30f, cy - r * 0.45f, cx + r * 0.45f, cy + r * 1.30f)
                canvas.drawArc(rect, -90f, 90f, false, paint)
            }
            "THEMES" -> {
                // Солнце с наполовину закрашенным диском — Контраст/Тема (нижний ряд по центру на скрине Монохром)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f
                canvas.drawCircle(cx, cy, r * 0.52f, paint)
                paint.style = Paint.Style.FILL
                rect.set(cx - r * 0.52f, cy - r * 0.52f, cx + r * 0.52f, cy + r * 0.52f)
                canvas.drawArc(rect, 90f, 180f, true, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f
                for (deg in 0 until 360 step 45) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawLine(
                        cx + (r * 0.62f * cos(rad)).toFloat(), cy + (r * 0.62f * sin(rad)).toFloat(),
                        cx + (r * 0.78f * cos(rad)).toFloat(), cy + (r * 0.78f * sin(rad)).toFloat(), paint
                    )
                }
            }
            "BUFFER" -> {
                // Две круговые стрелки перезагрузки (1-й ряд, 5-я иконка на скрине Монохром)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                rect.set(cx - r * 0.56f, cy - r * 0.56f, cx + r * 0.56f, cy + r * 0.56f)
                canvas.drawArc(rect, -150f, 130f, false, paint)
                canvas.drawArc(rect, 30f, 130f, false, paint)
            }
            "DECODER" -> {
                // Монохромная шестерёнка (1-й ряд, 6-я иконка на скрине Монохром)
                paint.style = Paint.Style.FILL
                for (deg in 0 until 360 step 45) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawCircle(cx + (r * 0.56f * cos(rad)).toFloat(), cy + (r * 0.56f * sin(rad)).toFloat(), r * 0.14f, paint)
                }
                canvas.drawCircle(cx, cy, r * 0.52f, paint)
                paint.color = Color.parseColor("#F8FAFC")
                canvas.drawCircle(cx, cy, r * 0.22f, paint)
            }
            "EPG_GRID" -> {
                // Календарь с сеткой точек (2-й ряд, 4-я иконка на скрине Монохром)
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
                // Растущие столбики сигнала/рейтинга (7-й ряд, 8-я иконка на скрине Монохром)
                paint.style = Paint.Style.FILL
                for (i in 0..3) {
                    val left = cx - r * 0.58f + i * r * 0.32f
                    val top = cy + r * 0.50f - (i + 1) * r * 0.26f
                    rect.set(left, top, left + r * 0.20f, cy + r * 0.50f)
                    canvas.drawRect(rect, paint)
                }
            }
            "FAVORITES" -> {
                // Залитая серая звезда (2-й ряд, 6-я иконка на скрине Монохром)
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
                // Рупор динамика со звуковыми волнами (7-й ряд, 2-я иконка на скрине Монохром)
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
                rect.set(cx - r * 0.22f, cy - r * 0.58f, cx + r * 0.60f, cy + r * 0.58f)
                canvas.drawArc(rect, -55f, 110f, false, paint)
            }
            "CONTROL_MODE" -> {
                // Монохромный смартфон (нижний ряд, 8-я иконка на скрине Монохром)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f
                rect.set(cx - r * 0.36f, cy - r * 0.64f, cx + r * 0.36f, cy + r * 0.64f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy + r * 0.46f, r * 0.07f, paint)
            }
            else -> {
                // 3 горизонтальных ползунка настроек (3-й ряд, 3-я иконка на скрине Монохром)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f
                val shifts = listOf(-0.25f, 0.25f, -0.10f)
                for (i in -1..1) {
                    val y = cy + i * r * 0.38f
                    canvas.drawLine(cx - r * 0.58f, y, cx + r * 0.58f, y, paint)
                    val kx = cx + shifts[i + 1] * r
                    canvas.drawLine(kx, y - r * 0.15f, kx, y + r * 0.15f, paint)
                }
            }
        }
    }

    // =========================================================================
    // ТЕМА 4: РЕТРО (Винтажные матовые карточки с фигурами со скриншота Ретро)
    // =========================================================================
    private fun drawRetroThemeIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val pad = minDim * 0.08f
        val r = (minDim - pad * 2) * 0.36f
        val cardColor = when (iconType) {
            "PLAYLISTS", "FAVORITES", "EDITOR", "TOP_CHANNELS", "MORE" -> Color.parseColor("#2A7B69") // Изумрудный со скрина Ретро
            "ANALYZER", "BUFFER", "AUDIO" -> Color.parseColor("#9B533F") // Терракотовый со скрина Ретро
            "THEMES", "EPG_GRID" -> Color.parseColor("#D68928") // Горчичный со скрина Ретро
            else -> Color.parseColor("#544F4E") // Тёмно-серый графит со скрина Ретро
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
                // Ретро-папка/дискета с зелёной и синей полосками (левый нижний угол на скрине Ретро)
                paint.style = Paint.Style.FILL; paint.color = cream
                rect.set(cx - r * 0.72f, cy - r * 0.56f, cx + r * 0.72f, cy + r * 0.62f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                paint.color = Color.parseColor("#2A7B69")
                rect.set(cx - r * 0.56f, cy - r * 0.14f, cx + r * 0.56f, cy + r * 0.08f)
                canvas.drawRect(rect, paint)
                paint.color = Color.parseColor("#2C3E6B")
                rect.set(cx - r * 0.56f, cy + r * 0.24f, cx + r * 0.56f, cy + r * 0.42f)
                canvas.drawRect(rect, paint)
            }
            "EDITOR" -> {
                // Кремовый угольник / чертёжный угол 45° (верхний ряд, 4-я иконка на скрине Ретро)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 9f; paint.color = cream
                path.reset()
                path.moveTo(cx + r * 0.35f, cy - r * 0.65f)
                path.lineTo(cx - r * 0.55f, cy + r * 0.45f)
                path.lineTo(cx + r * 0.55f, cy + r * 0.45f)
                canvas.drawPath(path, paint)
                canvas.drawLine(cx + r * 0.15f, cy + r * 0.10f, cx + r * 0.15f, cy + r * 0.45f, paint)
            }
            "ANALYZER" -> {
                // Красно-белая круглая мишень (верхний ряд, 5-я иконка на скрине Ретро)
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#E63926")
                canvas.drawCircle(cx, cy, r * 0.76f, paint)
                paint.color = cream
                canvas.drawCircle(cx, cy, r * 0.52f, paint)
                paint.color = Color.parseColor("#E63926")
                canvas.drawCircle(cx, cy, r * 0.32f, paint)
                paint.color = cream
                canvas.drawCircle(cx, cy, r * 0.16f, paint)
            }
            "THEMES" -> {
                // Горчичный квадрат с перекрестием внутри (верхний ряд справа на скрине Ретро)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
                paint.color = cream
                rect.set(cx - r * 0.65f, cy - r * 0.65f, cx + r * 0.65f, cy + r * 0.65f)
                canvas.drawRect(rect, paint)
                canvas.drawLine(cx - r * 0.65f, cy - r * 0.65f, cx + r * 0.65f, cy + r * 0.65f, paint)
                canvas.drawLine(cx + r * 0.65f, cy - r * 0.65f, cx - r * 0.65f, cy + r * 0.65f, paint)
            }
            "BUFFER" -> {
                // Круглый диск/радар с ручкой сверху справа (верхний ряд, 3-я иконка на скрине Ретро)
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#FDE047")
                canvas.drawCircle(cx, cy + r * 0.06f, r * 0.68f, paint)
                paint.color = Color.parseColor("#D97706")
                canvas.drawCircle(cx, cy + r * 0.06f, r * 0.44f, paint)
                paint.color = Color.parseColor("#FDE047")
                canvas.drawCircle(cx, cy + r * 0.06f, r * 0.20f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = cream
                canvas.drawLine(cx + r * 0.35f, cy - r * 0.45f, cx + r * 0.58f, cy - r * 0.72f, paint)
            }
            "DECODER" -> {
                // Ретро-радиоприёмник с антенной (2-й ряд, 5-я иконка на скрине Ретро)
                paint.style = Paint.Style.FILL; paint.color = cream
                rect.set(cx - r * 0.62f, cy - r * 0.32f, cx + r * 0.62f, cy + r * 0.62f)
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.color = cream
                canvas.drawLine(cx + r * 0.05f, cy - r * 0.32f, cx + r * 0.28f, cy - r * 0.70f, paint)
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#3B2F63")
                rect.set(cx - r * 0.44f, cy - r * 0.14f, cx + r * 0.44f, cy + r * 0.06f)
                canvas.drawRect(rect, paint)
                rect.set(cx - r * 0.44f, cy + r * 0.22f, cx + r * 0.44f, cy + r * 0.42f)
                canvas.drawRect(rect, paint)
            }
            "EPG_GRID" -> {
                // Вертикальный планшет/блокнот с колонкой слева (нижний ряд, 4-я иконка на скрине Ретро)
                paint.style = Paint.Style.FILL; paint.color = cream
                rect.set(cx - r * 0.66f, cy - r * 0.64f, cx - r * 0.36f, cy + r * 0.64f)
                canvas.drawRect(rect, paint)
                rect.set(cx - r * 0.20f, cy - r * 0.52f, cx + r * 0.66f, cy + r * 0.64f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
            }
            "TOP_CHANNELS" -> {
                // Треугольная пирамида с кругом над вершиной (2-й ряд справа на скрине Ретро)
                paint.style = Paint.Style.FILL; paint.color = cream
                path.reset()
                path.moveTo(cx, cy - r * 0.12f)
                path.lineTo(cx - r * 0.76f, cy + r * 0.56f)
                path.lineTo(cx + r * 0.76f, cy + r * 0.56f)
                path.close()
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
                canvas.drawCircle(cx, cy - r * 0.48f, r * 0.16f, paint)
            }
            "FAVORITES" -> {
                // Светильник с расходящимися вверх лучами (2-й ряд слева на скрине Ретро)
                paint.style = Paint.Style.FILL; paint.color = cream
                rect.set(cx - r * 0.65f, cy + r * 0.34f, cx + r * 0.65f, cy + r * 0.56f)
                canvas.drawRect(rect, paint)
                path.reset()
                path.moveTo(cx - r * 0.50f, cy + r * 0.20f)
                path.lineTo(cx - r * 0.25f, cy - r * 0.08f)
                path.lineTo(cx + r * 0.25f, cy - r * 0.08f)
                path.lineTo(cx + r * 0.50f, cy + r * 0.20f)
                path.close()
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f
                for (deg in listOf(-145, -115, -65, -35)) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawLine(
                        cx + (r * 0.42f * cos(rad)).toFloat(), cy + (r * 0.42f * sin(rad)).toFloat(),
                        cx + (r * 0.78f * cos(rad)).toFloat(), cy + (r * 0.78f * sin(rad)).toFloat(), paint
                    )
                }
            }
            "AUDIO" -> {
                // Трапеция динамика/корзины (нижний ряд справа на скрине Ретро)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f; paint.color = cream
                path.reset()
                path.moveTo(cx - r * 0.68f, cy - r * 0.52f)
                path.lineTo(cx + r * 0.68f, cy - r * 0.52f)
                path.lineTo(cx + r * 0.44f, cy + r * 0.56f)
                path.lineTo(cx - r * 0.44f, cy + r * 0.56f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(cx - r * 0.46f, cy + r * 0.12f, cx + r * 0.46f, cy + r * 0.12f, paint)
            }
            "CONTROL_MODE" -> {
                // Трапеция с рычажком сверху (верхний левый угол на скрине Ретро)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f; paint.color = cream
                path.reset()
                path.moveTo(cx - r * 0.36f, cy - r * 0.18f)
                path.lineTo(cx + r * 0.36f, cy - r * 0.18f)
                path.lineTo(cx + r * 0.56f, cy + r * 0.48f)
                path.lineTo(cx - r * 0.56f, cy + r * 0.48f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(cx - r * 0.32f, cy - r * 0.36f, cx + r * 0.16f, cy - r * 0.36f, paint)
                canvas.drawLine(cx + r * 0.16f, cy - r * 0.36f, cx + r * 0.44f, cy - r * 0.68f, paint)
            }
            else -> {
                // Изогнутая широкая стрелка обновления (нижний ряд, 5-я иконка на скрине Ретро)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 10f; paint.color = cream
                rect.set(cx - r * 0.56f, cy - r * 0.48f, cx + r * 0.56f, cy + r * 0.56f)
                canvas.drawArc(rect, 15f, 275f, false, paint)
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx + r * 0.15f, cy - r * 0.72f)
                path.lineTo(cx + r * 0.72f, cy - r * 0.25f)
                path.lineTo(cx + r * 0.15f, cy + r * 0.05f)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }

    // =========================================================================
    // ТЕМА 5: КАРУСЕЛЬ 3D (Объёмные круглые шайбы-монетки со скриншота Карусель)
    // =========================================================================
    private fun drawCarousel3DThemeIcon(canvas: Canvas, cx: Float, cy: Float, minDim: Float) {
        val discR = minDim * 0.41f
        val topCy = cy - minDim * 0.04f
        val r = discR * 0.65f

        val (topHex, edgeHex) = when (iconType) {
            "PLAYLISTS" -> Pair("#EF4444", "#991B1B") // Красная шайба YouTube
            "EDITOR" -> Pair("#65A30D", "#3F6212") // Оливково-зелёная шайба Калькулятор
            "ANALYZER" -> Pair("#DC2626", "#7F1D1D") // Красная шайба Камера
            "THEMES" -> Pair("#9333EA", "#D97706") // Градиентная шайба Instagram
            "BUFFER" -> Pair("#0284C7", "#075985") // Голубая шайба Облако
            "DECODER" -> Pair("#16A34A", "#14532D") // Мульти-шайба Chrome
            "EPG_GRID" -> Pair("#B91C1C", "#7F1D1D") // Красная шайба Почта/@
            "TOP_CHANNELS" -> Pair("#2563EB", "#1E3A8A") // Синяя шайба Молния Messenger
            "FAVORITES" -> Pair("#EA580C", "#9A3412") // Оранжевая шайба Галерея с горами
            "AUDIO" -> Pair("#0EA5E9", "#0369A1") // Голубая шайба с чёрной нотой
            "CONTROL_MODE" -> Pair("#0284C7", "#075985") // Голубая шайба Телефон
            else -> Pair("#0284C7", "#075985") // Синяя шайба Античный храм
        }

        // 1) Нижняя мягкая тень
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#66000000")
        canvas.drawCircle(cx, topCy + minDim * 0.12f, discR, paint)
        // 2) Нижнее цветное 3D-дно шайбы
        paint.color = Color.parseColor(edgeHex)
        canvas.drawCircle(cx, topCy + minDim * 0.088f, discR, paint)
        // 3) Белая прослойка сэндвича
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawCircle(cx, topCy + minDim * 0.044f, discR, paint)
        // 4) Верхняя цветная крышка шайбы
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

        // 5) Уникальный рисунок внутри каждой 3D-шайбы со скриншота «карусель»
        when (iconType) {
            "PLAYLISTS" -> {
                // Белый прямоугольник YouTube с красным треугольником внутри
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                rect.set(cx - r * 0.62f, topCy - r * 0.42f, cx + r * 0.62f, topCy + r * 0.42f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                paint.color = Color.parseColor("#EF4444")
                path.reset()
                path.moveTo(cx - r * 0.16f, topCy - r * 0.22f)
                path.lineTo(cx + r * 0.24f, topCy)
                path.lineTo(cx - r * 0.16f, topCy + r * 0.22f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "EDITOR" -> {
                // Жёлтые знаки - × + = на зелёной шайбе (верхний ряд по центру на скрине Карусель)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                paint.color = Color.parseColor("#FDE047")
                canvas.drawLine(cx - r * 0.48f, topCy - r * 0.28f, cx - r * 0.16f, topCy - r * 0.28f, paint)
                canvas.drawLine(cx + r * 0.18f, topCy - r * 0.40f, cx + r * 0.44f, topCy - r * 0.14f, paint)
                canvas.drawLine(cx + r * 0.44f, topCy - r * 0.40f, cx + r * 0.18f, topCy - r * 0.14f, paint)
                canvas.drawLine(cx - r * 0.48f, topCy + r * 0.28f, cx - r * 0.16f, topCy + r * 0.28f, paint)
                canvas.drawLine(cx - r * 0.32f, topCy + r * 0.12f, cx - r * 0.32f, topCy + r * 0.44f, paint)
                canvas.drawLine(cx + r * 0.16f, topCy + r * 0.20f, cx + r * 0.48f, topCy + r * 0.20f, paint)
                canvas.drawLine(cx + r * 0.16f, topCy + r * 0.36f, cx + r * 0.48f, topCy + r * 0.36f, paint)
            }
            "ANALYZER" -> {
                // Белое кольцо объектива камеры с красным зрачком (верхний ряд по центру на скрине Карусель)
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                canvas.drawCircle(cx, topCy, r * 0.56f, paint)
                paint.color = Color.parseColor("#991B1B")
                canvas.drawCircle(cx, topCy, r * 0.36f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx - r * 0.10f, topCy - r * 0.10f, r * 0.10f, paint)
            }
            "THEMES" -> {
                // Белый контур Instagram на градиентной шайбе (средний ряд справа на скрине Карусель)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.8f; paint.color = Color.WHITE
                rect.set(cx - r * 0.56f, topCy - r * 0.56f, cx + r * 0.56f, topCy + r * 0.56f)
                canvas.drawRoundRect(rect, 16f, 16f, paint)
                canvas.drawCircle(cx, topCy, r * 0.26f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx + r * 0.34f, topCy - r * 0.34f, r * 0.07f, paint)
            }
            "BUFFER" -> {
                // Белый контур облака с жёлтой стрелкой вниз (верхний ряд по центру на скрине Карусель)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.8f; paint.color = Color.WHITE
                rect.set(cx - r * 0.62f, topCy - r * 0.32f, cx + r * 0.62f, topCy + r * 0.32f)
                canvas.drawRoundRect(rect, 22f, 22f, paint)
                paint.color = Color.parseColor("#FACC15")
                canvas.drawLine(cx, topCy - r * 0.15f, cx, topCy + r * 0.35f, paint)
                canvas.drawLine(cx - r * 0.18f, topCy + r * 0.18f, cx, topCy + r * 0.38f, paint)
                canvas.drawLine(cx + r * 0.18f, topCy + r * 0.18f, cx, topCy + r * 0.38f, paint)
            }
            "DECODER" -> {
                // Шайба Chrome с цветными секторами и синим ядром (верхний левый блок на скрине Карусель)
                paint.style = Paint.Style.FILL
                rect.set(cx - discR, topCy - discR, cx + discR, topCy + discR)
                paint.color = Color.parseColor("#DC2626"); canvas.drawArc(rect, -150f, 120f, true, paint)
                paint.color = Color.parseColor("#FACC15"); canvas.drawArc(rect, -30f, 120f, true, paint)
                paint.color = Color.parseColor("#16A34A"); canvas.drawArc(rect, 90f, 120f, true, paint)
                paint.color = Color.WHITE; canvas.drawCircle(cx, topCy, r * 0.56f, paint)
                paint.color = Color.parseColor("#2563EB"); canvas.drawCircle(cx, topCy, r * 0.42f, paint)
            }
            "EPG_GRID" -> {
                // Открытый конверт с белым листом и знаком @ (средний ряд по центру на скрине Карусель)
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                rect.set(cx - r * 0.48f, topCy - r * 0.48f, cx + r * 0.48f, topCy + r * 0.25f)
                canvas.drawRect(rect, paint)
                paint.color = Color.parseColor("#1E293B")
                paint.textSize = r * 0.55f; paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText("@", cx, topCy - r * 0.04f, paint)
            }
            "TOP_CHANNELS" -> {
                // Белая молния Messenger внутри синей шайбы (верхний правый блок на скрине Карусель)
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                path.reset()
                path.moveTo(cx - r * 0.58f, topCy + r * 0.18f)
                path.lineTo(cx - r * 0.06f, topCy - r * 0.28f)
                path.lineTo(cx + r * 0.12f, topCy + r * 0.02f)
                path.lineTo(cx + r * 0.58f, topCy - r * 0.22f)
                path.lineTo(cx + r * 0.06f, topCy + r * 0.26f)
                path.lineTo(cx - r * 0.12f, topCy - r * 0.02f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "FAVORITES" -> {
                // Оранжевая шайба с тёмными силуэтами гор и солнцем (нижний ряд по центру на скрине Карусель)
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#431407")
                canvas.drawCircle(cx - r * 0.28f, topCy - r * 0.28f, r * 0.14f, paint)
                path.reset()
                path.moveTo(cx - r * 0.65f, topCy + r * 0.36f)
                path.lineTo(cx - r * 0.15f, topCy - r * 0.15f)
                path.lineTo(cx + r * 0.22f, topCy + r * 0.22f)
                path.lineTo(cx + r * 0.42f, topCy)
                path.lineTo(cx + r * 0.68f, topCy + r * 0.36f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "AUDIO" -> {
                // Двойная чёрная музыкальная нота на голубой шайбе (нижний ряд по центру на скрине Карусель)
                paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#0F172A")
                canvas.drawCircle(cx - r * 0.28f, topCy + r * 0.32f, r * 0.18f, paint)
                canvas.drawCircle(cx + r * 0.22f, topCy + r * 0.24f, r * 0.18f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.5f
                canvas.drawLine(cx - r * 0.12f, topCy + r * 0.32f, cx - r * 0.12f, topCy - r * 0.36f, paint)
                canvas.drawLine(cx + r * 0.38f, topCy + r * 0.24f, cx + r * 0.38f, topCy - r * 0.44f, paint)
                canvas.drawLine(cx - r * 0.12f, topCy - r * 0.36f, cx + r * 0.38f, topCy - r * 0.44f, paint)
            }
            "CONTROL_MODE" -> {
                // Белая телефонная трубка на голубой шайбе (нижний ряд по центру на скрине Карусель)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 9f; paint.color = Color.WHITE
                rect.set(cx - r * 0.48f, topCy - r * 0.48f, cx + r * 0.48f, topCy + r * 0.48f)
                canvas.drawArc(rect, 35f, 115f, false, paint)
            }
            else -> {
                // Белый античный храм с колоннами и крышей (левый верхний угол на скрине Карусель)
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
                path.reset()
                path.moveTo(cx, topCy - r * 0.56f)
                path.lineTo(cx - r * 0.58f, topCy - r * 0.18f)
                path.lineTo(cx + r * 0.58f, topCy - r * 0.18f)
                path.close()
                canvas.drawPath(path, paint)
                rect.set(cx - r * 0.56f, topCy + r * 0.36f, cx + r * 0.56f, topCy + r * 0.50f)
                canvas.drawRect(rect, paint)
                for (k in -1..1) {
                    val x = cx + k * r * 0.32f
                    rect.set(x - r * 0.07f, topCy - r * 0.16f, x + r * 0.07f, topCy + r * 0.36f)
                    canvas.drawRect(rect, paint)
                }
            }
        }
    }
}
