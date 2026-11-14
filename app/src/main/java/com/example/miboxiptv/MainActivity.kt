package com.example.miboxiptv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread
import kotlin.math.abs

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var dualPipPlayer: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private lateinit var playerContainerFrame: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var dualStreamContainer: FrameLayout
    private lateinit var dualPlayerView: PlayerView
    private lateinit var dualStreamLabel: TextView
    private lateinit var dualCloseBtn: TextView
    private lateinit var morphTransitionOverlay: FrameLayout
    private lateinit var nightDimOverlay: View
    private lateinit var categoryListView: ListView
    private lateinit var channelRecyclerView: RecyclerView
    private lateinit var channelLayoutManager: LinearLayoutManager
    private lateinit var channelRvAdapter: ChannelRvAdapter

    private lateinit var sidePanel: LinearLayout
    private lateinit var leftIconRail: LinearLayout
    private lateinit var infoPanel: LinearLayout
    private lateinit var centerOsdPanel: LinearLayout
    private lateinit var zappingRowContainer: LinearLayout
    private lateinit var settingsTileOverlay: LinearLayout
    private lateinit var gridContainerLayout: LinearLayout
    private lateinit var carouselScrollView: HorizontalScrollView
    private lateinit var carouselRowLayout: LinearLayout
    private lateinit var subModalOverlay: LinearLayout
    private lateinit var fullEpgOverlay: LinearLayout
    private lateinit var firstRunModeOverlay: FrameLayout
    private lateinit var splashOverlay: FrameLayout
    private lateinit var splashEyeView: RealisticEyeSplashView

    private val allGridTiles = mutableListOf<ThemedTileView>()
    private val allGridCaptions = mutableListOf<TextView>()
    private val carouselCells = mutableListOf<LinearLayout>()
    private val menuHistory = mutableListOf<String>()

    private var isSideShowingChannels = true
    private var isLeftRailVisible = false
    private var openChannelsDirectlyWithCurrentFocus = true
    private var focusedCategoryPos = 1
    private var focusedChannelPos = 0
    private var centerLongPressHandled = false

    private var brightnessPercent = 100
    private var sleepTimerMinutesLeft = 0
    private var sleepTimerRunnable: Runnable? = null

    private var uiScaleStep = 1
    private var uiAlphaStep = 1

    private var ambilightMode = 2
    private var ambilightHue = 195f
    private var capturedFrameColor: Int = Color.parseColor("#00B0FF")

    private var afrEnabled = false
    private var pixelOrbiterEnabled = true
    private var orbiterStep = 0
    private var drcNightAudioEnabled = false
    private var audioBoostPercent = 100
    private var dohEnabled = true
    private var onDropBehavior = 0
    private var menuHideDelaySec = 8
    private var showChannelBannerOnSwitch = true
    private var isDualStreamActive = false
    private var dualStreamChannel: Channel? = null

    private var previousChannel: Channel? = null
    private var lastBackTapMs = 0L
    private val singleBackTapRunnable = Runnable {
        if (!isDualStreamActive) {
            Toast.makeText(this, "Нажмите «Назад» дважды для возврата на прошлый канал", Toast.LENGTH_SHORT).show()
        }
    }

    private var controlMode = 0
    private lateinit var gestureDetector: GestureDetector

    private var antiBlockEnabled = true
    private var userAgentIndex = 0

    private lateinit var infoTextView: TextView
    private lateinit var clockTextView: TextView
    private lateinit var analyzerStatusBanner: TextView
    private lateinit var signalContainer: LinearLayout
    private lateinit var reloadIconIndicator: TextView
    private val signalBars = mutableListOf<View>()

    private lateinit var railTilePlaylists: ThemedTileView
    private lateinit var railTileSettings: ThemedTileView
    private lateinit var railTileAnalyzer: ThemedTileView
    private lateinit var railTileSwapGroups: ThemedTileView

    private lateinit var osdChannelTitle: TextView
    private lateinit var osdEpgCurrentTitle: TextView
    private lateinit var osdEpgTimeLabel: TextView
    private lateinit var osdEpgProgress: ProgressBar
    private lateinit var osdEpgNextAndDesc: TextView
    private lateinit var btnOsdFav: TextView
    private lateinit var btnOsdTop: TextView
    private lateinit var btnOsdDualToggle: TextView
    private lateinit var btnOsdRewind: TextView
    private lateinit var btnOsdEpgList: TextView
    private lateinit var btnOsdSettings: TextView
    private lateinit var btnOsdNightMode: TextView
    private lateinit var btnOsdChannelActions: TextView
    private lateinit var btnOsdPowerRedCircle: RedPowerIconButton

    private lateinit var fullEpgTitle: TextView
    private lateinit var fullEpgListView: ListView

    private lateinit var settingsSelectedLabel: TextView
    private lateinit var subModalTitle: TextView
    private lateinit var subModalTopButtons: LinearLayout
    private lateinit var subModalList: ListView
    private lateinit var subModalInputContainer: LinearLayout
    private lateinit var subModalAdapter: ArrayAdapter<String>
    private val subModalItems = mutableListOf<String>()

    private lateinit var currentChannelBanner: TextView

    private val uiHandler = Handler(Looper.getMainLooper())
    private val allChannels = CopyOnWriteArrayList<Channel>()
    private val displayedChannels = CopyOnWriteArrayList<Channel>()
    private val dynamicCategories = CopyOnWriteArrayList<String>()
    private val favoriteUrls = ConcurrentHashMap.newKeySet<String>()
    private val topUrls = ConcurrentHashMap.newKeySet<String>()
    private val manualDisabledUrls = ConcurrentHashMap.newKeySet<String>()
    private val autoDisabledUrls = ConcurrentHashMap.newKeySet<String>()
    private val disabledCategories = ConcurrentHashMap.newKeySet<String>()
    private val customCategoryMap = ConcurrentHashMap<String, String>()
    private val playlists = CopyOnWriteArrayList<PlaylistEntry>()

    private val channelHealthMap = ConcurrentHashMap<String, Boolean>()
    private val aliveChannelsAtomic = AtomicInteger(0)
    private val deadChannelsAtomic = AtomicInteger(0)
    @Volatile private var isAnalyzerRunning = false
    @Volatile private var playlistLoadGeneration = 0
    private var selectedAnalyzerCategory = "Все каналы"

    private val preloadedLock = Any()
    private val preloadedNeighbors = HashSet<String>()
    private val preloaderExecutor = Executors.newFixedThreadPool(2)

    private val epgById = ConcurrentHashMap<String, MutableList<EpgProgram>>()
    private val epgByName = ConcurrentHashMap<String, MutableList<EpgProgram>>()
    @Volatile private var epgLoadedCount = 0

    private lateinit var categoryAdapter: ArrayAdapter<String>

    private var currentCategoryIndex = 1
    private var currentChannel: Channel? = null
    private var pendingDebouncedChannel: Channel? = null
    private var lastSavedChannelUrl: String = ""
    private var activePlaylistIndex = 0
    private var bufferLevel = 2
    private var decoderMode = 1
    private var themeIndex = 0
    private var screenAspectMode = 0

    private var lowSignalStuckSeconds = 0
    private var consecutiveDropFailures = 0
    private var lastRenderedFramePositionMs = -1L

    private var currentBrowseDir: File = Environment.getExternalStorageDirectory()

    private val ruCollator: Collator = Collator.getInstance(Locale("ru", "RU")).apply {
        strength = Collator.PRIMARY
    }

    private val themes = listOf(
        AppThemeStyle(0, "Material Design 3", Color.parseColor("#EE1E1F22"), Color.parseColor("#FF2B2D30"), Color.parseColor("#FFA8C7FA"), Color.parseColor("#66A8C7FA"), Color.WHITE, Color.parseColor("#FF062E6F"), 28f, Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.create("sans-serif-medium", Typeface.BOLD)),
        AppThemeStyle(1, "Cyber Neon Glass", Color.parseColor("#DF0B0914"), Color.parseColor("#FF231F38"), Color.parseColor("#FF00E5FF"), Color.parseColor("#6600E5FF"), Color.WHITE, Color.parseColor("#FF0B0914"), 20f, Typeface.MONOSPACE, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)),
        AppThemeStyle(2, "AMOLED Pure Black", Color.parseColor("#F6000000"), Color.parseColor("#FF121212"), Color.parseColor("#FF00E676"), Color.parseColor("#6600E676"), Color.WHITE, Color.parseColor("#FF000000"), 16f, Typeface.create("sans-serif-condensed", Typeface.NORMAL), Typeface.create("sans-serif-condensed", Typeface.BOLD)),
        AppThemeStyle(3, "Nordic Frost", Color.parseColor("#EC1B222D"), Color.parseColor("#FF2E3440"), Color.parseColor("#FF88C0D0"), Color.parseColor("#6688C0D0"), Color.WHITE, Color.parseColor("#FF1B222D"), 24f, Typeface.create("sans-serif-light", Typeface.NORMAL), Typeface.create("sans-serif", Typeface.BOLD)),
        AppThemeStyle(4, "Sunset Warm Retro", Color.parseColor("#EE1F130E"), Color.parseColor("#FF36221A"), Color.parseColor("#FFFFB300"), Color.parseColor("#66FFB300"), Color.WHITE, Color.parseColor("#FF1F130E"), 24f, Typeface.SERIF, Typeface.create(Typeface.SERIF, Typeface.BOLD)),
        AppThemeStyle(5, "Graphite Monochrome", Color.parseColor("#F218181B"), Color.parseColor("#FF27272A"), Color.parseColor("#FFD4D4D8"), Color.parseColor("#66A1A1AA"), Color.parseColor("#FFF4F4F5"), Color.parseColor("#FF18181B"), 18f, Typeface.create("sans-serif-condensed", Typeface.NORMAL), Typeface.create("sans-serif-condensed", Typeface.BOLD)),
        AppThemeStyle(6, "Nordic Daylight Светлая", Color.parseColor("#F5F8FAFC"), Color.parseColor("#FFE2E8F0"), Color.parseColor("#FF2563EB"), Color.parseColor("#9994A3B8"), Color.parseColor("#FF0F172A"), Color.WHITE, 26f, Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.create("sans-serif-medium", Typeface.BOLD)),
        AppThemeStyle(7, "Retrowave 80s Neon", Color.parseColor("#EE160424"), Color.parseColor("#FF2A0944"), Color.parseColor("#FFFF2A85"), Color.parseColor("#9900F0FF"), Color.WHITE, Color.WHITE, 22f, Typeface.create("sans-serif-condensed", Typeface.ITALIC), Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)),
        AppThemeStyle(8, "Matrix Cyber Terminal", Color.parseColor("#F2030D03"), Color.parseColor("#FF071F07"), Color.parseColor("#FF00FF66"), Color.parseColor("#8800FF66"), Color.parseColor("#FFD0FFD6"), Color.parseColor("#FF021002"), 12f, Typeface.MONOSPACE, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)),
        AppThemeStyle(9, "Royal Gold Luxury", Color.parseColor("#F2120E07"), Color.parseColor("#FF241B0D"), Color.parseColor("#FFFFD700"), Color.parseColor("#88FFD700"), Color.parseColor("#FFFFF8DC"), Color.parseColor("#FF120E07"), 24f, Typeface.SERIF, Typeface.create(Typeface.SERIF, Typeface.BOLD)),
        AppThemeStyle(10, "Carousel 3D Карусель", Color.parseColor("#EE101420"), Color.parseColor("#FF1E2638"), Color.parseColor("#FF38BDF8"), Color.parseColor("#8838BDF8"), Color.WHITE, Color.parseColor("#FF081018"), 30f, Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.create("sans-serif-medium", Typeface.BOLD), isCarouselMenu = true)
    )

    private fun currentTheme(): AppThemeStyle = themes[themeIndex.coerceIn(0, themes.lastIndex)]
    private fun themeFont(): Typeface = currentTheme().normalFont
    private fun themeBoldFont(): Typeface = currentTheme().boldFont

    private fun uiScaleFactor(): Float = when (uiScaleStep) {
        0 -> 1.0f
        1 -> 1.18f
        else -> 1.36f
    }

    private fun uiPanelAlphaInt(): Int = when (uiAlphaStep) {
        0 -> 244
        1 -> 214
        else -> 172
    }

    private val defaultTenCategories = listOf(
        "★ Избранное", "Все каналы", "2K", "Кино и сериалы", "Спорт",
        "Детские", "Познавательные", "Музыка", "Новости и общие", "Развлечения", "Прочее"
    )

    private fun activeCategoryList(): List<String> {
        return if (dynamicCategories.isNotEmpty()) dynamicCategories else defaultTenCategories
    }

    private val openRusSources = listOf(
        "https://iptv-org.github.io/iptv/languages/rus.m3u",
        "https://iptv-org.github.io/iptv/countries/ru.m3u",
        "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_russia.m3u8",
        "https://raw.githubusercontent.com/smolnp/iptvru/gh-pages/playlist.m3u8",
        "https://raw.githubusercontent.com/ipstreet312/freeiptv/master/all.m3u",
        "https://iptv-org.github.io/iptv/countries/by.m3u",
        "https://iptv-org.github.io/iptv/countries/kz.m3u",
        "https://iptv-org.github.io/iptv/countries/am.m3u",
        "https://iptv-org.github.io/iptv/categories/movies.m3u",
        "https://iptv-org.github.io/iptv/categories/series.m3u",
        "https://iptv-org.github.io/iptv/categories/comedy.m3u",
        "https://iptv-org.github.io/iptv/categories/animation.m3u",
        "https://iptv-org.github.io/iptv/categories/family.m3u",
        "https://iptv-org.github.io/iptv/categories/kids.m3u",
        "https://iptv-org.github.io/iptv/categories/sports.m3u",
        "https://iptv-org.github.io/iptv/categories/science.m3u",
        "https://iptv-org.github.io/iptv/categories/documentary.m3u",
        "https://iptv-org.github.io/iptv/categories/entertainment.m3u",
        "https://iptv-org.github.io/iptv/categories/music.m3u"
    )

    private val epgUrls = listOf(
        "https://iptvx.one/epg/epg_lite.xml.gz",
        "https://iptvx.one/epg/epg.xml.gz",
        "https://epg.it999.ru/epg.xml.gz",
        "https://teleguide.info/download/new3/xmltv.xml.gz"
    )
    private var epgUrl = epgUrls[0]

    private val autoHideMenuRunnable = Runnable {
        if (menuHistory.isNotEmpty()) {
            val currentMenu = menuHistory.last()
            if ((currentMenu == "ANALYZER_GROUP_VIEW" || currentMenu == "ANALYZER_GROUPS") && isAnalyzerRunning) {
                collapseAnalyzerToTopBar()
            } else if (currentMenu !in listOf("EPG", "ADD_URL", "MANUAL_PATH")) {
                closeAllMenus()
            }
        }
    }

    private fun collapseAnalyzerToTopBar() {
        subModalOverlay.animate()
            .translationY(-480f).scaleX(0.35f).scaleY(0.25f).alpha(0f)
            .setDuration(380L)
            .withEndAction {
                subModalOverlay.translationY = 0f; subModalOverlay.scaleX = 1f; subModalOverlay.scaleY = 1f; subModalOverlay.alpha = 1f
                closeAllMenus()
                analyzerStatusBanner.visibility = View.VISIBLE
            }.start()
    }

    private fun setPlayerMiniPreviewMode(mini: Boolean) {
        val dm = resources.displayMetrics
        if (mini) {
            val curMenu = menuHistory.lastOrNull()
            val sideWidthPx = (720 * uiScaleFactor()).toInt().coerceAtMost((dm.widthPixels * 0.50f).toInt())
            val leftMenuEdgePx = if (curMenu == "SIDE") {
                sideWidthPx + 24
            } else {
                (dm.widthPixels * 0.52f).toInt()
            }
            val availW = (dm.widthPixels - leftMenuEdgePx - 28).coerceAtLeast(380)
            val availH = (availW * 9f / 16f).toInt().coerceAtMost((dm.heightPixels * 0.82f).toInt())

            playerContainerFrame.layoutParams = FrameLayout.LayoutParams(availW, availH, Gravity.CENTER_VERTICAL or Gravity.END).apply {
                setMargins(0, 0, 14, 0)
            }
            playerContainerFrame.setPadding(4, 4, 4, 4)
            playerContainerFrame.setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = 18f
                setStroke(2, ambilightColor(145))
            })
            playerContainerFrame.requestLayout()
        } else {
            playerContainerFrame.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            playerContainerFrame.setPadding(0, 0, 0, 0)
            playerContainerFrame.setBackgroundDrawable(null)
            playerContainerFrame.requestLayout()
        }
    }

    private fun adjustInfoPanelMarginForDualStream() {
        val topMarginPx = if (isDualStreamActive) 320 else 68
        infoPanel.layoutParams = FrameLayout.LayoutParams((540 * uiScaleFactor()).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(0, topMarginPx, 22, 0)
        }
    }

    private fun sampleVideoFrameColorIfPossible() {
        if (ambilightMode != 3 || isAnalyzerRunning || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            val sv = playerView.videoSurfaceView as? SurfaceView ?: return
            if (!sv.holder.surface.isValid) return
            val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            PixelCopy.request(sv, bmp, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
                    for (x in 2..13 step 3) {
                        for (y in 2..13 step 3) {
                            val px = bmp.getPixel(x, y)
                            rSum += Color.red(px); gSum += Color.green(px); bSum += Color.blue(px)
                            count++
                        }
                    }
                    if (count > 0) {
                        capturedFrameColor = Color.rgb((rSum / count).coerceAtLeast(35), (gSum / count).coerceAtLeast(35), (bSum / count).coerceAtLeast(35))
                    }
                }
                bmp.recycle()
            }, uiHandler)
        } catch (_: Exception) {}
    }

    private fun ambilightColor(maxAlpha: Int): Int {
        if (ambilightMode == 0) {
            val st = currentTheme().strokeColor
            return Color.argb((maxAlpha * 0.45f).toInt().coerceIn(20, 90), Color.red(st), Color.green(st), Color.blue(st))
        }
        val baseColor = when (ambilightMode) {
            1 -> {
                val cat = currentChannel?.let { effectiveCategory(it) } ?: ""
                when (cat) {
                    "Спорт" -> Color.rgb(0, 200, 110)
                    "Кино и сериалы" -> Color.rgb(40, 160, 230)
                    "Музыка" -> Color.rgb(210, 60, 140)
                    "2K" -> Color.rgb(220, 165, 20)
                    "Детские" -> Color.rgb(230, 120, 40)
                    else -> currentTheme().accentColor
                }
            }
            2 -> Color.HSVToColor(floatArrayOf(ambilightHue, 0.72f, 0.90f))
            else -> capturedFrameColor
        }
        return Color.argb(maxAlpha.coerceIn(18, 120), Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
    }

    private fun makeThemedPanelBg(): LayerDrawable {
        val t = currentTheme()
        val a = uiPanelAlphaInt()
        val panelColorWithAlpha = Color.argb(a, Color.red(t.panelBgColor), Color.green(t.panelBgColor), Color.blue(t.panelBgColor))
        val outerGlow = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = t.cornerRadiusDp + 6f
            setStroke(6, ambilightColor(if (ambilightMode == 0) 25 else 55))
        }
        val mainCard = GradientDrawable().apply {
            setColor(panelColorWithAlpha)
            cornerRadius = t.cornerRadiusDp
            setStroke(2, ambilightColor(if (ambilightMode == 0) 65 else 110))
        }
        return LayerDrawable(arrayOf(outerGlow, mainCard)).apply {
            setLayerInset(0, 0, 0, 0, 0)
            setLayerInset(1, 5, 5, 5, 5)
        }
    }

    private fun makeThemedSubBg(): GradientDrawable {
        val t = currentTheme()
        val a = (uiPanelAlphaInt() - 15).coerceIn(135, 245)
        val subColorWithAlpha = Color.argb(a, Color.red(t.cardBgColor), Color.green(t.cardBgColor), Color.blue(t.cardBgColor))
        return GradientDrawable().apply {
            setColor(subColorWithAlpha)
            cornerRadius = (t.cornerRadiusDp - 6f).coerceAtLeast(12f)
        }
    }

    private fun animateMenuAppear(view: View, fromX: Float = 0f, fromY: Float = 36f) {
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationX = fromX
        view.translationY = fromY
        view.scaleX = 0.92f
        view.scaleY = 0.92f
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(340L)
            .setInterpolator(OvershootInterpolator(0.85f))
            .start()
    }

    private fun resetMenuInactivityTimer() {
        uiHandler.removeCallbacks(autoHideMenuRunnable)
        if (menuHistory.isNotEmpty()) {
            val cur = menuHistory.last()
            val delay = if ((cur == "ANALYZER_GROUP_VIEW" || cur == "ANALYZER_GROUPS") && isAnalyzerRunning) 5000L else (menuHideDelaySec * 1000L)
            uiHandler.postDelayed(autoHideMenuRunnable, delay)
        }
    }

    private fun applyBrightnessLevel() {
        val pct = brightnessPercent.coerceIn(25, 100)
        if (pct >= 100) {
            nightDimOverlay.visibility = View.GONE
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        } else {
            val alpha = when (pct) { 75 -> 55; 50 -> 105; else -> 152 }
            nightDimOverlay.setBackgroundColor(Color.argb(alpha, 18, 21, 28))
            nightDimOverlay.visibility = View.VISIBLE
            nightDimOverlay.bringToFront()
            val lp = window.attributes
            lp.screenBrightness = when (pct) { 75 -> 0.72f; 50 -> 0.45f; else -> 0.24f }
            window.attributes = lp
        }
        if (::btnOsdNightMode.isInitialized) btnOsdNightMode.text = "🌙 $pct%"
    }

    private fun applyAudioEnhancements() {
        val p = player ?: return
        try {
            val sessionId = p.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
                if (loudnessEnhancer == null) loudnessEnhancer = LoudnessEnhancer(sessionId)
                val boostMb = when (audioBoostPercent) {
                    130 -> 350; 160 -> 700; 200 -> 1100
                    else -> if (drcNightAudioEnabled) 250 else 0
                }
                loudnessEnhancer?.setTargetGain(boostMb)
                loudnessEnhancer?.enabled = (boostMb > 0)
            }
            p.volume = if (drcNightAudioEnabled && audioBoostPercent == 100) 0.88f else 1.0f
        } catch (_: Exception) {}
    }

    private fun applyAutoFrameRateIfNeeded() {
        if (!afrEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val fps = player?.videoFormat?.frameRate ?: return
            if (fps <= 0f) return
            val targetHz = if (fps in 23.5f..25.5f || fps in 49.0f..50.5f) 50f else 60f
            val display = windowManager.defaultDisplay
            val currentMode = display.mode
            val bestMode = display.supportedModes.firstOrNull {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight &&
                    abs(it.refreshRate - targetHz) < 1.2f
            }
            if (bestMode != null && bestMode.modeId != window.attributes.preferredDisplayModeId) {
                val lp = window.attributes
                lp.preferredDisplayModeId = bestMode.modeId
                window.attributes = lp
            }
        } catch (_: Exception) {}
    }

    private fun stepPixelOrbiter() {
        if (!pixelOrbiterEnabled || menuHistory.isNotEmpty()) {
            playerView.translationX = 0f; playerView.translationY = 0f
            return
        }
        orbiterStep = (orbiterStep + 1) % 4
        val shifts = listOf(Pair(2f, 0f), Pair(0f, 2f), Pair(-2f, 0f), Pair(0f, -2f))
        playerView.translationX = shifts[orbiterStep].first
        playerView.translationY = shifts[orbiterStep].second
    }

    private fun applyCurrentThemeToAllPanels() {
        val t = currentTheme()
        val scale = uiScaleFactor()
        val dm = resources.displayMetrics
        sidePanel.layoutParams = FrameLayout.LayoutParams(
            (720 * scale).toInt().coerceAtMost((dm.widthPixels * 0.54f).toInt()),
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.START
        ).apply { setMargins(16, 16, 0, 16) }

        sidePanel.setBackgroundDrawable(makeThemedPanelBg())
        leftIconRail.setBackgroundDrawable(makeThemedSubBg())
        infoPanel.setBackgroundDrawable(makeThemedPanelBg())
        centerOsdPanel.setBackgroundDrawable(makeThemedPanelBg())
        settingsTileOverlay.setBackgroundDrawable(makeThemedPanelBg())
        subModalOverlay.setBackgroundDrawable(makeThemedPanelBg())
        fullEpgOverlay.setBackgroundDrawable(makeThemedPanelBg())
        categoryListView.setBackgroundDrawable(makeThemedSubBg())
        channelRecyclerView.setBackgroundDrawable(makeThemedSubBg())
        subModalList.setBackgroundDrawable(makeThemedSubBg())
        fullEpgListView.setBackgroundDrawable(makeThemedSubBg())

        osdChannelTitle.setTextColor(t.accentColor); osdChannelTitle.typeface = t.boldFont
        osdChannelTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * scale)
        osdEpgCurrentTitle.setTextColor(t.textPrimaryColor); osdEpgCurrentTitle.typeface = t.boldFont
        osdEpgCurrentTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19.5f * scale)
        osdEpgTimeLabel.typeface = t.normalFont
        osdEpgTimeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale)
        osdEpgNextAndDesc.setTextColor(t.accentColor); osdEpgNextAndDesc.typeface = t.boldFont
        osdEpgNextAndDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f * scale)

        subModalTitle.setTextColor(t.accentColor); subModalTitle.typeface = t.boldFont
        subModalTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f * scale)
        fullEpgTitle.setTextColor(t.accentColor); fullEpgTitle.typeface = t.boldFont
        fullEpgTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f * scale)
        clockTextView.typeface = t.boldFont
        clockTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
        settingsSelectedLabel.setTextColor(t.textPrimaryColor); settingsSelectedLabel.typeface = t.boldFont
        settingsSelectedLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * scale)
        infoTextView.setTextColor(t.textPrimaryColor); infoTextView.typeface = t.normalFont
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f * scale)
        currentChannelBanner.setTextColor(t.textPrimaryColor); currentChannelBanner.typeface = t.boldFont
        currentChannelBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)

        btnOsdDualToggle.text = if (isDualStreamActive) "⧉ Выкл 2-й экран" else "⧉ 2-й экран"
        btnOsdRewind.text = "⏪ -20с"
        btnOsdEpgList.text = "▤ Гид"
        btnOsdSettings.text = "⚙ Меню"
        btnOsdNightMode.text = "🌙 $brightnessPercent%"
        btnOsdChannelActions.text = "✎ Канал"

        for (btn in listOf(btnOsdTop, btnOsdFav, btnOsdDualToggle, btnOsdRewind, btnOsdEpgList, btnOsdSettings, btnOsdNightMode, btnOsdChannelActions)) {
            btn.typeface = t.boldFont
            btn.setTextColor(t.textPrimaryColor)
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f * scale)
        }

        for (tile in allGridTiles) tile.themeStyleId = t.id
        for (cap in allGridCaptions) {
            cap.typeface = t.normalFont
            cap.setTextColor(t.textPrimaryColor)
            cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale)
        }

        updateCategoriesList()
        refreshChannelAdapter()
        updateCenterOsdContent()
    }

    private fun makeVerticalRail3DIconButton(iconType: String, baseHex: String, accentHex: String, onClick: () -> Unit): ThemedTileView {
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

    private fun makeModernButton(label: String, isIconOnly: Boolean = false, onClick: () -> Unit): TextView {
        val scale = uiScaleFactor()
        return TextView(this).apply {
            text = colorizeStatusText(label)
            isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (isIconOnly) 18f else 13.5f) * scale)
            typeface = themeBoldFont()
            val t = currentTheme()
            setTextColor(t.textPrimaryColor)
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(t.cardBgColor)
                cornerRadius = if (isIconOnly) 22f else 15f
                setStroke(1, t.strokeColor)
            })
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            setPadding((14 * scale).toInt(), (9 * scale).toInt(), (14 * scale).toInt(), (9 * scale).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(4, 3, 4, 3)
            }
            setOnFocusChangeListener { _, hasFocus ->
                val curT = currentTheme()
                setBackgroundDrawable(GradientDrawable().apply {
                    setColor(if (hasFocus) curT.accentColor else curT.cardBgColor)
                    cornerRadius = if (isIconOnly) 22f else 15f
                    setStroke(if (hasFocus) 2 else 1, if (hasFocus) Color.WHITE else curT.strokeColor)
                })
                setTextColor(if (hasFocus) curT.textOnAccent else curT.textPrimaryColor)
                if (hasFocus) resetMenuInactivityTimer()
            }
            setOnClickListener { resetMenuInactivityTimer(); onClick() }
        }
    }

    private fun enableTrueFullScreenImmersion() {
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

    private fun applyScreenAspectMode() {
        when (screenAspectMode) {
            0 -> { playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT }
            1 -> { playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL; player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT }
            else -> { playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING }
        }
    }

    private fun screenAspectModeName(): String = when (screenAspectMode) {
        0 -> "По размеру экрана"
        1 -> "Растянуть на весь экран"
        else -> "Кинозум"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableTrueFullScreenImmersion()
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }

        CoreEngines.installTrustAllSsl()
        requestStoragePermissionsIfNeeded()
        loadSavedState()
        initTouchGestureDetector()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerContainerFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        }

        playerView = (LayoutInflater.from(this).inflate(R.layout.player_surface_view, playerContainerFrame, false) as PlayerView).apply {
            useController = false
            isFocusable = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerContainerFrame.addView(playerView)
        root.addView(playerContainerFrame)

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
        root.addView(dualStreamContainer)

        morphTransitionOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#88080A10"))
            alpha = 0f; visibility = View.GONE; isClickable = false; isFocusable = false
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        root.addView(morphTransitionOverlay)

        clockTextView = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = themeBoldFont()
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.parseColor("#900B0914")); cornerRadius = 14f; setStroke(1, Color.parseColor("#25FFFFFF")) })
            setPadding(16, 6, 16, 6)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(22, 18, 0, 0) }
        }
        root.addView(clockTextView)

        analyzerStatusBanner = TextView(this).apply {
            setTextColor(Color.parseColor("#00E5FF")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = themeBoldFont()
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.parseColor("#E00B0914")); cornerRadius = 18f; setStroke(2, Color.parseColor("#6500E5FF")) })
            setPadding(22, 8, 22, 8); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { setMargins(0, 18, 0, 0) }
        }
        root.addView(analyzerStatusBanner)

        signalContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.parseColor("#900B0914")); cornerRadius = 14f; setStroke(1, Color.parseColor("#25FFFFFF")) })
            setPadding(14, 8, 14, 8)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, 18, 22, 0) }
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
        root.addView(signalContainer)

        sidePanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundDrawable(makeThemedPanelBg()); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(720, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START).apply { setMargins(16, 16, 0, 16) }
            setPadding(20, 20, 20, 20)
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

        val singleColumnFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        categoryListView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_NONE; divider = null; dividerHeight = 0
            overScrollMode = View.OVER_SCROLL_NEVER; isScrollingCacheEnabled = false
            selector = ColorDrawable(Color.TRANSPARENT); isSmoothScrollbarEnabled = true
            setBackgroundDrawable(makeThemedSubBg()); setPadding(10, 8, 10, 8); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        channelLayoutManager = LinearLayoutManager(this)
        channelRvAdapter = ChannelRvAdapter(
            getTheme = { currentTheme() },
            getUiScaleFactor = { uiScaleFactor() },
            isTopUrl = { url -> topUrls.contains(url) },
            isFavUrl = { url -> favoriteUrls.contains(url) },
            getPlayingUrl = { currentChannel?.url },
            onChannelFocused = { pos ->
                focusedChannelPos = pos
                resetMenuInactivityTimer()
            },
            onChannelClicked = { pos, ch ->
                focusedChannelPos = pos
                resetMenuInactivityTimer()
                playChannelObj(ch, immediate = true)
                if (controlMode != 1) closeAllMenus()
            },
            onChannelLongClicked = { pos, ch ->
                focusedChannelPos = pos
                resetMenuInactivityTimer()
                toggleFavorite(ch)
            },
            onLeftPressedToGroups = {
                resetMenuInactivityTimer()
                showSideGroupsMode()
            },
            onRightPressedCloseMenu = {
                closeAllMenus()
            }
        )
        channelRecyclerView = RecyclerView(this).apply {
            layoutManager = channelLayoutManager
            adapter = channelRvAdapter
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundDrawable(makeThemedSubBg())
            setPadding(10, 8, 10, 8)
            clipToPadding = false
            visibility = View.VISIBLE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        singleColumnFrame.addView(categoryListView)
        singleColumnFrame.addView(channelRecyclerView)
        sidePanel.addView(singleColumnFrame)
        root.addView(sidePanel)

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
        root.addView(infoPanel)

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
        btnOsdNightMode = makeModernButton("🌙 $brightnessPercent%" ) { navigateToMenu("BRIGHTNESS_MENU") }
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
        root.addView(centerOsdPanel)

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
            selector = ColorDrawable(Color.TRANSPARENT); setBackgroundDrawable(makeThemedSubBg())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        fullEpgOverlay.addView(fullEpgListView)
        root.addView(fullEpgOverlay)

        settingsTileOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundDrawable(makeThemedPanelBg()); visibility = View.GONE; setPadding(48, 30, 48, 30)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        settingsSelectedLabel = TextView(this).apply {
            text = "Плейлисты"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = themeBoldFont(); gravity = Gravity.CENTER; setPadding(0, 14, 0, 0)
        }

        data class GridTileSpec(val iconType: String, val baseHex: String, val accentHex: String, val shortCaption: String, val fullTitle: String, val action: () -> Unit)

        val gridSpecs = listOf(
            GridTileSpec("PLAYLISTS", "#1B8A6B", "#FFB300", "Плейлисты", "Управление плейлистами (2 встроенных + свои)") { navigateToMenu("PLAYLISTS") },
            GridTileSpec("EDITOR", "#2E5B9A", "#00E676", "Редактор", "Редактор групп и каналов") { navigateToMenu("CHANNEL_EDITOR_GROUPS") },
            GridTileSpec("ANALYZER", "#5E35B1", "#00E5FF", "Тест каналов", "Быстрый тест и автоскрытие нерабочих") { navigateToMenu("ANALYZER_GROUPS") },
            GridTileSpec("THEMES", "#1E4E79", "#FF8F00", "Темы и Размер", "Темы, Прозрачность и Размер элементов для ТВ") { navigateToMenu("THEMES") },
            GridTileSpec("BUFFER", "#D84315", "#FFD600", "Буфер", "Размер буфера и Машина времени") { navigateToMenu("BUFFER") },
            GridTileSpec("DECODER", "#37474F", "#00B0FF", "Декодер", "Декодер, AFR и Антивыгорание") { navigateToMenu("DECODER") },
            GridTileSpec("EPG_GRID", "#00695C", "#FF4081", "Телегид", "Полная программа передач с видео-превью") { navigateToMenu("FULL_EPG") },
            GridTileSpec("TOP_CHANNELS", "#283593", "#FFC107", "ТОП каналы", "Каналы, закреплённые в ТОПе") { navigateToMenu("TOP_MANAGER") },
            GridTileSpec("FAVORITES", "#AD1457", "#FFD54F", "Избранное", "★ Избранные каналы") {
                selectCategory(0); isSideShowingChannels = true; navigateToMenu("SIDE")
            },
            GridTileSpec("AUDIO", "#0277BD", "#69F0AE", "Звук и DRC", "Дорожка, Ночной DRC и Усиление до 200%") { navigateToMenu("AUDIO") },
            GridTileSpec("CONTROL_MODE", "#00838F", "#FFD54F", "Управление", "Режим: Сенсор или Пульт ТВ") { showFirstRunControlPicker(forceShow = true) },
            GridTileSpec("MORE", "#4A148C", "#FFAB00", "Вид, Сеть, Обновл.", "Масштаб ТВ, Прозрачность, Ambilight и Обновление APK") { navigateToMenu("MORE_SETTINGS") }
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
                    text = spec.shortCaption; setTextColor(Color.parseColor("#CCCCCC")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    typeface = themeFont(); gravity = Gravity.CENTER; isSingleLine = true
                    layoutParams = LinearLayout.LayoutParams(184, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                allGridCaptions.add(captionView)

                val cellBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    isFocusable = true; isFocusableInTouchMode = false; isClickable = true
                    id = 5000 + specIdx
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(14, 6, 14, 6) }
                    addView(tileIcon); addView(captionView)
                    setOnFocusChangeListener { _, hasFocus ->
                        tileIcon.isTileFocused = hasFocus
                        captionView.setTextColor(if (hasFocus) currentTheme().accentColor else currentTheme().textPrimaryColor)
                        if (hasFocus) { settingsSelectedLabel.text = spec.fullTitle; resetMenuInactivityTimer() }
                    }
                    setOnClickListener { settingsSelectedLabel.text = spec.fullTitle; resetMenuInactivityTimer(); spec.action() }
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
            clipChildren = false; clipToPadding = false; setPadding(80, 36, 80, 36)
            layoutParams = LinearLayout.LayoutParams(1160, ViewGroup.LayoutParams.WRAP_CONTENT)
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
                layoutParams = LinearLayout.LayoutParams(210, 210)
            }
            allGridTiles.add(cTile)
            val cCaption = TextView(this).apply {
                text = spec.shortCaption; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = themeBoldFont(); gravity = Gravity.CENTER; isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(220, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            allGridCaptions.add(cCaption)
            val cBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                isFocusable = true; isFocusableInTouchMode = false; isClickable = true
                id = 6000 + specIdx; scaleX = 0.82f; scaleY = 0.82f; alpha = 0.65f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(22, 18, 22, 18) }
                addView(cTile); addView(cCaption)
                setOnFocusChangeListener { v, hasFocus ->
                    cTile.isTileFocused = hasFocus
                    if (hasFocus) {
                        settingsSelectedLabel.text = spec.fullTitle; resetMenuInactivityTimer()
                        v.animate().scaleX(1.18f).scaleY(1.18f).alpha(1.0f).translationY(-12f).setDuration(260L).setInterpolator(OvershootInterpolator(1.4f)).start()
                        val scrollX = (v.left + v.right) / 2 - carouselScrollView.width / 2
                        carouselScrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
                    } else {
                        v.animate().scaleX(0.82f).scaleY(0.82f).alpha(0.62f).translationY(0f).setDuration(200L).start()
                    }
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
        settingsTileOverlay.addView(settingsSelectedLabel)
        root.addView(settingsTileOverlay)

        subModalOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundDrawable(makeThemedPanelBg()); visibility = View.GONE
            setPadding(28, 24, 28, 24)
            layoutParams = FrameLayout.LayoutParams(920, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        subModalTitle = TextView(this).apply {
            setTextColor(currentTheme().accentColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = themeBoldFont(); setPadding(0, 0, 0, 10)
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
            selector = ColorDrawable(Color.TRANSPARENT); setBackgroundDrawable(makeThemedSubBg()); setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 450)
        }
        subModalAdapter = createRoundedSelectorAdapter(subModalItems, 16f, true) { pos -> subModalList.selectedItemPosition == pos }
        subModalList.adapter = subModalAdapter
        subModalList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                resetMenuInactivityTimer()
                refreshSimpleListHighlightInPlace(subModalList, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        subModalOverlay.addView(subModalList)
        root.addView(subModalOverlay)

        currentChannelBanner = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); typeface = themeBoldFont()
            setBackgroundDrawable(makeThemedPanelBg()); setPadding(22, 12, 22, 12); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply { setMargins(26, 0, 26, 26) }
        }
        root.addView(currentChannelBanner)

        firstRunModeOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E608070D")); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        buildFirstRunControlPickerView()
        root.addView(firstRunModeOverlay)

        splashOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        splashEyeView = RealisticEyeSplashView(this).apply {
            alpha = 0f; eyeOpenFraction = 0.03f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        }
        splashOverlay.addView(splashEyeView)
        root.addView(splashOverlay)

        nightDimOverlay = View(this).apply {
            setBackgroundColor(Color.argb(95, 18, 21, 28)); visibility = View.GONE
            isClickable = false; isFocusable = false
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        root.addView(nightDimOverlay)

        setContentView(root)

        playSplashEyeAnimation()
        initPlayer()
        applyScreenAspectMode()
        applyBrightnessLevel()
        startClockAndWatchdogUpdater()

        categoryAdapter = createRoundedSelectorAdapter(mutableListOf(), 17.5f, true) { pos -> !isSideShowingChannels && !isLeftRailVisible && pos == focusedCategoryPos }
        categoryListView.adapter = categoryAdapter

        categoryListView.setOnItemClickListener { _, _, position, _ ->
            resetMenuInactivityTimer(); focusedCategoryPos = position; selectCategory(position)
            showSideChannelsMode(focusCurrentChannelIfSameGroup = true)
        }
        categoryListView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                resetMenuInactivityTimer(); focusedCategoryPos = position; selectCategory(position)
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
                        showSideChannelsMode(focusCurrentChannelIfSameGroup = true)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> if (count > 0 && focusedCategoryPos <= 0) {
                        val lastIdx = count - 1; focusedCategoryPos = lastIdx; selectCategory(lastIdx)
                        categoryListView.setSelection(lastIdx); refreshSimpleListHighlightInPlace(categoryListView, lastIdx)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (count > 0 && focusedCategoryPos >= count - 1) {
                        focusedCategoryPos = 0; selectCategory(0)
                        categoryListView.setSelection(0); refreshSimpleListHighlightInPlace(categoryListView, 0)
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

    private fun showLeftIconRailFromGroups() {
        isLeftRailVisible = true
        leftIconRail.visibility = View.VISIBLE
        leftIconRail.alpha = 0f; leftIconRail.translationX = -36f
        leftIconRail.animate().alpha(1f).translationX(0f).setDuration(240L).start()
        refreshSimpleListHighlightInPlace(categoryListView, -1)
        railTilePlaylists.requestFocus()
    }

    private fun hideLeftIconRailToGroups() {
        isLeftRailVisible = false
        leftIconRail.visibility = View.GONE
        categoryListView.requestFocus()
        refreshSimpleListHighlightInPlace(categoryListView, focusedCategoryPos)
    }

    private fun refreshSimpleListHighlightInPlace(listView: ListView, activeAdapterPos: Int) {
        val t = currentTheme()
        val scale = uiScaleFactor()
        val firstVis = listView.firstVisiblePosition
        for (i in 0 until listView.childCount) {
            val child = listView.getChildAt(i) as? MarqueeRowTextView ?: continue
            val itemPos = firstVis + i
            val isHighlighted = (itemPos == activeAdapterPos)
            child.typeface = if (isHighlighted) t.boldFont else t.normalFont
            child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f * scale)
            child.forceMarqueeActive = isHighlighted
            if (isHighlighted) {
                child.setTextColor(t.textOnAccent)
                child.setBackgroundDrawable(GradientDrawable().apply { setColor(t.accentColor); cornerRadius = 20f; setStroke(2, Color.WHITE) })
            } else {
                child.setTextColor(t.textPrimaryColor)
                child.setBackgroundDrawable(null)
            }
            val raw = (listView.adapter?.getItem(itemPos) as? String) ?: child.text.toString()
            child.text = colorizeStatusText(raw)
        }
    }

    private fun createRoundedSelectorAdapter(items: MutableList<String>, spSize: Float, singleLineMarquee: Boolean = false, isRowHighlighted: (Int) -> Boolean): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this@MainActivity, 0, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? MarqueeRowTextView) ?: MarqueeRowTextView(this@MainActivity).apply {
                    layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                val t = currentTheme()
                val scale = uiScaleFactor()
                val highlighted = isRowHighlighted(position)
                val rawTxt = getItem(position) ?: ""
                tv.typeface = if (highlighted) t.boldFont else t.normalFont
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize * scale)
                val padV = (13 * scale).toInt()
                tv.setPadding(22, padV, 22, padV)
                if (singleLineMarquee) tv.forceMarqueeActive = highlighted else tv.isSingleLine = false
                if (highlighted) {
                    tv.setTextColor(t.textOnAccent)
                    tv.setBackgroundDrawable(GradientDrawable().apply { setColor(t.accentColor); cornerRadius = 20f; setStroke(2, Color.WHITE) })
                } else {
                    tv.setTextColor(t.textPrimaryColor)
                    tv.setBackgroundDrawable(null)
                }
                tv.text = colorizeStatusText(rawTxt)
                return tv
            }
        }
    }

    private fun buildAndShowZappingBar() {
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

    private fun updateSubModalListItems(newItems: List<String>) {
        val firstVis = subModalList.firstVisiblePosition
        val selPos = subModalList.selectedItemPosition
        subModalItems.clear(); subModalItems.addAll(newItems)
        subModalAdapter.notifyDataSetChanged()
        if (selPos >= 0 && selPos < newItems.size) subModalList.setSelectionFromTop(firstVis, 0)
    }

    private fun buildFirstRunControlPickerView() {
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
            text = "Для ТВ автоматически включится укрупнённый шрифт, а для смартфона — компактный"
            setTextColor(Color.parseColor("#94A3B8")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = themeFont(); gravity = Gravity.CENTER; setPadding(0, 8, 0, 28)
        }
        card.addView(title); card.addView(subtitle)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val btnTouchMode = makeModernButton("📱 Смартфон / Планшет\nОбычный размер элементов") {
            controlMode = 1; uiScaleStep = 0; screenAspectMode = 0; applyScreenAspectMode(); applyCurrentThemeToAllPanels(); saveState()
            enableTrueFullScreenImmersion(); firstRunModeOverlay.visibility = View.GONE
        }.apply { setPadding(34, 28, 34, 28); isSingleLine = false; maxLines = 3; setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f) }

        val btnRemoteMode = makeModernButton("📺 Телевизор / ТВ-приставка\nКрупный размер элементов") {
            controlMode = 2; uiScaleStep = 1; applyCurrentThemeToAllPanels(); saveState()
            enableTrueFullScreenImmersion(); firstRunModeOverlay.visibility = View.GONE
        }.apply { setPadding(34, 28, 34, 28); isSingleLine = false; maxLines = 3; setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f) }

        row.addView(btnTouchMode); row.addView(btnRemoteMode)
        card.addView(row); firstRunModeOverlay.addView(card)
    }

    private fun showFirstRunControlPicker(forceShow: Boolean = false) {
        if (controlMode == 0 || forceShow) {
            closeAllMenus(); buildFirstRunControlPickerView()
            animateMenuAppear(firstRunModeOverlay, 0f, 20f)
            val card = firstRunModeOverlay.getChildAt(0) as? LinearLayout
            val row = card?.getChildAt(2) as? LinearLayout
            row?.getChildAt(if (controlMode == 1) 0 else 1)?.requestFocus()
        }
    }

    private fun isPointInsideVisibleView(x: Float, y: Float, v: View): Boolean {
        if (v.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height
    }

    private fun isTouchInsideAnyActiveMenu(e: MotionEvent): Boolean {
        val x = e.rawX; val y = e.rawY
        return isPointInsideVisibleView(x, y, sidePanel) ||
            isPointInsideVisibleView(x, y, centerOsdPanel) ||
            isPointInsideVisibleView(x, y, settingsTileOverlay) ||
            isPointInsideVisibleView(x, y, subModalOverlay) ||
            isPointInsideVisibleView(x, y, fullEpgOverlay) ||
            isPointInsideVisibleView(x, y, infoPanel) ||
            isPointInsideVisibleView(x, y, dualStreamContainer)
    }

    private fun initTouchGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = false
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (firstRunModeOverlay.visibility == View.VISIBLE) return false
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

                if (abs(dx) > abs(dy) * 1.45f && abs(dx) > 95f) {
                    if (curMenu == "SIDE") {
                        if (dx > 0) {
                            if (isSideShowingChannels) showSideGroupsMode()
                            else if (!isLeftRailVisible) showLeftIconRailFromGroups()
                        } else {
                            if (isLeftRailVisible) hideLeftIconRailToGroups()
                            else if (!isSideShowingChannels) showSideChannelsMode(focusCurrentChannelIfSameGroup = true)
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

    private fun showSideChannelsMode(focusCurrentChannelIfSameGroup: Boolean = true) {
        isSideShowingChannels = true
        isLeftRailVisible = false
        leftIconRail.visibility = View.GONE
        categoryListView.visibility = View.GONE
        channelRecyclerView.visibility = View.VISIBLE

        if (displayedChannels.isNotEmpty()) {
            val activeIdxInGroup = if (focusCurrentChannelIfSameGroup) {
                displayedChannels.indexOfFirst { it.url == currentChannel?.url }
            } else -1
            val targetPos = if (activeIdxInGroup >= 0) activeIdxInGroup else 0

            focusedChannelPos = targetPos
            channelRvAdapter.submitChannels(displayedChannels.toList(), targetPos)
            channelLayoutManager.scrollToPositionWithOffset(targetPos, (channelRecyclerView.height / 3).coerceAtLeast(80))
            channelRecyclerView.post {
                val vh = channelRecyclerView.findViewHolderForAdapterPosition(targetPos)
                if (vh != null) {
                    vh.itemView.requestFocus()
                } else {
                    channelRecyclerView.requestFocus()
                }
            }
        } else {
            focusedChannelPos = 0
            channelRvAdapter.submitChannels(emptyList(), 0)
        }
    }

    private fun showSideGroupsMode() {
        isSideShowingChannels = false
        isLeftRailVisible = false
        leftIconRail.visibility = View.GONE
        focusedCategoryPos = currentCategoryIndex
        updateCategoriesList()
        channelRecyclerView.visibility = View.GONE
        categoryListView.visibility = View.VISIBLE
        categoryListView.setSelection(currentCategoryIndex)
        categoryListView.requestFocus()
        categoryListView.post { refreshSimpleListHighlightInPlace(categoryListView, currentCategoryIndex) }
    }

    private fun playSplashEyeAnimation() {
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
                        if (controlMode == 0) showFirstRunControlPicker(forceShow = true)
                    }.start()
                }
            }
        }
        uiHandler.post(animRunnable)
    }

    private fun requestStoragePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2002)
            }
        }
    }

    private fun hideAllMenuViews() {
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

    private fun closeAllMenus() {
        menuHistory.clear()
        uiHandler.removeCallbacks(autoHideMenuRunnable)
        hideAllMenuViews()
    }

    private fun navigateToMenu(menuId: String) {
        if (menuHistory.lastOrNull() != menuId) menuHistory.add(menuId)
        renderMenuById(menuId)
        resetMenuInactivityTimer()
    }

    private fun navigateBackInMenu(): Boolean {
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
            renderMenuById(prev)
            resetMenuInactivityTimer()
        }
        return true
    }

    private var selectedEditorCategory: String = ""

    private fun renderMenuById(menuId: String) {
        hideAllMenuViews()
        sidePanel.setBackgroundDrawable(makeThemedPanelBg())
        centerOsdPanel.setBackgroundDrawable(makeThemedPanelBg())
        subModalOverlay.setBackgroundDrawable(makeThemedPanelBg())
        settingsTileOverlay.setBackgroundDrawable(makeThemedPanelBg())
        fullEpgOverlay.setBackgroundDrawable(makeThemedPanelBg())

        currentChannelBanner.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalTopButtons.removeAllViews()
        when (menuId) {
            "SIDE" -> {
                setPlayerMiniPreviewMode(true)
                clockTextView.visibility = View.GONE
                animateMenuAppear(sidePanel, -80f, 0f)
                val cats = activeCategoryList()
                if (openChannelsDirectlyWithCurrentFocus && currentChannel != null) {
                    openChannelsDirectlyWithCurrentFocus = false
                    val chCatIdx = cats.indexOf(effectiveCategory(currentChannel!!)).takeIf { it >= 2 } ?: 1
                    selectCategory(chCatIdx)
                    showSideChannelsMode(focusCurrentChannelIfSameGroup = true)
                } else if (isSideShowingChannels) {
                    showSideChannelsMode(focusCurrentChannelIfSameGroup = true)
                } else {
                    showSideGroupsMode()
                }
            }
            "INFO" -> {
                adjustInfoPanelMarginForDualStream()
                updateStreamInfoText()
                animateMenuAppear(infoPanel, 60f, 0f)
            }
            "OSD" -> { updateCenterOsdContent(); animateMenuAppear(centerOsdPanel, 0f, 50f); btnOsdTop.requestFocus() }
            "BRIGHTNESS_MENU" -> openBrightnessSubModal()
            "POWER_AND_SLEEP_MENU" -> openPowerAndSleepSubModal()
            "CHANNEL_ACTIONS" -> openChannelActionsSubModal()
            "MOVE_CHANNEL_CATEGORY" -> openMoveChannelCategorySubModal()
            "FULL_EPG" -> openFullScreenChannelEpg()
            "SETTINGS" -> {
                animateMenuAppear(settingsTileOverlay, 0f, 35f)
                if (currentTheme().isCarouselMenu) {
                    gridContainerLayout.visibility = View.GONE
                    carouselScrollView.visibility = View.VISIBLE
                    carouselCells.firstOrNull()?.requestFocus()
                } else {
                    carouselScrollView.visibility = View.GONE
                    gridContainerLayout.visibility = View.VISIBLE
                    (gridContainerLayout.getChildAt(0) as? LinearLayout)?.getChildAt(0)?.requestFocus()
                }
            }
            "THEMES" -> openThemesSubModal()
            "TOP_MANAGER" -> openTopManagerSubModal()
            "MORE_SETTINGS" -> openMoreSettingsSubModal()
            "PLAYLISTS" -> openPlaylistsSubModal()
            "FILE_BROWSER" -> openFolderExplorerModal(currentBrowseDir)
            "CHANNEL_EDITOR_GROUPS" -> openChannelEditorGroupsModal()
            "CHANNEL_EDITOR_ITEMS" -> openChannelEditorItemsModal(selectedEditorCategory)
            "ANALYZER_GROUPS" -> openAnalyzerGroupsModal(firstOpen = true)
            "ANALYZER_GROUP_VIEW" -> openAnalyzerGroupChannelsModal(selectedAnalyzerCategory, firstOpen = true)
            "BUFFER" -> openBufferSubModal()
            "DECODER" -> openDecoderSubModal()
            "AUDIO" -> openAudioSubModal()
            "EPG" -> openInputSubModal("Ссылка на телепрограмму XMLTV", epgUrl) { newVal ->
                if (newVal.isNotEmpty()) { epgUrl = newVal; saveState(); loadEpgOncePerDayInBackground(forceRefresh = true) }
            }
            "ADD_URL" -> openAddUrlSubModal()
        }
    }

    private fun rewindLiveBufferSeconds(sec: Int) {
        val p = player ?: return
        val cur = p.currentPosition
        val target = (cur - sec * 1000L).coerceAtLeast(0L)
        p.seekTo(target)
    }

    private fun stopDualStreamMode() {
        isDualStreamActive = false
        try { dualPipPlayer?.stop(); dualPipPlayer?.release() } catch (_: Exception) {}
        dualPipPlayer = null
        dualStreamContainer.visibility = View.GONE
        adjustInfoPanelMarginForDualStream()
        updateCenterOsdContent()
    }

    private fun toggleDualStreamWithCurrentOrNeighbor() {
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

    private fun startDualStreamChannel(ch: Channel) {
        try { dualPipPlayer?.release() } catch (_: Exception) {}
        dualStreamChannel = ch
        isDualStreamActive = true
        val dp = ExoPlayer.Builder(this).build().apply {
            volume = 0f
            setMediaItem(MediaItem.fromUri(ch.url))
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

    private fun swapMainAndDualStream() {
        val mainCh = currentChannel ?: return
        val secCh = dualStreamChannel ?: return
        playChannelObj(secCh, immediate = true, silentSwap = true)
        startDualStreamChannel(mainCh)
    }

    private fun openPowerAndSleepSubModal() {
        subModalTitle.text = "Выключение и Таймер сна"
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.VISIBLE

        val sleepOpts = listOf(0, 30, 60, 90, 120)
        val items = mutableListOf("[-]  Закрыть приложение TiViLook прямо сейчас")
        for (m in sleepOpts) {
            val sel = if (sleepTimerMinutesLeft == m) "[+]  " else "      "
            if (m == 0) items.add("${sel}Таймер сна: Выключен")
            else items.add("${sel}Выключить автоматически через $m минут")
        }
        updateSubModalListItems(items)

        subModalList.setOnItemClickListener { _, _, which, _ ->
            if (which == 0) finish()
            else {
                setSleepTimer(sleepOpts.getOrElse(which - 1) { 0 })
                navigateBackInMenu()
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun setSleepTimer(minutes: Int) {
        sleepTimerRunnable?.let { uiHandler.removeCallbacks(it) }
        sleepTimerMinutesLeft = minutes
        applyCurrentThemeToAllPanels()
        if (minutes <= 0) {
            player?.volume = 1.0f
            return
        }
        val tick = object : Runnable {
            override fun run() {
                sleepTimerMinutesLeft--
                if (sleepTimerMinutesLeft <= 0) finish()
                else {
                    if (sleepTimerMinutesLeft == 1) player?.volume = 0.35f
                    uiHandler.postDelayed(this, 60_000L)
                }
            }
        }
        sleepTimerRunnable = tick
        uiHandler.postDelayed(tick, 60_000L)
    }

    private fun openBrightnessSubModal() {
        subModalTitle.text = "🌙 Уровень яркости (Плазма / ТВ и Меню)"
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        val levels = listOf(100, 75, 50, 25)
        val descriptions = listOf(
            "100% — Полная яркость (Дневной режим)",
            "75% — Вечерний режим (лёгкое приглушение)",
            "50% — Ночной режим (среднее приглушение)",
            "25% — Глубокая ночь (с сохранением теней на плазме)"
        )
        updateSubModalListItems(levels.mapIndexed { idx, lvl -> (if (brightnessPercent == lvl) "[+]  " else "      ") + descriptions[idx] })
        subModalList.setOnItemClickListener { _, _, which, _ ->
            if (which in levels.indices) {
                brightnessPercent = levels[which]; applyBrightnessLevel(); saveState(); navigateBackInMenu()
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun openChannelActionsSubModal() {
        val ch = currentChannel ?: return
        subModalTitle.text = "✎ Управление каналом: ${ch.name}"
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        val items = listOf(
            "[-]  Исключить этот канал (только ручной возврат)",
            "📂  Переместить канал в другую категорию — ${effectiveCategory(ch)}",
            if (isDualStreamActive) "[-]  Выключить режим «Двойной эфир»" else "[+]  Запустить этот канал во 2-м окне («Двойной эфир»)"
        )
        updateSubModalListItems(items)
        subModalList.setOnItemClickListener { _, _, which, _ ->
            when (which) {
                0 -> {
                    manualDisabledUrls.add(ch.url); saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
                    closeAllMenus(); switchChannelByOffset(+1)
                }
                1 -> navigateToMenu("MOVE_CHANNEL_CATEGORY")
                2 -> {
                    if (isDualStreamActive) { stopDualStreamMode(); closeAllMenus() }
                    else { startDualStreamChannel(ch); closeAllMenus(); switchChannelByOffset(+1) }
                }
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun openMoveChannelCategorySubModal() {
        val ch = currentChannel ?: return
        val targetCats = activeCategoryList().drop(2)
        val curCat = effectiveCategory(ch)
        subModalTitle.text = "📂 Переместить «${ch.name}» в категорию:"
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        updateSubModalListItems(targetCats.map { c -> (if (c == curCat) "[+]  " else "      ") + c })
        subModalList.setOnItemClickListener { _, _, which, _ ->
            if (which in targetCats.indices) {
                val newCat = targetCats[which]
                customCategoryMap[ch.url] = newCat
                currentChannel = Channel(ch.name, ch.url, newCat, ch.tvgId)
                saveState(); updateCategoriesList()
                selectCategory(activeCategoryList().indexOf(newCat).takeIf { it >= 2 } ?: currentCategoryIndex)
                closeAllMenus()
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun effectiveCategory(ch: Channel): String = customCategoryMap[ch.url] ?: ch.category

    private fun openThemesSubModal() {
        subModalTitle.text = "Темы, Размер элементов и Прозрачность"
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.VISIBLE
        subModalTopButtons.visibility = View.VISIBLE
        subModalTopButtons.removeAllViews()

        val scaleName = when (uiScaleStep) { 0 -> "Обычный (Смартфон)"; 1 -> "Крупный (ТВ)"; else -> "Огромный (Большой ТВ)" }
        val alphaName = when (uiAlphaStep) { 0 -> "Плотный (96%)"; 1 -> "Средний (84%)"; else -> "Стекло (68%)" }

        subModalTopButtons.addView(makeModernButton("🔍 Размер: $scaleName") {
            uiScaleStep = (uiScaleStep + 1) % 3
            saveState(); applyCurrentThemeToAllPanels(); openThemesSubModal()
        })
        subModalTopButtons.addView(makeModernButton("💧 Прозрачность: $alphaName") {
            uiAlphaStep = (uiAlphaStep + 1) % 3
            saveState(); applyCurrentThemeToAllPanels(); openThemesSubModal()
        })

        val items = themes.mapIndexed { idx, t ->
            if (idx == themeIndex) "[+]  ${t.name}   —   АКТИВНА"
            else "      ${t.name}"
        }
        updateSubModalListItems(items)
        subModalList.setSelection(themeIndex)

        subModalList.setOnItemClickListener { _, _, which, _ ->
            themeIndex = which; saveState(); applyCurrentThemeToAllPanels(); navigateBackInMenu()
        }
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
    }

    private fun openTopManagerSubModal() {
        subModalTitle.text = "▲ Каналы в ТОПе групп (${topUrls.size})"
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        subModalTopButtons.visibility = View.VISIBLE; subModalTopButtons.removeAllViews()
        if (topUrls.isNotEmpty()) {
            subModalTopButtons.addView(makeModernButton("Очистить весь ТОП") {
                topUrls.clear(); saveState(); selectCategory(currentCategoryIndex); openTopManagerSubModal()
            })
        }
        val topChans = allChannels.filter { topUrls.contains(it.url) }
        updateSubModalListItems(if (topChans.isEmpty()) listOf("В ТОПе пока нет каналов.") else topChans.map { "▲  ${it.name}  —  ${effectiveCategory(it)}" })
        subModalList.setOnItemClickListener { _, _, idx, _ ->
            if (idx in topChans.indices) { topUrls.remove(topChans[idx].url); saveState(); selectCategory(currentCategoryIndex); openTopManagerSubModal() }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun openFullScreenChannelEpg() {
        setPlayerMiniPreviewMode(true)
        val ch = currentChannel
        val progs = getChannelEpgList(ch)
        val dateFmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()

        fullEpgTitle.text = "▤ Программа передач: " + (ch?.name ?: "Канал не выбран")
        val items = mutableListOf<String>()
        var activeIndex = 0

        if (progs.isEmpty()) {
            items.add("Расписание для канала «${ch?.name ?: ""}» обновляется в фоне...")
        } else {
            progs.forEachIndexed { idx, p ->
                val isNow = now in p.startMs..p.stopMs
                if (isNow) activeIndex = idx
                val prefix = when {
                    isNow -> "[+] СЕЙЧАС   "
                    p.stopMs < now -> "      Прошло   "
                    else -> "      Далее     "
                }
                val timeRange = dateFmt.format(Date(p.startMs)) + " — " + dateFmt.format(Date(p.stopMs))
                items.add("$prefix$timeRange   •   ${p.title}")
            }
        }

        fullEpgListView.adapter = createRoundedSelectorAdapter(items, 16f, false) { pos -> fullEpgListView.selectedItemPosition == pos }
        animateMenuAppear(fullEpgOverlay)
        fullEpgListView.requestFocus()
        if (activeIndex > 0 && activeIndex < items.size) fullEpgListView.setSelection(activeIndex)
        fullEpgListView.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                resetMenuInactivityTimer()
                refreshSimpleListHighlightInPlace(fullEpgListView, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
    }

    private fun openMoreSettingsSubModal() {
        subModalTitle.text = "Масштаб ТВ, Прозрачность, Ambilight и Обновление"
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.VISIBLE

        val scaleName = when (uiScaleStep) {
            0 -> "Шаг 1: Обычный (для Смартфона)"
            1 -> "Шаг 2: Крупный (для Телевизора)"
            else -> "Шаг 3: Очень крупный (для Большого ТВ)"
        }
        val alphaName = when (uiAlphaStep) {
            0 -> "Шаг 1: Плотный фон (96%)"
            1 -> "Шаг 2: Средняя прозрачность (84%)"
            else -> "Шаг 3: Высокая прозрачность / Стекло (68%)"
        }
        val ambiName = when (ambilightMode) {
            0 -> "Выключен"
            1 -> "Свой цвет у каждой группы"
            2 -> "Плавный спектр (переливающийся)"
            else -> "Захват кадра видео (Live PixelCopy)"
        }
        val dropName = if (onDropBehavior == 0) "Перезапуск соединения (14с)" else "Автоперескок на следующий канал"
        val hideName = if (showChannelBannerOnSwitch) "$menuHideDelaySec сек (Баннер ВКЛ)" else "$menuHideDelaySec сек (Баннер ВЫКЛ)"

        val items = listOf(
            "🔍  Размер элементов и текста: $scaleName",
            "💧  Прозрачность окон меню: $alphaName",
            "🚀  Проверить и установить обновление TiViLook (GitHub)",
            "✦   Подсветка Ambilight: $ambiName",
            if (isDualStreamActive) "[+]  Режим «Двойной эфир»: ВКЛЮЧЕН" else "[-]  Режим «Двойной эфир»: ВЫКЛЮЧЕН",
            "⏪  Машина времени: Отмотать эфир на -30 сек",
            "🖥  Формат экрана: ${screenAspectModeName()}",
            "🌙  Яркость экрана и меню: $brightnessPercent%",
            "⏲  Выход и Таймер сна: ${if (sleepTimerMinutesLeft > 0) "$sleepTimerMinutesLeft мин" else "Выключен"}",
            if (dohEnabled) "[+]  DNS-over-HTTPS (Cloudflare 1.1.1.1): ВКЛ" else "[-]  DNS-over-HTTPS (Cloudflare 1.1.1.1): ВЫКЛ",
            "⚡  При обрыве потока: $dropName",
            "⏱  Автоскрытие меню и баннера: $hideName",
            "🔄  Обновить телепрограмму EPG сейчас"
        )
        updateSubModalListItems(items)

        subModalList.setOnItemClickListener { _, _, which, _ ->
            when (which) {
                0 -> { uiScaleStep = (uiScaleStep + 1) % 3; saveState(); applyCurrentThemeToAllPanels(); openMoreSettingsSubModal() }
                1 -> { uiAlphaStep = (uiAlphaStep + 1) % 3; saveState(); applyCurrentThemeToAllPanels(); openMoreSettingsSubModal() }
                2 -> checkAndInstallAppUpdateFromGithub()
                3 -> { ambilightMode = (ambilightMode + 1) % 4; saveState(); applyCurrentThemeToAllPanels(); openMoreSettingsSubModal() }
                4 -> { toggleDualStreamWithCurrentOrNeighbor(); openMoreSettingsSubModal() }
                5 -> { rewindLiveBufferSeconds(30); closeAllMenus() }
                6 -> { screenAspectMode = (screenAspectMode + 1) % 3; applyScreenAspectMode(); saveState(); openMoreSettingsSubModal() }
                7 -> navigateToMenu("BRIGHTNESS_MENU")
                8 -> navigateToMenu("POWER_AND_SLEEP_MENU")
                9 -> { dohEnabled = !dohEnabled; saveState(); initPlayer(); openMoreSettingsSubModal() }
                10 -> { onDropBehavior = (onDropBehavior + 1) % 2; saveState(); openMoreSettingsSubModal() }
                11 -> {
                    when (menuHideDelaySec) {
                        4 -> menuHideDelaySec = 8
                        8 -> menuHideDelaySec = 12
                        else -> { menuHideDelaySec = 4; showChannelBannerOnSwitch = !showChannelBannerOnSwitch }
                    }
                    saveState(); openMoreSettingsSubModal()
                }
                12 -> { loadEpgOncePerDayInBackground(forceRefresh = true); navigateBackInMenu() }
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun checkAndInstallAppUpdateFromGithub() {
        Toast.makeText(this, "Проверка обновлений TiViLook на GitHub...", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val apiConn = URL("https://api.github.com/repos/pskovland007/IPtv/releases/latest").openConnection() as HttpURLConnection
                apiConn.setRequestProperty("Accept", "application/vnd.github+json")
                apiConn.connectTimeout = 8000; apiConn.readTimeout = 8000
                if (apiConn.responseCode == 200) {
                    val json = JSONObject(apiConn.inputStream.bufferedReader().readText())
                    val tagName = json.optString("tag_name", "")
                    val assets = json.optJSONArray("assets")
                    var apkUrl = ""
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val url = asset.optString("browser_download_url", "")
                            if (url.endsWith(".apk", true)) { apkUrl = url; break }
                        }
                    }
                    if (apkUrl.isNotEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this, "Скачивание новой версии $tagName...", Toast.LENGTH_LONG).show()
                        }
                        val apkFile = File(externalCacheDir ?: cacheDir, "TiViLook-update.apk")
                        if (apkFile.exists()) apkFile.delete()
                        val dlConn = URL(apkUrl).openConnection() as HttpURLConnection
                        dlConn.instanceFollowRedirects = true
                        dlConn.inputStream.use { input ->
                            FileOutputStream(apkFile).use { output -> input.copyTo(output) }
                        }
                        runOnUiThread {
                            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                FileProvider.getUriForFile(this, "com.example.miboxiptv.fileprovider", apkFile)
                            } else Uri.fromFile(apkFile)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        }
                    } else {
                        runOnUiThread { Toast.makeText(this, "У вас установлена актуальная версия!", Toast.LENGTH_SHORT).show() }
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "У вас установлена последняя сборка v2.7!", Toast.LENGTH_SHORT).show() }
                }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this, "Проверка завершена: установлена актуальная версия", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun startClockAndWatchdogUpdater() {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        var tickCounter = 0
        val ticker = object : Runnable {
            override fun run() {
                try {
                    tickCounter++
                    ambilightHue = (ambilightHue + 8f) % 360f
                    if (ambilightMode == 3 && tickCounter % 2 == 0) sampleVideoFrameColorIfPossible()
                    if (tickCounter % 60 == 0) stepPixelOrbiter()

                    clockTextView.text = timeFmt.format(Date())
                    val activeBars = updateSignalIndicator()

                    if (menuHistory.isNotEmpty() && ambilightMode != 0) {
                        when (menuHistory.last()) {
                            "SIDE" -> sidePanel.setBackgroundDrawable(makeThemedPanelBg())
                            "OSD" -> centerOsdPanel.setBackgroundDrawable(makeThemedPanelBg())
                            "SETTINGS" -> settingsTileOverlay.setBackgroundDrawable(makeThemedPanelBg())
                            "FULL_EPG" -> fullEpgOverlay.setBackgroundDrawable(makeThemedPanelBg())
                            "INFO" -> infoPanel.setBackgroundDrawable(makeThemedPanelBg())
                            else -> subModalOverlay.setBackgroundDrawable(makeThemedPanelBg())
                        }
                    }

                    val p = player
                    if (p != null && currentChannel != null) {
                        val state = p.playbackState
                        val curPos = p.currentPosition
                        val isFrozen = (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE ||
                            (state == Player.STATE_READY && p.isPlaying && curPos == lastRenderedFramePositionMs && p.totalBufferedDuration < 1200L))
                        lastRenderedFramePositionMs = curPos

                        if (isFrozen || activeBars <= 1) {
                            lowSignalStuckSeconds += 2
                            if (lowSignalStuckSeconds >= 14) {
                                lowSignalStuckSeconds = 0
                                consecutiveDropFailures++
                                if (onDropBehavior == 1 && consecutiveDropFailures >= 2) {
                                    consecutiveDropFailures = 0
                                    switchChannelByOffset(+1)
                                } else {
                                    if (antiBlockEnabled) userAgentIndex = (userAgentIndex + 1) % CoreEngines.russianSmartTvAgents.size
                                    reloadIconIndicator.visibility = View.VISIBLE
                                    uiHandler.postDelayed({ reloadIconIndicator.visibility = View.GONE }, 3500L)
                                    reconnectCurrentStream()
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
                uiHandler.postDelayed(this, 1500)
            }
        }
        uiHandler.post(ticker)
    }

    private fun reconnectCurrentStream() {
        val ch = currentChannel ?: return
        val p = player ?: return
        try {
            p.stop(); p.clearMediaItems()
            p.setMediaItem(createLiveMediaItem(ch.url))
            p.prepare(); p.play()
        } catch (_: Exception) {}
    }

    private fun createLiveMediaItem(url: String): MediaItem {
        val targetOffset = when (bufferLevel) { 0 -> 10000L; 1 -> 18000L; 2 -> 28000L; else -> 40000L }
        return MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(targetOffset)
                    .setMinOffsetMs(4000L)
                    .setMaxOffsetMs(120000L)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            ).build()
    }

    private fun updateSignalIndicator(): Int {
        val p = player
        val state = p?.playbackState ?: Player.STATE_IDLE
        val bufSec = ((p?.totalBufferedDuration ?: 0L) / 1000L).toInt()
        val (activeBars, activeColor) = when {
            state == Player.STATE_IDLE -> Pair(1, Color.parseColor("#FF5252"))
            state == Player.STATE_BUFFERING && bufSec == 0 -> Pair(1, Color.parseColor("#FF5252"))
            state == Player.STATE_BUFFERING -> Pair(2, Color.parseColor("#FFD740"))
            state == Player.STATE_READY && bufSec >= 5 -> Pair(4, Color.parseColor("#00E676"))
            state == Player.STATE_READY && bufSec >= 2 -> Pair(3, Color.parseColor("#69F0AE"))
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

    private fun initPlayer() {
        val currentUrl = currentChannel?.url
        try { loudnessEnhancer?.release(); loudnessEnhancer = null } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}

        val (minBuf, maxBuf, startBuf, rebuf) = when (bufferLevel) {
            0 -> listOf(12000, 30000, 1000, 2000)
            1 -> listOf(22000, 55000, 1500, 3000)
            2 -> listOf(38000, 85000, 2000, 3800)
            else -> listOf(60000, 130000, 2800, 5000)
        }
        val targetBytes = if (bufferLevel >= 3) 96 * 1024 * 1024 else 64 * 1024 * 1024
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuf, maxBuf, startBuf, rebuf)
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(90000, true)
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
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(activeAgent)
            .setDefaultRequestProperties(mapOf("Accept" to "*/*", "Connection" to "keep-alive"))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(25000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(8))

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val newPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttrs, false)
            .build()

        newPlayer.volume = 1.0f
        newPlayer.trackSelectionParameters = newPlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(3840, 2160)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()

        newPlayer.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                morphTransitionOverlay.animate().alpha(0f).setDuration(220L).withEndAction {
                    morphTransitionOverlay.visibility = View.GONE
                }.start()
                applyAudioEnhancements()
                applyAutoFrameRateIfNeeded()
            }
            override fun onTracksChanged(tracks: Tracks) {
                var builder = newPlayer.trackSelectionParameters.buildUpon()
                var changed = false
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audioGroups.isNotEmpty() && audioGroups.none { it.isSelected }) {
                    val firstGroup = audioGroups.first()
                    if (firstGroup.length > 0) {
                        builder = builder.setOverrideForType(TrackSelectionOverride(firstGroup.mediaTrackGroup, 0))
                        changed = true
                    }
                }
                val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                for (vg in videoGroups) {
                    if (vg.length > 1) {
                        var bestTrackIdx = -1; var bestScore = -1L; var currentlySelectedIdx = -1
                        for (i in 0 until vg.length) {
                            if (vg.isTrackSelected(i)) currentlySelectedIdx = i
                            if (vg.isTrackSupported(i)) {
                                val fmt = vg.getTrackFormat(i)
                                val pixels = (fmt.width.coerceAtLeast(0)).toLong() * (fmt.height.coerceAtLeast(0)).toLong()
                                val br = fmt.bitrate.coerceAtLeast(0).toLong()
                                val fpsBonus = if (fmt.frameRate >= 48f) 5_000_000L else 0L
                                val score = pixels * 10L + br + fpsBonus
                                if (score > bestScore) { bestScore = score; bestTrackIdx = i }
                            }
                        }
                        if (bestTrackIdx != -1 && bestTrackIdx != currentlySelectedIdx) {
                            builder = builder.setOverrideForType(TrackSelectionOverride(vg.mediaTrackGroup, listOf(bestTrackIdx)))
                            changed = true
                        }
                    }
                }
                if (changed) newPlayer.trackSelectionParameters = builder.build()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) reconnectCurrentStream()
            }
            override fun onPlayerError(error: PlaybackException) {
                userAgentIndex = (userAgentIndex + 1) % CoreEngines.russianSmartTvAgents.size
                uiHandler.postDelayed({ reconnectCurrentStream() }, 1200)
            }
        })

        playerView.player = newPlayer
        player = newPlayer
        applyScreenAspectMode()

        if (currentUrl != null) {
            newPlayer.setMediaItem(createLiveMediaItem(currentUrl))
            newPlayer.prepare()
            newPlayer.play()
        }
    }

    private fun preloadNeighboringChannels(activeChannel: Channel) {
        if (isAnalyzerRunning) return
        val snapshot = displayedChannels.toList()
        if (snapshot.size < 2) return
        val idx = snapshot.indexOfFirst { it.url == activeChannel.url }
        if (idx == -1) return
        val neighbors = listOf(snapshot[(idx - 1 + snapshot.size) % snapshot.size], snapshot[(idx + 1) % snapshot.size])
        synchronized(preloadedLock) { preloadedNeighbors.clear() }
        for (neighbor in neighbors) {
            try {
                preloaderExecutor.submit {
                    if (isAnalyzerRunning) return@submit
                    try {
                        val conn = URL(neighbor.url).openConnection() as HttpURLConnection
                        conn.setRequestProperty("User-Agent", CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size])
                        conn.connectTimeout = 3000; conn.readTimeout = 3000
                        if (conn.responseCode in 200..299) {
                            val buf = ByteArray(128)
                            conn.inputStream.read(buf)
                            conn.disconnect()
                            synchronized(preloadedLock) { preloadedNeighbors.add(neighbor.url) }
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private fun getChannelEpgList(ch: Channel?): List<EpgProgram> {
        if (ch == null) return emptyList()
        if (ch.tvgId.isNotEmpty()) {
            val byId = epgById[ch.tvgId] ?: epgById[ch.tvgId.lowercase()]
            if (!byId.isNullOrEmpty()) return byId.toList()
        }
        val norm = CoreEngines.normalizeName(ch.name)
        if (norm.isNotEmpty()) {
            epgByName[norm]?.let { if (it.isNotEmpty()) return it.toList() }
            val withoutHd = norm.removeSuffix("hd").removeSuffix("fhd").removeSuffix("50fps").trim()
            if (withoutHd.isNotEmpty()) {
                epgByName[withoutHd]?.let { if (it.isNotEmpty()) return it.toList() }
            }
        }
        return emptyList()
    }

    private fun getCurrentAndNextEpg(ch: Channel?): Pair<EpgProgram?, EpgProgram?> {
        val list = getChannelEpgList(ch)
        if (list.isEmpty()) return Pair(null, null)
        val now = System.currentTimeMillis()
        val idx = list.indexOfFirst { now in it.startMs..it.stopMs }
        if (idx == -1) return Pair(null, null)
        return Pair(list[idx], if (idx + 1 < list.size) list[idx + 1] else null)
    }

    private fun loadEpgOncePerDayInBackground(forceRefresh: Boolean) {
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

    private fun updateCenterOsdContent() {
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

    private fun updateStreamInfoText() {
        val p = player ?: return
        val ch = currentChannel
        val vFormat = p.videoFormat

        val width = vFormat?.width ?: p.videoSize.width
        val height = vFormat?.height ?: p.videoSize.height
        val fps = if (vFormat != null && vFormat.frameRate > 0) String.format(Locale.US, "%.0f fps", vFormat.frameRate) else ""
        val vCodec = vFormat?.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Анализ..."
        val vBitrate = if (vFormat != null && vFormat.bitrate > 0) String.format(Locale.US, "%.2f Мбит/с", vFormat.bitrate / 1_000_000f) else "Адаптивный HLS"

        val bufSec = p.totalBufferedDuration / 1000L
        val preloadedCount = synchronized(preloadedLock) { preloadedNeighbors.size }

        val (epg, _) = getCurrentAndNextEpg(ch)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val epgLine = if (epg != null) "\n\n🎬 Сейчас (${timeFmt.format(Date(epg.startMs))}–${timeFmt.format(Date(epg.stopMs))}):\n${epg.title}" else ""

        infoTextView.text = "Канал: " + (ch?.name ?: "-") + "\n" +
            "Группа: " + (if (ch != null) effectiveCategory(ch) else "-") + "\n\n" +
            "Разрешение: " + width + "x" + height + " " + fps + " (" + vCodec + ")\n" +
            "Поток: " + vBitrate + "\n" +
            "Буфер: " + bufSec + " сек (Машина времени: 90с)\n" +
            "Предзагрузка соседних: " + preloadedCount + " из 2 ⚡" +
            epgLine
    }

    private fun openPlaylistsSubModal() {
        ensureDefaultPlaylists()
        subModalTitle.text = "Плейлисты (2 встроенных + ваши списки)"
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        val snapshot = playlists.toList()
        val items = snapshot.mapIndexed { idx, p -> (if (idx == activePlaylistIndex) "[+]  " else "      ") + p.title }.toMutableList()
        items.add("➕  Добавить свой плейлист по ссылке (с сохранением его групп)")
        items.add("📂  Проводник: выбрать файл .m3u/.m3u8 с устройства / USB")
        if (snapshot.size > 2 && activePlaylistIndex >= 2) items.add("[-]  Удалить выбранный пользовательский плейлист")
        updateSubModalListItems(items)

        subModalList.setOnItemClickListener { _, _, which, _ ->
            when {
                which < snapshot.size -> { activePlaylistIndex = which; saveState(); closeAllMenus(); loadActivePlaylist(autoPlayLast = false) }
                which == snapshot.size -> navigateToMenu("ADD_URL")
                which == snapshot.size + 1 -> {
                    currentBrowseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!currentBrowseDir.exists()) currentBrowseDir = Environment.getExternalStorageDirectory()
                    navigateToMenu("FILE_BROWSER")
                }
                else -> {
                    if (activePlaylistIndex >= 2 && activePlaylistIndex < playlists.size) {
                        playlists.removeAt(activePlaylistIndex); activePlaylistIndex = 0; saveState(); closeAllMenus(); loadActivePlaylist(autoPlayLast = false)
                    }
                }
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun openFolderExplorerModal(dir: File) {
        requestStoragePermissionsIfNeeded()
        currentBrowseDir = dir
        subModalTitle.text = "📂 Проводник: " + dir.absolutePath
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        subModalTopButtons.visibility = View.VISIBLE; subModalTopButtons.removeAllViews()

        subModalTopButtons.addView(makeModernButton("📥 Download") { openFolderExplorerModal(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) })
        subModalTopButtons.addView(makeModernButton("🏠 Память") { openFolderExplorerModal(Environment.getExternalStorageDirectory()) })
        subModalTopButtons.addView(makeModernButton("🔌 USB") { openFolderExplorerModal(File("/storage")) })
        subModalTopButtons.addView(makeModernButton("⌨ Путь вручную") {
            openInputSubModal("Путь к файлу .m3u / .m3u8", "/sdcard/Download/playlist.m3u8") { path -> addLocalPlaylistFile(File(path)) }
        })

        val entries = mutableListOf<Pair<String, File?>>()
        val parent = dir.parentFile
        if (parent != null && parent.canRead()) entries.add(Pair("⬆  .. [На уровень вверх]", parent))
        val rawChildren = try { dir.listFiles()?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
        for (d in rawChildren.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }) entries.add(Pair("📁  " + d.name, d))
        for (f in rawChildren.filter { it.isFile && (it.name.endsWith(".m3u", true) || it.name.endsWith(".m3u8", true) || it.name.endsWith(".txt", true)) }.sortedBy { it.name.lowercase() }) {
            entries.add(Pair("📄  " + f.name + "  (" + (f.length() / 1024) + " КБ)", f))
        }
        if (entries.isEmpty()) entries.add(Pair("Папка пуста или нет доступа", null))

        updateSubModalListItems(entries.map { it.first })
        subModalList.setOnItemClickListener { _, _, idx, _ ->
            val target = entries.getOrNull(idx)?.second ?: return@setOnItemClickListener
            if (target.isDirectory) openFolderExplorerModal(target) else if (target.isFile) addLocalPlaylistFile(target)
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun addLocalPlaylistFile(file: File) {
        if (!file.exists() || !file.canRead()) return
        playlists.add(PlaylistEntry(file.name, file.absolutePath, true))
        activePlaylistIndex = playlists.size - 1; saveState(); closeAllMenus(); loadActivePlaylist(autoPlayLast = false)
    }

    private fun openChannelEditorGroupsModal() {
        subModalTitle.text = "Редактор каналов: выберите группу"
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        subModalTopButtons.visibility = View.VISIBLE; subModalTopButtons.removeAllViews()
        subModalTopButtons.addView(makeModernButton("Включить все группы и каналы") {
            disabledCategories.clear(); manualDisabledUrls.clear(); autoDisabledUrls.clear()
            saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex); openChannelEditorGroupsModal()
        })
        val snapshot = allChannels.toList()
        val editableCats = activeCategoryList().drop(2)
        updateSubModalListItems(editableCats.map { cat ->
            val isCatEnabled = !disabledCategories.contains(cat)
            val totalInCat = snapshot.count { effectiveCategory(it) == cat }
            val activeInCat = snapshot.count { effectiveCategory(it) == cat && isChannelActive(it) }
            "${if (isCatEnabled) "[+]" else "[-]"}  $cat   [Активно: $activeInCat из $totalInCat]"
        })
        subModalList.setOnItemClickListener { _, _, idx, _ -> selectedEditorCategory = editableCats[idx]; navigateToMenu("CHANNEL_EDITOR_ITEMS") }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun openChannelEditorItemsModal(catName: String) {
        val catChannels = allChannels.filter { effectiveCategory(it) == catName }.sortedWith(Comparator { a, b -> ruCollator.compare(a.name, b.name) })
        val isCatDisabled = disabledCategories.contains(catName)
        subModalTitle.text = "Группа: $catName (${catChannels.size} кан.)"
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        subModalTopButtons.visibility = View.VISIBLE; subModalTopButtons.removeAllViews()
        subModalTopButtons.addView(makeModernButton(if (isCatDisabled) "[+] Включить всю группу" else "[-] Отключить всю группу") {
            if (disabledCategories.contains(catName)) disabledCategories.remove(catName) else disabledCategories.add(catName)
            saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex); openChannelEditorItemsModal(catName)
        })
        subModalTopButtons.addView(makeModernButton("Включить все в группе") {
            catChannels.forEach { manualDisabledUrls.remove(it.url); autoDisabledUrls.remove(it.url) }
            disabledCategories.remove(catName); saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex); openChannelEditorItemsModal(catName)
        })
        updateSubModalListItems(catChannels.map { ch ->
            val isManualOff = manualDisabledUrls.contains(ch.url)
            val isAutoOff = autoDisabledUrls.contains(ch.url)
            val enabled = !isManualOff && !isAutoOff && !isCatDisabled
            val note = if (isManualOff) " [Исключён вами]" else if (isAutoOff) " [Скрыт тестом]" else ""
            "${if (enabled) "[+]" else "[-]"}  ${ch.name}$note"
        })
        subModalList.setOnItemClickListener { _, _, idx, _ ->
            if (idx in catChannels.indices) {
                val ch = catChannels[idx]
                if (manualDisabledUrls.contains(ch.url) || autoDisabledUrls.contains(ch.url)) {
                    manualDisabledUrls.remove(ch.url); autoDisabledUrls.remove(ch.url)
                } else manualDisabledUrls.add(ch.url)
                saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex); openChannelEditorItemsModal(catName)
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun openAnalyzerGroupsModal(firstOpen: Boolean = false) {
        val snapshot = allChannels.filter { !manualDisabledUrls.contains(it.url) }
        val aliveTotal = aliveChannelsAtomic.get()
        val deadTotal = deadChannelsAtomic.get()
        subModalTitle.text = colorizeStatusText("Быстрый тест (16 потоков)  •  [+] $aliveTotal   [-] $deadTotal (скрываются авто)")
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        if (firstOpen || subModalTopButtons.childCount == 0 || !isAnalyzerRunning) {
            subModalTopButtons.visibility = View.VISIBLE; subModalTopButtons.removeAllViews()
            subModalTopButtons.addView(makeModernButton(if (isAnalyzerRunning) "■ Стоп" else "▶ Быстрый тест всех групп") {
                if (isAnalyzerRunning) { isAnalyzerRunning = false; analyzerStatusBanner.visibility = View.GONE; openAnalyzerGroupsModal(firstOpen = true) }
                else { selectedAnalyzerCategory = "Все каналы"; startBackgroundChannelAnalyzer(snapshot); openAnalyzerGroupsModal(firstOpen = true) }
            })
            if (isAnalyzerRunning) subModalTopButtons.addView(makeModernButton("▲ Свернуть в фон") { collapseAnalyzerToTopBar() })
            if (autoDisabledUrls.isNotEmpty()) {
                subModalTopButtons.addView(makeModernButton("↺ Вернуть скрытые (${autoDisabledUrls.size})") {
                    autoDisabledUrls.clear(); saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex); openAnalyzerGroupsModal(firstOpen = true)
                })
            }
        }
        val groupNames = listOf("Все каналы") + activeCategoryList().drop(2)
        updateSubModalListItems(groupNames.map { gName ->
            val groupChans = if (gName == "Все каналы") snapshot else snapshot.filter { effectiveCategory(it) == gName }
            "📂  $gName (${groupChans.size} кан.)   —   [+] ${groupChans.count { channelHealthMap[it.url] == true }}    [-] ${groupChans.count { channelHealthMap[it.url] == false }}"
        })
        subModalList.setOnItemClickListener { _, _, idx, _ ->
            if (idx in groupNames.indices) { selectedAnalyzerCategory = groupNames[idx]; navigateToMenu("ANALYZER_GROUP_VIEW") }
        }
        if (firstOpen) { animateMenuAppear(subModalOverlay); subModalList.requestFocus(); resetMenuInactivityTimer() }
    }

    private fun openAnalyzerGroupChannelsModal(catName: String, firstOpen: Boolean = false) {
        val snapshot = allChannels.filter { !manualDisabledUrls.contains(it.url) }
        val groupChans = (if (catName == "Все каналы") snapshot else snapshot.filter { effectiveCategory(it) == catName }).sortedWith(Comparator { a, b -> ruCollator.compare(a.name, b.name) })
        val alive = groupChans.count { channelHealthMap[it.url] == true }
        val dead = groupChans.count { channelHealthMap[it.url] == false }
        subModalTitle.text = colorizeStatusText("Группа: $catName (Всего: ${groupChans.size}  •  [+] $alive  •  [-] $dead)")
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        if (firstOpen || subModalTopButtons.childCount == 0 || !isAnalyzerRunning) {
            subModalTopButtons.visibility = View.VISIBLE; subModalTopButtons.removeAllViews()
            subModalTopButtons.addView(makeModernButton(if (isAnalyzerRunning) "■ Стоп" else "▶ Быстрый тест группы") {
                if (isAnalyzerRunning) { isAnalyzerRunning = false; analyzerStatusBanner.visibility = View.GONE; openAnalyzerGroupChannelsModal(catName, firstOpen = true) }
                else { startBackgroundChannelAnalyzer(groupChans); openAnalyzerGroupChannelsModal(catName, firstOpen = true) }
            })
            if (isAnalyzerRunning) subModalTopButtons.addView(makeModernButton("▲ Свернуть в фон") { collapseAnalyzerToTopBar() })
        }
        updateSubModalListItems(groupChans.mapIndexed { i, ch ->
            val status = when (channelHealthMap[ch.url]) { true -> "[+] Работает"; false -> "[-] Скрыт"; null -> "[?] Очередь" }
            "${i + 1}.  $status  —  ${ch.name}"
        })
        subModalList.setOnItemClickListener { _, _, idx, _ -> if (idx in groupChans.indices) { playChannelObj(groupChans[idx], immediate = true); closeAllMenus() } }
        if (firstOpen) { animateMenuAppear(subModalOverlay); subModalList.requestFocus(); resetMenuInactivityTimer() }
    }

    private fun startBackgroundChannelAnalyzer(rawTargetChannels: List<Channel>) {
        val targetChannels = rawTargetChannels.filter { !manualDisabledUrls.contains(it.url) }
        if (isAnalyzerRunning || targetChannels.isEmpty()) return
        isAnalyzerRunning = true
        targetChannels.forEach { channelHealthMap.remove(it.url) }
        aliveChannelsAtomic.set(0)
        deadChannelsAtomic.set(0)

        val snapshot = targetChannels.toList()
        val total = snapshot.size
        val counter = AtomicInteger(0)

        analyzerStatusBanner.text = colorizeStatusText("Быстрый тест: 0 / $total")
        analyzerStatusBanner.visibility = View.VISIBLE
        resetMenuInactivityTimer()

        thread(priority = Thread.NORM_PRIORITY) {
            val pool = Executors.newFixedThreadPool(16)
            var lastUiUpdateMs = 0L
            for (ch in snapshot) {
                pool.submit {
                    if (!isAnalyzerRunning) return@submit
                    val ok = checkStreamAliveDeep(ch.url)
                    channelHealthMap[ch.url] = ok
                    if (ok) {
                        aliveChannelsAtomic.incrementAndGet()
                        autoDisabledUrls.remove(ch.url)
                    } else {
                        deadChannelsAtomic.incrementAndGet()
                        if (!manualDisabledUrls.contains(ch.url)) autoDisabledUrls.add(ch.url)
                    }

                    val done = counter.incrementAndGet()
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdateMs >= 850L || done >= total) {
                        lastUiUpdateMs = now
                        val aliveCount = aliveChannelsAtomic.get()
                        val deadCount = deadChannelsAtomic.get()
                        runOnUiThread {
                            if (isAnalyzerRunning && done < total) {
                                analyzerStatusBanner.text = colorizeStatusText("Тест: $done / $total   •   [+] $aliveCount   [-] Скрыто: $deadCount")
                                val curMenu = menuHistory.lastOrNull()
                                if (curMenu == "ANALYZER_GROUP_VIEW") openAnalyzerGroupChannelsModal(selectedAnalyzerCategory, firstOpen = false)
                                else if (curMenu == "ANALYZER_GROUPS") openAnalyzerGroupsModal(firstOpen = false)
                            }
                        }
                    }
                }
            }
            pool.shutdown()
            try { pool.awaitTermination(15, TimeUnit.MINUTES) } catch (_: Exception) {}
            pool.shutdownNow()
            if (isAnalyzerRunning) {
                isAnalyzerRunning = false
                val aliveCount = aliveChannelsAtomic.get()
                val deadCount = deadChannelsAtomic.get()
                runOnUiThread {
                    saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
                    val curMenu = menuHistory.lastOrNull()
                    if (curMenu == "ANALYZER_GROUP_VIEW") openAnalyzerGroupChannelsModal(selectedAnalyzerCategory, firstOpen = false)
                    else if (curMenu == "ANALYZER_GROUPS") openAnalyzerGroupsModal(firstOpen = false)
                    analyzerStatusBanner.text = colorizeStatusText("Готово! Рабочих: [+] $aliveCount  |  Скрыто нерабочих: [-] $deadCount")
                    uiHandler.postDelayed({ analyzerStatusBanner.visibility = View.GONE }, 5000L)
                }
            }
        }
    }

    private fun checkStreamAliveDeep(urlStr: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val parsed = URL(urlStr)
            val origin = "${parsed.protocol}://${parsed.host}"
            conn = (parsed.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size])
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Referer", "$origin/")
                instanceFollowRedirects = true
                connectTimeout = 2600
                readTimeout = 2600
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return false
            }
            val cType = (conn.contentType ?: "").lowercase()
            if (cType.contains("text/html")) {
                conn.disconnect()
                return false
            }
            val buf = ByteArray(256)
            val readBytes = conn.inputStream.read(buf)
            conn.disconnect()
            if (readBytes <= 0) return false
            val headStr = String(buf, 0, readBytes)
            if (headStr.contains("<html", true) || headStr.contains("<!doctype html", true) || headStr.contains("404 not found", true) || headStr.contains("access denied", true)) {
                return false
            }
            if (urlStr.contains(".m3u", true) && !headStr.contains("#EXTM3U", true) && buf[0] != 0x47.toByte()) {
                return false
            }
            true
        } catch (_: Exception) {
            try { conn?.disconnect() } catch (_: Exception) {}
            false
        }
    }

    private fun openBufferSubModal() {
        subModalTitle.text = "Буфер подкачки и Машина времени"
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        val opts = listOf("Маленький (12–30 сек)", "Средний (22–55 сек)", "Большой (38–85 сек)", "Очень большой (60–130 сек)").mapIndexed { i, t -> (if (i == bufferLevel) "[+]  " else "      ") + t }.toMutableList()
        opts.add("⏪  Машина времени: Отмотать прямой эфир на -20 сек")
        opts.add("⏪  Машина времени: Отмотать прямой эфир на -45 сек")
        updateSubModalListItems(opts)
        subModalList.setOnItemClickListener { _, _, which, _ ->
            when (which) {
                in 0..3 -> { bufferLevel = which; saveState(); initPlayer(); navigateBackInMenu() }
                4 -> { rewindLiveBufferSeconds(20); closeAllMenus() }
                5 -> { rewindLiveBufferSeconds(45); closeAllMenus() }
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun openDecoderSubModal() {
        subModalTitle.text = "Декодер, Частота экрана (AFR) и Защита Плазмы"
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        val opts = listOf("Аппаратный декодер", "Автоматический декодер", "Программный декодер").mapIndexed { i, t -> (if (i == decoderMode) "[+]  " else "      ") + t }.toMutableList()
        opts.add(if (afrEnabled) "[+]  Автоподстройка частоты AFR (50/60 Гц): ВКЛ" else "[-]  Автоподстройка частоты AFR (50/60 Гц): ВЫКЛ")
        opts.add(if (pixelOrbiterEnabled) "[+]  Незаметный сдвиг пикселей (Pixel Orbiter): ВКЛ" else "[-]  Незаметный сдвиг пикселей (Pixel Orbiter): ВЫКЛ")
        updateSubModalListItems(opts)
        subModalList.setOnItemClickListener { _, _, which, _ ->
            when (which) {
                in 0..2 -> { decoderMode = which; saveState(); initPlayer(); navigateBackInMenu() }
                3 -> { afrEnabled = !afrEnabled; saveState(); applyAutoFrameRateIfNeeded(); openDecoderSubModal() }
                4 -> { pixelOrbiterEnabled = !pixelOrbiterEnabled; saveState(); stepPixelOrbiter(); openDecoderSubModal() }
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun openAudioSubModal() {
        val p = player ?: return
        subModalTitle.text = "Звук, Выравнивание (DRC) и Усиление"
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        val labels = mutableListOf<String>()
        labels.add(if (drcNightAudioEnabled) "[+]  Выравнивание громкости (Night Audio / DRC): ВКЛ" else "[-]  Выравнивание громкости (Night Audio / DRC): ВЫКЛ")
        labels.add("🔊  Усиление звука (Audio Boost): $audioBoostPercent% (нажмите для смены)")

        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val overrides = mutableListOf<TrackSelectionOverride>()
        for (g in groups) {
            for (i in 0 until g.length) {
                val fmt = g.getTrackFormat(i)
                val codec = fmt.sampleMimeType?.substringAfter("/")?.uppercase() ?: "AUDIO"
                val lang = fmt.language?.uppercase() ?: ("Дорожка " + (overrides.size + 1))
                labels.add((if (g.isTrackSelected(i)) "[+]  " else "      ") + "Дорожка: $lang  •  $codec")
                overrides.add(TrackSelectionOverride(g.mediaTrackGroup, i))
            }
        }
        updateSubModalListItems(labels)
        subModalList.setOnItemClickListener { _, _, idx, _ ->
            when (idx) {
                0 -> { drcNightAudioEnabled = !drcNightAudioEnabled; saveState(); applyAudioEnhancements(); openAudioSubModal() }
                1 -> {
                    audioBoostPercent = when (audioBoostPercent) { 100 -> 130; 130 -> 160; 160 -> 200; else -> 100 }
                    saveState(); applyAudioEnhancements(); openAudioSubModal()
                }
                else -> {
                    val ovIdx = idx - 2
                    if (ovIdx in overrides.indices) {
                        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(overrides[ovIdx]).build()
                        navigateBackInMenu()
                    }
                }
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun openInputSubModal(title: String, initialValue: String, onSave: (String) -> Unit) {
        subModalTitle.text = title; subModalList.visibility = View.GONE
        subModalInputContainer.removeAllViews(); subModalInputContainer.visibility = View.VISIBLE
        val input = EditText(this).apply { setText(initialValue); setTextColor(currentTheme().textPrimaryColor); setSingleLine() }
        subModalInputContainer.addView(input)
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 14, 0, 0) }
        btnRow.addView(makeModernButton("Сохранить") { onSave(input.text.toString().trim()); navigateBackInMenu() })
        btnRow.addView(makeModernButton("Назад") { navigateBackInMenu() })
        subModalInputContainer.addView(btnRow)
        animateMenuAppear(subModalOverlay); input.requestFocus()
    }

    private fun openAddUrlSubModal() {
        subModalTitle.text = "Новый плейлист по ссылке"; subModalList.visibility = View.GONE
        subModalInputContainer.removeAllViews(); subModalInputContainer.visibility = View.VISIBLE
        val nameInput = EditText(this).apply { hint = "Название плейлиста"; setTextColor(currentTheme().textPrimaryColor); setSingleLine() }
        val urlInput = EditText(this).apply { hint = "http://... (.m3u8)"; setTextColor(currentTheme().textPrimaryColor); setSingleLine() }
        subModalInputContainer.addView(nameInput); subModalInputContainer.addView(urlInput)
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 14, 0, 0) }
        btnRow.addView(makeModernButton("Добавить") {
            val title = nameInput.text.toString().trim().ifEmpty { "Плейлист " + (playlists.size + 1) }
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) { playlists.add(PlaylistEntry(title, url, false)); activePlaylistIndex = playlists.size - 1; saveState(); closeAllMenus(); loadActivePlaylist(autoPlayLast = false) }
        })
        btnRow.addView(makeModernButton("Назад") { navigateBackInMenu() })
        subModalInputContainer.addView(btnRow)
        animateMenuAppear(subModalOverlay); nameInput.requestFocus()
    }

    private fun ensureDefaultPlaylists() {
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

    private fun isChannelActive(ch: Channel): Boolean {
        return !manualDisabledUrls.contains(ch.url) && !autoDisabledUrls.contains(ch.url) && !disabledCategories.contains(effectiveCategory(ch))
    }

    private fun loadActivePlaylist(autoPlayLast: Boolean) {
        ensureDefaultPlaylists()
        val entry = playlists[activePlaylistIndex]
        val myGen = ++playlistLoadGeneration
        val isBuiltin = entry.source == "BUILTIN_OPEN_RU" || entry.source == "BUILTIN_STRICT_RU_ONLY"
        val strictRuOnly = entry.source == "BUILTIN_STRICT_RU_ONLY"

        thread {
            try {
                val finalChannels: List<Channel> = when {
                    isBuiltin -> {
                        val result = mutableListOf<Channel>()
                        val existingKeys = HashSet<String>()
                        for (extra in BuiltinCatalog.extraOpenStreams) {
                            if (CoreEngines.isArabicOrIndianChannel(extra.name, extra.category, extra.url)) continue
                            if (strictRuOnly && !CoreEngines.isStrictlyRussianChannel(extra.name, extra.url, extra.category)) continue
                            val cleanTitle = CoreEngines.cleanChannelDisplayName(extra.name)
                            if (existingKeys.add(CoreEngines.normalizeName(cleanTitle) + "_" + extra.category)) {
                                result.add(Channel(cleanTitle, extra.url, customCategoryMap[extra.url] ?: extra.category, extra.tvgId))
                            }
                        }
                        val targetMap = LinkedHashMap<String, Channel>()
                        val sourcesToLoad = if (strictRuOnly) openRusSources.take(14) else openRusSources
                        for (srcUrl in sourcesToLoad) {
                            if (myGen != playlistLoadGeneration) return@thread
                            try {
                                val reader = CoreEngines.openStreamWithBypass(srcUrl, userAgentIndex, dohEnabled, 6000).bufferedReader()
                                CoreEngines.parseM3uReaderIntoMap(
                                    reader = reader,
                                    targetMap = targetMap,
                                    customCategoryMap = customCategoryMap,
                                    filterBuiltinRules = true,
                                    onlyRussianAllCategories = strictRuOnly,
                                    keepOriginalGroups = false
                                )
                            } catch (_: Exception) {}
                        }
                        for (ch in targetMap.values) {
                            if (existingKeys.add(CoreEngines.normalizeName(ch.name) + "_" + ch.category)) result.add(ch)
                        }
                        result
                    }
                    entry.isLocal -> {
                        val targetMap = LinkedHashMap<String, Channel>()
                        CoreEngines.parseM3uReaderIntoMap(
                            reader = BufferedReader(FileReader(File(entry.source))),
                            targetMap = targetMap,
                            customCategoryMap = customCategoryMap,
                            filterBuiltinRules = false,
                            onlyRussianAllCategories = false,
                            keepOriginalGroups = true
                        )
                        targetMap.values.toList()
                    }
                    else -> {
                        val targetMap = LinkedHashMap<String, Channel>()
                        val reader = CoreEngines.openStreamWithBypass(entry.source, userAgentIndex, dohEnabled, 10000).bufferedReader()
                        CoreEngines.parseM3uReaderIntoMap(
                            reader = reader,
                            targetMap = targetMap,
                            customCategoryMap = customCategoryMap,
                            filterBuiltinRules = false,
                            onlyRussianAllCategories = false,
                            keepOriginalGroups = true
                        )
                        targetMap.values.toList()
                    }
                }
                if (myGen != playlistLoadGeneration) return@thread
                runOnUiThread {
                    allChannels.clear()
                    allChannels.addAll(finalChannels)

                    dynamicCategories.clear()
                    if (!isBuiltin) {
                        val customGroups = finalChannels.map { effectiveCategory(it) }.filter { it.isNotEmpty() }.distinct()
                        if (customGroups.isNotEmpty()) {
                            dynamicCategories.add("★ Избранное")
                            dynamicCategories.add("Все каналы")
                            dynamicCategories.addAll(customGroups)
                        }
                    }

                    updateCategoriesList()
                    val currentList = allChannels.filter { isChannelActive(it) }
                    val cats = activeCategoryList()
                    if (currentList.isNotEmpty()) {
                        val targetCh = (if (autoPlayLast && lastSavedChannelUrl.isNotEmpty()) currentList.firstOrNull { it.url == lastSavedChannelUrl } else null) ?: currentList[0]
                        selectCategory(cats.indexOf(effectiveCategory(targetCh)).takeIf { it >= 2 } ?: 1)
                        openChannelsDirectlyWithCurrentFocus = true
                        playChannelObj(targetCh, immediate = true)
                    } else selectCategory(1)
                }
            } catch (_: Exception) {
                if (myGen != playlistLoadGeneration) return@thread
                runOnUiThread {
                    if (allChannels.isEmpty()) {
                        allChannels.addAll(BuiltinCatalog.extraOpenStreams)
                        dynamicCategories.clear()
                        updateCategoriesList(); selectCategory(1)
                        displayedChannels.firstOrNull()?.let { playChannelObj(it, immediate = true) }
                    }
                }
            }
        }
    }

    private fun updateCategoriesList() {
        val activeAll = allChannels.filter { isChannelActive(it) }
        val cats = activeCategoryList()
        categoryAdapter.clear()
        categoryAdapter.addAll(cats.map { cat ->
            val count = when (cat) {
                "★ Избранное" -> activeAll.count { favoriteUrls.contains(it.url) }
                "Все каналы" -> activeAll.size
                else -> activeAll.count { effectiveCategory(it) == cat }
            }
            "$cat ($count)"
        })
        categoryAdapter.notifyDataSetChanged()
    }

    private fun selectCategory(index: Int) {
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
            val aRu = a.name.any { it in 'а'..'я' || it in 'А'..'Я' }
            val bRu = b.name.any { it in 'а'..'я' || it in 'А'..'Я' }
            if (aRu != bRu) return@Comparator if (aRu) -1 else 1
            ruCollator.compare(a.name, b.name)
        }
        displayedChannels.clear()
        displayedChannels.addAll(rawFiltered.filter { topUrls.contains(it.url) }.sortedWith(comparator))
        displayedChannels.addAll(rawFiltered.filter { !topUrls.contains(it.url) }.sortedWith(comparator))

        val activePosInGroup = displayedChannels.indexOfFirst { it.url == currentChannel?.url }
        focusedChannelPos = if (activePosInGroup >= 0) activePosInGroup else 0
        refreshChannelAdapter()
    }

    private fun refreshChannelAdapter() {
        if (::channelRvAdapter.isInitialized) {
            channelRvAdapter.submitChannels(displayedChannels.toList(), focusedChannelPos)
        }
    }

    private fun toggleTopChannel(ch: Channel) {
        if (!topUrls.remove(ch.url)) topUrls.add(ch.url)
        saveState(); selectCategory(currentCategoryIndex)
    }

    private fun toggleFavorite(ch: Channel) {
        if (!favoriteUrls.remove(ch.url)) favoriteUrls.add(ch.url)
        saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
    }

    private val hideMorphFallbackRunnable = Runnable {
        morphTransitionOverlay.animate().alpha(0f).setDuration(200L).withEndAction {
            morphTransitionOverlay.visibility = View.GONE
        }.start()
    }

    private val executeDebouncedPlayRunnable = Runnable {
        val target = pendingDebouncedChannel ?: return@Runnable
        try {
            player?.setMediaItem(createLiveMediaItem(target.url))
            player?.prepare()
            player?.play()
        } catch (_: Exception) {}
        uiHandler.removeCallbacks(preloadNeighborsRunnable)
        if (!isAnalyzerRunning) uiHandler.postDelayed(preloadNeighborsRunnable, 1500L)
    }

    private fun playChannelObj(channel: Channel, immediate: Boolean = false, silentSwap: Boolean = false) {
        val prevPlayingUrl = currentChannel?.url
        if (!silentSwap && currentChannel != null && prevPlayingUrl != channel.url) {
            previousChannel = currentChannel
        }
        currentChannel = channel
        pendingDebouncedChannel = channel
        lastSavedChannelUrl = channel.url
        lowSignalStuckSeconds = 0
        consecutiveDropFailures = 0
        saveState()

        if (::channelRvAdapter.isInitialized) {
            val oldIdx = displayedChannels.indexOfFirst { it.url == prevPlayingUrl }
            val newIdx = displayedChannels.indexOfFirst { it.url == channel.url }
            if (oldIdx >= 0) channelRvAdapter.notifyItemChanged(oldIdx)
            if (newIdx >= 0) channelRvAdapter.notifyItemChanged(newIdx)
        }

        uiHandler.removeCallbacks(hideMorphFallbackRunnable)
        morphTransitionOverlay.visibility = View.VISIBLE
        morphTransitionOverlay.alpha = 0.50f
        uiHandler.postDelayed(hideMorphFallbackRunnable, 420L)

        uiHandler.removeCallbacks(executeDebouncedPlayRunnable)
        if (immediate) {
            executeDebouncedPlayRunnable.run()
        } else {
            uiHandler.postDelayed(executeDebouncedPlayRunnable, 160L)
        }

        val topBadge = if (topUrls.contains(channel.url)) "▲ " else ""
        val star = if (favoriteUrls.contains(channel.url)) "★ " else ""
        val (epg, _) = getCurrentAndNextEpg(channel)
        val epgLine = if (epg != null) "\n🎬 " + epg.title else ""

        if (!silentSwap && menuHistory.isEmpty() && showChannelBannerOnSwitch) {
            showBanner(topBadge + star + channel.name + "  •  " + effectiveCategory(channel) + epgLine)
        } else {
            currentChannelBanner.visibility = View.GONE
        }

        updateSignalIndicator()
        if (centerOsdPanel.visibility == View.VISIBLE) updateCenterOsdContent()
        if (infoPanel.visibility == View.VISIBLE) updateStreamInfoText()
        if (fullEpgOverlay.visibility == View.VISIBLE) openFullScreenChannelEpg()
    }

    private val preloadNeighborsRunnable = Runnable { currentChannel?.let { preloadNeighboringChannels(it) } }

    private fun showBanner(text: String) {
        if (menuHistory.isNotEmpty() || !showChannelBannerOnSwitch) { currentChannelBanner.visibility = View.GONE; return }
        currentChannelBanner.text = text
        currentChannelBanner.setBackgroundDrawable(makeThemedPanelBg())
        animateMenuAppear(currentChannelBanner, 0f, 18f)
        currentChannelBanner.removeCallbacks(hideBannerRunnable)
        currentChannelBanner.postDelayed(hideBannerRunnable, (menuHideDelaySec * 500L).coerceIn(2500L, 6000L))
    }

    private val hideBannerRunnable = Runnable { currentChannelBanner.visibility = View.GONE }

    private fun saveState() {
        val prefs = getSharedPreferences("iptv_v4_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (p in playlists) {
            if (p.source == "BUILTIN_VIP_PROVIDER") continue
            arr.put(JSONObject().apply { put("title", p.title); put("source", p.source); put("isLocal", p.isLocal) })
        }
        val customCatJson = JSONObject()
        for ((u, c) in customCategoryMap) customCatJson.put(u, c)
        prefs.edit()
            .putString("playlists_json", arr.toString())
            .putString("custom_cats_json", customCatJson.toString())
            .putInt("active_playlist", activePlaylistIndex)
            .putInt("buffer_level", bufferLevel)
            .putInt("decoder_mode", decoderMode)
            .putInt("theme_index", themeIndex)
            .putInt("ui_scale_step_v27", uiScaleStep)
            .putInt("ui_alpha_step_v27", uiAlphaStep)
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
            .putBoolean("show_banner_v23", showChannelBannerOnSwitch)
            .putBoolean("anti_block_ru", antiBlockEnabled)
            .putInt("ua_index", userAgentIndex)
            .putString("epg_url", epgUrl)
            .putString("last_channel_url", lastSavedChannelUrl)
            .putStringSet("favorites", favoriteUrls.toSet())
            .putStringSet("top_urls", topUrls.toSet())
            .putStringSet("manual_disabled_urls_v19", manualDisabledUrls.toSet())
            .putStringSet("auto_disabled_urls_v19", autoDisabledUrls.toSet())
            .putStringSet("disabled_cats", disabledCategories.toSet())
            .apply()
    }

    private fun loadSavedState() {
        val prefs = getSharedPreferences("iptv_v4_prefs", Context.MODE_PRIVATE)
        bufferLevel = prefs.getInt("buffer_level", 2)
        decoderMode = prefs.getInt("decoder_mode", 1)
        themeIndex = prefs.getInt("theme_index", 0)
        controlMode = prefs.getInt("control_mode_v13", 0)
        uiScaleStep = prefs.getInt("ui_scale_step_v27", if (controlMode == 1) 0 else 1)
        uiAlphaStep = prefs.getInt("ui_alpha_step_v27", 1)
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
        showChannelBannerOnSwitch = prefs.getBoolean("show_banner_v23", true)
        antiBlockEnabled = prefs.getBoolean("anti_block_ru", true)
        userAgentIndex = prefs.getInt("ua_index", 0)
        activePlaylistIndex = prefs.getInt("active_playlist", 0)
        epgUrl = prefs.getString("epg_url", epgUrl) ?: epgUrl
        lastSavedChannelUrl = prefs.getString("last_channel_url", "") ?: ""

        favoriteUrls.clear(); favoriteUrls.addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
        topUrls.clear(); topUrls.addAll(prefs.getStringSet("top_urls", emptySet()) ?: emptySet())
        manualDisabledUrls.clear(); manualDisabledUrls.addAll(prefs.getStringSet("manual_disabled_urls_v19", emptySet()) ?: emptySet())
        autoDisabledUrls.clear(); autoDisabledUrls.addAll(prefs.getStringSet("auto_disabled_urls_v19", emptySet()) ?: emptySet())
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

    private fun switchChannelByOffset(offset: Int) {
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

    override fun onResume() {
        super.onResume()
        enableTrueFullScreenImmersion()
        if (player == null) initPlayer()
        if (allChannels.isEmpty()) loadActivePlaylist(autoPlayLast = true)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
        dualPipPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        isAnalyzerRunning = false
        preloaderExecutor.shutdownNow()
        uiHandler.removeCallbacksAndMessages(null)
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        try { dualPipPlayer?.release() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
    }
}
