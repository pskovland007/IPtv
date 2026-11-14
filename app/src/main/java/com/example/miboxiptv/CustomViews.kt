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

// Пункт 1: Строка канала теперь имеет точно такую же высоту и вертикальные отступы, как строка группы
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
    val eqView = LiveEqualizerView(context).apply {
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

// Пункт 5: Иконки главного меню в точном соответствии с 6 темами со скриншотов
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
        val cx = w / 2f; val cy = h / 2f
        val pad = minDim * 0.08f
        val r = (minDim - pad * 2) * 0.38f

        paint.shader = null
        paint.style = Paint.Style.FILL

        // 1. ОТРИСОВКА ПОДЛОЖКИ / ФОНА ПЛИТКИ ПО СТИЛЮ ТЕМЫ:
        // ID 0: Материал Дизайн (плоские яркие фигуры без квадратной подложки, мягкое кольцо при фокусе)
        // ID 1: Неон (без подложки, радужный неоновый градиент на чёрном)
        // ID 2: Амолед Блэк (без подложки, тонкие белые линии на чёрном)
        // ID 3: Монохром (светло-серый строгий квадрат с тонкой рамкой)
        // ID 4: Ретро (винтажные матовые скруглённые карточки с тёплой тенью)
        // ID 5: Карусель (3D круглая фишка-монетка с белой прослойкой и цветным дном)

        when (themeStyleId) {
            0 -> {
                // Материал Дизайн: мягкий круглый ореол только при наведении фокуса
                if (isTileFocused) {
                    paint.color = Color.parseColor("#222563EB")
                    canvas.drawCircle(cx, cy, minDim * 0.46f, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    paint.color = Color.parseColor("#2563EB")
                    canvas.drawCircle(cx, cy, minDim * 0.46f, paint)
                }
            }
            1 -> {
                // Неон: без квадратных коробок, только неоновый ореол вокруг активной иконки
                if (isTileFocused) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.5f
                    paint.shader = LinearGradient(
                        0f, 0f, w, h,
                        intArrayOf(Color.parseColor("#FF7597"), Color.parseColor("#FFE082"), Color.parseColor("#64FFDA")),
                        null, Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(cx, cy, minDim * 0.46f, paint)
                    paint.shader = null
                }
            }
            2 -> {
                // Амолед Блэк: чисто чёрный фон без коробок, тонкое белое кольцо только при фокусе
                if (isTileFocused) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = Color.WHITE
                    canvas.drawCircle(cx, cy, minDim * 0.45f, paint)
                }
            }
            3 -> {
                // Монохром: светло-серая квадратная плитка как на скриншоте
                rect.set(pad, pad, w - pad, h - pad)
                paint.style = Paint.Style.FILL
                paint.color = if (isTileFocused) Color.parseColor("#E4E4E7") else Color.parseColor("#F4F4F5")
                canvas.drawRect(rect, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (isTileFocused) 3.5f else 1.5f
                paint.color = if (isTileFocused) Color.parseColor("#27272A") else Color.parseColor("#D4D4D8")
                canvas.drawRect(rect, paint)
            }
            4 -> {
                // Ретро: матовые винтажные квадраты с мягкими углами и глубокой тенью снизу
                val retroTileColor = when (iconType) {
                    "PLAYLISTS", "EPG_GRID", "AUDIO" -> Color.parseColor("#277A68") // Винтажный изумрудно-зелёный
                    "EDITOR", "BUFFER", "FAVORITES" -> Color.parseColor("#9C513B") // Терракотово-коричневый
                    "THEMES", "TOP_CHANNELS", "MORE" -> Color.parseColor("#D4892A") // Тёплый горчично-рыжий
                    else -> Color.parseColor("#4D4948") // Тёплый винтажный графит
                }
                val corner = minDim * 0.16f
                // Тень снизу
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#88090706")
                rect.set(pad + 2f, pad + 7f, w - pad + 2f, h - pad + 7f)
                canvas.drawRoundRect(rect, corner, corner, paint)
                // Сама карточка
                paint.color = retroTileColor
                rect.set(pad, pad, w - pad, h - pad)
                canvas.drawRoundRect(rect, corner, corner, paint)
                if (isTileFocused) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 4f
                    paint.color = Color.parseColor("#F5E6CC")
                    canvas.drawRoundRect(rect, corner, corner, paint)
                }
            }
            else -> {
                // ID 5: Карусель — круглая 3D-фишка (шайба с цветным ребром и белой прослойкой как на скриншоте)
                val discColor = Color.parseColor(baseColorHex)
                val darkEdgeColor = Color.rgb(
                    (Color.red(discColor) * 0.68f).toInt(),
                    (Color.green(discColor) * 0.68f).toInt(),
                    (Color.blue(discColor) * 0.68f).toInt()
                )
                val discR = minDim * 0.41f
                val topCy = cy - minDim * 0.04f
                // 1) Нижняя тень
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#66000000")
                canvas.drawCircle(cx, topCy + minDim * 0.12f, discR, paint)
                // 2) Нижнее 3D-ребро в цвет шайбы
                paint.color = darkEdgeColor
                canvas.drawCircle(cx, topCy + minDim * 0.085f, discR, paint)
                // 3) Белая фирменная прослойка
                paint.color = Color.parseColor("#F1F5F9")
                canvas.drawCircle(cx, topCy + minDim * 0.042f, discR, paint)
                // 4) Верхняя цветная крышка шайбы
                paint.color = discColor
                canvas.drawCircle(cx, topCy, discR, paint)
                if (isTileFocused) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3.5f
                    paint.color = Color.WHITE
                    canvas.drawCircle(cx, topCy, discR, paint)
                }
            }
        }

        // 2. НАСТРОЙКА КИСТИ ДЛЯ САМОГО СИМВОЛА ИКОНКИ:
        val symbolCy = if (themeStyleId == 5) cy - minDim * 0.04f else cy
        val iconR = when (themeStyleId) {
            0 -> r * 1.15f // В Материал Дизайне сама фигура крупная
            5 -> r * 0.78f // Внутри 3D-шайбы символ аккуратный по центру
            else -> r
        }

        // Вспомогательная настройка стиля для контурных/залитых тем
        fun setupSymbolPaint(isSecondaryAccent: Boolean = false) {
            paint.shader = null
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            when (themeStyleId) {
                0 -> {
                    // Материал Дизайн: сочные плоские заливки
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor(if (isSecondaryAccent) accentColorHex else baseColorHex)
                }
                1 -> {
                    // Неон: градиентный неоновый контур (розовый -> жёлтый -> бирюзовый)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 4.2f
                    val c1 = if (isSecondaryAccent) Color.parseColor("#64FFDA") else Color.parseColor("#FF7597")
                    val c2 = Color.parseColor("#FFE082")
                    val c3 = if (isSecondaryAccent) Color.parseColor("#FF80AB") else Color.parseColor("#00E5FF")
                    paint.shader = LinearGradient(cx - iconR, symbolCy - iconR, cx + iconR, symbolCy + iconR, intArrayOf(c1, c2, c3), null, Shader.TileMode.CLAMP)
                }
                2 -> {
                    // Амолед Блэк: тонкие чисто белые контуры
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.4f
                    paint.color = Color.WHITE
                }
                3 -> {
                    // Монохром: тёмно-серый строгий силуэт (#52525B)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor(if (isSecondaryAccent) "#3F3F46" else "#64646E")
                }
                4 -> {
                    // Ретро: тёплый кремово-бежевый символ (#F5E6CC) и горчичный акцент (#F4A261)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor(if (isSecondaryAccent) "#F4A261" else "#F5E6CC")
                }
                else -> {
                    // Карусель 3D: белые символы на ярких круглых фишках
                    paint.style = Paint.Style.FILL
                    paint.color = if (isSecondaryAccent) Color.parseColor("#FFE082") else Color.WHITE
                }
            }
        }

        val isOutlineOnly = (themeStyleId == 1 || themeStyleId == 2)

        // В Материал Дизайне (ID 0) рисуем фирменную плоскую цветную подложку-значок для каждого типа иконки
        if (themeStyleId == 0) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor(baseColorHex)
            when (iconType) {
                "PLAYLISTS", "BUFFER", "AUDIO", "CONTROL_MODE" -> canvas.drawCircle(cx, symbolCy, iconR * 1.05f, paint)
                else -> {
                    rect.set(cx - iconR * 1.02f, symbolCy - iconR * 0.88f, cx + iconR * 1.02f, symbolCy + iconR * 0.88f)
                    canvas.drawRoundRect(rect, 18f, 18f, paint)
                }
            }
            // Полупрозрачный диагональный блик Material Design
            paint.color = Color.argb(38, 255, 255, 255)
            rect.set(cx - iconR * 1.02f, symbolCy - iconR * 0.88f, cx + iconR * 1.02f, symbolCy)
            canvas.drawRoundRect(rect, 18f, 18f, paint)
        }

        // 3. ОТРИСОВКА КОНКРЕТНОЙ ИКОНКИ:
        when (iconType) {
            "PLAYLISTS" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                for (i in -1..1) {
                    val ly = symbolCy + i * (iconR * 0.48f)
                    if (isOutlineOnly) {
                        canvas.drawCircle(cx - iconR * 0.56f, ly, iconR * 0.10f, paint)
                        canvas.drawLine(cx - iconR * 0.25f, ly, cx + iconR * 0.65f, ly, paint)
                    } else {
                        canvas.drawCircle(cx - iconR * 0.54f, ly, iconR * 0.13f, paint)
                        rect.set(cx - iconR * 0.24f, ly - iconR * 0.10f, cx + iconR * 0.66f, ly + iconR * 0.10f)
                        canvas.drawRoundRect(rect, 6f, 6f, paint)
                    }
                }
            }
            "EDITOR" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 4f
                canvas.drawLine(cx - iconR * 0.60f, symbolCy - iconR * 0.40f, cx + iconR * 0.15f, symbolCy - iconR * 0.40f, paint)
                canvas.drawLine(cx - iconR * 0.60f, symbolCy, cx - iconR * 0.05f, symbolCy, paint)
                canvas.drawLine(cx - iconR * 0.60f, symbolCy + iconR * 0.40f, cx - iconR * 0.20f, symbolCy + iconR * 0.40f, paint)
                setupSymbolPaint(true)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.6f else 4.8f
                canvas.drawLine(cx + iconR * 0.05f, symbolCy + iconR * 0.45f, cx + iconR * 0.60f, symbolCy - iconR * 0.35f, paint)
            }
            "ANALYZER" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 3.8f
                for (ring in 1..3) {
                    val rad = iconR * (0.28f * ring)
                    rect.set(cx - rad, symbolCy - rad + iconR * 0.22f, cx + rad, symbolCy + rad + iconR * 0.22f)
                    canvas.drawArc(rect, 210f, 120f, false, paint)
                }
                setupSymbolPaint(true)
                if (!isOutlineOnly) paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, symbolCy + iconR * 0.34f, iconR * 0.14f, paint)
            }
            "THEMES" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 3.8f
                canvas.drawCircle(cx, symbolCy, iconR * 0.72f, paint)
                setupSymbolPaint(true)
                if (!isOutlineOnly) paint.style = Paint.Style.FILL
                canvas.drawCircle(cx - iconR * 0.26f, symbolCy - iconR * 0.18f, iconR * 0.15f, paint)
                canvas.drawCircle(cx + iconR * 0.24f, symbolCy - iconR * 0.22f, iconR * 0.15f, paint)
                canvas.drawCircle(cx + iconR * 0.22f, symbolCy + iconR * 0.24f, iconR * 0.15f, paint)
            }
            "BUFFER" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 3.6f
                rect.set(cx - iconR * 0.72f, symbolCy - iconR * 0.72f, cx + iconR * 0.72f, symbolCy + iconR * 0.72f)
                canvas.drawArc(rect, 145f, 250f, false, paint)
                setupSymbolPaint(true)
                if (!isOutlineOnly) paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx + iconR * 0.08f, symbolCy - iconR * 0.50f)
                path.lineTo(cx - iconR * 0.30f, symbolCy + iconR * 0.04f)
                path.lineTo(cx + iconR * 0.02f, symbolCy + iconR * 0.04f)
                path.lineTo(cx - iconR * 0.10f, symbolCy + iconR * 0.50f)
                path.lineTo(cx + iconR * 0.32f, symbolCy - iconR * 0.05f)
                path.lineTo(cx + iconR * 0.02f, symbolCy - iconR * 0.05f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "DECODER" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 3.4f
                rect.set(cx - iconR * 0.52f, symbolCy - iconR * 0.52f, cx + iconR * 0.52f, symbolCy + iconR * 0.52f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                // Ножки микросхемы (как CPU-Z на скриншоте AMOLED)
                for (k in -1..1) {
                    val off = k * iconR * 0.28f
                    canvas.drawLine(cx + off, symbolCy - iconR * 0.74f, cx + off, symbolCy - iconR * 0.52f, paint)
                    canvas.drawLine(cx + off, symbolCy + iconR * 0.52f, cx + off, symbolCy + iconR * 0.74f, paint)
                    canvas.drawLine(cx - iconR * 0.74f, symbolCy + off, cx - iconR * 0.52f, symbolCy + off, paint)
                    canvas.drawLine(cx + iconR * 0.52f, symbolCy + off, cx + iconR * 0.74f, symbolCy + off, paint)
                }
                setupSymbolPaint(true)
                if (!isOutlineOnly) paint.style = Paint.Style.FILL
                rect.set(cx - iconR * 0.22f, symbolCy - iconR * 0.22f, cx + iconR * 0.22f, symbolCy + iconR * 0.22f)
                canvas.drawRoundRect(rect, 4f, 4f, paint)
            }
            "EPG_GRID" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 3.4f
                rect.set(cx - iconR * 0.66f, symbolCy - iconR * 0.58f, cx + iconR * 0.66f, symbolCy + iconR * 0.62f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawLine(cx - iconR * 0.66f, symbolCy - iconR * 0.16f, cx + iconR * 0.66f, symbolCy - iconR * 0.16f, paint)
                setupSymbolPaint(true)
                if (!isOutlineOnly) paint.style = Paint.Style.FILL
                canvas.drawCircle(cx - iconR * 0.28f, symbolCy + iconR * 0.18f, iconR * 0.11f, paint)
                canvas.drawCircle(cx, symbolCy + iconR * 0.18f, iconR * 0.11f, paint)
                canvas.drawCircle(cx + iconR * 0.28f, symbolCy + iconR * 0.18f, iconR * 0.11f, paint)
            }
            "TOP_CHANNELS" -> {
                setupSymbolPaint(true)
                if (themeStyleId == 0) paint.color = Color.parseColor("#FFD54F")
                if (!isOutlineOnly) paint.style = Paint.Style.FILL else paint.style = Paint.Style.STROKE
                path.reset()
                path.moveTo(cx - iconR * 0.62f, symbolCy + iconR * 0.44f)
                path.lineTo(cx - iconR * 0.70f, symbolCy - iconR * 0.32f)
                path.lineTo(cx - iconR * 0.24f, symbolCy + iconR * 0.06f)
                path.lineTo(cx, symbolCy - iconR * 0.52f)
                path.lineTo(cx + iconR * 0.24f, symbolCy + iconR * 0.06f)
                path.lineTo(cx + iconR * 0.70f, symbolCy - iconR * 0.32f)
                path.lineTo(cx + iconR * 0.62f, symbolCy + iconR * 0.44f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "FAVORITES" -> {
                setupSymbolPaint(true)
                if (themeStyleId == 0) paint.color = Color.WHITE
                if (!isOutlineOnly) paint.style = Paint.Style.FILL else paint.style = Paint.Style.STROKE
                path.reset()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val rad = if (i % 2 == 0) iconR * 0.74f else iconR * 0.32f
                    val sx = cx + (rad * cos(a)).toFloat(); val sy = symbolCy + (rad * sin(a)).toFloat()
                    if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "AUDIO" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                val pos = listOf(-0.48f, -0.16f, 0.16f, 0.48f)
                val hts = listOf(0.38f, 0.72f, 0.52f, 0.30f)
                for (i in pos.indices) {
                    if (i == 1) setupSymbolPaint(true).also { if (themeStyleId == 0) paint.color = Color.WHITE }
                    else setupSymbolPaint(false).also { if (themeStyleId == 0) paint.color = Color.WHITE }
                    if (isOutlineOnly) {
                        paint.style = Paint.Style.STROKE
                        canvas.drawLine(cx + pos[i] * iconR, symbolCy - hts[i] * iconR, cx + pos[i] * iconR, symbolCy + hts[i] * iconR, paint)
                    } else {
                        paint.style = Paint.Style.FILL
                        rect.set(cx + pos[i] * iconR - 4.5f, symbolCy - hts[i] * iconR, cx + pos[i] * iconR + 4.5f, symbolCy + hts[i] * iconR)
                        canvas.drawRoundRect(rect, 4f, 4f, paint)
                    }
                }
            }
            "CONTROL_MODE" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 3.4f
                rect.set(cx - iconR * 0.44f, symbolCy - iconR * 0.72f, cx + iconR * 0.44f, symbolCy + iconR * 0.72f)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                setupSymbolPaint(true)
                if (!isOutlineOnly) paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, symbolCy - iconR * 0.20f, iconR * 0.16f, paint)
            }
            "SETTINGS_GEAR" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 3.6f
                canvas.drawCircle(cx, symbolCy, iconR * 0.52f, paint)
                for (deg in 0 until 360 step 45) {
                    val rad = Math.toRadians(deg.toDouble())
                    canvas.drawLine(
                        cx + (iconR * 0.52f * cos(rad)).toFloat(), symbolCy + (iconR * 0.52f * sin(rad)).toFloat(),
                        cx + (iconR * 0.74f * cos(rad)).toFloat(), symbolCy + (iconR * 0.74f * sin(rad)).toFloat(),
                        paint
                    )
                }
            }
            "SWAP_GROUPS" -> {
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 3.6f
                canvas.drawLine(cx - iconR * 0.56f, symbolCy - iconR * 0.24f, cx + iconR * 0.56f, symbolCy - iconR * 0.24f, paint)
                canvas.drawLine(cx + iconR * 0.22f, symbolCy - iconR * 0.50f, cx + iconR * 0.56f, symbolCy - iconR * 0.24f, paint)
                setupSymbolPaint(true)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 3.6f
                canvas.drawLine(cx - iconR * 0.56f, symbolCy + iconR * 0.24f, cx + iconR * 0.56f, symbolCy + iconR * 0.24f, paint)
                canvas.drawLine(cx - iconR * 0.22f, symbolCy + iconR * 0.50f, cx - iconR * 0.56f, symbolCy + iconR * 0.24f, paint)
            }
            else -> {
                // Иконка «Вид» (Монитор / Экран с глазом)
                setupSymbolPaint(false)
                if (themeStyleId == 0) paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (themeStyleId == 2) 2.4f else 3.4f
                rect.set(cx - iconR * 0.70f, symbolCy - iconR * 0.52f, cx + iconR * 0.70f, symbolCy + iconR * 0.38f)
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                canvas.drawLine(cx - iconR * 0.34f, symbolCy + iconR * 0.62f, cx + iconR * 0.34f, symbolCy + iconR * 0.62f, paint)
                setupSymbolPaint(true)
                if (!isOutlineOnly) paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, symbolCy - iconR * 0.07f, iconR * 0.20f, paint)
            }
        }
        paint.shader = null
    }
}
