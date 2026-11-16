package com.example.miboxiptv

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.MediaItem

import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    internal lateinit var rootFrame: FrameLayout
    internal lateinit var playerContainerFrame: FrameLayout
    internal lateinit var playerView: PlayerView
    internal var player: SimpleExoPlayer? = null
    internal var dualPipPlayer: SimpleExoPlayer? = null
    internal var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null
    internal var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    internal lateinit var sidePanel: LinearLayout
    internal lateinit var singleColumnFrame: FrameLayout
    internal lateinit var categoryListView: ListView
    internal lateinit var channelListView: ListView
    internal lateinit var leftIconRail: LinearLayout
    
    internal lateinit var railTilePlaylists: ThemedTileView
    internal lateinit var railTileSettings: ThemedTileView
    internal lateinit var railTileAnalyzer: ThemedTileView
    internal lateinit var railTileSwapGroups: ThemedTileView
    
    internal lateinit var infoPanel: LinearLayout
    internal lateinit var infoTextView: TextView
    
    internal lateinit var centerOsdPanel: LinearLayout
    internal lateinit var osdChannelTitle: TextView
    internal lateinit var osdEpgCurrentTitle: TextView
    internal lateinit var osdEpgTimeLabel: TextView
    internal lateinit var osdEpgProgress: ProgressBar
    internal lateinit var osdEpgNextAndDesc: TextView
    internal lateinit var zappingRowContainer: LinearLayout
    internal lateinit var btnOsdTop: TextView
    internal lateinit var btnOsdFav: TextView
    internal lateinit var btnOsdDualToggle: TextView
    internal lateinit var btnOsdRewind: TextView
    internal lateinit var btnOsdEpgList: TextView
    internal lateinit var btnOsdSettings: TextView
    internal lateinit var btnOsdNightMode: TextView
    internal lateinit var btnOsdChannelActions: TextView
    internal lateinit var btnOsdPowerRedCircle: View
    
    internal lateinit var clockTextView: TextView
    internal lateinit var analyzerStatusBanner: TextView
    internal lateinit var currentChannelBanner: TextView
    internal lateinit var signalContainer: LinearLayout
    internal lateinit var reloadIconIndicator: TextView
    internal val signalBars = mutableListOf<View>()
    
    internal lateinit var fullEpgOverlay: LinearLayout
    internal lateinit var fullEpgTitle: TextView
    internal lateinit var fullEpgListView: ListView
    
    internal lateinit var dualStreamContainer: FrameLayout
    internal lateinit var dualPlayerView: PlayerView
    internal lateinit var dualStreamLabel: TextView
    internal lateinit var dualCloseBtn: TextView
    
    internal lateinit var settingsTileOverlay: LinearLayout
    internal lateinit var gridContainerLayout: LinearLayout
    internal lateinit var carouselScrollView: HorizontalScrollView
    internal lateinit var carouselRowLayout: LinearLayout
    internal val carouselCells = mutableListOf<LinearLayout>()
    
    internal lateinit var subModalOverlay: LinearLayout
    internal lateinit var subModalTitle: TextView
    internal lateinit var subModalList: ListView
    internal lateinit var subModalTopButtons: LinearLayout
    internal lateinit var subModalInputContainer: LinearLayout
    
    internal lateinit var firstRunModeOverlay: FrameLayout
    internal lateinit var splashOverlay: FrameLayout
    internal lateinit var splashEyeView: RealisticEyeSplashView
    internal lateinit var nightDimOverlay: View
    internal lateinit var crtTurnOffView: CrtTurnOffView
    internal lateinit var gestureDetector: GestureDetector

    internal lateinit var categoryAdapter: ArrayAdapter<String>
    internal lateinit var channelListAdapter: ArrayAdapter<String>
    internal lateinit var subModalAdapter: ArrayAdapter<String>

    internal val categoryDisplayItems = mutableListOf<String>()
    internal val channelDisplayItems = mutableListOf<String>()
    internal val subModalItems = mutableListOf<String>()
    
    internal val allChannels = mutableListOf<Channel>()
    internal val displayedChannels = mutableListOf<Channel>()
    internal val playlists = mutableListOf<PlaylistEntry>()
    internal val dynamicCategories = mutableListOf<String>()
    internal val customCategoryMap = HashMap<String, String>()
    
    internal var currentChannel: Channel? = null
    internal var previousChannel: Channel? = null
    internal var pendingDebouncedChannel: Channel? = null
    internal var dualStreamChannel: Channel? = null
    
    internal val favoriteUrls = HashSet<String>()
    internal val topUrls = HashSet<String>()
    internal val manualDisabledUrls = HashSet<String>()
    internal val autoDisabledUrls = HashSet<String>()
    internal val disabledCategories = HashSet<String>()
    
    internal var epgUrl: String = "http://epg.it999.ru/edem.xml.gz"
    internal val epgUrls = listOf("http://epg.it999.ru/edem.xml.gz", "https://iptvx.one/epg/epg.xml.gz")
    internal val epgById = ConcurrentHashMap<String, MutableList<EpgProgram>>()
    internal val epgByName = ConcurrentHashMap<String, MutableList<EpgProgram>>()
    
    internal var activePlaylistIndex = 0
    internal var currentCategoryIndex = 1
    internal var focusedCategoryPos = 1
    internal var focusedChannelPos = 0
    internal var subModalHighlightedPos = 0
    
    internal var lastSavedChannelUrl = ""
    internal var silentFallbackAttemptedForUrl = ""
    
    internal var controlMode = 0
    internal var uiScaleStep = 1
    internal var uiAlphaStep = 1
    internal var cameraCutoutSafeOffset = false
    internal var screenAspectMode = 0
    internal var brightnessPercent = 100
    internal var bufferLevel = 1
    internal var decoderMode = 1
    internal var ambilightMode = 2
    internal var afrEnabled = false
    internal var pixelOrbiterEnabled = true
    internal var drcNightAudioEnabled = false
    internal var audioBoostPercent = 100
    internal var dohEnabled = true
    internal var onDropBehavior = 0
    internal var menuHideDelaySec = 8
    internal var showChannelBannerOnSwitch = true
    internal var antiBlockEnabled = true
    internal var userAgentIndex = 0
    internal var themeIndex = 0

    internal val themes = listOf(
        AppThemeStyle(0, "Neon Cyan", Color.parseColor("#0B0F19"), Color.parseColor("#131B2E"), Color.parseColor("#00E5FF"), Color.parseColor("#FFFFFF"), Color.parseColor("#94A3B8"), true),
        AppThemeStyle(1, "Deep Purple", Color.parseColor("#120A22"), Color.parseColor("#1E1138"), Color.parseColor("#E040FB"), Color.parseColor("#FFFFFF"), Color.parseColor("#B0BEC5"), true),
        AppThemeStyle(2, "Emerald Dark", Color.parseColor("#071912"), Color.parseColor("#0E2E21"), Color.parseColor("#00E676"), Color.parseColor("#FFFFFF"), Color.parseColor("#A3E4D7"), true),
        AppThemeStyle(3, "Amber Gold", Color.parseColor("#1A1205"), Color.parseColor("#2E200A"), Color.parseColor("#FFAB00"), Color.parseColor("#FFFFFF"), Color.parseColor("#F5CBA7"), true)
    )

    internal var isLeftRailVisible = false
    internal var isSideShowingChannels = false
    internal var isDualStreamActive = false
    internal var openChannelsDirectlyWithCurrentFocus = false
    internal var animateNextSubModalChange = false
    internal var isWatchdogTickerActive = false
    internal var isAnalyzerRunning = false

    internal var menuHistory = mutableListOf<String>()
    internal var lastMenuChangeMs = 0L
    internal var lastBackTapMs = 0L
    internal var centerLongPressHandled = false
    internal var playlistLoadGeneration = 0
    internal var epgLoadedCount = 0
    
    internal var subModalBaseTitleText = ""
    internal var selectedEditorCategory = ""
    internal var selectedAnalyzerCategory = ""
    internal var currentBrowseDir = File("/storage/emulated/0")
    internal var currentAnimatedAlphaInt = 210

    internal var lowSignalStuckSeconds = 0
    internal var consecutiveDropFailures = 0
    internal var lastRenderedFramePositionMs = -1L
    internal val preloadedNeighbors = HashSet<String>()
    internal val preloadedLock = Any()
    internal val preloaderExecutor = Executors.newFixedThreadPool(2)

    internal val allGridTiles = mutableListOf<ThemedTileView>()
    internal val allGridCaptions = mutableListOf<TextView>()
    
    internal val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    internal var alphaAnimator: ValueAnimator? = null
    internal var ambilightHue = 0f
    
    internal val ruCollator = Collator.getInstance(Locale("ru", "RU")).apply { strength = Collator.PRIMARY }
    internal val openRusSources = listOf(
        "https://raw.githubusercontent.com/blackbird-team/RUTV/main/RUTV.m3u",
        "https://smarttvnews.ru/apps/iptvchannels.m3u"
    )

    internal val singleBackTapRunnable = Runnable {
        lastBackTapMs = 0L
        if (menuHistory.isEmpty()) {
            if (firstRunModeOverlay.visibility != View.VISIBLE) navigateToMenu("SIDE")
        }
    }

    internal val autoHideMenuRunnable = Runnable {
        if (menuHistory.isNotEmpty()) closeAllMenus()
    }

    internal fun resetMenuInactivityTimer() {
        uiHandler.removeCallbacks(autoHideMenuRunnable)
        if (menuHideDelaySec > 0 && menuHistory.isNotEmpty()) {
            uiHandler.postDelayed(autoHideMenuRunnable, menuHideDelaySec * 1000L)
        }
    }

    internal fun applyAudioEnhancements() {
        val p = player ?: return
        try {
            if (drcNightAudioEnabled) {
                p.audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build()
            } else {
                p.audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
            }
            if (audioBoostPercent > 100) {
                val sessionId = p.audioSessionId
                if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                    if (loudnessEnhancer == null) {
                        loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(sessionId).apply { enabled = true }
                    }
                    loudnessEnhancer?.setTargetGain((audioBoostPercent - 100) * 12)
                }
            } else {
                loudnessEnhancer?.enabled = false
            }
        } catch (_: Exception) {}
    }

    internal fun applyBrightnessLevel() {
        val p = brightnessPercent.coerceIn(10, 100)
        if (p == 100) {
            nightDimOverlay.visibility = View.GONE
        } else {
            val alpha = (255 * (100 - p) / 100f).toInt().coerceIn(0, 240)
            nightDimOverlay.setBackgroundColor(Color.argb(alpha, 12, 14, 20))
            nightDimOverlay.visibility = View.VISIBLE
        }
    }

    internal fun stepPixelOrbiter() {
        if (!pixelOrbiterEnabled || playerContainerFrame.visibility != View.VISIBLE) {
            playerContainerFrame.translationX = 0f
            playerContainerFrame.translationY = 0f
            return
        }
        val maxOffset = 6f
        val rx = (Math.random() * maxOffset * 2) - maxOffset
        val ry = (Math.random() * maxOffset * 2) - maxOffset
        playerContainerFrame.animate().translationX(rx.toFloat()).translationY(ry.toFloat()).setDuration(4000L).start()
    }

    internal fun uiScaleFactor(): Float = when (uiScaleStep) { 0 -> 0.85f; 2 -> 1.15f; 3 -> 1.30f; else -> 1.0f }
    internal fun leftSafeMarginPx(): Int = if (cameraCutoutSafeOffset && controlMode == 1) 120 else 0
    internal fun targetPanelAlphaInt(): Int = when (uiAlphaStep) { 0 -> 245; 2 -> 180; 3 -> 140; else -> 210 }
    
    internal fun makeThemedPanelBg(): GradientDrawable = GradientDrawable().apply {
        val t = currentTheme()
        setColor(if (ambilightMode != 0) Color.argb(currentAnimatedAlphaInt, Color.red(t.panelBgColor), Color.green(t.panelBgColor), Color.blue(t.panelBgColor)) else t.panelBgColor)
        cornerRadius = 24f * uiScaleFactor()
        setStroke(2, t.accentColor)
    }

    internal fun makeThemedSubBg(): GradientDrawable = GradientDrawable().apply {
        val t = currentTheme()
        setColor(if (ambilightMode != 0) Color.argb(currentAnimatedAlphaInt, Color.red(t.subPanelBgColor), Color.green(t.subPanelBgColor), Color.blue(t.subPanelBgColor)) else t.subPanelBgColor)
        cornerRadius = 18f * uiScaleFactor()
    }

    internal fun exactModalRowHeightPx(): Int = (54 * uiScaleFactor()).toInt()
    internal fun themeFont(): Typeface = currentTheme().normalFont
    internal fun themeBoldFont(): Typeface = currentTheme().boldFont
    internal fun currentTheme(): AppThemeStyle = themes[themeIndex.coerceIn(0, 3)]
    internal fun activeCategoryList(): List<String> = dynamicCategories.takeIf { it.isNotEmpty() } ?: listOf("★ Избранное", "Все каналы")

    internal fun setPlayerMiniPreviewMode(mini: Boolean) {
        val targetScale = if (mini) 0.82f else 1.0f
        val targetX = if (mini) (if (controlMode == 1) 320f else 280f) else 0f
        playerContainerFrame.animate()
            .scaleX(targetScale).scaleY(targetScale)
            .translationX(targetX)
            .setDuration(280L)
            .setInterpolator(DecelerateInterpolator(1.2f))
            .start()
    }

    internal fun animateMenuAppear(v: View, startDx: Float, startDy: Float) {
        v.alpha = 0f; v.translationX = startDx; v.translationY = startDy; v.visibility = View.VISIBLE
        v.animate().alpha(1f).translationX(0f).translationY(0f).setDuration(240L).setInterpolator(DecelerateInterpolator(1.1f)).start()
    }

    internal fun styleButtonForCurrentTheme(btn: TextView, isFocused: Boolean) {
        val t = currentTheme()
        val scale = uiScaleFactor()
        if (isFocused) {
            btn.setTextColor(t.textOnAccent)
            btn.setBackgroundDrawable(GradientDrawable().apply { setColor(t.accentColor); cornerRadius = 16f * scale; setStroke(2, Color.WHITE) })
            btn.animate().scaleX(1.06f).scaleY(1.06f).setDuration(160L).start()
        } else {
            btn.setTextColor(t.textPrimaryColor)
            btn.setBackgroundDrawable(GradientDrawable().apply { setColor(t.subPanelBgColor); cornerRadius = 14f * scale; setStroke(1, Color.parseColor("#44FFFFFF")) })
            btn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160L).start()
        }
    }

    internal fun applyCurrentThemeToAllPanels() {
        sidePanel.setBackgroundDrawable(makeThemedPanelBg())
        leftIconRail.setBackgroundDrawable(makeThemedSubBg())
        categoryListView.setBackgroundDrawable(makeThemedSubBg())
        channelListView.setBackgroundDrawable(makeThemedSubBg())
        infoPanel.setBackgroundDrawable(makeThemedPanelBg())
        centerOsdPanel.setBackgroundDrawable(makeThemedPanelBg())
        settingsTileOverlay.setBackgroundDrawable(makeThemedPanelBg())
        subModalOverlay.setBackgroundDrawable(makeThemedPanelBg())
        subModalList.setBackgroundDrawable(makeThemedSubBg())
        fullEpgOverlay.setBackgroundDrawable(makeThemedPanelBg())
        fullEpgListView.setBackgroundDrawable(makeThemedSubBg())
        currentChannelBanner.setBackgroundDrawable(makeThemedPanelBg())
        val dm = resources.displayMetrics
        val h = exactModalRowHeightPx()
        val maxH = (dm.heightPixels - 68).coerceAtLeast(300)
        val exactH = ((maxH / h).coerceAtLeast(5)) * h + 16
        categoryListView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, exactH, Gravity.CENTER_VERTICAL)
        channelListView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, exactH, Gravity.CENTER_VERTICAL)
        val pad = (8 * uiScaleFactor()).toInt()
        categoryListView.setPadding(pad, pad, pad, pad)
        channelListView.setPadding(pad, pad, pad, pad)
        for (b in listOf(btnOsdTop, btnOsdFav, btnOsdDualToggle, btnOsdRewind, btnOsdEpgList, btnOsdSettings, btnOsdNightMode, btnOsdChannelActions)) {
            styleButtonForCurrentTheme(b, b.hasFocus())
        }
        for (t in allGridTiles) t.themeStyleId = currentTheme().id
        for (c in allGridCaptions) {
            c.typeface = themeFont()
            c.setTextColor(if (c.parent != null && (c.parent as View).hasFocus()) currentTheme().accentColor else currentTheme().textPrimaryColor)
        }
        subModalTitle.typeface = themeBoldFont()
        fullEpgTitle.typeface = themeBoldFont()
        osdChannelTitle.typeface = themeBoldFont()
        infoTextView.typeface = themeFont()
        (infoPanel.getChildAt(0) as? TextView)?.typeface = themeBoldFont()
        subModalAdapter.notifyDataSetChanged()
        categoryAdapter.notifyDataSetChanged()
        channelListAdapter.notifyDataSetChanged()
    }

    internal fun refreshPanelBackgroundsOnly() {
        val bgPanel = makeThemedPanelBg()
        val bgSub = makeThemedSubBg()
        sidePanel.setBackgroundDrawable(bgPanel)
        leftIconRail.setBackgroundDrawable(bgSub)
        categoryListView.setBackgroundDrawable(bgSub)
        channelListView.setBackgroundDrawable(bgSub)
        infoPanel.setBackgroundDrawable(bgPanel)
        centerOsdPanel.setBackgroundDrawable(bgPanel)
        settingsTileOverlay.setBackgroundDrawable(bgPanel)
        subModalOverlay.setBackgroundDrawable(bgPanel)
        subModalList.setBackgroundDrawable(bgSub)
        fullEpgOverlay.setBackgroundDrawable(bgPanel)
        fullEpgListView.setBackgroundDrawable(bgSub)
    }

    internal fun adjustInfoPanelMarginForDualStream() {
        val lp = infoPanel.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = if (isDualStreamActive) 320 else 68
        infoPanel.layoutParams = lp
    }

    internal fun sampleVideoFrameColorIfPossible() {
        val targetAlphaInt = targetPanelAlphaInt()
        if (currentAnimatedAlphaInt == targetAlphaInt) return
        alphaAnimator?.cancel()
        alphaAnimator = ValueAnimator.ofInt(currentAnimatedAlphaInt, targetAlphaInt).apply {
            duration = 1800L
            addUpdateListener { anim -> currentAnimatedAlphaInt = anim.animatedValue as Int }
            start()
        }
    }

    internal fun colorizeStatusText(raw: String): CharSequence {
        if (!raw.contains("[")) return raw
        val ss = android.text.SpannableString(raw)
        var i = 0
        val palette = intArrayOf(
            Color.parseColor("#E53935"), Color.parseColor("#8E24AA"), Color.parseColor("#3949AB"),
            Color.parseColor("#039BE5"), Color.parseColor("#00897B"), Color.parseColor("#43A047"),
            Color.parseColor("#FB8C00"), Color.parseColor("#D81B60"), Color.parseColor("#5E35B1"),
            Color.parseColor("#00ACC1")
        )
        while (i < raw.length - 2) {
            when {
                i <= raw.length - 6 && raw.startsWith("[B:", i) && raw[i + 5] == ']' -> {
                    val mono = raw.substring(i + 3, i + 5)
                    val cIdx = (raw.hashCode() and 0x7FFFFFFF) % palette.size
                    ss.setSpan(MonogramBadgeSpan(mono, palette[cIdx]), i, i + 6, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i += 6
                }
                i <= raw.length - 4 && raw.startsWith("[ON]", i) -> {
                    ss.setSpan(StatusOrbSpan(3), i, i + 4, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i += 4
                }
                i <= raw.length - 5 && raw.startsWith("[OFF]", i) -> {
                    ss.setSpan(StatusOrbSpan(4), i, i + 5, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i += 5
                }
                raw.startsWith("[+]", i) -> {
                    ss.setSpan(StatusOrbSpan(1), i, i + 3, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i += 3
                }
                raw.startsWith("[-]", i) -> {
                    ss.setSpan(StatusOrbSpan(0), i, i + 3, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i += 3
                }
                raw.startsWith("[?]", i) || raw.startsWith("[>]", i) -> {
                    ss.setSpan(StatusOrbSpan(2), i, i + 3, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i += 3
                }
                else -> i++
            }
        }
        return ss
    }

    internal fun makeVerticalRail3DIconButton(iconType: String, baseHex: String, accentHex: String, onClick: () -> Unit): ThemedTileView {
        val tile = ThemedTileView(this, iconType, baseHex, accentHex).apply {
            themeStyleId = currentTheme().id
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            layoutParams = LinearLayout.LayoutParams(86, 86).apply { setMargins(6, 10, 6, 10) }
            setOnFocusChangeListener { v, hasFocus ->
                isTileFocused = hasFocus
                v.animate().scaleX(if (hasFocus) 1.12f else 1.0f).scaleY(if (hasFocus) 1.12f else 1.0f).setDuration(160L).start()
                if (hasFocus) resetMenuInactivityTimer()
            }
            setOnClickListener { resetMenuInactivityTimer(); onClick() }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    resetMenuInactivityTimer()
                    hideLeftIconRailToGroups()
                    true
                } else false
            }
        }
        allGridTiles.add(tile)
        return tile
    }

    internal fun makeModernButton(label: String, isIconOnly: Boolean = false, onClick: () -> Unit): TextView {
        val scale = uiScaleFactor()
        return TextView(this).apply {
            text = colorizeStatusText(label)
            isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (isIconOnly) 18f else 13.5f) * scale)
            typeface = themeBoldFont()
            styleButtonForCurrentTheme(this, false)
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            setPadding((14 * scale).toInt(), (9 * scale).toInt(), (14 * scale).toInt(), (9 * scale).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(4, 3, 4, 3)
            }
            setOnFocusChangeListener { _, hasFocus ->
                styleButtonForCurrentTheme(this, hasFocus)
                if (hasFocus) resetMenuInactivityTimer()
            }
            setOnClickListener { resetMenuInactivityTimer(); onClick() }
        }
    }

    internal fun enableTrueFullScreenImmersion() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } catch (_: Exception) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableTrueFullScreenImmersion()
    }

    internal fun applyScreenAspectMode() {
        when (screenAspectMode) {
            0 -> { playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT }
            1 -> { playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL; player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT }
            else -> { playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING }
        }
    }

    internal fun updateCarouselEdgeTransforms() {
        val svW = carouselScrollView.width
        if (svW <= 0) return
        val scrollX = carouselScrollView.scrollX
        val leftVisibleEdge = scrollX.toFloat()
        val rightVisibleEdge = (scrollX + svW).toFloat()
        val fadeZone = 175f

        for (cell in carouselCells) {
            val cellCenter = (cell.left + cell.right) / 2f
            if (cell.hasFocus()) {
                cell.alpha = 1.0f
            } else {
                val distFromLeft = cellCenter - leftVisibleEdge
                val distFromRight = rightVisibleEdge - cellCenter
                val minEdgeDist = minOf(distFromLeft, distFromRight)
                val factor = (minEdgeDist / fadeZone).coerceIn(0f, 1f)
                cell.alpha = (0.30f + 0.58f * factor).coerceIn(0.30f, 0.88f)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableTrueFullScreenImmersion()

        CoreEngines.installTrustAllSsl()
        requestStoragePermissionsIfNeeded()
        loadSavedState()
        currentAnimatedAlphaInt = targetPanelAlphaInt()
        initTouchGestureDetector()

        rootFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerContainerFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        }

        playerView = (LayoutInflater.from(this).inflate(R.layout.player_surface_view, playerContainerFrame, false) as PlayerView).apply {
            useController = false
            isFocusable = false
            setKeepContentOnPlayerReset(true)
            setShutterBackgroundColor(Color.TRANSPARENT)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerContainerFrame.addView(playerView)
        rootFrame.addView(playerContainerFrame)

        dualStreamContainer = FrameLayout(this).apply {
            visibility = View.GONE
            isFocusable = false; isFocusableInTouchMode = false; isClickable = false
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.BLACK); cornerRadius = 16f; setStroke(2, Color.parseColor("#00E5FF"))
            })
            setPadding(4, 4, 4, 4)
            layoutParams = FrameLayout.LayoutParams(420, 236, Gravity.TOP or Gravity.END).apply { setMargins(0, 68, 22, 0) }
        }
        dualPlayerView = (LayoutInflater.from(this).inflate(R.layout.player_texture_view, dualStreamContainer, false) as PlayerView).apply {
            useController = false; isFocusable = false; isClickable = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        dualStreamLabel = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.parseColor("#AA000000")); setPadding(8, 3, 8, 3)
            isFocusable = false; isClickable = false
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
        }
        dualCloseBtn = TextView(this).apply {
            text = "✖"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; isFocusable = false; isFocusableInTouchMode = false; isClickable = true
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.parseColor("#CCFF1744")); cornerRadius = 14f })
            setPadding(10, 2, 10, 2)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(4, 4, 4, 4) }
            setOnClickListener { stopDualStreamMode() }
        }
        dualStreamContainer.addView(dualPlayerView)
        dualStreamContainer.addView(dualStreamLabel)
        dualStreamContainer.addView(dualCloseBtn)
        rootFrame.addView(dualStreamContainer)

        clockTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#DCE4EE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = themeBoldFont()
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.parseColor("#780B0914")); cornerRadius = 14f; setStroke(1, Color.parseColor("#22FFFFFF")) })
            setPadding(16, 6, 16, 6)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(leftSafeMarginPx() + 10, 20, 0, 0) }
        }
        rootFrame.addView(clockTextView)

        analyzerStatusBanner = TextView(this).apply {
            setTextColor(Color.parseColor("#00E5FF")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = themeBoldFont()
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.parseColor("#E00B0914")); cornerRadius = 18f; setStroke(2, Color.parseColor("#6500E5FF")) })
            setPadding(22, 8, 22, 8); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { setMargins(0, 18, 0, 0) }
        }
        rootFrame.addView(analyzerStatusBanner)

        signalContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.parseColor("#780B0914")); cornerRadius = 14f; setStroke(1, Color.parseColor("#22FFFFFF")) })
            setPadding(14, 8, 14, 8)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, 20, 26, 0) }
        }
        reloadIconIndicator = TextView(this).apply {
            text = "↻"; setTextColor(Color.parseColor("#00E5FF")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD; visibility = View.GONE; setPadding(0, 0, 8, 0)
        }
        signalContainer.addView(reloadIconIndicator)

        val barsBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        for (h in listOf(10, 15, 20, 26)) {
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(6, h).apply { setMargins(3, 0, 3, 0) }
                setBackgroundDrawable(GradientDrawable().apply { setColor(Color.parseColor("#00E676")); cornerRadius = 3f })
            }
            signalBars.add(bar); barsBox.addView(bar)
        }
        signalContainer.addView(barsBox)
        rootFrame.addView(signalContainer)

        sidePanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundDrawable(makeThemedPanelBg()); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(680, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START).apply { setMargins(leftSafeMarginPx(), 16, 0, 16) }
            setPadding(18, 16, 18, 16)
        }

        allGridTiles.clear(); allGridCaptions.clear()

        leftIconRail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundDrawable(makeThemedSubBg()); visibility = View.GONE; setPadding(10, 16, 10, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).apply { rightMargin = 14 }
        }

        railTilePlaylists = makeVerticalRail3DIconButton("PLAYLISTS", "#1B8A6B", "#FFB300") { navigateToMenu("PLAYLISTS") }.apply { id = 7101 }
        railTileSettings = makeVerticalRail3DIconButton("SETTINGS_GEAR", "#1E4E79", "#00E5FF") { navigateToMenu("SETTINGS") }.apply { id = 7102 }
        railTileAnalyzer = makeVerticalRail3DIconButton("ANALYZER", "#5E35B1", "#00E676") { navigateToMenu("ANALYZER_GROUPS") }.apply { id = 7103 }
        railTileSwapGroups = makeVerticalRail3DIconButton("SWAP_GROUPS", "#D84315", "#FFD54F") {
            if (isSideShowingChannels) showSideGroupsMode() else showSideChannelsMode(focusCurrentChannelIfSameGroup = true)
        }.apply { id = 7104 }

        val railButtons = listOf(railTilePlaylists, railTileSettings, railTileAnalyzer, railTileSwapGroups)
        for (i in railButtons.indices) {
            val cur = railButtons[i]
            cur.nextFocusUpId = railButtons[(i - 1 + railButtons.size) % railButtons.size].id
            cur.nextFocusDownId = railButtons[(i + 1) % railButtons.size].id
            leftIconRail.addView(cur)
        }
        sidePanel.addView(leftIconRail)

        singleColumnFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        categoryListView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_NONE; divider = null; dividerHeight = 0
            overScrollMode = View.OVER_SCROLL_NEVER; isScrollingCacheEnabled = false
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            selector = ColorDrawable(Color.TRANSPARENT); isSmoothScrollbarEnabled = false
            setBackgroundDrawable(makeThemedSubBg()); setPadding(8, 8, 8, 8); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        channelListView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_NONE; divider = null; dividerHeight = 0
            overScrollMode = View.OVER_SCROLL_NEVER; isScrollingCacheEnabled = false
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            selector = ColorDrawable(Color.TRANSPARENT); isSmoothScrollbarEnabled = false
            setBackgroundDrawable(makeThemedSubBg()); setPadding(8, 8, 8, 8); visibility = View.VISIBLE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        singleColumnFrame.addView(categoryListView)
        singleColumnFrame.addView(channelListView)
        sidePanel.addView(singleColumnFrame)
        rootFrame.addView(sidePanel)

        infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundDrawable(makeThemedPanelBg()); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(540, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, 68, 22, 0) }
            setPadding(26, 22, 26, 22)
        }
        val infoTitle = TextView(this).apply {
            text = "Параметры канала"; setTextColor(Color.parseColor("#00E5FF")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = themeBoldFont(); setPadding(0, 0, 0, 8)
        }
        infoPanel.addView(infoTitle)
        infoTextView = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = themeFont(); setLineSpacing(5f, 1f)
        }
        infoPanel.addView(infoTextView)
        rootFrame.addView(infoPanel)

        centerOsdPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundDrawable(makeThemedPanelBg()); visibility = View.GONE
            setPadding(24, 18, 24, 18)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { setMargins(44, 0, 44, 20) }
        }

        osdChannelTitle = TextView(this).apply {
            setTextColor(currentTheme().accentColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = themeBoldFont(); gravity = Gravity.CENTER_HORIZONTAL
        }
        centerOsdPanel.addView(osdChannelTitle)

        osdEpgCurrentTitle = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 19.5f)
            typeface = themeBoldFont(); gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 6, 0, 2)
        }
        centerOsdPanel.addView(osdEpgCurrentTitle)

        osdEpgTimeLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#B0BEC5")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = themeFont(); gravity = Gravity.CENTER_HORIZONTAL
        }
        centerOsdPanel.addView(osdEpgTimeLabel)

        osdEpgProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 10).apply { setMargins(0, 8, 0, 8) }
        }
        centerOsdPanel.addView(osdEpgProgress)

        osdEpgNextAndDesc = TextView(this).apply {
            setTextColor(currentTheme().accentColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f)
            typeface = themeBoldFont(); gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 2, 0, 10)
        }
        centerOsdPanel.addView(osdEpgNextAndDesc)

        val osdButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        btnOsdTop = makeModernButton("▲ В ТОП") { currentChannel?.let { toggleTopChannel(it) }; updateCenterOsdContent() }
        btnOsdFav = makeModernButton("★ Избр.") { currentChannel?.let { toggleFavorite(it) }; updateCenterOsdContent() }
        btnOsdDualToggle = makeModernButton("⧉ 2-й экран") { toggleDualStreamWithCurrentOrNeighbor(); updateCenterOsdContent() }
        btnOsdRewind = makeModernButton("⏪ -20с") { rewindLiveBufferSeconds(20) }
        btnOsdEpgList = makeModernButton("▤ Гид") { navigateToMenu("FULL_EPG") }
        btnOsdSettings = makeModernButton("⚙ Меню") { navigateToMenu("SETTINGS") }
        btnOsdNightMode = makeModernButton("🌙 $brightnessPercent%") { navigateToMenu("BRIGHTNESS_MENU") }
        btnOsdChannelActions = makeModernButton("✎ Канал") { navigateToMenu("CHANNEL_ACTIONS") }
        btnOsdPowerRedCircle = RedPowerIconButton(this) { navigateToMenu("POWER_AND_SLEEP_MENU") }.apply {
            layoutParams = LinearLayout.LayoutParams(54, 54).apply { setMargins(10, 2, 4, 2) }
        }

        val textOsdBtns = listOf(btnOsdTop, btnOsdFav, btnOsdDualToggle, btnOsdRewind, btnOsdEpgList, btnOsdSettings, btnOsdNightMode, btnOsdChannelActions)
        for (b in textOsdBtns) {
            b.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    buildAndShowZappingBar(); true
                } else if (v == btnOsdTop && event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    closeAllMenus(); navigateToMenu("SIDE"); true
                } else false
            }
            osdButtonsRow.addView(b)
        }
        btnOsdPowerRedCircle.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                buildAndShowZappingBar(); true
            } else if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                closeAllMenus(); navigateToMenu("INFO"); true
            } else false
        }
        osdButtonsRow.addView(btnOsdPowerRedCircle)
        centerOsdPanel.addView(osdButtonsRow)

        zappingRowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            visibility = View.GONE; setPadding(0, 12, 0, 4)
        }
        centerOsdPanel.addView(zappingRowContainer)
        rootFrame.addView(centerOsdPanel)

        fullEpgOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundDrawable(makeThemedPanelBg()); visibility = View.GONE
            setPadding(28, 22, 28, 22)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.BOTTOM).apply {
                setMargins(28, 40, 560, 28)
            }
        }
        fullEpgTitle = TextView(this).apply {
            setTextColor(currentTheme().accentColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = themeBoldFont(); setPadding(0, 0, 0, 12)
        }
        fullEpgOverlay.addView(fullEpgTitle)

        fullEpgListView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_NONE; divider = null; dividerHeight = 0; overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = true; isScrollbarFadingEnabled = false
            selector = ColorDrawable(Color.TRANSPARENT); setBackgroundDrawable(makeThemedSubBg())
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        fullEpgOverlay.addView(fullEpgListView)
        rootFrame.addView(fullEpgOverlay)

        settingsTileOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            setBackgroundDrawable(makeThemedPanelBg()); visibility = View.GONE; setPadding(28, 28, 28, 28)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }

        data class GridTileSpec(val iconType: String, val baseHex: String, val accentHex: String, val shortCaption: String, val action: () -> Unit)

        val gridSpecs = listOf(
            GridTileSpec("PLAYLISTS", "#1B8A6B", "#FFB300", "Плейлисты") { navigateToMenu("PLAYLISTS") },
            GridTileSpec("EDITOR", "#2E5B9A", "#00E676", "Редактор") { navigateToMenu("CHANNEL_EDITOR_GROUPS") },
            GridTileSpec("ANALYZER", "#5E35B1", "#00E5FF", "Тест каналов") { navigateToMenu("ANALYZER_GROUPS") },
            GridTileSpec("THEMES", "#1E4E79", "#FF8F00", "Темы") { navigateToMenu("THEMES") },
            GridTileSpec("BUFFER", "#D84315", "#FFD600", "Буфер") { navigateToMenu("BUFFER") },
            GridTileSpec("DECODER", "#37474F", "#00B0FF", "Декодер") { navigateToMenu("DECODER") },
            GridTileSpec("EPG_GRID", "#00695C", "#FF4081", "Телегид") { navigateToMenu("FULL_EPG") },
            GridTileSpec("TOP_CHANNELS", "#283593", "#FFC107", "ТОП каналы") { navigateToMenu("TOP_MANAGER") },
            GridTileSpec("FAVORITES", "#AD1457", "#FFD54F", "Избранное") {
                selectCategory(0); isSideShowingChannels = true; navigateToMenu("SIDE")
            },
            GridTileSpec("AUDIO", "#0277BD", "#69F0AE", "Звук и DRC") { navigateToMenu("AUDIO") },
            GridTileSpec("CONTROL_MODE", "#00838F", "#FFD54F", "Управление") { showFirstRunControlPicker(forceShow = true) },
            GridTileSpec("MORE", "#4A148C", "#FFAB00", "Общие") { navigateToMenu("MORE_SETTINGS") }
        )

        gridContainerLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val cellViewsMatrix = Array(3) { arrayOfNulls<LinearLayout>(4) }

        for (rowIdx in 0 until 3) {
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (colIdx in 0 until 4) {
                val specIdx = rowIdx * 4 + colIdx
                val spec = gridSpecs[specIdx]
                val tileIcon = ThemedTileView(this, spec.iconType, spec.baseHex, spec.accentHex).apply {
                    themeStyleId = currentTheme().id
                    layoutParams = LinearLayout.LayoutParams(172, 172)
                }
                allGridTiles.add(tileIcon)

                val captionView = TextView(this).apply {
                    text = spec.shortCaption; setTextColor(Color.parseColor("#CCCCCC")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    typeface = themeFont(); gravity = Gravity.CENTER; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, 6, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(220, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                allGridCaptions.add(captionView)

                val cellBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    isFocusable = true; isFocusableInTouchMode = false; isClickable = true
                    id = 5000 + specIdx
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(22, 10, 22, 10)
                    }
                    addView(tileIcon); addView(captionView)
                    setOnFocusChangeListener { v, hasFocus ->
                        tileIcon.isTileFocused = hasFocus
                        v.animate().scaleX(if (hasFocus) 1.08f else 1.0f).scaleY(if (hasFocus) 1.08f else 1.0f).setDuration(180L).start()
                        captionView.setTextColor(if (hasFocus) currentTheme().accentColor else currentTheme().textPrimaryColor)
                        if (hasFocus) resetMenuInactivityTimer()
                    }
                    setOnClickListener { resetMenuInactivityTimer(); spec.action() }
                }
                cellViewsMatrix[rowIdx][colIdx] = cellBox
                rowLayout.addView(cellBox)
            }
            gridContainerLayout.addView(rowLayout)
        }

        for (r in 0 until 3) {
            for (c in 0 until 4) {
                val v = cellViewsMatrix[r][c] ?: continue
                v.nextFocusLeftId = cellViewsMatrix[r][(c + 3) % 4]!!.id
                v.nextFocusRightId = cellViewsMatrix[r][(c + 1) % 4]!!.id
                v.nextFocusUpId = cellViewsMatrix[(r + 2) % 3][c]!!.id
                v.nextFocusDownId = cellViewsMatrix[(r + 1) % 3][c]!!.id
            }
        }

        carouselScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; visibility = View.GONE
            isHorizontalFadingEdgeEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false; clipToPadding = false
            setPadding(48, 64, 48, 52)
            layoutParams = LinearLayout.LayoutParams(1040, ViewGroup.LayoutParams.WRAP_CONTENT)
            viewTreeObserver.addOnScrollChangedListener { updateCarouselEdgeTransforms() }
        }
        carouselRowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
        }
        carouselCells.clear()

        for (specIdx in gridSpecs.indices) {
            val spec = gridSpecs[specIdx]
            val cTile = ThemedTileView(this, spec.iconType, spec.baseHex, spec.accentHex).apply {
                themeStyleId = currentTheme().id
                layoutParams = LinearLayout.LayoutParams(184, 184)
            }
            allGridTiles.add(cTile)
            val cCaption = TextView(this).apply {
                text = spec.shortCaption; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                typeface = themeBoldFont(); gravity = Gravity.CENTER; isSingleLine = true
                includeFontPadding = false
                setPadding(4, 10, 4, 0)
                layoutParams = LinearLayout.LayoutParams(224, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            allGridCaptions.add(cCaption)
            val cBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                isFocusable = true; isFocusableInTouchMode = false; isClickable = true
                id = 6000 + specIdx; scaleX = 0.88f; scaleY = 0.88f; alpha = 0.80f
                layoutParams = LinearLayout.LayoutParams(224, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(6, 16, 6, 16)
                }
                addView(cTile); addView(cCaption)
                setOnFocusChangeListener { v, hasFocus ->
                    cTile.isTileFocused = hasFocus
                    if (hasFocus) {
                        resetMenuInactivityTimer()
                        cCaption.setTextColor(currentTheme().accentColor)
                        v.animate().scaleX(1.22f).scaleY(1.22f).alpha(1.0f).translationY(-10f)
                            .setDuration(220L).setInterpolator(DecelerateInterpolator(1.3f)).start()
                        val maxScroll = (carouselRowLayout.width + carouselScrollView.paddingLeft + carouselScrollView.paddingRight - carouselScrollView.width).coerceAtLeast(0)
                        val desiredCenterScroll = ((v.left + v.right) / 2 + carouselScrollView.paddingLeft) - (carouselScrollView.width / 2)
                        val clampedScroll = desiredCenterScroll.coerceIn(0, maxScroll)
                        smoothScrollCarouselTo(clampedScroll)
                    } else {
                        cCaption.setTextColor(currentTheme().textPrimaryColor)
                        v.animate().scaleX(0.88f).scaleY(0.88f).translationY(0f)
                            .setDuration(190L).setInterpolator(DecelerateInterpolator(1.2f)).start()
                    }
                    carouselScrollView.postDelayed({ updateCarouselEdgeTransforms() }, 40L)
                }
                setOnClickListener { resetMenuInactivityTimer(); spec.action() }
            }
            carouselCells.add(cBox)
            carouselRowLayout.addView(cBox)
        }
        for (i in carouselCells.indices) {
            carouselCells[i].nextFocusLeftId = carouselCells[(i - 1 + carouselCells.size) % carouselCells.size].id
            carouselCells[i].nextFocusRightId = carouselCells[(i + 1) % carouselCells.size].id
        }
        carouselScrollView.addView(carouselRowLayout)
        settingsTileOverlay.addView(gridContainerLayout)
        settingsTileOverlay.addView(carouselScrollView)
        rootFrame.addView(settingsTileOverlay)

        subModalOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundDrawable(makeThemedPanelBg()); visibility = View.GONE
            setPadding(26, 18, 26, 18)
            layoutParams = FrameLayout.LayoutParams(940, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        subModalTitle = TextView(this).apply {
            setTextColor(currentTheme().accentColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.5f)
            typeface = themeBoldFont(); setPadding(4, 0, 4, 10)
        }
        subModalOverlay.addView(subModalTitle)

        subModalTopButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE; setPadding(0, 0, 0, 10)
        }
        subModalOverlay.addView(subModalTopButtons)

        subModalInputContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        subModalOverlay.addView(subModalInputContainer)

        subModalList = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_NONE; divider = null; dividerHeight = 0; overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            isSmoothScrollbarEnabled = false
            selector = ColorDrawable(Color.TRANSPARENT); setBackgroundDrawable(makeThemedSubBg()); setPadding(8, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 432)
        }
        subModalAdapter = createRoundedSelectorAdapter(subModalItems, 15.5f, true, fixedRowHeight = true) { pos -> subModalHighlightedPos == pos }
        subModalList.adapter = subModalAdapter
        subModalList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                subModalHighlightedPos = position
                resetMenuInactivityTimer()
                updateSubModalTitleWithCounter()
                refreshSimpleListHighlightInPlace(subModalList, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        subModalOverlay.addView(subModalList)
        rootFrame.addView(subModalOverlay)

        currentChannelBanner = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); typeface = themeBoldFont()
            setBackgroundDrawable(makeThemedPanelBg()); setPadding(22, 12, 22, 12); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply { setMargins(26, 0, 26, 26) }
        }
        rootFrame.addView(currentChannelBanner)

        firstRunModeOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E608070D")); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        buildFirstRunControlPickerView()
        rootFrame.addView(firstRunModeOverlay)

        splashOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        splashEyeView = RealisticEyeSplashView(this).apply {
            alpha = 0f; eyeOpenFraction = 0.03f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        }
        splashOverlay.addView(splashEyeView)
        rootFrame.addView(splashOverlay)

        nightDimOverlay = View(this).apply {
            setBackgroundColor(Color.argb(95, 18, 21, 28)); visibility = View.GONE
            isClickable = false; isFocusable = false
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        rootFrame.addView(nightDimOverlay)

        crtTurnOffView = CrtTurnOffView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        rootFrame.addView(crtTurnOffView)

        setContentView(rootFrame)

        playSplashEyeAnimation()
        initPlayer()
        applyScreenAspectMode()
        applyBrightnessLevel()

        categoryAdapter = createRoundedSelectorAdapter(categoryDisplayItems, 16f, true, fixedRowHeight = true) { pos ->
            !isSideShowingChannels && !isLeftRailVisible && pos == focusedCategoryPos
        }
        categoryListView.adapter = categoryAdapter

        categoryListView.setOnItemClickListener { _, _, position, _ ->
            resetMenuInactivityTimer()
            focusedCategoryPos = position
            selectCategory(position)
            showSideChannelsMode(focusCurrentChannelIfSameGroup = false)
        }
        categoryListView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                resetMenuInactivityTimer()
                focusedCategoryPos = position
                refreshSimpleListHighlightInPlace(categoryListView, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        categoryListView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                resetMenuInactivityTimer()
                val count = categoryAdapter.count
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { showLeftIconRailFromGroups(); return@setOnKeyListener true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        selectCategory(focusedCategoryPos.coerceAtLeast(0))
                        showSideChannelsMode(focusCurrentChannelIfSameGroup = false)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> if (count > 0 && focusedCategoryPos <= 0) {
                        val lastIdx = count - 1; focusedCategoryPos = lastIdx
                        categoryListView.setSelection(lastIdx); refreshSimpleListHighlightInPlace(categoryListView, lastIdx)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (count > 0 && focusedCategoryPos >= count - 1) {
                        focusedCategoryPos = 0
                        categoryListView.setSelection(0); refreshSimpleListHighlightInPlace(categoryListView, 0)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        channelListAdapter = createRoundedSelectorAdapter(channelDisplayItems, 15.5f, true, fixedRowHeight = true) { pos ->
            isSideShowingChannels && pos == focusedChannelPos
        }
        channelListView.adapter = channelListAdapter

        channelListView.setOnItemClickListener { _, _, position, _ ->
            resetMenuInactivityTimer()
            val list = displayedChannels.toList()
            if (position in list.indices) {
                focusedChannelPos = position
                playChannelObj(list[position], immediate = true)
                if (controlMode != 1) closeAllMenus() else refreshChannelAdapter()
            }
        }
        channelListView.setOnItemLongClickListener { _, _, position, _ ->
            resetMenuInactivityTimer()
            val list = displayedChannels.toList()
            if (position in list.indices) {
                focusedChannelPos = position
                toggleFavorite(list[position])
            }
            true
        }
        channelListView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                resetMenuInactivityTimer()
                focusedChannelPos = position
                refreshSimpleListHighlightInPlace(channelListView, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        channelListView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                resetMenuInactivityTimer()
                val count = channelListAdapter.count
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { showSideGroupsMode(); return@setOnKeyListener true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { closeAllMenus(); return@setOnKeyListener true }
                    KeyEvent.KEYCODE_DPAD_UP -> if (count > 0 && focusedChannelPos <= 0) {
                        val lastIdx = count - 1; focusedChannelPos = lastIdx
                        channelListView.setSelection(lastIdx); refreshSimpleListHighlightInPlace(channelListView, lastIdx)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (count > 0 && focusedChannelPos >= count - 1) {
                        focusedChannelPos = 0
                        channelListView.setSelection(0); refreshSimpleListHighlightInPlace(channelListView, 0)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        applyCurrentThemeToAllPanels()
        loadActivePlaylist(autoPlayLast = true)
        loadEpgOncePerDayInBackground(forceRefresh = false)
    }

    internal fun setSubModalHeaderTitle(baseTitle: String) {
        subModalBaseTitleText = baseTitle
        updateSubModalTitleWithCounter()
    }

    internal fun updateSubModalTitleWithCounter() {
        val total = subModalItems.size
        if (total > 1 && subModalList.visibility == View.VISIBLE) {
            val cur = (subModalHighlightedPos + 1).coerceIn(1, total)
            subModalTitle.text = colorizeStatusText("$subModalBaseTitleText   •   [$cur / $total]")
        } else {
            subModalTitle.text = colorizeStatusText(subModalBaseTitleText)
        }
    }

    internal var carouselScrollAnimator: ValueAnimator? = null

    internal fun smoothScrollCarouselTo(targetX: Int) {
        carouselScrollAnimator?.cancel()
        val startX = carouselScrollView.scrollX
        if (startX == targetX) {
            updateCarouselEdgeTransforms()
            return
        }
        carouselScrollAnimator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 210L
            interpolator = DecelerateInterpolator(1.25f)
            addUpdateListener { anim ->
                carouselScrollView.scrollTo(anim.animatedValue as Int, 0)
                updateCarouselEdgeTransforms()
            }
            start()
        }
    }

    internal fun showLeftIconRailFromGroups() {
        isLeftRailVisible = true
        leftIconRail.visibility = View.VISIBLE
        leftIconRail.alpha = 0f; leftIconRail.translationX = -36f
        leftIconRail.animate().alpha(1f).translationX(0f).setDuration(220L).start()
        refreshSimpleListHighlightInPlace(categoryListView, -1)
        railTilePlaylists.requestFocus()
    }

    internal fun hideLeftIconRailToGroups() {
        isLeftRailVisible = false
        leftIconRail.visibility = View.GONE
        categoryListView.requestFocus()
        refreshSimpleListHighlightInPlace(categoryListView, focusedCategoryPos)
    }

    internal fun refreshSimpleListHighlightInPlace(listView: ListView, activeAdapterPos: Int) {
        val t = currentTheme()
        val scale = uiScaleFactor()
        val firstVis = listView.firstVisiblePosition
        for (i in 0 until listView.childCount) {
            val child = listView.getChildAt(i) as? MarqueeRowTextView ?: continue
            val itemPos = firstVis + i
            val isHighlighted = (itemPos == activeAdapterPos)
            child.typeface = if (isHighlighted) t.boldFont else t.normalFont
            child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f * scale)
            child.forceMarqueeActive = isHighlighted
            if (isHighlighted) {
                child.setTextColor(t.textOnAccent)
                child.setBackgroundDrawable(GradientDrawable().apply { setColor(t.accentColor); cornerRadius = 16f * scale; setStroke(2, Color.WHITE) })
            } else {
                child.setTextColor(t.textPrimaryColor)
                child.setBackgroundDrawable(null)
            }
            val raw = (listView.adapter?.getItem(itemPos) as? String) ?: child.text.toString()
            child.text = colorizeStatusText(raw)
        }
    }

    internal fun createRoundedSelectorAdapter(
        items: MutableList<String>,
        spSize: Float,
        singleLineMarquee: Boolean = false,
        fixedRowHeight: Boolean = false,
        isRowHighlighted: (Int) -> Boolean
    ): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this@MainActivity, 0, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? MarqueeRowTextView) ?: MarqueeRowTextView(this@MainActivity)
                val t = currentTheme()
                val scale = uiScaleFactor()
                val highlighted = isRowHighlighted(position)
                val rawTxt = getItem(position) ?: ""
                if (fixedRowHeight) {
                    val rowH = exactModalRowHeightPx()
                    tv.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH)
                    tv.gravity = Gravity.CENTER_VERTICAL
                    tv.setPadding(20, 0, 20, 0)
                } else {
                    tv.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    val padV = (10 * scale).toInt()
                    tv.setPadding(20, padV, 20, padV)
                }
                tv.typeface = if (highlighted) t.boldFont else t.normalFont
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize * scale)
                if (singleLineMarquee) tv.forceMarqueeActive = highlighted else tv.isSingleLine = false
                if (highlighted) {
                    tv.setTextColor(t.textOnAccent)
                    tv.setBackgroundDrawable(GradientDrawable().apply { setColor(t.accentColor); cornerRadius = 16f * scale; setStroke(2, Color.WHITE) })
                } else {
                    tv.setTextColor(t.textPrimaryColor)
                    tv.setBackgroundDrawable(null)
                }
                tv.setTextWithSmoothToggle(colorizeStatusText(rawTxt), animateNextSubModalChange)
                return tv
            }
        }
    }

    internal fun buildAndShowZappingBar() {
        val list = displayedChannels.toList()
        if (list.isEmpty()) return
        zappingRowContainer.removeAllViews()
        zappingRowContainer.visibility = View.VISIBLE

        val curIdx = list.indexOfFirst { it.url == currentChannel?.url }.takeIf { it >= 0 } ?: 0
        val offsets = listOf(-2, -1, 0, 1, 2)
        var centerCard: View? = null

        for (off in offsets) {
            val idx = (curIdx + off + list.size) % list.size
            val ch = list[idx]
            val (epg, _) = getCurrentAndNextEpg(ch)
            val epgTxt = epg?.title ?: effectiveCategory(ch)
            val prefix = if (off == 0) "▶ " else ""
            val cardBtn = makeModernButton("$prefix${ch.name}\n🎬 ${epgTxt.take(20)}") {
                playChannelObj(ch, immediate = true)
                buildAndShowZappingBar()
            }.apply {
                isSingleLine = false; maxLines = 2
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 2, 4, 2) }
            }
            if (off == 0) centerCard = cardBtn
            zappingRowContainer.addView(cardBtn)
        }
        centerCard?.requestFocus()
    }

    internal fun updateSubModalListItems(newItems: List<String>, smoothToggle: Boolean = false, preservePositionIndex: Int = -1) {
        val targetPos = when {
            preservePositionIndex in newItems.indices -> preservePositionIndex
            subModalHighlightedPos in newItems.indices -> subModalHighlightedPos
            else -> 0
        }
        val firstVis = subModalList.firstVisiblePosition
        val selectedChildIdx = targetPos - firstVis
        val selectedRowTopPx = if (smoothToggle && selectedChildIdx in 0 until subModalList.childCount) {
            subModalList.getChildAt(selectedChildIdx)?.top ?: 8
        } else {
            subModalList.getChildAt(0)?.top ?: 8
        }

        subModalHighlightedPos = targetPos
        animateNextSubModalChange = smoothToggle
        subModalItems.clear()
        subModalItems.addAll(newItems)
        fitSubModalListToWholeRows(newItems.size)
        subModalAdapter.notifyDataSetChanged()
        updateSubModalTitleWithCounter()

        subModalList.post {
            animateNextSubModalChange = false
            if (newItems.isNotEmpty()) {
                if (smoothToggle) {
                    subModalList.setSelectionFromTop(targetPos, selectedRowTopPx)
                } else {
                    subModalList.setSelection(targetPos)
                }
                refreshSimpleListHighlightInPlace(subModalList, targetPos)
            }
        }
    }

    internal fun fitSubModalListToWholeRows(itemCount: Int) {
        val dm = resources.displayMetrics
        val rowH = exactModalRowHeightPx()
        val maxAvailH = (dm.heightPixels * 0.62f).toInt()
        val maxWholeRows = (maxAvailH / rowH).coerceIn(5, 10)
        val visibleRows = itemCount.coerceAtLeast(1).coerceAtMost(maxWholeRows)
        val exactListH = visibleRows * rowH + 16
        subModalList.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            exactListH
        )
    }

    internal fun buildFirstRunControlPickerView() {
        firstRunModeOverlay.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundDrawable(makeThemedPanelBg()); setPadding(48, 40, 48, 40)
            layoutParams = FrameLayout.LayoutParams(880, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        val title = TextView(this).apply {
            text = "TiViLook 2.7 — Выберите устройство"
            setTextColor(currentTheme().textPrimaryColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = themeBoldFont(); gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Для смартфона автоматически включится отступ под вырез камеры и компактный шрифт"
            setTextColor(Color.parseColor("#94A3B8")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = themeFont(); gravity = Gravity.CENTER; setPadding(0, 8, 0, 28)
        }
        card.addView(title); card.addView(subtitle)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val btnTouchMode = makeModernButton("📱 Смартфон / Планшет\nОтступ под камеру + сенсор") {
            controlMode = 1; uiScaleStep = 0; cameraCutoutSafeOffset = true; screenAspectMode = 0
            applyScreenAspectMode(); applyCurrentThemeToAllPanels(); saveState()
            enableTrueFullScreenImmersion(); firstRunModeOverlay.visibility = View.GONE
        }.apply { setPadding(34, 28, 34, 28); isSingleLine = false; maxLines = 3; setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f) }

        val btnRemoteMode = makeModernButton("📺 Телевизор / ТВ-приставка\nКрупный шрифт на весь экран") {
            controlMode = 2; uiScaleStep = 1; cameraCutoutSafeOffset = false
            applyCurrentThemeToAllPanels(); saveState()
            enableTrueFullScreenImmersion(); firstRunModeOverlay.visibility = View.GONE
        }.apply { setPadding(34, 28, 34, 28); isSingleLine = false; maxLines = 3; setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f) }

        row.addView(btnTouchMode); row.addView(btnRemoteMode)
        card.addView(row); firstRunModeOverlay.addView(card)
    }

    internal fun showFirstRunControlPicker(forceShow: Boolean = false) {
        if (controlMode == 0 || forceShow) {
            closeAllMenus(); buildFirstRunControlPickerView()
            animateMenuAppear(firstRunModeOverlay, 0f, 20f)
            val card = firstRunModeOverlay.getChildAt(0) as? LinearLayout
            val row = card?.getChildAt(2) as? LinearLayout
            row?.getChildAt(if (controlMode == 1) 0 else 1)?.requestFocus()
        }
    }

    internal fun isPointInsideVisibleView(x: Float, y: Float, v: View): Boolean {
        if (v.visibility != View.VISIBLE) return false
        if (v.width == 0 || v.height == 0) return true
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height
    }

    internal fun isTouchInsideAnyActiveMenu(e: MotionEvent): Boolean {
        val x = e.rawX; val y = e.rawY
        return isPointInsideVisibleView(x, y, sidePanel) ||
            isPointInsideVisibleView(x, y, centerOsdPanel) ||
            isPointInsideVisibleView(x, y, settingsTileOverlay) ||
            isPointInsideVisibleView(x, y, subModalOverlay) ||
            isPointInsideVisibleView(x, y, fullEpgOverlay) ||
            isPointInsideVisibleView(x, y, infoPanel) ||
            isPointInsideVisibleView(x, y, dualStreamContainer)
    }

    internal fun initTouchGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = false
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (firstRunModeOverlay.visibility == View.VISIBLE) return false
                if (System.currentTimeMillis() - lastMenuChangeMs < 500L) return true
                if (isDualStreamActive && isPointInsideVisibleView(e.rawX, e.rawY, dualStreamContainer) && !isPointInsideVisibleView(e.rawX, e.rawY, dualCloseBtn)) {
                    swapMainAndDualStream()
                    return true
                }
                if (menuHistory.isNotEmpty()) {
                    if (!isTouchInsideAnyActiveMenu(e)) {
                        closeAllMenus()
                        return true
                    }
                    return false
                }
                enableTrueFullScreenImmersion()
                val screenW = resources.displayMetrics.widthPixels
                when {
                    e.x < screenW * 0.28f -> navigateToMenu("SIDE")
                    e.x > screenW * 0.72f -> navigateToMenu("INFO")
                    else -> navigateToMenu("OSD")
                }
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (firstRunModeOverlay.visibility == View.VISIBLE || menuHistory.isNotEmpty()) return
                navigateToMenu("SETTINGS")
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (firstRunModeOverlay.visibility == View.VISIBLE || menuHistory.isNotEmpty()) return false
                if (isDualStreamActive) {
                    swapMainAndDualStream()
                } else {
                    previousChannel?.let { playChannelObj(it, immediate = true) } ?: navigateToMenu("SETTINGS")
                }
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || firstRunModeOverlay.visibility == View.VISIBLE) return false
                val dx = e2.x - e1.x; val dy = e2.y - e1.y
                val curMenu = menuHistory.lastOrNull()

                if (curMenu == "SETTINGS" && (currentTheme().isCarouselMenu || isPointInsideVisibleView(e1.rawX, e1.rawY, carouselScrollView))) {
                    return false
                }

                if (abs(dx) > abs(dy) * 1.45f && abs(dx) > 95f) {
                    if (curMenu == "SIDE") {
                        if (dx > 0) {
                            if (isSideShowingChannels) showSideGroupsMode()
                            else if (!isLeftRailVisible) showLeftIconRailFromGroups()
                        } else {
                            if (isLeftRailVisible) hideLeftIconRailToGroups()
                            else if (!isSideShowingChannels) showSideChannelsMode(focusCurrentChannelIfSameGroup = false)
                            else closeAllMenus()
                        }
                        return true
                    } else if (curMenu != null) {
                        if (dx < 0) {
                            navigateBackInMenu()
                            return true
                        }
                    } else {
                        navigateToMenu("SIDE")
                        return true
                    }
                } else if (abs(dy) > abs(dx) * 1.35f && abs(dy) > 85f) {
                    if (curMenu == null || curMenu == "INFO") {
                        switchChannelByOffset(if (dy < 0) +1 else -1)
                        return true
                    } else if (curMenu == "OSD" && dy > 0) {
                        buildAndShowZappingBar()
                        return true
                    }
                }
                return false
            }
        })
    }

    internal fun extractTwoLetters(name: String): String {
        val clean = name.replace(Regex("[^a-zA-Zа-яА-Я0-9 ]"), " ").trim()
        val parts = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val raw = if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}" else (parts.firstOrNull() ?: "TV").take(2)
        return raw.uppercase().padEnd(2, 'T').take(2)
    }

    internal fun fitSideListsExactRows() {
        val rowH = exactModalRowHeightPx().coerceAtLeast(42)
        val maxH = (resources.displayMetrics.heightPixels - 68).coerceAtLeast(300)
        val exactRows = (maxH / rowH).coerceAtLeast(5)
        val exactH = exactRows * rowH + 16
        categoryListView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, exactH, Gravity.CENTER_VERTICAL)
        channelListView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, exactH, Gravity.CENTER_VERTICAL)
    }

    internal fun showSideChannelsMode(focusCurrentChannelIfSameGroup: Boolean = true) {
        isSideShowingChannels = true
        isLeftRailVisible = false
        leftIconRail.visibility = View.GONE
        fitSideListsExactRows()
        categoryListView.visibility = View.GONE
        channelListView.visibility = View.VISIBLE

        val list = displayedChannels.toList()
        val activeIdx = if (focusCurrentChannelIfSameGroup) list.indexOfFirst { it.url == currentChannel?.url } else focusedChannelPos
        val targetPos = if (activeIdx in list.indices) activeIdx else 0
        focusedChannelPos = targetPos
        refreshChannelAdapter()

        channelListView.post {
            if (channelDisplayItems.isNotEmpty()) {
                channelListView.setSelectionFromTop(targetPos, (channelListView.height / 3).coerceAtLeast(70))
                channelListView.requestFocus()
                refreshSimpleListHighlightInPlace(channelListView, targetPos)
            }
        }
    }

    internal fun showSideGroupsMode() {
        isSideShowingChannels = false
        isLeftRailVisible = false
        leftIconRail.visibility = View.GONE
        fitSideListsExactRows()
        channelListView.visibility = View.GONE
        categoryListView.visibility = View.VISIBLE

        updateCategoriesList()
        val targetPos = currentCategoryIndex.coerceIn(0, (categoryAdapter.count - 1).coerceAtLeast(0))
        focusedCategoryPos = targetPos

        categoryListView.post {
            categoryListView.setSelectionFromTop(targetPos, (categoryListView.height / 3).coerceAtLeast(60))
            categoryListView.requestFocus()
            refreshSimpleListHighlightInPlace(categoryListView, targetPos)
        }
    }

    internal fun playSplashEyeAnimation() {
        val startMs = System.currentTimeMillis()
        val totalDuration = 2800L
        val animRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startMs
                if (elapsed <= 700L) {
                    val p = (elapsed / 700f).coerceIn(0f, 1f)
                    splashEyeView.alpha = p; splashEyeView.eyeOpenFraction = 0.05f + 0.15f * p
                    uiHandler.postDelayed(this, 16L)
                } else if (elapsed <= 1500L) {
                    splashEyeView.alpha = 1f
                    val p = ((elapsed - 700L) / 800f).coerceIn(0f, 1f)
                    splashEyeView.eyeOpenFraction = 0.20f + 0.80f * (1f - (1f - p) * (1f - p))
                    uiHandler.postDelayed(this, 16L)
                } else if (elapsed <= 2300L) {
                    val p = ((elapsed - 1500L) / 800f).coerceIn(0f, 1f)
                    splashEyeView.lookOffsetX = (Math.sin(p * Math.PI * 2.0) * 0.42).toFloat()
                    splashEyeView.eyeOpenFraction = 1.0f
                    uiHandler.postDelayed(this, 16L)
                } else if (elapsed <= totalDuration) {
                    val p = ((elapsed - 2300L) / 500f).coerceIn(0f, 1f)
                    splashEyeView.lookOffsetX = 0f
                    val blink = abs(p - 0.4f) / 0.6f
                    splashEyeView.eyeOpenFraction = (0.12f + 0.88f * blink).coerceIn(0.12f, 1f)
                    uiHandler.postDelayed(this, 16L)
                } else {
                    splashOverlay.animate().alpha(0f).setDuration(450L).withEndAction {
                        splashOverlay.visibility = View.GONE
                        applyAudioEnhancements()
                        if (controlMode == 0) {
                            showFirstRunControlPicker(forceShow = true)
                        } else if (currentChannel != null && showChannelBannerOnSwitch && menuHistory.isEmpty()) {
                            val ch = currentChannel!!
                            val topBadge = if (topUrls.contains(ch.url)) "▲ " else ""
                            val star = if (favoriteUrls.contains(ch.url)) "★ " else ""
                            showBanner(topBadge + star + ch.name + "  •  " + effectiveCategory(ch))
                        }
                    }.start()
                }
            }
        }
        uiHandler.post(animRunnable)
    }

    internal fun requestStoragePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 2002)
            }
        }
    }

    internal fun hideAllMenuViews() {
        setPlayerMiniPreviewMode(false)
        sidePanel.visibility = View.GONE
        leftIconRail.visibility = View.GONE
        isLeftRailVisible = false
        infoPanel.visibility = View.GONE
        centerOsdPanel.visibility = View.GONE
        zappingRowContainer.visibility = View.GONE
        settingsTileOverlay.visibility = View.GONE
        subModalOverlay.visibility = View.GONE
        fullEpgOverlay.visibility = View.GONE
        clockTextView.visibility = View.VISIBLE
    }

    internal fun closeAllMenus() {
        menuHistory.clear()
        uiHandler.removeCallbacks(autoHideMenuRunnable)
        hideAllMenuViews()
    }

    internal fun navigateToMenu(menuId: String) {
        lastMenuChangeMs = System.currentTimeMillis()
        if (menuHistory.lastOrNull() != menuId) {
            menuHistory.add(menuId)
            subModalHighlightedPos = 0
        }
        renderMenuById(menuId)
        resetMenuInactivityTimer()
    }

    internal fun navigateBackInMenu(): Boolean {
        lastMenuChangeMs = System.currentTimeMillis()
        if (firstRunModeOverlay.visibility == View.VISIBLE && controlMode != 0) {
            firstRunModeOverlay.visibility = View.GONE
            return true
        }
        if (menuHistory.isEmpty()) return false
        val leaving = menuHistory.removeAt(menuHistory.lastIndex)
        if ((leaving == "ANALYZER_GROUP_VIEW" || leaving == "ANALYZER_GROUPS") && isAnalyzerRunning) {
            analyzerStatusBanner.visibility = View.VISIBLE
        }
        val prev = menuHistory.lastOrNull()
        if (prev == null) {
            uiHandler.removeCallbacks(autoHideMenuRunnable)
            hideAllMenuViews()
        } else {
            subModalHighlightedPos = 0
            renderMenuById(prev)
            resetMenuInactivityTimer()
        }
        return true
    }

    internal fun renderMenuById(menuId: String) {
        hideAllMenuViews()
        sidePanel.setBackgroundDrawable(makeThemedPanelBg())
        centerOsdPanel.setBackgroundDrawable(makeThemedPanelBg())
        subModalOverlay.setBackgroundDrawable(makeThemedPanelBg())
        settingsTileOverlay.setBackgroundDrawable(makeThemedPanelBg())
        fullEpgOverlay.setBackgroundDrawable(makeThemedPanelBg())

        currentChannelBanner.animate().cancel()
        currentChannelBanner.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalTopButtons.removeAllViews()

        val dm = resources.displayMetrics
        subModalOverlay.layoutParams = FrameLayout.LayoutParams(
            (dm.widthPixels * 0.76f).toInt().coerceIn(800, 1360),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )

        when (menuId) {
            "SIDE" -> {
                setPlayerMiniPreviewMode(true)
                clockTextView.visibility = View.GONE
                animateMenuAppear(sidePanel, -55f, 0f)
                val cats = activeCategoryList()
                if (openChannelsDirectlyWithCurrentFocus && currentChannel != null) {
                    openChannelsDirectlyWithCurrentFocus = false
                    val chCatIdx = cats.indexOf(effectiveCategory(currentChannel!!)).takeIf { it >= 2 } ?: 1
                    selectCategory(chCatIdx)
                    showSideChannelsMode(focusCurrentChannelIfSameGroup = true)
                } else if (isSideShowingChannels) {
                    showSideChannelsMode(focusCurrentChannelIfSameGroup = false)
                } else {
                    showSideGroupsMode()
                }
            }
            "INFO" -> {
                adjustInfoPanelMarginForDualStream()
                updateStreamInfoText()
                animateMenuAppear(infoPanel, 45f, 0f)
            }
            "OSD" -> { updateCenterOsdContent(); animateMenuAppear(centerOsdPanel, 0f, 35f); btnOsdTop.requestFocus() }
            "BRIGHTNESS_MENU" -> openBrightnessSubModal()
            "POWER_AND_SLEEP_MENU" -> openPowerAndSleepSubModal()
            "CHANNEL_ACTIONS" -> openChannelActionsSubModal()
            "MOVE_CHANNEL_CATEGORY" -> openMoveChannelCategorySubModal()
            "FULL_EPG" -> openFullScreenChannelEpg()
            "SETTINGS" -> {
                animateMenuAppear(settingsTileOverlay, 0f, 24f)
                if (currentTheme().isCarouselMenu) {
                    gridContainerLayout.visibility = View.GONE
                    carouselScrollView.visibility = View.VISIBLE
                    carouselScrollView.scrollTo(0, 0)
                    carouselCells.firstOrNull()?.requestFocus()
                    carouselScrollView.postDelayed({ updateCarouselEdgeTransforms() }, 40L)
                } else {
                    carouselScrollView.visibility = View.GONE
                    gridContainerLayout.visibility = View.VISIBLE
                    (gridContainerLayout.getChildAt(0) as? LinearLayout)?.getChildAt(0)?.requestFocus()
                }
            }
            "THEMES" -> openThemesSubModal()
            "TOP_MANAGER" -> openTopManagerSubModal(inPlace = false)
            "MORE_SETTINGS" -> openMoreSettingsSubModal(inPlace = false)
            "PLAYLISTS" -> openPlaylistsSubModal()
            "FILE_BROWSER" -> openFolderExplorerModal(currentBrowseDir)
            "CHANNEL_EDITOR_GROUPS" -> openChannelEditorGroupsModal(inPlace = false)
            "CHANNEL_EDITOR_ITEMS" -> openChannelEditorItemsModal(selectedEditorCategory, inPlace = false)
            "ANALYZER_GROUPS" -> openAnalyzerGroupsModal(firstOpen = true)
            "ANALYZER_GROUP_VIEW" -> openAnalyzerGroupChannelsModal(selectedAnalyzerCategory, firstOpen = true)
            "BUFFER" -> openBufferSubModal(inPlace = false)
            "DECODER" -> openDecoderSubModal(inPlace = false)
            "AUDIO" -> openAudioSubModal(inPlace = false)
            "EPG" -> openInputSubModal("Ссылка на телепрограмму XMLTV", epgUrl) { newVal ->
                if (newVal.isNotEmpty()) { epgUrl = newVal; saveState(); loadEpgOncePerDayInBackground(forceRefresh = true) }
            }
            "ADD_URL" -> openAddUrlSubModal()
        }
    }

    internal fun openBrightnessSubModal() {}
    internal fun openPowerAndSleepSubModal() {}
    internal fun openChannelActionsSubModal() {}
    internal fun openMoveChannelCategorySubModal() {}
    internal fun openFullScreenChannelEpg() {}
    internal fun openThemesSubModal() {}
    internal fun openTopManagerSubModal(inPlace: Boolean) {}
    internal fun openMoreSettingsSubModal(inPlace: Boolean) {}
    internal fun openPlaylistsSubModal() {}
    internal fun openFolderExplorerModal(dir: File) {}
    internal fun openChannelEditorGroupsModal(inPlace: Boolean) {}
    internal fun openChannelEditorItemsModal(cat: String, inPlace: Boolean) {}
    internal fun openAnalyzerGroupsModal(firstOpen: Boolean) {}
    internal fun openAnalyzerGroupChannelsModal(cat: String, firstOpen: Boolean) {}
    internal fun openBufferSubModal(inPlace: Boolean) {}
    internal fun openDecoderSubModal(inPlace: Boolean) {}
    internal fun openAudioSubModal(inPlace: Boolean) {}
    internal fun openInputSubModal(title: String, initialText: String, onSave: (String) -> Unit) {}
    internal fun openAddUrlSubModal() {}

    internal fun rewindLiveBufferSeconds(sec: Int) {
        val p = player ?: return
        val cur = p.currentPosition
        val target = (cur - sec * 1000L).coerceAtLeast(0L)
        p.seekTo(target)
    }

    internal fun stopDualStreamMode() {
        isDualStreamActive = false
        try { dualPipPlayer?.stop(); dualPipPlayer?.release() } catch (_: Exception) {}
        dualPipPlayer = null
        dualStreamContainer.visibility = View.GONE
        adjustInfoPanelMarginForDualStream()
        updateCenterOsdContent()
    }

    internal fun toggleDualStreamWithCurrentOrNeighbor() {
        if (isDualStreamActive) {
            stopDualStreamMode()
        } else {
            val list = displayedChannels.toList()
            if (list.size < 2) return
            val curIdx = list.indexOfFirst { it.url == currentChannel?.url }.takeIf { it >= 0 } ?: 0
            val secondCh = previousChannel ?: list[(curIdx + 1) % list.size]
            startDualStreamChannel(secondCh)
        }
    }

    internal fun startDualStreamChannel(ch: Channel) {
        try { dualPipPlayer?.stop(); dualPipPlayer?.release() } catch (_: Exception) {}
        dualStreamChannel = ch
        isDualStreamActive = true
        val cleanUrl = ch.url.substringBefore("#")
        val pipDsFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size])
            .setDefaultRequestProperties(CoreEngines.headersForStreamUrl(cleanUrl))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
        val pipMediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(pipDsFactory)
        val dp = SimpleSimpleExoPlayer.Builder(this)
            .setMediaSourceFactory(pipMediaSourceFactory)
            .build().apply {
                volume = 0f
                setMediaItem(MediaItem.fromUri(cleanUrl))
                prepare()
                play()
            }
        dualPlayerView.player = dp
        dualPipPlayer = dp
        dualStreamLabel.text = "2-й: ${ch.name}"
        dualStreamContainer.visibility = View.VISIBLE
        adjustInfoPanelMarginForDualStream()
        updateCenterOsdContent()
    }

    internal fun swapMainAndDualStream() {
        val mainCh = currentChannel ?: return
        val secCh = dualStreamChannel ?: return
        playChannelObj(secCh, immediate = true, silentSwap = true)
        startDualStreamChannel(mainCh)
    }

    internal fun effectiveCategory(ch: Channel): String = customCategoryMap[ch.url] ?: ch.category

    internal var watchdogTickCounter = 0
    internal val timeFmtTicker = SimpleDateFormat("HH:mm", Locale.getDefault())

    internal val watchdogTickerRunnable = object : Runnable {
        override fun run() {
            if (!isWatchdogTickerActive) return
            try {
                watchdogTickCounter++
                if (watchdogTickCounter % 20 == 0) stepPixelOrbiter()

                clockTextView.text = timeFmtTicker.format(Date())
                updateSignalIndicator()

                if (menuHistory.isNotEmpty() && ambilightMode != 0 && alphaAnimator?.isRunning != true) {
                    ambilightHue = (ambilightHue + 8f) % 360f
                    if (ambilightMode == 3 && watchdogTickCounter % 2 == 0) sampleVideoFrameColorIfPossible()
                    refreshPanelBackgroundsOnly()
                }

                val p = player
                if (p != null && currentChannel != null) {
                    val state = p.playbackState
                    val curPos = p.currentPosition
                    val bufMs = (p.bufferedPosition - p.currentPosition).coerceAtLeast(0L)
                    
                    val isTrulyStalled = (state == Player.STATE_IDLE ||
                        (state == Player.STATE_BUFFERING && bufMs < 80L && curPos == lastRenderedFramePositionMs))
                    lastRenderedFramePositionMs = curPos

                    if (isTrulyStalled) {
                        lowSignalStuckSeconds += 2
                        if (lowSignalStuckSeconds >= 22) {
                            lowSignalStuckSeconds = 0
                            consecutiveDropFailures++
                            if (onDropBehavior == 1 && consecutiveDropFailures >= 3) {
                                consecutiveDropFailures = 0
                                switchChannelByOffset(+1)
                            } else {
                                if (antiBlockEnabled) userAgentIndex = (userAgentIndex + 1) % CoreEngines.russianSmartTvAgents.size
                                reloadIconIndicator.visibility = View.VISIBLE
                                uiHandler.postDelayed({ reloadIconIndicator.visibility = View.GONE }, 2200L)
                                reconnectCurrentStreamSilently()
                            }
                        }
                    } else {
                        lowSignalStuckSeconds = 0
                        consecutiveDropFailures = 0
                    }
                }

                if (infoPanel.visibility == View.VISIBLE) updateStreamInfoText()
                if (centerOsdPanel.visibility == View.VISIBLE) updateCenterOsdContent()
            } catch (_: Exception) {}
            if (isWatchdogTickerActive) {
                uiHandler.postDelayed(this, 1500L)
            }
        }
    }

    internal fun startClockAndWatchdogUpdater() {
        if (isWatchdogTickerActive) return
        isWatchdogTickerActive = true
        uiHandler.removeCallbacks(watchdogTickerRunnable)
        uiHandler.post(watchdogTickerRunnable)
    }

    internal fun stopClockAndWatchdogUpdater() {
        isWatchdogTickerActive = false
        uiHandler.removeCallbacks(watchdogTickerRunnable)
    }

    internal fun applyHeadersToDataSourceForUrl(urlStr: String) {
        val activeAgent = CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size]
        val hdrs = CoreEngines.headersForStreamUrl(urlStr)
        httpDataSourceFactory?.setUserAgent(activeAgent)
        httpDataSourceFactory?.setDefaultRequestProperties(hdrs)
    }

    internal fun reconnectCurrentStreamSilently() {
        val ch = currentChannel ?: return
        val p = player ?: return
        try {
            val cleanUrl = ch.url.substringBefore("#")
            val targetUrl = if (silentFallbackAttemptedForUrl != ch.url && !CoreEngines.isSingleSessionTokenUrl(cleanUrl)) {
                silentFallbackAttemptedForUrl = ch.url
                val normTarget = CoreEngines.normalizeName(ch.name)
                val mirror = allChannels.firstOrNull {
                    it.url != ch.url && !autoDisabledUrls.contains(it.url) && CoreEngines.normalizeName(it.name) == normTarget
                }
                mirror?.url?.substringBefore("#") ?: cleanUrl
            } else {
                cleanUrl
            }
            p.stop()
            applyHeadersToDataSourceForUrl(targetUrl)
            p.setMediaItem(createLiveMediaItem(targetUrl), true)
            p.prepare()
            p.play()
        } catch (_: Exception) {}
    }

    internal fun createLiveMediaItem(url: String): MediaItem {
        val cleanUrl = url.substringBefore("#")
        return MediaItem.Builder()
            .setUri(cleanUrl)
            .build()
    }

    internal fun updateSignalIndicator(): Int {
        val p = player
        val state = p?.playbackState ?: Player.STATE_IDLE
        val bufMs = (p?.let { it.bufferedPosition - it.currentPosition } ?: 0L).coerceAtLeast(0L) ?: 0L
        val (activeBars, activeColor) = when {
            state == Player.STATE_IDLE -> Pair(1, Color.parseColor("#FF5252"))
            state == Player.STATE_BUFFERING && bufMs < 150L -> Pair(1, Color.parseColor("#FF5252"))
            state == Player.STATE_BUFFERING -> Pair(2, Color.parseColor("#FFD740"))
            state == Player.STATE_READY && bufMs >= 2000L -> Pair(4, Color.parseColor("#00E676"))
            state == Player.STATE_READY && bufMs >= 600L -> Pair(3, Color.parseColor("#69F0AE"))
            state == Player.STATE_READY -> Pair(2, Color.parseColor("#FFD740"))
            else -> Pair(1, Color.parseColor("#FF5252"))
        }
        val inactiveColor = Color.parseColor("#35FFFFFF")
        for (i in signalBars.indices) {
            val bg = signalBars[i].background as? GradientDrawable ?: GradientDrawable()
            bg.cornerRadius = 3f
            bg.setColor(if (i < activeBars) activeColor else inactiveColor)
            signalBars[i].setBackgroundDrawable(bg)
        }
        return activeBars
    }

    internal fun initPlayer() {
        val currentUrl = currentChannel?.url
        try { loudnessEnhancer?.release(); loudnessEnhancer = null } catch (_: Exception) {}
        try { player?.stop(); player?.release() } catch (_: Exception) {}

        val (minBuf, maxBuf, startBuf, rebuf) = when (bufferLevel) {
            0 -> listOf(6000, 14000, 500, 1000)
            1 -> listOf(14000, 28000, 700, 1400)
            2 -> listOf(18000, 36000, 800, 1600)
            else -> listOf(24000, 45000, 900, 1800)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuf, maxBuf, startBuf, rebuf)
            .setTargetBufferBytes(32 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(15000, false)
            .build()

        val extensionMode = when (decoderMode) {
            0 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            2 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        }
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(extensionMode)
            .setEnableDecoderFallback(true)

        val activeAgent = CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size]
        val dsFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(activeAgent)
            .setDefaultRequestProperties(CoreEngines.headersForStreamUrl(currentUrl ?: "http://tsmxkuov.rostelekom.xyz"))
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
        httpDataSourceFactory = dsFactory

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dsFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val newPlayer = SimpleExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttrs, false)
            .build()

        newPlayer.volume = if (::splashOverlay.isInitialized && splashOverlay.visibility == View.VISIBLE) 0f else 1.0f

        newPlayer.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                updateSignalIndicator()
                if (playbackState == Player.STATE_ENDED) reconnectCurrentStreamSilently()
            }
            override fun onPlayerError(error: ExoPlaybackException) {
                userAgentIndex = (userAgentIndex + 1) % CoreEngines.russianSmartTvAgents.size
                uiHandler.postDelayed({ reconnectCurrentStreamSilently() }, 650L)
            }
            override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
                // track override not needed for stable legacy behavior
            }
        })
        
        newPlayer.addVideoListener(object : com.google.android.exoplayer2.video.VideoListener {
            override fun onRenderedFirstFrame() {
                lowSignalStuckSeconds = 0
                consecutiveDropFailures = 0
                applyAudioEnhancements()
                updateSignalIndicator()
            }
        })


        playerView.player = newPlayer
        player = newPlayer
        applyScreenAspectMode()

        if (currentUrl != null) {
            applyHeadersToDataSourceForUrl(currentUrl)
            newPlayer.setMediaItem(createLiveMediaItem(currentUrl))
            newPlayer.prepare()
            newPlayer.play()
        }
    }

    internal fun preloadNeighboringChannels(activeChannel: Channel) {
        if (isAnalyzerRunning || CoreEngines.isSingleSessionTokenUrl(activeChannel.url)) return
        val snapshot = displayedChannels.toList()
        if (snapshot.size < 2) return
        val idx = snapshot.indexOfFirst { it.url == activeChannel.url }
        if (idx == -1) return
        val neighbors = listOf(snapshot[(idx - 1 + snapshot.size) % snapshot.size], snapshot[(idx + 1) % snapshot.size])
        synchronized(preloadedLock) { preloadedNeighbors.clear() }
        for (neighbor in neighbors) {
            if (CoreEngines.isSingleSessionTokenUrl(neighbor.url)) continue
            try {
                preloaderExecutor.submit {
                    if (isAnalyzerRunning) return@submit
                    try {
                        val cleanUrl = neighbor.url.substringBefore("#")
                        val parsed = URL(cleanUrl)
                        if (dohEnabled) CoreEngines.resolveHostViaCloudflareDoH(parsed.host, true)
                        val hdrs = CoreEngines.headersForStreamUrl(cleanUrl)
                        val conn = (parsed.openConnection() as HttpURLConnection).apply {
                            requestMethod = "HEAD"
                            setRequestProperty("User-Agent", CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size])
                            for ((k, v) in hdrs) setRequestProperty(k, v)
                            connectTimeout = 2200; readTimeout = 2200
                        }
                        conn.responseCode
                        conn.disconnect()
                        synchronized(preloadedLock) { preloadedNeighbors.add(neighbor.url) }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    internal fun getChannelEpgList(ch: Channel?): List<EpgProgram> {
        if (ch == null) return emptyList()
        if (ch.tvgId.isNotEmpty()) {
            val byId = epgById[ch.tvgId] ?: epgById[ch.tvgId.lowercase()]
            if (!byId.isNullOrEmpty()) return byId.toList()
        }
        val norm = CoreEngines.normalizeName(ch.name)
        if (norm.isNotEmpty()) {
            epgByName[norm]?.let { if (it.isNotEmpty()) return it.toList() }
            val withoutHd = norm.removeSuffix("hd").removeSuffix("fhd").removeSuffix("50fps").removeSuffix("50").trim()
            if (withoutHd.isNotEmpty()) {
                epgByName[withoutHd]?.let { if (it.isNotEmpty()) return it.toList() }
            }
        }
        return emptyList()
    }

    internal fun getCurrentAndNextEpg(ch: Channel?): Pair<EpgProgram?, EpgProgram?> {
        val list = getChannelEpgList(ch)
        if (list.isEmpty()) return Pair(null, null)
        val now = System.currentTimeMillis()
        val idx = list.indexOfFirst { now in it.startMs..it.stopMs }
        if (idx == -1) return Pair(null, null)
        return Pair(list[idx], if (idx + 1 < list.size) list[idx + 1] else null)
    }

    internal fun loadEpgOncePerDayInBackground(forceRefresh: Boolean) {
        thread(priority = Thread.MIN_PRIORITY) {
            val cacheFile = File(filesDir, "epg_stream_v27.jsonl")
            val prefs = getSharedPreferences("iptv_v4_prefs", Context.MODE_PRIVATE)
            val lastSyncMs = prefs.getLong("last_epg_sync_v27_ms", 0L)
            val now = System.currentTimeMillis()
            val dayMs = 12 * 3600_000L

            if (!forceRefresh && cacheFile.exists() && (now - lastSyncMs) in 1 until dayMs) {
                val restored = CoreEngines.restoreEpgFromStreamCache(cacheFile, epgByName)
                if (restored > 0) {
                    epgLoadedCount = restored
                    return@thread
                }
            }

            val mergedById = HashMap<String, MutableList<EpgProgram>>()
            val mergedByName = HashMap<String, MutableList<EpgProgram>>()
            var totalProgramsCount = 0

            for (candidateUrl in (listOf(epgUrl) + epgUrls).distinct()) {
                try {
                    val rawStream = CoreEngines.openStreamWithBypass(candidateUrl, userAgentIndex, dohEnabled, 25000)
                    val stream: InputStream = if (candidateUrl.endsWith(".gz", true)) GZIPInputStream(rawStream) else rawStream

                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(stream, "UTF-8")

                    val minTime = now - 2 * 3600_000L; val maxTime = now + 18 * 3600_000L
                    val tempById = HashMap<String, MutableList<EpgProgram>>()
                    val tempIdToNames = HashMap<String, MutableList<String>>()

                    var eventType = parser.eventType
                    var currentChanId = ""; var progChanId = ""
                    var progStart = 0L; var progStop = 0L
                    var progTitle = ""; var currentTag = ""

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                currentTag = parser.name ?: ""
                                if (currentTag == "channel") currentChanId = parser.getAttributeValue(null, "id") ?: ""
                                else if (currentTag == "programme") {
                                    progChanId = parser.getAttributeValue(null, "channel") ?: ""
                                    progStart = CoreEngines.parseXmltvDate(parser.getAttributeValue(null, "start") ?: "")
                                    progStop = CoreEngines.parseXmltvDate(parser.getAttributeValue(null, "stop") ?: "")
                                    progTitle = ""
                                }
                            }
                            XmlPullParser.TEXT -> {
                                val txt = parser.text ?: ""
                                if (txt.isNotBlank()) {
                                    if (currentTag == "display-name" && currentChanId.isNotEmpty()) {
                                        val nKey = CoreEngines.normalizeName(txt)
                                        if (nKey.isNotEmpty()) tempIdToNames.getOrPut(currentChanId) { mutableListOf() }.add(nKey)
                                    } else if (currentTag == "title" && progStop >= minTime && progStart <= maxTime) progTitle += txt
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                val endName = parser.name ?: ""
                                if (endName == "channel") currentChanId = ""
                                else if (endName == "programme") {
                                    if (progChanId.isNotEmpty() && progTitle.isNotEmpty() && progStop >= minTime && progStart <= maxTime) {
                                        tempById.getOrPut(progChanId) { mutableListOf() }.add(EpgProgram(progTitle.trim(), "", progStart, progStop))
                                        totalProgramsCount++
                                    }
                                    progChanId = ""
                                }
                                currentTag = ""
                            }
                        }
                        eventType = parser.next()
                    }

                    for ((id, progs) in tempById) {
                        progs.sortBy { it.startMs }
                        mergedById[id] = progs
                        mergedById[id.lowercase()] = progs
                        val names = tempIdToNames[id] ?: continue
                        for (n in names) if (n.isNotEmpty() && !mergedByName.containsKey(n)) mergedByName[n] = progs
                    }

                    if (mergedByName.isNotEmpty()) {
                        runOnUiThread {
                            epgById.putAll(mergedById); epgByName.putAll(mergedByName)
                            epgLoadedCount = totalProgramsCount
                            if (centerOsdPanel.visibility == View.VISIBLE) updateCenterOsdContent()
                            if (fullEpgOverlay.visibility == View.VISIBLE) openFullScreenChannelEpg()
                        }
                        break
                    }
                } catch (_: Exception) {}
            }

            if (mergedByName.isNotEmpty()) {
                CoreEngines.saveEpgToStreamCache(cacheFile, mergedByName)
                prefs.edit().putLong("last_epg_sync_v27_ms", System.currentTimeMillis()).apply()
            }
        }
    }

    internal fun updateCenterOsdContent() {
        val ch = currentChannel ?: return
        val isFav = favoriteUrls.contains(ch.url)
        val isTop = topUrls.contains(ch.url)
        btnOsdTop.text = if (isTop) "▲ Убрать ТОП" else "▲ В ТОП"
        btnOsdFav.text = if (isFav) "★ В Избр." else "★ Избр."
        btnOsdDualToggle.text = if (isDualStreamActive) "⧉ Выкл 2-й экран" else "⧉ 2-й экран"
        btnOsdNightMode.text = "🌙 $brightnessPercent%"

        val badges = (if (isTop) "▲ " else "") + (if (isFav) "★ " else "")
        osdChannelTitle.text = badges + ch.name + "  •  " + effectiveCategory(ch)

        val (curr, next) = getCurrentAndNextEpg(ch)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()

        if (curr != null) {
            val totalMin = ((curr.stopMs - curr.startMs) / 60000L).coerceAtLeast(1L)
            val passedMin = ((now - curr.startMs) / 60000L).coerceIn(0L, totalMin)
            val leftMin = (totalMin - passedMin).coerceAtLeast(0L)
            val pct = ((now - curr.startMs) * 100L / (curr.stopMs - curr.startMs).coerceAtLeast(1L)).toInt().coerceIn(0, 100)

            osdEpgCurrentTitle.text = "🎬 Сейчас: " + curr.title
            osdEpgTimeLabel.text = timeFmt.format(Date(curr.startMs)) + " — " + timeFmt.format(Date(curr.stopMs)) +
                "   •   Прошло $passedMin мин  (осталось $leftMin мин)"
            osdEpgProgress.progress = pct

            osdEpgNextAndDesc.text = if (next != null) {
                "⏭ Далее в " + timeFmt.format(Date(next.startMs)) + ":  " + next.title
            } else ""
        } else {
            osdEpgCurrentTitle.text = "🎬 Прямой эфир"
            osdEpgTimeLabel.text = effectiveCategory(ch) + "  (▼ Вниз — быстрый выбор из 5 соседних каналов)"
            osdEpgProgress.progress = 100
            osdEpgNextAndDesc.text = ""
        }
    }

    internal fun updateStreamInfoText() {
        val p = player ?: return
        val ch = currentChannel
        val width = 0
        val height = 0
        val fps = ""
        val vCodec = "H.264/HEVC"
        val vBitrate = "Адаптивный"if (vFormat != null && vFormat.bitrate > 0) String.format(Locale.US, "%.2f Мбит/с", vFormat.bitrate / 1_000_000f) else "Адаптивный HLS"

        val bufSec = String.format(Locale.US, "%.1f", ((p.bufferedPosition - p.currentPosition).coerceAtLeast(0L) / 1000f))
        val preloadNote = if (ch != null && CoreEngines.isSingleSessionTokenUrl(ch.url)) {
            "Защита 1-сессионного ключа (активна)"
        } else {
            val preloadedCount = synchronized(preloadedLock) { preloadedNeighbors.size }
            "$preloadedCount из 2 ⚡"
        }

        val (epg, _) = getCurrentAndNextEpg(ch)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val epgLine = if (epg != null) "\n\n🎬 Сейчас (${timeFmt.format(Date(epg.startMs))}–${timeFmt.format(Date(epg.stopMs))}):\n${epg.title}" else ""

        infoTextView.text = "Канал: " + (ch?.name ?: "-") + "\n" +
            "Группа: " + (if (ch != null) effectiveCategory(ch) else "-") + "\n\n" +
            "Разрешение: " + width + "x" + height + " " + fps + " (" + vCodec + ")\n" +
            "Поток: " + vBitrate + "\n" +
            "Буфер: " + bufSec + " сек\n" +
            "Режим линии: " + preloadNote +
            epgLine
    }

    internal fun ensureDefaultPlaylists() {
        playlists.removeAll { it.source == "BUILTIN_VIP_PROVIDER" }
        val hasMain = playlists.any { it.source == "BUILTIN_OPEN_RU" }
        val hasStrictRu = playlists.any { it.source == "BUILTIN_STRICT_RU_ONLY" }
        if (!hasMain) {
            playlists.add(0, PlaylistEntry("1. Основной (RU + Кинозалы + Мир)", "BUILTIN_OPEN_RU", false))
        }
        if (!hasStrictRu) {
            val insertIdx = if (playlists.isNotEmpty()) 1 else 0
            playlists.add(insertIdx, PlaylistEntry("2. Только Русские (Строго RU во всех группах)", "BUILTIN_STRICT_RU_ONLY", false))
        }
        if (activePlaylistIndex !in playlists.indices) activePlaylistIndex = 0
    }

    internal fun isChannelActive(ch: Channel): Boolean {
        if (manualDisabledUrls.contains(ch.url)) return false
        if (disabledCategories.contains(effectiveCategory(ch))) return false
        if (autoDisabledUrls.contains(ch.url)) return false
        return true
    }

    internal fun loadActivePlaylist(autoPlayLast: Boolean) {
        ensureDefaultPlaylists()
        val entry = playlists[activePlaylistIndex]
        val myGen = ++playlistLoadGeneration
        val isBuiltin = entry.source == "BUILTIN_OPEN_RU" || entry.source == "BUILTIN_STRICT_RU_ONLY"
        val strictRuOnly = entry.source == "BUILTIN_STRICT_RU_ONLY"
        val requestedStartupUrl = if (autoPlayLast) lastSavedChannelUrl else ""

        if (isBuiltin) {
            val initialBuiltin = mutableListOf<Channel>()
            val initialKeys = HashSet<String>()
            for (extra in BuiltinCatalog.extraOpenStreams) {
                if (CoreEngines.isArabicOrIndianChannel(extra.name, extra.category, extra.url)) continue
                if (strictRuOnly && !CoreEngines.isStrictlyRussianChannel(extra.name, extra.url, extra.category)) continue
                val cleanTitle = CoreEngines.cleanChannelDisplayName(extra.name)
                if (initialKeys.add(CoreEngines.normalizeName(cleanTitle) + "_" + extra.category + "_" + extra.url.hashCode())) {
                    initialBuiltin.add(Channel(cleanTitle, extra.url, customCategoryMap[extra.url] ?: extra.category, extra.tvgId))
                }
            }
            allChannels.clear()
            allChannels.addAll(initialBuiltin)
            dynamicCategories.clear()
            updateCategoriesList()
            val currentList = allChannels.filter { isChannelActive(it) }
            val cats = activeCategoryList()
            val matchedInBuiltin = if (requestedStartupUrl.isNotEmpty()) currentList.firstOrNull { it.url == requestedStartupUrl } else null

            if (currentList.isNotEmpty()) {
                val targetCh = matchedInBuiltin ?: currentList[0]
                selectCategory(cats.indexOf(effectiveCategory(targetCh)).takeIf { it >= 2 } ?: 1)
                openChannelsDirectlyWithCurrentFocus = true
                playChannelObj(targetCh, immediate = true, updateSavedUrl = (matchedInBuiltin != null || requestedStartupUrl.isEmpty()))
            } else {
                selectCategory(1)
            }

            thread(priority = Thread.MIN_PRIORITY) {
                val mergedMap = ConcurrentHashMap<String, Channel>()
                val loaderPool = Executors.newFixedThreadPool(4)
                for (srcUrl in openRusSources) {
                    loaderPool.submit {
                        if (myGen != playlistLoadGeneration) return@submit
                        try {
                            val localMap = LinkedHashMap<String, Channel>()
                            val reader = CoreEngines.openStreamWithBypass(srcUrl, userAgentIndex, dohEnabled, 5500).bufferedReader()
                            CoreEngines.parseM3uReaderIntoMap(
                                reader = reader,
                                targetMap = localMap,
                                customCategoryMap = customCategoryMap,
                                filterBuiltinRules = true,
                                onlyRussianAllCategories = strictRuOnly,
                                keepOriginalGroups = false
                            )
                            for ((k, v) in localMap) mergedMap.putIfAbsent(k, v)
                        } catch (_: Exception) {}
                    }
                }
                loaderPool.shutdown()
                try { loaderPool.awaitTermination(14, TimeUnit.SECONDS) } catch (_: Exception) {}
                loaderPool.shutdownNow()
                if (myGen != playlistLoadGeneration || mergedMap.isEmpty()) return@thread

                val combined = ArrayList<Channel>(initialBuiltin)
                for (ch in mergedMap.values) {
                    val key = CoreEngines.normalizeName(ch.name) + "_" + ch.category + "_" + ch.url.substringBefore("?").hashCode()
                    if (initialKeys.add(key)) combined.add(ch)
                }
                runOnUiThread {
                    if (myGen != playlistLoadGeneration) return@runOnUiThread
                    allChannels.clear()
                    allChannels.addAll(combined)
                    updateCategoriesList()

                    if (matchedInBuiltin == null && requestedStartupUrl.isNotEmpty()) {
                        val lateMatch = allChannels.firstOrNull { isChannelActive(it) && it.url == requestedStartupUrl }
                        if (lateMatch != null) {
                            val lateCatIdx = activeCategoryList().indexOf(effectiveCategory(lateMatch)).takeIf { it >= 2 } ?: 1
                            selectCategory(lateCatIdx)
                            playChannelObj(lateMatch, immediate = true, updateSavedUrl = true)
                            return@runOnUiThread
                        }
                    }
                    if (sidePanel.visibility != View.VISIBLE) {
                        selectCategory(currentCategoryIndex)
                    }
                }
            }
            return
        }

        thread {
            try {
                val targetMap = LinkedHashMap<String, Channel>()
                val reader = if (entry.isLocal) {
                    BufferedReader(FileReader(File(entry.source)))
                } else {
                    CoreEngines.openStreamWithBypass(entry.source, userAgentIndex, dohEnabled, 10000).bufferedReader()
                }
                CoreEngines.parseM3uReaderIntoMap(
                    reader = reader,
                    targetMap = targetMap,
                    customCategoryMap = customCategoryMap,
                    filterBuiltinRules = false,
                    onlyRussianAllCategories = false,
                    keepOriginalGroups = true
                )
                val finalChannels = targetMap.values.toList()
                if (myGen != playlistLoadGeneration) return@thread
                runOnUiThread {
                    allChannels.clear()
                    allChannels.addAll(finalChannels)
                    dynamicCategories.clear()
                    val customGroups = finalChannels.map { effectiveCategory(it) }.filter { it.isNotEmpty() }.distinct()
                    if (customGroups.isNotEmpty()) {
                        dynamicCategories.add("★ Избранное")
                        dynamicCategories.add("Все каналы")
                        dynamicCategories.addAll(customGroups)
                    }
                    updateCategoriesList()
                    val currentList = allChannels.filter { isChannelActive(it) }
                    val cats = activeCategoryList()
                    if (currentList.isNotEmpty()) {
                        val targetCh = (if (requestedStartupUrl.isNotEmpty()) currentList.firstOrNull { it.url == requestedStartupUrl } else null) ?: currentList[0]
                        selectCategory(cats.indexOf(effectiveCategory(targetCh)).takeIf { it >= 2 } ?: 1)
                        openChannelsDirectlyWithCurrentFocus = true
                        playChannelObj(targetCh, immediate = true, updateSavedUrl = true)
                    } else selectCategory(1)
                }
            } catch (_: Exception) {}
        }
    }

    internal fun updateCategoriesList() {
        if (!::categoryAdapter.isInitialized) return
        val activeAll = allChannels.filter { isChannelActive(it) }
        val cats = activeCategoryList()
        categoryDisplayItems.clear()
        categoryDisplayItems.addAll(cats.map { cat ->
            val count = when (cat) {
                "★ Избранное" -> activeAll.count { favoriteUrls.contains(it.url) }
                "Все каналы" -> activeAll.size
                else -> activeAll.count { effectiveCategory(it) == cat }
            }
            val mono = extractTwoLetters(cat)
            "[B:$mono] $cat ($count)"
        })
        categoryAdapter.notifyDataSetChanged()
    }

    internal fun charAlphabetGroup(name: String): Int {
        val firstChar = name.trim().firstOrNull { it.isLetterOrDigit() } ?: return 3
        return when {
            firstChar in 'а'..'я' || firstChar in 'А'..'Я' || firstChar == 'ё' || firstChar == 'Ё' -> 0
            firstChar in 'a'..'z' || firstChar in 'A'..'Z' -> 1
            else -> 2
        }
    }

    internal fun selectCategory(index: Int) {
        val cats = activeCategoryList()
        if (index !in cats.indices) return
        currentCategoryIndex = index; focusedCategoryPos = index
        val catName = cats[index]
        val activeAll = allChannels.filter { isChannelActive(it) }
        val rawFiltered = when (catName) {
            "★ Избранное" -> activeAll.filter { favoriteUrls.contains(it.url) }
            "Все каналы" -> activeAll
            else -> activeAll.filter { effectiveCategory(it) == catName }
        }
        val comparator = Comparator<Channel> { a, b ->
            val gA = charAlphabetGroup(a.name)
            val gB = charAlphabetGroup(b.name)
            if (gA != gB) return@Comparator gA.compareTo(gB)
            ruCollator.compare(a.name, b.name)
        }
        displayedChannels.clear()
        displayedChannels.addAll(rawFiltered.filter { topUrls.contains(it.url) }.sortedWith(comparator))
        displayedChannels.addAll(rawFiltered.filter { !topUrls.contains(it.url) }.sortedWith(comparator))

        val activePosInGroup = displayedChannels.indexOfFirst { it.url == currentChannel?.url }
        focusedChannelPos = if (activePosInGroup >= 0) activePosInGroup else 0
        refreshChannelAdapter()
    }

    internal fun refreshChannelAdapter() {
        if (!::channelListAdapter.isInitialized) return
        val list = displayedChannels.toList()
        channelDisplayItems.clear()
        if (list.isEmpty()) {
            channelDisplayItems.add("[-]   В этой категории пока нет каналов")
        } else {
            channelDisplayItems.addAll(list.map { ch ->
                val mono = extractTwoLetters(ch.name)
                val isPlaying = (ch.url == currentChannel?.url)
                val playOrb = if (isPlaying) "[+] " else ""
                val topBadge = if (topUrls.contains(ch.url)) "▲ " else ""
                val favBadge = if (favoriteUrls.contains(ch.url)) "★ " else ""
                "[B:$mono] $playOrb$topBadge$favBadge${ch.name}"
            })
        }
        channelListAdapter.notifyDataSetChanged()
        if (channelListView.visibility == View.VISIBLE) {
            refreshSimpleListHighlightInPlace(channelListView, focusedChannelPos)
        }
    }

    internal fun toggleTopChannel(ch: Channel) {
        if (!topUrls.remove(ch.url)) topUrls.add(ch.url)
        saveState(); selectCategory(currentCategoryIndex)
    }

    internal fun toggleFavorite(ch: Channel) {
        if (!favoriteUrls.remove(ch.url)) favoriteUrls.add(ch.url)
        saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
    }

    internal val executeDebouncedPlayRunnable = Runnable {
        val target = pendingDebouncedChannel ?: return@Runnable
        try {
            player?.stop()
            applyHeadersToDataSourceForUrl(target.url)
            player?.setMediaItem(createLiveMediaItem(target.url), true)
            player?.prepare()
            player?.play()
        } catch (_: Exception) {}
        uiHandler.removeCallbacks(preloadNeighborsRunnable)
        if (!isAnalyzerRunning) uiHandler.postDelayed(preloadNeighborsRunnable, 2200L)
    }

    internal fun playChannelObj(channel: Channel, immediate: Boolean = false, silentSwap: Boolean = false, updateSavedUrl: Boolean = true) {
        val prevPlayingUrl = currentChannel?.url
        if (!silentSwap && currentChannel != null && prevPlayingUrl != channel.url) {
            previousChannel = currentChannel
        }
        currentChannel = channel
        pendingDebouncedChannel = channel
        if (updateSavedUrl) {
            lastSavedChannelUrl = channel.url
            saveLastChannelOnly(syncCommit = true)
        }
        silentFallbackAttemptedForUrl = ""
        lowSignalStuckSeconds = 0
        consecutiveDropFailures = 0

        refreshChannelAdapter()

        uiHandler.removeCallbacks(executeDebouncedPlayRunnable)
        if (immediate) {
            executeDebouncedPlayRunnable.run()
        } else {
            uiHandler.postDelayed(executeDebouncedPlayRunnable, 80L)
        }

        val topBadge = if (topUrls.contains(channel.url)) "▲ " else ""
        val star = if (favoriteUrls.contains(channel.url)) "★ " else ""
        val (epg, _) = getCurrentAndNextEpg(channel)
        val epgLine = if (epg != null) "\n🎬 " + epg.title else ""

        if (!silentSwap && menuHistory.isEmpty() && showChannelBannerOnSwitch && splashOverlay.visibility != View.VISIBLE) {
            showBanner(topBadge + star + channel.name + "  •  " + effectiveCategory(channel) + epgLine)
        } else {
            currentChannelBanner.visibility = View.GONE
        }

        updateSignalIndicator()
        if (centerOsdPanel.visibility == View.VISIBLE) updateCenterOsdContent()
        if (infoPanel.visibility == View.VISIBLE) updateStreamInfoText()
        if (fullEpgOverlay.visibility == View.VISIBLE) openFullScreenChannelEpg()
    }

    internal val preloadNeighborsRunnable = Runnable { currentChannel?.let { preloadNeighboringChannels(it) } }

    internal fun showBanner(text: String) {
        if (menuHistory.isNotEmpty() || !showChannelBannerOnSwitch || splashOverlay.visibility == View.VISIBLE) {
            currentChannelBanner.visibility = View.GONE
            return
        }
        currentChannelBanner.removeCallbacks(hideBannerRunnable)
        currentChannelBanner.animate().cancel()
        currentChannelBanner.text = text
        currentChannelBanner.setTextColor(currentTheme().textPrimaryColor)
        currentChannelBanner.setBackgroundDrawable(makeThemedPanelBg())
        currentChannelBanner.alpha = 1f
        currentChannelBanner.scaleX = 1f
        currentChannelBanner.scaleY = 1f
        currentChannelBanner.translationX = 0f
        currentChannelBanner.translationY = 14f
        currentChannelBanner.visibility = View.VISIBLE
        currentChannelBanner.animate()
            .translationY(0f)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        currentChannelBanner.postDelayed(hideBannerRunnable, (menuHideDelaySec * 500L).coerceIn(2800L, 6000L))
    }

    internal val hideBannerRunnable = Runnable {
        currentChannelBanner.animate().alpha(0f).setDuration(220L).withEndAction {
            currentChannelBanner.visibility = View.GONE
            currentChannelBanner.alpha = 1f
        }.start()
    }

    internal fun saveLastChannelOnly(syncCommit: Boolean = false) {
        if (lastSavedChannelUrl.isEmpty()) return
        val editor = getSharedPreferences("iptv_v4_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_channel_url", lastSavedChannelUrl)
        if (syncCommit) editor.commit() else editor.apply()
    }

    internal fun saveState(syncCommit: Boolean = false) {
        val prefs = getSharedPreferences("iptv_v4_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (p in playlists) {
            if (p.source == "BUILTIN_VIP_PROVIDER") continue
            arr.put(JSONObject().apply { put("title", p.title); put("source", p.source); put("isLocal", p.isLocal) })
        }
        val customCatJson = JSONObject()
        for ((u, c) in customCategoryMap) customCatJson.put(u, c)
        val editor = prefs.edit()
            .putString("playlists_json", arr.toString())
            .putString("custom_cats_json", customCatJson.toString())
            .putInt("active_playlist", activePlaylistIndex)
            .putInt("buffer_level_v33", bufferLevel)
            .putInt("decoder_mode", decoderMode)
            .putInt("theme_index", themeIndex.coerceIn(0, 3))
            .putInt("ui_scale_step_v27", uiScaleStep)
            .putInt("ui_alpha_step_v27", uiAlphaStep)
            .putBoolean("cutout_offset_v31", cameraCutoutSafeOffset)
            .putInt("control_mode_v13", controlMode)
            .putInt("screen_aspect_v15", screenAspectMode)
            .putInt("brightness_pct_v18", brightnessPercent)
            .putInt("ambilight_mode_v23", ambilightMode)
            .putBoolean("afr_enabled_v23", afrEnabled)
            .putBoolean("pixel_orbiter_v23", pixelOrbiterEnabled)
            .putBoolean("drc_night_v23", drcNightAudioEnabled)
            .putInt("audio_boost_v23", audioBoostPercent)
            .putBoolean("doh_enabled_v23", dohEnabled)
            .putInt("on_drop_v23", onDropBehavior)
            .putInt("menu_hide_sec_v23", menuHideDelaySec)
            .putBoolean("show_banner_v32", showChannelBannerOnSwitch)
            .putBoolean("anti_block_ru", antiBlockEnabled)
            .putInt("ua_index", userAgentIndex)
            .putString("epg_url", epgUrl)
            .putString("last_channel_url", lastSavedChannelUrl)
            .putStringSet("favorites", favoriteUrls.toSet())
            .putStringSet("top_urls", topUrls.toSet())
            .putStringSet("manual_disabled_urls_v19", manualDisabledUrls.toSet())
            .putStringSet("auto_disabled_urls_v33", autoDisabledUrls.toSet())
            .putStringSet("disabled_cats", disabledCategories.toSet())
        if (syncCommit) editor.commit() else editor.apply()
    }

    internal fun loadSavedState() {
        val prefs = getSharedPreferences("iptv_v4_prefs", Context.MODE_PRIVATE)
        bufferLevel = prefs.getInt("buffer_level_v33", 1)
        decoderMode = prefs.getInt("decoder_mode", 1)
        themeIndex = prefs.getInt("theme_index", 0).coerceIn(0, 3)
        controlMode = prefs.getInt("control_mode_v13", 0)
        uiScaleStep = prefs.getInt("ui_scale_step_v27", if (controlMode == 1) 0 else 1)
        uiAlphaStep = prefs.getInt("ui_alpha_step_v27", 1)
        cameraCutoutSafeOffset = prefs.getBoolean("cutout_offset_v31", controlMode == 1)
        screenAspectMode = prefs.getInt("screen_aspect_v15", 0)
        brightnessPercent = prefs.getInt("brightness_pct_v18", 100)
        ambilightMode = prefs.getInt("ambilight_mode_v23", 2)
        afrEnabled = prefs.getBoolean("afr_enabled_v23", false)
        pixelOrbiterEnabled = prefs.getBoolean("pixel_orbiter_v23", true)
        drcNightAudioEnabled = prefs.getBoolean("drc_night_v23", false)
        audioBoostPercent = prefs.getInt("audio_boost_v23", 100)
        dohEnabled = prefs.getBoolean("doh_enabled_v23", true)
        onDropBehavior = prefs.getInt("on_drop_v23", 0)
        menuHideDelaySec = prefs.getInt("menu_hide_sec_v23", 8)
        showChannelBannerOnSwitch = prefs.getBoolean("show_banner_v32", true)
        antiBlockEnabled = prefs.getBoolean("anti_block_ru", true)
        userAgentIndex = prefs.getInt("ua_index", 0)
        activePlaylistIndex = prefs.getInt("active_playlist", 0)
        epgUrl = prefs.getString("epg_url", epgUrl) ?: epgUrl
        lastSavedChannelUrl = prefs.getString("last_channel_url", "") ?: ""
        if (lastSavedChannelUrl.contains("sc.id-tv.kz") || lastSavedChannelUrl.contains("ott-cdn.ucom.am")) {
            lastSavedChannelUrl = ""
        }

        val cleanFilter: (String) -> Boolean = { !it.contains("sc.id-tv.kz") && !it.contains("ott-cdn.ucom.am") }
        favoriteUrls.clear(); favoriteUrls.addAll((prefs.getStringSet("favorites", emptySet()) ?: emptySet()).filter(cleanFilter))
        topUrls.clear(); topUrls.addAll((prefs.getStringSet("top_urls", emptySet()) ?: emptySet()).filter(cleanFilter))
        manualDisabledUrls.clear(); manualDisabledUrls.addAll(prefs.getStringSet("manual_disabled_urls_v19", emptySet()) ?: emptySet())
        autoDisabledUrls.clear(); autoDisabledUrls.addAll(prefs.getStringSet("auto_disabled_urls_v33", emptySet()) ?: emptySet())
        disabledCategories.clear(); disabledCategories.addAll(prefs.getStringSet("disabled_cats", emptySet()) ?: emptySet())

        customCategoryMap.clear()
        prefs.getString("custom_cats_json", null)?.let { str ->
            try {
                val o = JSONObject(str)
                for (k in o.keys()) customCategoryMap[k] = o.getString(k)
            } catch (_: Exception) {}
        }
        playlists.clear()
        prefs.getString("playlists_json", null)?.let { jsonStr ->
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val src = o.getString("source")
                    if (src != "BUILTIN_VIP_PROVIDER") playlists.add(PlaylistEntry(o.getString("title"), src, o.getBoolean("isLocal")))
                }
            } catch (_: Exception) {}
        }
        ensureDefaultPlaylists()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && menuHistory.isNotEmpty()) resetMenuInactivityTimer()
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && menuHistory.isNotEmpty()) resetMenuInactivityTimer()
        if (::gestureDetector.isInitialized && gestureDetector.onTouchEvent(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    internal fun switchChannelByOffset(offset: Int) {
        val list = displayedChannels.toList()
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.url == currentChannel?.url }
        val targetIdx = if (idx == -1) 0 else (idx + offset + list.size) % list.size
        focusedChannelPos = targetIdx
        playChannelObj(list[targetIdx], immediate = false)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && menuHistory.isEmpty()) {
            centerLongPressHandled = true
            navigateToMenu("SETTINGS")
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && menuHistory.isEmpty()) {
            if (!centerLongPressHandled) navigateToMenu("OSD")
            centerLongPressHandled = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (navigateBackInMenu()) return true
            val now = System.currentTimeMillis()
            if (now - lastBackTapMs < 550L) {
                uiHandler.removeCallbacks(singleBackTapRunnable)
                lastBackTapMs = 0L
                if (isDualStreamActive) {
                    swapMainAndDualStream()
                } else {
                    val prev = previousChannel
                    if (prev != null) playChannelObj(prev, immediate = true)
                }
            } else {
                lastBackTapMs = now
                uiHandler.postDelayed(singleBackTapRunnable, 560L)
            }
            return true
        }

        if (firstRunModeOverlay.visibility == View.VISIBLE) return super.onKeyDown(keyCode, event)

        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP) { switchChannelByOffset(+1); return true }
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) { switchChannelByOffset(-1); return true }

        val activeMenu = menuHistory.lastOrNull()
        if (activeMenu == null || activeMenu == "INFO" || activeMenu == "OSD") {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { switchChannelByOffset(+1); return true }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && activeMenu != "OSD") { switchChannelByOffset(-1); return true }
        }

        if (activeMenu != null && activeMenu != "INFO") return super.onKeyDown(keyCode, event)

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { navigateToMenu("SIDE"); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (activeMenu == "INFO") navigateBackInMenu() else navigateToMenu("INFO")
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event?.repeatCount == 0) {
                    centerLongPressHandled = false
                    event.startTracking()
                } else if ((event?.repeatCount ?: 0) >= 3 && !centerLongPressHandled) {
                    centerLongPressHandled = true
                    navigateToMenu("SETTINGS")
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        startClockAndWatchdogUpdater()
    }

    override fun onResume() {
        super.onResume()
        enableTrueFullScreenImmersion()
        startClockAndWatchdogUpdater()
        if (player == null) initPlayer() else player?.play()
        if (allChannels.isEmpty()) loadActivePlaylist(autoPlayLast = true)
    }

    override fun onStop() {
        super.onStop()
        saveLastChannelOnly(syncCommit = true)
        stopClockAndWatchdogUpdater()
        player?.pause()
        dualPipPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveLastChannelOnly(syncCommit = true)
        isAnalyzerRunning = false
        stopClockAndWatchdogUpdater()
        alphaAnimator?.cancel()
        carouselScrollAnimator?.cancel()
        preloaderExecutor.shutdownNow()
        uiHandler.removeCallbacksAndMessages(null)
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        try { dualPipPlayer?.stop(); dualPipPlayer?.release() } catch (_: Exception) {}
        try { player?.stop(); player?.release() } catch (_: Exception) {}
    }
}
