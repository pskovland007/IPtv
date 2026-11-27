package com.example.miboxiptv

import android.animation.ValueAnimator
import android.app.Activity
import android.app.DownloadManager
import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.pm.ResolveInfo
import android.media.MediaScannerConnection
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.view.ViewConfiguration
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
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import java.io.IOException
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
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
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.system.exitProcess

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {
    private val systemPlaylistPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                importPlaylistFromUri(uri)
            }
        }
    }

    private val systemEpgPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                importEpgFromUri(uri)
            }
        }
    }

    private var pendingStoragePermissionAction: (() -> Unit)? = null
    private var cachedShareTargets: List<ResolveInfo> = emptyList()
    private var cachedShareFileUri: Uri? = null
    private var lastDiagExportTriggerMs: Long = 0L
    private val updateManager: UpdateManager by lazy { UpdateManager(this) }
    private val backupManager: BackupManager by lazy { BackupManager(this) }
    private var autoCheckUpdatesEnabled: Boolean = true
    @Volatile private var availableUpdateInfo: UpdateManager.ReleaseInfo? = null
    @Volatile private var updateModalRelease: UpdateManager.ReleaseInfo? = null
    private var updateModalManualCheck: Boolean = false
    @Volatile private var pendingUpdateRelease: UpdateManager.ReleaseInfo? = null
    @Volatile private var isUpdateCheckInFlight: Boolean = false
    private var lastSuccessfulUpdateCheckMs: Long = 0L
    private var lastPromptedUpdateKey: String = ""
    private var activeUpdateDownloadHandle: UpdateManager.DownloadHandle? = null
    private var isUpdateDownloading: Boolean = false
    private var downloadedUpdateApkFile: File? = null
    private var onUpdatePermissionResume: (() -> Unit)? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true ||
            hasPublicDownloadWritePermission()
        val action = pendingStoragePermissionAction
        pendingStoragePermissionAction = null
        if (granted) {
            CoreEngines.appendDiagLog("PERM", "Выдано разрешение WRITE_EXTERNAL_STORAGE")
        } else {
            CoreEngines.appendDiagLog("PERM_ERR", "Нет разрешения WRITE_EXTERNAL_STORAGE (проверьте AndroidManifest.xml и Права приложения на ТВ)")
            Toast.makeText(
                this,
                "Нет доступа к папке Download — выдайте разрешение «Память» приложению TiViLook",
                Toast.LENGTH_LONG
            ).show()
        }
        action?.invoke()
    }

    private var player: ExoPlayer? = null
    private var libVlc: LibVLC? = null
    private var vlcPlayer: VlcMediaPlayer? = null
    private var vlcViewsAttached: Boolean = false
    @Volatile private var vlcIsPlayingNow: Boolean = false
    @Volatile private var vlcIsBufferingNow: Boolean = false
    @Volatile private var vlcBufferPercent: Int = 0
    @Volatile private var vlcAppliedSavedTracks: Boolean = false
    private var media3AppliedSavedTracks: Boolean = false
    private var dualPipPlayer: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var lastAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var httpDataSourceFactory: OkHttpDataSource.Factory? = null
    private var wasStoppedInBackground: Boolean = false

    @Volatile private var streamOkHttpClientBacking: OkHttpClient? = null
    @Volatile private var tokenStreamOkHttpClientBacking: OkHttpClient? = null
    @Volatile private var analyzerOkHttpClientBacking: OkHttpClient? = null

    private val streamOkHttpClient: OkHttpClient
        get() = streamOkHttpClientBacking ?: buildStreamOkHttpClient().also { streamOkHttpClientBacking = it }

    private val tokenStreamOkHttpClient: OkHttpClient
        get() = tokenStreamOkHttpClientBacking ?: run {
            val tokenDispatcher = Dispatcher().apply {
                maxRequests = 4
                maxRequestsPerHost = 1
            }
            streamOkHttpClient.newBuilder()
                .dispatcher(tokenDispatcher)
                .connectionPool(ConnectionPool(2, 3, TimeUnit.MINUTES))
                .build()
                .also { tokenStreamOkHttpClientBacking = it }
        }

    private val streamCallFactory: Call.Factory by lazy {
        object : Call.Factory {
            override fun newCall(request: Request): Call {
                val u = request.url.toString()
                return if (CoreEngines.isSingleSessionTokenUrl(u)) {
                    tokenStreamOkHttpClient.newCall(request)
                } else {
                    streamOkHttpClient.newCall(request)
                }
            }
        }
    }

    private val analyzerOkHttpClient: OkHttpClient
        get() = analyzerOkHttpClientBacking ?: streamOkHttpClient.newBuilder()
            .connectTimeout(4000, TimeUnit.MILLISECONDS)
            .readTimeout(4500, TimeUnit.MILLISECONDS)
            .callTimeout(7500, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
            .also { analyzerOkHttpClientBacking = it }

    private fun rebuildNetworkClients() {
        NetworkHelper.updateActiveConfig(
            useDoh = dohEnabled,
            useDpiBypass = dpiBypassEnabled,
            proxyUrl = networkProxyUrl
        )
        runCatching { streamOkHttpClientBacking?.connectionPool?.evictAll() }
        runCatching { tokenStreamOkHttpClientBacking?.connectionPool?.evictAll() }
        streamOkHttpClientBacking = null
        tokenStreamOkHttpClientBacking = null
        analyzerOkHttpClientBacking = null
    }

    private fun buildStreamOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 4
        }
        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(8, 2, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(7000, TimeUnit.MILLISECONDS)
            .readTimeout(7000, TimeUnit.MILLISECONDS)
            .writeTimeout(7000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
        NetworkHelper.applyToOkHttpBuilder(builder)
        NetworkHelper.applyTrustAllSsl(builder)
        return builder.build()
    }

    private lateinit var rootFrame: FrameLayout
    private lateinit var playerContainerFrame: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var vlcVideoLayout: VLCVideoLayout
    private lateinit var gestureHudView: TextView
    private lateinit var dualStreamContainer: FrameLayout
    private lateinit var dualPlayerView: PlayerView
    private lateinit var dualStreamLabel: TextView
    private lateinit var dualCloseBtn: TextView
    private lateinit var nightDimOverlay: View
    private lateinit var crtTurnOffView: CrtTurnOffView
    private lateinit var singleColumnFrame: FrameLayout
    internal lateinit var categoryListView: ListView
    internal lateinit var channelListView: ListView

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
    private lateinit var splashEyeView: SunbeamEyeSplashView

    private val allGridTiles = mutableListOf<ThemedTileView>()
    private val allGridCaptions = mutableListOf<TextView>()
    private val carouselCells = mutableListOf<LinearLayout>()
    private val menuHistory = mutableListOf<String>()

    private var lastMenuChangeMs: Long = 0L
    private var isSideShowingChannels = true
    private var isLeftRailVisible = false
    private var openChannelsDirectlyWithCurrentFocus = true
    private var focusedCategoryPos = 1
    private var focusedChannelPos = 0
    private var topBrowseAnchorUrl: String? = null
    private var topBrowseAnchorName: String? = null
    private var topBrowseAnchorPrevUrl: String? = null
    private var topBrowseAnchorPrevName: String? = null
    private var topBrowseAnchorNextUrl: String? = null
    private var topBrowseAnchorNextName: String? = null
    private var topBrowseAnchorCategoryIndex: Int = -1
    private var topBrowseAnchorFallbackIndex: Int = -1

    internal fun clearTopBrowseAnchor() {
        topBrowseAnchorUrl = null
        topBrowseAnchorName = null
        topBrowseAnchorPrevUrl = null
        topBrowseAnchorPrevName = null
        topBrowseAnchorNextUrl = null
        topBrowseAnchorNextName = null
        topBrowseAnchorCategoryIndex = -1
        topBrowseAnchorFallbackIndex = -1
    }

    internal fun findActiveChannelIndex(list: List<Channel>, target: Channel?, hintPos: Int = -1): Int {
        if (list.isEmpty() || target == null) return -1
        if (hintPos in list.indices && list[hintPos].url == target.url && list[hintPos].name == target.name) {
            return hintPos
        }
        val exactIdx = list.indexOfFirst { it.url == target.url && it.name == target.name }
        if (exactIdx >= 0) return exactIdx
        if (hintPos in list.indices && list[hintPos].url == target.url) {
            return hintPos
        }
        return list.indexOfFirst { it.url == target.url }
    }
    private var centerLongPressHandled = false

    private var brightnessPercent = 100
    private var sleepTimerMinutesLeft = 0
    private var sleepTimerRunnable: Runnable? = null

    private var uiScaleStep = 1
    private var uiAlphaStep = 1
    private var currentAnimatedAlphaInt = 212
    private var alphaAnimator: ValueAnimator? = null
    private var cameraCutoutSafeOffset = false

    private var ambilightMode = 2
    private var ambilightHue = 195f
    private var capturedFrameColor: Int = Color.parseColor("#00B0FF")

    private var afrEnabled = false
    private var pixelOrbiterEnabled = true
    private var orbiterStep = 0
    private var drcNightAudioEnabled = false
    private var audioBoostPercent = 100
    private var dohEnabled = true
    private var dpiBypassEnabled = true
    private var networkProxyUrl = ""
    private var playerEngineMode = 0 // 0 = Media3, 1 = LibVLC
    private var preferredAudioLang = "rus"
    private var subtitlesEnabled = false
    private var touchEdgeGesturesEnabled = false
    private var edgeGestureActiveMode = 0 // 0 = none, 1 = brightness, 2 = volume
    private var edgeGestureStartX = 0f
    private var edgeGestureStartY = 0f
    private var edgeGestureStartBrightness = 0.7f
    private var edgeGestureStartVolume = 0
    private var edgeGestureMaxVolume = 15
    private val hideGestureHudRunnable = Runnable {
        if (::gestureHudView.isInitialized) {
            gestureHudView.animate().alpha(0f).setDuration(200L).withEndAction {
                gestureHudView.visibility = View.GONE
                gestureHudView.alpha = 1f
            }.start()
        }
    }
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
    private var silentFallbackAttemptedForUrl: String = ""

    private lateinit var infoTitleView: TextView
    private lateinit var infoTextView: TextView
    private val infoRowViews = mutableListOf<TextView>()
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

    private lateinit var subModalHeaderRow: LinearLayout
    private lateinit var subModalBadgeView: TextView
    private lateinit var subModalTitle: TextView
    private var subModalBaseTitleText: String = ""
    private lateinit var subModalTopButtons: LinearLayout
    private lateinit var subModalCustomBody: LinearLayout
    private lateinit var subModalBottomButtons: LinearLayout
    private lateinit var subModalList: ListView
    private lateinit var subModalInputContainer: LinearLayout
    private lateinit var subModalThemesGrid: LinearLayout
    private var selectedPlaylistModalIdx: Int = 0
    private var renamingPlaylistIdx: Int = -1
    private var selectedMoreSettingsTab: Int = 0
    private var selectedTopModalIdx: Int = 0
    private lateinit var subModalAdapter: ArrayAdapter<String>
    private val subModalItems = mutableListOf<String>()
    private lateinit var subModalStatsAdapter: ArrayAdapter<StatsModalRowData>
    private val subModalStatsItems = mutableListOf<StatsModalRowData>()
    private var animateNextSubModalChange = false
    private var subModalHighlightedPos = 0
    private var lastSwipeLeftFromIdleMs: Long = 0L

    private lateinit var currentChannelBanner: TextView

    private val uiHandler = Handler(Looper.getMainLooper())
    private var isWatchdogTickerActive = false
    private val allChannels = CopyOnWriteArrayList<Channel>()
    private val displayedChannels = CopyOnWriteArrayList<Channel>()
    private val dynamicCategories = CopyOnWriteArrayList<String>()
    private val favoriteUrls = ConcurrentHashMap.newKeySet<String>()
    private val topUrls = ConcurrentHashMap.newKeySet<String>()
    private val manualDisabledUrls = ConcurrentHashMap.newKeySet<String>()
    private val autoDisabledUrls = ConcurrentHashMap.newKeySet<String>()
    private val disabledCategories = ConcurrentHashMap.newKeySet<String>()
    private val customCategoryMap = ConcurrentHashMap<String, String>()
    private val backupMirrorsMap = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val playlists = CopyOnWriteArrayList<PlaylistEntry>()

    private val channelHealthMap = ConcurrentHashMap<String, Boolean>()
    private val aliveChannelsAtomic = AtomicInteger(0)
    private val deadChannelsAtomic = AtomicInteger(0)
    @Volatile private var isAnalyzerRunning = false
    @Volatile private var playlistLoadGeneration = 0
    internal var selectedAnalyzerCategory = "Все каналы"
    internal var selectedEditorCategory: String = ""

    private val preloadedLock = Any()
    private val preloadedNeighbors = HashSet<String>()
    private val preloaderExecutor = Executors.newFixedThreadPool(2)

    private val epgById = ConcurrentHashMap<String, MutableList<EpgProgram>>()
    private val epgByName = ConcurrentHashMap<String, MutableList<EpgProgram>>()
    @Volatile private var epgLoadedCount = 0

    internal lateinit var categoryAdapter: ArrayAdapter<String>
    internal lateinit var channelListAdapter: ArrayAdapter<String>
    internal val categoryDisplayItems = mutableListOf<String>()
    internal val channelDisplayItems = mutableListOf<String>()

    private var currentCategoryIndex = 1
    private var currentChannel: Channel? = null
    private var pendingDebouncedChannel: Channel? = null
    private var lastSavedChannelUrl: String = ""
    private var lastSavedChannelName: String = ""
    private var lastSavedChannelGroup: String = ""
    private var activePlaylistIndex = 0
    private var bufferLevel = 1
    private var decoderMode = 1
    private var themeIndex = 0
    private var screenAspectMode = 0

    private var lowSignalStuckSeconds = 0
    private var continuousBufferingTicks = 0
    private var lastChannelSwitchMs: Long = 0L
    private var consecutiveDropFailures = 0
    private var streamRetriesExhausted = false
    private var lastRenderedFramePositionMs = -1L

    private var currentBrowseDir: File = Environment.getExternalStorageDirectory()

    private val ruCollator: Collator = Collator.getInstance(Locale("ru", "RU")).apply {
        strength = Collator.PRIMARY
    }

    private val themes = listOf(
        AppThemeStyle(
            0, "Материал Дизайн",
            Color.parseColor("#EE242C3D"), Color.parseColor("#FF2F3A4F"),
            Color.parseColor("#FF3B82F6"), Color.parseColor("#8864748B"),
            Color.parseColor("#FFF1F5F9"), Color.WHITE, 28f,
            Typeface.create("sans-serif-medium", Typeface.NORMAL),
            Typeface.create("sans-serif-medium", Typeface.BOLD)
        ),
        AppThemeStyle(
            1, "Неон",
            Color.parseColor("#F006050A"), Color.parseColor("#FF181526"),
            Color.parseColor("#FF00E5FF"), Color.parseColor("#88FF7597"),
            Color.WHITE, Color.parseColor("#FF06050A"), 22f,
            Typeface.create("sans-serif-medium", Typeface.NORMAL),
            Typeface.create("sans-serif-medium", Typeface.BOLD)
        ),
        AppThemeStyle(
            2, "Амолед Блэк",
            Color.parseColor("#F8000000"), Color.parseColor("#FF161618"),
            Color.WHITE, Color.parseColor("#88FFFFFF"),
            Color.WHITE, Color.BLACK, 18f,
            Typeface.create("sans-serif-light", Typeface.NORMAL),
            Typeface.create("sans-serif-medium", Typeface.BOLD)
        ),
        AppThemeStyle(
            3, "Монохром",
            Color.parseColor("#F4F4F4F5"), Color.parseColor("#FFE4E4E7"),
            Color.parseColor("#FF52525B"), Color.parseColor("#99A1A1AA"),
            Color.parseColor("#FF27272A"), Color.WHITE, 14f,
            Typeface.create("sans-serif-condensed", Typeface.NORMAL),
            Typeface.create("sans-serif-condensed", Typeface.BOLD)
        ),
        AppThemeStyle(
            4, "Ретро",
            Color.parseColor("#F21F1D1B"), Color.parseColor("#FF322E2B"),
            Color.parseColor("#FFD4892A"), Color.parseColor("#88277A68"),
            Color.parseColor("#FFF5E6CC"), Color.parseColor("#FF1F1D1B"), 24f,
            Typeface.SERIF, Typeface.create(Typeface.SERIF, Typeface.BOLD)
        ),
        AppThemeStyle(
            5, "Карусель",
            Color.parseColor("#EE161922"), Color.parseColor("#FF232836"),
            Color.parseColor("#FF29B6F6"), Color.parseColor("#8829B6F6"),
            Color.WHITE, Color.parseColor("#FF0B101B"), 30f,
            Typeface.create("sans-serif-medium", Typeface.NORMAL),
            Typeface.create("sans-serif-medium", Typeface.BOLD),
            isCarouselMenu = true
        )
    )

    private fun currentTheme(): AppThemeStyle = themes[themeIndex.coerceIn(0, themes.lastIndex)]
    private fun themeFont(): Typeface = currentTheme().normalFont
    private fun themeBoldFont(): Typeface = currentTheme().boldFont

    private fun uiScaleFactor(): Float = when (uiScaleStep) {
        0 -> 1.0f
        1 -> 1.15f
        else -> 1.28f
    }

    private fun dpPx(dp: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
    ).toInt().coerceAtLeast(1)

    private fun exactModalRowHeightPx(): Int = dpPx(34f * uiScaleFactor()).coerceAtLeast((54f * uiScaleFactor()).toInt())

    private fun sidePanelPadPx(): Int = dpPx(10f).coerceAtLeast(18)

    private fun applySidePanelBackgroundAndPadding() {
        sidePanel.setBackgroundDrawable(makeThemedPanelBg())
        val p = sidePanelPadPx()
        sidePanel.setPadding(p, p, p, p)
    }

    private fun applySubModalBackgroundAndPadding() {
        subModalOverlay.setBackgroundDrawable(makeThemedPanelBg())
        val ph = dpPx(18f).coerceAtLeast(24)
        val pv = dpPx(14f).coerceAtLeast(20)
        subModalOverlay.setPadding(ph, pv, ph, pv)
    }

    private fun targetPanelAlphaInt(): Int = when (uiAlphaStep) {
        0 -> 244
        1 -> 212
        else -> 165
    }

    private fun uiPanelAlphaInt(): Int = currentAnimatedAlphaInt

    private fun animatePanelAlphaTransition() {
        val startA = currentAnimatedAlphaInt
        val endA = targetPanelAlphaInt()
        alphaAnimator?.cancel()
        alphaAnimator = ValueAnimator.ofInt(startA, endA).apply {
            duration = 250L
            addUpdateListener { anim ->
                currentAnimatedAlphaInt = anim.animatedValue as Int
                refreshPanelBackgroundsOnly()
            }
            start()
        }
    }

    private fun refreshPanelBackgroundsOnly() {
        if (sidePanel.visibility == View.VISIBLE) {
            applySidePanelBackgroundAndPadding()
            leftIconRail.setBackgroundDrawable(makeThemedSideCardBg())
            categoryListView.setBackgroundDrawable(makeThemedSideCardBg())
            channelListView.setBackgroundDrawable(makeThemedSideCardBg())
        }
        if (infoPanel.visibility == View.VISIBLE) infoPanel.setBackgroundDrawable(makeThemedPanelBg())
        if (centerOsdPanel.visibility == View.VISIBLE) centerOsdPanel.setBackgroundDrawable(makeThemedPanelBg())
        if (settingsTileOverlay.visibility == View.VISIBLE) settingsTileOverlay.setBackgroundDrawable(makeThemedPanelBg())
        if (subModalOverlay.visibility == View.VISIBLE) {
            applySubModalBackgroundAndPadding()
            subModalList.setBackgroundDrawable(makeThemedSubBg())
            if (::subModalCustomBody.isInitialized && subModalCustomBody.visibility == View.VISIBLE) {
                subModalCustomBody.setBackgroundDrawable(makeThemedSubBg())
            }
            if (::subModalThemesGrid.isInitialized && subModalThemesGrid.visibility == View.VISIBLE) {
                subModalThemesGrid.setBackgroundDrawable(makeThemedSubBg())
            }
        }
        if (fullEpgOverlay.visibility == View.VISIBLE) {
            fullEpgOverlay.setBackgroundDrawable(makeThemedPanelBg())
            fullEpgListView.setBackgroundDrawable(makeThemedSubBg())
        }
    }

    private fun leftSafeMarginPx(): Int = if (cameraCutoutSafeOffset) 68 else 16

    private fun sidePanelWidthPx(): Int {
        val dm = resources.displayMetrics
        val fraction = when (uiScaleStep) {
            0 -> 0.50f
            1 -> 0.56f
            else -> 0.62f
        }
        val minW = dpPx(350f).coerceAtMost((dm.widthPixels * 0.66f).toInt())
        val maxW = (dm.widthPixels * 0.66f).toInt().coerceAtLeast(minW)
        return (dm.widthPixels * fraction).toInt().coerceIn(minW, maxW)
    }

    private val defaultTenCategories = listOf(
        "★ Избранное", "Все каналы", "2K", "Кино и сериалы", "Спорт",
        "Детские", "Познавательные", "Музыка", "Новости и общие", "Развлечения", "Прочее"
    )

    private fun activeCategoryList(): List<String> {
        return if (dynamicCategories.isNotEmpty()) dynamicCategories else defaultTenCategories
    }

    private val openRusSources = listOf(
        "https://loganettv.github.io/playlists/all.m3u",
        "https://loganettv.github.io/playlists/movie.m3u",
        "https://loganettv.github.io/playlists/kids.m3u",
        "https://loganettv.github.io/playlists/sport.m3u",
        "https://loganettv.github.io/playlists/music.m3u",
        "https://raw.githubusercontent.com/CrocoUser/zabava-project/main/zabava-full.m3u",
        "https://m3u.su/ruz",
        "https://m3u.su/kk2",
        "https://m3u.su/tru",
        "https://m3u.su/rut",
        "https://m3u.su/runtv",
        "https://m3u.su/d1",
        "https://ngrch.github.io/iptv/ru.m3u",
        "https://ngrch.github.io/iptv/cartoons.m3u",
        "https://ngrch.github.io/iptv/music.m3u",
        "https://spirt007.github.io/Tvru/Rus.m3u",
        "https://cdn.jsdelivr.net/gh/Free-TV/IPTV@master/playlists/playlist_russia.m3u8",
        "https://iptv-org.github.io/iptv/countries/ru.m3u",
        "https://iptv-org.github.io/iptv/countries/by.m3u",
        "https://iptv-org.github.io/iptv/categories/music.m3u",
        "https://iptv-org.github.io/iptv/categories/sports.m3u"
    )

    private val epgUrls = listOf(
        "https://iptvx.one/epg/epg_lite.xml.gz",
        "https://iptvx.one/epg/epg.xml.gz",
        "https://epg.it999.ru/epg.xml.gz",
        "https://teleguide.info/download/new3/xmltv.xml.gz"
    )
    private var epgUrl = epgUrls[0]
    private var localEpgFilePath = ""

    private val autoHideMenuRunnable = Runnable {
        if (menuHistory.isNotEmpty()) {
            val currentMenu = menuHistory.last()
            if ((currentMenu == "ANALYZER_GROUP_VIEW" || currentMenu == "ANALYZER_GROUPS") && isAnalyzerRunning) {
                collapseAnalyzerToTopBar()
            } else if (currentMenu == "UPDATE_PROMPT" && (isUpdateDownloading || downloadedUpdateApkFile != null || updateModalManualCheck)) {
                // Не закрываем окно обновления во время загрузки или ручной проверки
            } else if (currentMenu !in listOf("EPG", "ADD_URL", "RENAME_PLAYLIST", "PROXY_INPUT", "MANUAL_PATH")) {
                closeAllMenus()
            }
        }
    }

    private fun collapseAnalyzerToTopBar() {
        subModalOverlay.animate()
            .translationY(-480f).scaleX(0.35f).scaleY(0.25f).alpha(0f)
            .setDuration(340L)
            .withEndAction {
                subModalOverlay.translationY = 0f; subModalOverlay.scaleX = 1f; subModalOverlay.scaleY = 1f; subModalOverlay.alpha = 1f
                closeAllMenus()
                analyzerStatusBanner.visibility = View.VISIBLE
            }.start()
    }

    private var isPlayerCurrentlyMini = false

    private fun setPlayerMiniPreviewMode(mini: Boolean) {
        if (isPlayerCurrentlyMini == mini) return
        isPlayerCurrentlyMini = mini
        val dm = resources.displayMetrics
        playerContainerFrame.animate().cancel()

        if (mini) {
            val curMenu = menuHistory.lastOrNull()
            val sideWidthPx = sidePanelWidthPx()
            val leftMenuEdgePx = if (curMenu == "SIDE") {
                sideWidthPx + leftSafeMarginPx() + 12
            } else {
                (dm.widthPixels * 0.50f).toInt()
            }
            val rightPadPx = 14
            val availW = (dm.widthPixels - leftMenuEdgePx - rightPadPx).coerceAtLeast(360)
            val availH = (dm.heightPixels - 32).coerceAtLeast(240)
            val isWideScreenPhone = (controlMode == 1 || dm.widthPixels.toFloat() / dm.heightPixels.toFloat() > 1.85f)
            val targetScale = if (isWideScreenPhone) {
                val videoBaseW = minOf(dm.widthPixels.toFloat(), dm.heightPixels.toFloat() * 16f / 9f).coerceAtLeast(360f)
                val videoBaseH = minOf(dm.heightPixels.toFloat(), dm.widthPixels.toFloat() * 9f / 16f).coerceAtLeast(240f)
                minOf(availW.toFloat() / videoBaseW, availH.toFloat() / videoBaseH).coerceIn(0.45f, 0.94f)
            } else {
                (availW.toFloat() / dm.widthPixels.toFloat()).coerceIn(0.38f, 0.54f)
            }
            val targetCenterX = leftMenuEdgePx + availW / 2f
            val shiftX = targetCenterX - dm.widthPixels / 2f

            playerContainerFrame.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .translationX(shiftX)
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()
        } else {
            playerContainerFrame.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(DecelerateInterpolator(1.3f))
                .start()
        }
    }

    private fun adjustInfoPanelMarginForDualStream() {
        val dm = resources.displayMetrics
        val topMarginPx = if (isDualStreamActive) 320 else 68
        val panelW = (620 * uiScaleFactor()).toInt().coerceIn(500, (dm.widthPixels * 0.56f).toInt().coerceAtLeast(500))
        infoPanel.layoutParams = FrameLayout.LayoutParams(panelW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(0, topMarginPx, 24, 0)
        }
    }

    private val ambilightSampleBitmap: Bitmap by lazy { Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888) }
    @Volatile private var isPixelCopyInFlight: Boolean = false

    private fun sampleVideoFrameColorIfPossible() {
        if (isVlcEngineActive() || ambilightMode != 3 || isAnalyzerRunning || menuHistory.isEmpty() || isPixelCopyInFlight || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            val sv = playerView.videoSurfaceView as? SurfaceView ?: return
            if (!sv.holder.surface.isValid) return
            val bmp = ambilightSampleBitmap
            if (bmp.isRecycled) return
            isPixelCopyInFlight = true
            PixelCopy.request(sv, bmp, { copyResult ->
                try {
                    if (copyResult == PixelCopy.SUCCESS && !bmp.isRecycled) {
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
                } finally {
                    isPixelCopyInFlight = false
                }
            }, uiHandler)
        } catch (_: Exception) {
            isPixelCopyInFlight = false
        }
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
        return Color.argb(maxAlpha.coerceIn(24, 245), Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
    }

    private fun makeThemedPanelBg(): LayerDrawable {
        val t = currentTheme()
        val a = uiPanelAlphaInt()
        val panelColorWithAlpha = Color.argb(a, Color.red(t.panelBgColor), Color.green(t.panelBgColor), Color.blue(t.panelBgColor))
        val glowAlphas = if (ambilightMode == 0) {
            intArrayOf(8, 16, 26, 38, 52, 70)
        } else {
            intArrayOf(12, 24, 42, 66, 98, 138)
        }
        val layers = ArrayList<GradientDrawable>(glowAlphas.size + 1)
        val totalAura = glowAlphas.size * 2
        for (i in glowAlphas.indices) {
            val inset = i * 2
            val layer = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = t.cornerRadiusDp + (totalAura - inset).toFloat()
                setStroke(3, ambilightColor(glowAlphas[i]))
            }
            layers.add(layer)
        }
        val mainCard = GradientDrawable().apply {
            setColor(panelColorWithAlpha)
            cornerRadius = t.cornerRadiusDp
            setStroke(2, ambilightColor(if (ambilightMode == 0) 110 else 205))
        }
        layers.add(mainCard)
        return LayerDrawable(layers.toTypedArray()).apply {
            for (i in glowAlphas.indices) {
                val inset = i * 2
                setLayerInset(i, inset, inset, inset, inset)
            }
            setLayerInset(glowAlphas.size, totalAura, totalAura, totalAura, totalAura)
        }
    }

    private fun makeThemedSubBg(): GradientDrawable {
        val t = currentTheme()
        val a = (uiPanelAlphaInt() - 15).coerceIn(130, 245)
        val subColorWithAlpha = Color.argb(a, Color.red(t.cardBgColor), Color.green(t.cardBgColor), Color.blue(t.cardBgColor))
        return GradientDrawable().apply {
            setColor(subColorWithAlpha)
            cornerRadius = (t.cornerRadiusDp - 6f).coerceAtLeast(12f)
        }
    }

    private fun makeThemedSideCardBg(): GradientDrawable {
        val t = currentTheme()
        val a = (uiPanelAlphaInt() - 8).coerceIn(145, 248)
        val subColorWithAlpha = Color.argb(a, Color.red(t.cardBgColor), Color.green(t.cardBgColor), Color.blue(t.cardBgColor))
        return GradientDrawable().apply {
            setColor(subColorWithAlpha)
            cornerRadius = (t.cornerRadiusDp - 6f).coerceAtLeast(12f)
            setStroke(dpPx(1f), themeCardBorderColor())
        }
    }

    private fun animateMenuAppear(view: View, fromX: Float = 0f, fromY: Float = 24f) {
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationX = fromX * 0.6f
        view.translationY = fromY * 0.6f
        view.scaleX = 0.96f
        view.scaleY = 0.96f
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }

    private fun resetMenuInactivityTimer() {
        uiHandler.removeCallbacks(autoHideMenuRunnable)
        if (menuHistory.isNotEmpty()) {
            val cur = menuHistory.last()
            val delay = when {
                (cur == "ANALYZER_GROUP_VIEW" || cur == "ANALYZER_GROUPS") && isAnalyzerRunning -> 5000L
                cur == "UPDATE_PROMPT" -> 24000L
                else -> menuHideDelaySec * 1000L
            }
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

    private fun isVlcEngineActive(): Boolean = playerEngineMode == 1

    private fun applyVlcAudioBoost() {
        val vlc = vlcPlayer ?: return
        val mutedSplash = ::splashOverlay.isInitialized && splashOverlay.visibility == View.VISIBLE
        val volPct = when {
            mutedSplash -> 0
            audioBoostPercent == 130 -> 130
            audioBoostPercent == 160 -> 160
            audioBoostPercent == 200 -> 200
            drcNightAudioEnabled -> 90
            else -> 100
        }
        runCatching { vlc.setVolume(volPct) }
    }

    private fun applyAudioEnhancements() {
        if (isVlcEngineActive()) {
            applyVlcAudioBoost()
            return
        }
        val p = player ?: return
        try {
            val sessionId = p.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
                if (loudnessEnhancer == null || lastAudioSessionId != sessionId) {
                    try { loudnessEnhancer?.release() } catch (_: Exception) {}
                    loudnessEnhancer = LoudnessEnhancer(sessionId)
                    lastAudioSessionId = sessionId
                }
                val boostMb = when (audioBoostPercent) {
                    130 -> 350; 160 -> 700; 200 -> 1100
                    else -> if (drcNightAudioEnabled) 250 else 0
                }
                loudnessEnhancer?.setTargetGain(boostMb)
                loudnessEnhancer?.enabled = (boostMb > 0)
            }
            if (::splashOverlay.isInitialized && splashOverlay.visibility == View.VISIBLE) {
                p.volume = 0f
            } else {
                p.volume = if (drcNightAudioEnabled && audioBoostPercent == 100) 0.88f else 1.0f
            }
        } catch (_: Exception) {}
    }

    private fun stepPixelOrbiter() {
        if (!pixelOrbiterEnabled) {
            playerView.translationX = 0f; playerView.translationY = 0f
            if (::vlcVideoLayout.isInitialized) { vlcVideoLayout.translationX = 0f; vlcVideoLayout.translationY = 0f }
            clockTextView.translationX = 0f; clockTextView.translationY = 0f
            signalContainer.translationX = 0f; signalContainer.translationY = 0f
            return
        }
        orbiterStep = (orbiterStep + 1) % 8
        val videoShifts = listOf(
            Pair(2f, 0f), Pair(2f, 2f), Pair(0f, 2f), Pair(-2f, 2f),
            Pair(-2f, 0f), Pair(-2f, -2f), Pair(0f, -2f), Pair(2f, -2f)
        )
        val hudShifts = listOf(
            Pair(0f, 0f), Pair(5f, 3f), Pair(10f, 5f), Pair(6f, -4f),
            Pair(0f, -6f), Pair(-6f, -4f), Pair(-10f, 3f), Pair(-5f, 6f)
        )
        if (menuHistory.isEmpty()) {
            val vx = videoShifts[orbiterStep].first
            val vy = videoShifts[orbiterStep].second
            playerView.translationX = vx; playerView.translationY = vy
            if (::vlcVideoLayout.isInitialized) { vlcVideoLayout.translationX = vx; vlcVideoLayout.translationY = vy }
        } else {
            playerView.translationX = 0f; playerView.translationY = 0f
            if (::vlcVideoLayout.isInitialized) { vlcVideoLayout.translationX = 0f; vlcVideoLayout.translationY = 0f }
        }
        val hx = hudShifts[orbiterStep].first
        val hy = hudShifts[orbiterStep].second
        clockTextView.animate().translationX(hx).translationY(hy).setDuration(900L).start()
        signalContainer.animate().translationX(-hx).translationY(hy).setDuration(900L).start()
    }

    private fun powerOffAppWithCrtEffect() {
        saveLastChannelOnly(syncCommit = true)
        saveState(syncCommit = true)
        closeAllMenus()
        try { player?.stop() } catch (_: Exception) {}
        try { vlcPlayer?.stop() } catch (_: Exception) {}
        try { dualPipPlayer?.stop() } catch (_: Exception) {}
        crtTurnOffView.playCrtAnimation {
            try { player?.release(); player = null } catch (_: Exception) {}
            releaseVlcPlayer()
            try { dualPipPlayer?.release(); dualPipPlayer = null } catch (_: Exception) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                finish()
            }
            exitProcess(0)
        }
    }

    private fun styleButtonForCurrentTheme(btn: TextView, isFocused: Boolean) {
        val curT = currentTheme()
        btn.setBackgroundDrawable(GradientDrawable().apply {
            setColor(if (isFocused) curT.accentColor else curT.cardBgColor)
            cornerRadius = 15f
            setStroke(if (isFocused) 2 else 1, if (isFocused) Color.WHITE else curT.strokeColor)
        })
        btn.setTextColor(if (isFocused) curT.textOnAccent else curT.textPrimaryColor)
    }

    private fun isThemeDarkSurface(color: Int): Boolean {
        val lum = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
        return lum < 145
    }

    private fun themeInnerCornerRadiusPx(scale: Float = uiScaleFactor()): Float {
        val baseDp = when (currentTheme().id) {
            3 -> 7f   // Монохром — строгие углы
            2 -> 9f   // Амолед Блэк — чёткие углы
            1 -> 11f  // Неон
            4 -> 12f  // Ретро
            else -> 14f // Материал Дизайн / Карусель
        }
        return dpPx(baseDp * scale).toFloat()
    }

    private fun themeFocusStrokeColor(): Int {
        return when (currentTheme().id) {
            2 -> Color.parseColor("#94A3B8") // Амолед Блэк (акцент белый -> контрастная серебристая рамка)
            3 -> Color.parseColor("#18181B") // Монохром (светлый фон -> графитовая рамка)
            1 -> Color.parseColor("#FF7597") // Неон (фирменный розовый контур)
            4 -> Color.parseColor("#FFF5E6CC") // Ретро (тёплый кремовый)
            else -> Color.WHITE
        }
    }

    private fun themeCardBorderColor(): Int {
        val t = currentTheme()
        val dark = isThemeDarkSurface(t.panelBgColor)
        return when (t.id) {
            1 -> Color.argb(132, 255, 117, 151) // Неон — розовый вторичный контур
            4 -> Color.argb(138, 39, 122, 104)  // Ретро — винтажный изумрудный контур
            2 -> Color.argb(95, 255, 255, 255)  // Амолед — чёткая тонкая линия
            3 -> Color.argb(95, 82, 82, 91)     // Монохром — графитовый контур
            else -> Color.argb(if (dark) 62 else 52, Color.red(t.strokeColor), Color.green(t.strokeColor), Color.blue(t.strokeColor))
        }
    }

    private fun themeOnPillBgColor(): Int {
        return when (currentTheme().id) {
            1 -> Color.parseColor("#00F5A0") // Неон — яркий неоновый мятный
            2 -> Color.WHITE                 // Амолед — чистый монохромный белый
            3 -> Color.parseColor("#27272A") // Монохром — строгий графит
            4 -> Color.parseColor("#2E8B6E") // Ретро — винтажный изумруд
            else -> Color.parseColor("#22C55E")
        }
    }

    private fun themeOnPillTextColor(): Int {
        return when (currentTheme().id) {
            1 -> Color.parseColor("#041F17")
            2 -> Color.BLACK
            3 -> Color.WHITE
            4 -> Color.parseColor("#FFF8EB")
            else -> Color.parseColor("#052E16")
        }
    }

    private fun computeCenteredModalWidth(baseFraction: Float): Int {
        val w = resources.displayMetrics.widthPixels
        val scaleBoost = when (uiScaleStep) {
            0 -> 0.0f
            1 -> 0.08f
            else -> 0.15f
        }
        val targetFraction = (baseFraction + scaleBoost).coerceIn(0.68f, 0.94f)
        val minW = dpPx(500f * uiScaleFactor()).coerceAtMost((w * 0.94f).toInt())
        val maxW = (w * 0.94f).toInt().coerceAtLeast(minW)
        return (w * targetFraction).toInt().coerceIn(minW, maxW)
    }

    private fun computeCenteredModalHeight(baseFraction: Float): Int {
        val h = resources.displayMetrics.heightPixels
        val scaleBoost = when (uiScaleStep) {
            0 -> 0.0f
            1 -> 0.08f
            else -> 0.14f
        }
        val targetFraction = (baseFraction + scaleBoost).coerceIn(0.56f, 0.92f)
        val minH = dpPx(280f * uiScaleFactor()).coerceAtMost((h * 0.92f).toInt())
        val maxH = (h * 0.92f).toInt().coerceAtLeast(minH)
        return (h * targetFraction).toInt().coerceIn(minH, maxH)
    }

    private fun applyCenteredModalDimensions(menuId: String) {
        val isWrapHeight = menuId in listOf(
            "EPG", "ADD_URL", "RENAME_PLAYLIST", "PROXY_INPUT"
        )
        val isScrollCardModal = menuId in listOf("BUFFER", "DECODER", "AUDIO", "UPDATE_PROMPT")
        val modalWidthPx = when (menuId) {
            "PLAYLISTS", "FULL_EPG", "MORE_SETTINGS", "THEMES" -> computeCenteredModalWidth(0.79f)
            "DIAG_LOG_VIEW", "ANALYZER_GROUPS", "ANALYZER_GROUP_VIEW", "CHANNEL_EDITOR_GROUPS", "CHANNEL_EDITOR_ITEMS" -> computeCenteredModalWidth(0.78f)
            "BRIGHTNESS_MENU", "CHANNEL_ACTIONS", "MOVE_CHANNEL_CATEGORY" -> computeCenteredModalWidth(0.68f)
            else -> computeCenteredModalWidth(0.74f)
        }
        val modalHeightPx = when {
            isWrapHeight -> FrameLayout.LayoutParams.WRAP_CONTENT
            isScrollCardModal -> computeCenteredModalHeight(if (menuId == "AUDIO") 0.74f else 0.68f)
            menuId == "BRIGHTNESS_MENU" || menuId == "CHANNEL_ACTIONS" -> computeCenteredModalHeight(0.58f)
            menuId == "POWER_AND_SLEEP_MENU" || menuId == "TOP_MANAGER" -> computeCenteredModalHeight(0.70f)
            else -> computeCenteredModalHeight(0.78f)
        }
        val mH = dpPx(12f)
        val mV = dpPx(12f)
        subModalOverlay.layoutParams = FrameLayout.LayoutParams(
            modalWidthPx,
            modalHeightPx,
            Gravity.CENTER
        ).apply {
            setMargins(mH, mV, mH, mV)
        }
    }

    private fun styleModalMonogramBadge(badge: TextView) {
        val t = currentTheme()
        val scale = uiScaleFactor()
        val minW = dpPx(38f * scale)
        val hPx = dpPx(30f * scale)
        val padH = dpPx(8f * scale)
        badge.setTextColor(t.textOnAccent)
        badge.typeface = t.boldFont
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f * scale)
        badge.gravity = Gravity.CENTER
        badge.isSingleLine = true
        badge.maxLines = 1
        badge.includeFontPadding = false
        badge.minWidth = minW
        badge.setPadding(padH, 0, padH, 0)
        badge.setBackgroundDrawable(GradientDrawable().apply {
            setColor(t.accentColor)
            cornerRadius = (themeInnerCornerRadiusPx(scale) * 0.65f).coerceAtLeast(dpPx(5f).toFloat())
            if (t.id == 1 || t.id == 4) {
                setStroke(dpPx(1f), themeCardBorderColor())
            }
        })
        badge.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, hPx).apply {
            rightMargin = dpPx(10f * scale)
        }
    }

    private fun resolveMonogramForTitle(title: String): String {
        val low = title.lowercase(Locale.getDefault())
        return when {
            low.contains("плейлист") || low.contains("переименов") -> "ПЛ"
            low.contains("буфер") -> "БФ"
            low.contains("декодер") -> "ДК"
            low.contains("телегид") || low.contains("xmltv") || low.contains("epg") -> "ТГ"
            low.contains("редактор") -> "РД"
            low.contains("тест") -> "ТС"
            low.contains("тем") -> "ТМ"
            low.contains("топ") -> "ТП"
            low.contains("звук") || low.contains("drc") -> "ЗВ"
            low.contains("управлен") -> "УП"
            low.contains("общие") || low.contains("обновлен") -> "ОБ"
            low.contains("яркость") -> "ЯР"
            low.contains("сон") || low.contains("сна") || low.contains("таймер") -> "СН"
            low.contains("лог") || low.contains("поделить") -> "ЛО"
            low.contains("групп") -> "ГР"
            low.contains("канал") -> "КН"
            else -> title.trim().take(2).uppercase(Locale.getDefault()).ifBlank { "TV" }
        }
    }

    private fun makeUnifiedActionButton(
        label: String,
        isDanger: Boolean = false,
        isEnabledState: Boolean = true,
        fillWidth: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)
        fun applyBtnStyle(tv: TextView, focused: Boolean) {
            val curT = currentTheme()
            val bg = when {
                !isEnabledState -> if (dark) Color.argb(42, 255, 255, 255) else Color.argb(24, 0, 0, 0)
                focused && isDanger -> Color.parseColor("#DC2626")
                focused -> curT.accentColor
                isDanger -> if (dark) Color.argb(85, 185, 28, 28) else Color.argb(46, 220, 38, 38)
                else -> if (dark) Color.argb(82, 255, 255, 255) else Color.argb(36, 0, 0, 0)
            }
            val strokeCol = when {
                focused -> themeFocusStrokeColor()
                isDanger && isEnabledState -> Color.argb(130, 248, 113, 113)
                else -> themeCardBorderColor()
            }
            val txtCol = when {
                !isEnabledState -> Color.argb(105, Color.red(curT.textPrimaryColor), Color.green(curT.textPrimaryColor), Color.blue(curT.textPrimaryColor))
                focused && isDanger -> Color.WHITE
                focused -> curT.textOnAccent
                isDanger -> if (dark) Color.parseColor("#FCA5A5") else Color.parseColor("#B91C1C")
                else -> curT.textPrimaryColor
            }
            tv.setBackgroundDrawable(GradientDrawable().apply {
                setColor(bg)
                cornerRadius = themeInnerCornerRadiusPx(scale)
                setStroke(if (focused) dpPx(2f) else dpPx(1f), strokeCol)
            })
            tv.setTextColor(txtCol)
        }
        return TextView(this).apply {
            text = label
            isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.8f * scale)
            typeface = t.boldFont
            isFocusable = isEnabledState
            isFocusableInTouchMode = false
            isClickable = isEnabledState
            isEnabled = isEnabledState
            val padH = dpPx(12f * scale)
            val padV = dpPx(7f * scale)
            setPadding(padH, padV, padH, padV)
            layoutParams = if (fillWidth) {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    val mv = dpPx(3f * scale)
                    setMargins(0, mv, 0, mv)
                }
            } else {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    val mh = dpPx(4f * scale)
                    val mv = dpPx(2f * scale)
                    setMargins(mh, mv, mh, mv)
                }
            }
            applyBtnStyle(this, false)
            setOnFocusChangeListener { v, hasFocus ->
                applyBtnStyle(this, hasFocus)
                v.animate().scaleX(if (hasFocus) 1.03f else 1f).scaleY(if (hasFocus) 1.03f else 1f).setDuration(110L).start()
                if (hasFocus) resetMenuInactivityTimer()
            }
            setOnClickListener {
                if (!isEnabledState) return@setOnClickListener
                resetMenuInactivityTimer()
                onClick()
            }
        }
    }

    private fun makeStatusPillView(textLabel: String, pillKind: Int): TextView {
        // pillKind: 0 = neutral value, 1 = ON green, 2 = OFF muted, 3 = accent highlight
        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)
        val bgCol = when (pillKind) {
            1 -> themeOnPillBgColor()
            2 -> if (dark) Color.argb(72, 148, 163, 184) else Color.argb(65, 100, 116, 139)
            3 -> t.accentColor
            else -> if (dark) Color.argb(76, 255, 255, 255) else Color.argb(38, 0, 0, 0)
        }
        val txtCol = when (pillKind) {
            1 -> themeOnPillTextColor()
            2 -> if (dark) Color.parseColor("#E2E8F0") else Color.parseColor("#334155")
            3 -> t.textOnAccent
            else -> t.textPrimaryColor
        }
        return TextView(this).apply {
            text = textLabel
            isSingleLine = true; maxLines = 1
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = t.boldFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale)
            setTextColor(txtCol)
            val ph = dpPx(10f * scale)
            val pv = dpPx(4f * scale)
            setPadding(ph, pv, ph, pv)
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(bgCol)
                cornerRadius = if (t.id == 3) dpPx(6f).toFloat() else 999f
            })
        }
    }

    private fun makeUnifiedPresetCard(
        titleText: String,
        subtitleText: String,
        isSelected: Boolean,
        onFocused: (() -> Unit)? = null,
        onClick: () -> Unit
    ): LinearLayout {
        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)
        val titleTv = TextView(this).apply {
            text = titleText
            typeface = t.boldFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
            gravity = Gravity.CENTER
            isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val subTv = TextView(this).apply {
            text = subtitleText
            typeface = t.normalFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.6f * scale)
            gravity = Gravity.CENTER
            isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (4 * scale).toInt(), 0, 0)
        }
        val checkBadge = TextView(this).apply {
            text = if (isSelected) "✓" else ""
            typeface = t.boldFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f * scale)
            gravity = Gravity.CENTER
            visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            includeFontPadding = false
            val sz = dpPx(23f * scale)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply { bottomMargin = dpPx(4f * scale) }
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(themeOnPillBgColor())
                cornerRadius = if (t.id == 3) dpPx(5f).toFloat() else 999f
            })
            setTextColor(themeOnPillTextColor())
        }

        fun applyStyle(box: LinearLayout, focused: Boolean) {
            val curT = currentTheme()
            val bgCol = when {
                focused -> curT.accentColor
                isSelected -> Color.argb(66, Color.red(curT.accentColor), Color.green(curT.accentColor), Color.blue(curT.accentColor))
                else -> if (dark) Color.argb(68, 0, 0, 0) else Color.argb(26, 0, 0, 0)
            }
            val strokeCol = when {
                focused -> themeFocusStrokeColor()
                isSelected -> curT.accentColor
                else -> themeCardBorderColor()
            }
            box.setBackgroundDrawable(GradientDrawable().apply {
                setColor(bgCol)
                cornerRadius = themeInnerCornerRadiusPx(scale)
                setStroke(if (focused) dpPx(2f) else dpPx(1f), strokeCol)
            })
            val mainTxt = if (focused) curT.textOnAccent else curT.textPrimaryColor
            titleTv.setTextColor(mainTxt)
            subTv.setTextColor(mainTxt)
            subTv.alpha = if (focused) 0.92f else 0.74f
            box.animate().scaleX(if (focused) 1.04f else 1f).scaleY(if (focused) 1.04f else 1f).setDuration(120L).start()
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            val pv = dpPx(10f * scale)
            val ph = dpPx(8f * scale)
            setPadding(ph, pv, ph, pv)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                val m = dpPx(4f * scale)
                setMargins(m, m, m, m)
            }
            addView(checkBadge)
            addView(titleTv)
            if (subtitleText.isNotEmpty()) addView(subTv)
            applyStyle(this, false)
            setOnFocusChangeListener { _, hasFocus ->
                applyStyle(this, hasFocus)
                if (hasFocus) {
                    resetMenuInactivityTimer()
                    onFocused?.invoke()
                }
            }
            setOnClickListener {
                resetMenuInactivityTimer()
                onClick()
            }
        }
    }

    private fun makeUnifiedSettingRow(
        titleText: String,
        pillText: String,
        pillKind: Int,
        onFocused: (() -> Unit)? = null,
        onClick: () -> Unit
    ): LinearLayout {
        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)
        val titleTv = TextView(this).apply {
            text = titleText
            typeface = t.normalFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f * scale)
            setTextColor(t.textPrimaryColor)
            isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (12 * scale).toInt()
            }
        }
        val pillTv = makeStatusPillView(pillText, pillKind)

        fun applyStyle(row: LinearLayout, focused: Boolean) {
            val curT = currentTheme()
            if (focused) {
                row.setBackgroundDrawable(GradientDrawable().apply {
                    setColor(curT.accentColor)
                    cornerRadius = themeInnerCornerRadiusPx(scale)
                    setStroke(dpPx(2f), themeFocusStrokeColor())
                })
                titleTv.setTextColor(curT.textOnAccent)
                titleTv.typeface = curT.boldFont
            } else {
                row.setBackgroundDrawable(GradientDrawable().apply {
                    setColor(if (dark) Color.argb(62, 0, 0, 0) else Color.argb(24, 0, 0, 0))
                    cornerRadius = themeInnerCornerRadiusPx(scale)
                    setStroke(dpPx(1f), themeCardBorderColor())
                })
                titleTv.setTextColor(curT.textPrimaryColor)
                titleTv.typeface = curT.normalFont
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            val ph = dpPx(12f * scale)
            val pv = dpPx(8f * scale)
            minimumHeight = dpPx(40f * scale)
            setPadding(ph, pv, ph, pv)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                val mv = dpPx(3f * scale)
                setMargins(2, mv, 2, mv)
            }
            addView(titleTv)
            addView(pillTv)
            applyStyle(this, false)
            setOnFocusChangeListener { _, hasFocus ->
                applyStyle(this, hasFocus)
                if (hasFocus) {
                    resetMenuInactivityTimer()
                    onFocused?.invoke()
                }
            }
            setOnClickListener {
                resetMenuInactivityTimer()
                onClick()
            }
        }
    }

    private fun applyCurrentThemeToAllPanels() {
        val t = currentTheme()
        val scale = uiScaleFactor()
        val dm = resources.displayMetrics
        val leftMargin = leftSafeMarginPx()

        sidePanel.layoutParams = FrameLayout.LayoutParams(
            sidePanelWidthPx(),
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.START
        ).apply { setMargins(leftMargin, 16, 0, 16) }

        clockTextView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ).apply { setMargins(leftMargin + 10, 20, 0, 0) }

        applySidePanelBackgroundAndPadding()
        leftIconRail.setBackgroundDrawable(makeThemedSideCardBg())
        infoPanel.setBackgroundDrawable(makeThemedPanelBg())
        centerOsdPanel.setBackgroundDrawable(makeThemedPanelBg())
        settingsTileOverlay.setBackgroundDrawable(makeThemedPanelBg())
        applySubModalBackgroundAndPadding()
        fullEpgOverlay.setBackgroundDrawable(makeThemedPanelBg())
        categoryListView.setBackgroundDrawable(makeThemedSideCardBg())
        channelListView.setBackgroundDrawable(makeThemedSideCardBg())
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

        subModalTitle.setTextColor(t.textPrimaryColor); subModalTitle.typeface = t.boldFont
        subModalTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f * scale)
        if (::subModalBadgeView.isInitialized) styleModalMonogramBadge(subModalBadgeView)
        if (subModalOverlay.visibility == View.VISIBLE) {
            menuHistory.lastOrNull()?.let { applyCenteredModalDimensions(it) }
        }
        fullEpgTitle.setTextColor(t.accentColor); fullEpgTitle.typeface = t.boldFont
        fullEpgTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f * scale)
        clockTextView.typeface = t.boldFont
        clockTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
        if (::infoTitleView.isInitialized) {
            infoTitleView.setTextColor(t.accentColor)
            infoTitleView.typeface = t.boldFont
            infoTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f * scale)
        }
        infoTextView.setTextColor(t.textPrimaryColor); infoTextView.typeface = t.normalFont
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f * scale)
        val infoGapTop = (16 * scale).toInt().coerceAtLeast(12)
        for (i in infoRowViews.indices) {
            val rv = infoRowViews[i]
            rv.setTextColor(t.textPrimaryColor)
            rv.typeface = t.normalFont
            rv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f * scale)
            rv.setPadding(4, if (i == 2) infoGapTop else 5, 4, 5)
        }
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
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f * scale)
            styleButtonForCurrentTheme(btn, btn.hasFocus())
        }

        for (tile in allGridTiles) tile.themeStyleId = t.id
        for (cap in allGridCaptions) {
            cap.typeface = t.normalFont
            cap.setTextColor(t.textPrimaryColor)
            cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f * scale)
        }

        fitSideListsExactRows()
        if (::subModalAdapter.isInitialized) {
            if (subModalList.visibility == View.VISIBLE) {
                if (::subModalStatsAdapter.isInitialized && subModalList.adapter === subModalStatsAdapter && subModalStatsItems.isNotEmpty()) {
                    fitSubModalListToWholeRows(subModalStatsItems.size)
                } else if (subModalItems.isNotEmpty()) {
                    fitSubModalListToWholeRows(subModalItems.size)
                }
            }
            subModalAdapter.notifyDataSetChanged()
            if (::subModalStatsAdapter.isInitialized) subModalStatsAdapter.notifyDataSetChanged()
        }
        updateCategoriesList()
        refreshChannelAdapter()
        updateCenterOsdContent()
    }


    private fun openPowerAndSleepSubModal() {
        setSubModalHeaderTitle("Таймер сна")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.VISIBLE

        val sleepOpts = listOf(0, 30, 60, 90, 120)
        val items = mutableListOf("[-]   Выключить сейчас")
        for (m in sleepOpts) {
            val sel = if (sleepTimerMinutesLeft == m) "[+]   " else "[-]   "
            if (m == 0) items.add("${sel}Таймер выключен")
            else items.add("${sel}Через $m минут")
        }
        updateSubModalListItems(items)

        subModalList.setOnItemClickListener { _, _, which, _ ->
            if (which == 0) {
                powerOffAppWithCrtEffect()
            } else {
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
                if (sleepTimerMinutesLeft <= 0) {
                    powerOffAppWithCrtEffect()
                } else {
                    if (sleepTimerMinutesLeft == 1) player?.volume = 0.35f
                    uiHandler.postDelayed(this, 60_000L)
                }
            }
        }
        sleepTimerRunnable = tick
        uiHandler.postDelayed(tick, 60_000L)
    }

    private fun openBrightnessSubModal() {
        setSubModalHeaderTitle("Яркость")
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        val levels = listOf(100, 75, 50, 25)
        val descriptions = listOf(
            "100%   •   Дневной режим",
            "75%   •   Вечерний режим",
            "50%   •   Ночной режим",
            "25%   •   Глубокая ночь"
        )
        val curIdx = levels.indexOf(brightnessPercent).coerceAtLeast(0)
        updateSubModalListItems(levels.mapIndexed { idx, lvl -> (if (brightnessPercent == lvl) "[+]   " else "[-]   ") + descriptions[idx] }, preservePositionIndex = curIdx)
        subModalList.setOnItemClickListener { _, _, which, _ ->
            if (which in levels.indices) {
                brightnessPercent = levels[which]; applyBrightnessLevel(); saveState(); navigateBackInMenu()
            }
        }
        animateMenuAppear(subModalOverlay); subModalList.requestFocus()
    }

    private fun openChannelActionsSubModal() {
        val ch = currentChannel ?: return
        setSubModalHeaderTitle("Канал")
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        val items = listOf(
            "[-]   Скрыть канал из списка",
            "[>]   Переместить в группу   •   ${effectiveCategory(ch)}",
            if (isDualStreamActive) "[ON]   Двойной эфир   •   Вкл" else "[OFF]   Двойной эфир   •   Выкл"
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
        setSubModalHeaderTitle("Выбор группы")
        subModalInputContainer.visibility = View.GONE; subModalList.visibility = View.VISIBLE
        val curIdx = targetCats.indexOf(curCat).coerceAtLeast(0)
        updateSubModalListItems(targetCats.map { c -> (if (c == curCat) "[+]   " else "[-]   ") + c }, preservePositionIndex = curIdx)
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

    private fun openThemesSubModal() {
        setSubModalHeaderTitle("Темы", "ТМ")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalTopButtons.removeAllViews()
        subModalThemesGrid.removeAllViews()
        subModalThemesGrid.visibility = View.VISIBLE
        subModalThemesGrid.orientation = LinearLayout.HORIZONTAL
        subModalThemesGrid.gravity = Gravity.CENTER_VERTICAL
        subModalThemesGrid.setBackgroundDrawable(makeThemedSubBg())
        subModalThemesGrid.setPadding(16, 14, 18, 14)

        val curTheme = currentTheme()
        val scale = uiScaleFactor()
        val dm = resources.displayMetrics
        var previewedIdx = themeIndex.coerceIn(0, themes.lastIndex)

        // Left column: tidy single-line list of themes
        val leftScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = (themes.size > 7)
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.44f).apply {
                rightMargin = 18
            }
        }
        val leftList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 4, 4, 4)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            )
        }
        leftScroll.addView(leftList)

        // Right column: live framed 16:9 TV preview
        val rightContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.56f)
        }
        val maxPrevH = (dm.heightPixels * 0.42f).toInt().coerceIn(dpPx(140f), dpPx(300f))
        val livePreview = ThemeLivePreviewView(this, themes[previewedIdx]).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxPrevH)
            setOnClickListener {
                resetMenuInactivityTimer()
                themeIndex = previewedIdx
                saveState()
                applyCurrentThemeToAllPanels()
                navigateBackInMenu()
            }
        }
        val previewCaption = TextView(this).apply {
            text = themes[previewedIdx].name
            setTextColor(curTheme.textPrimaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f * scale)
            typeface = curTheme.boldFont
            gravity = Gravity.CENTER
            alpha = 0.85f
            setPadding(0, 12, 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        rightContainer.addView(livePreview)
        rightContainer.addView(previewCaption)

        val rowViews = mutableListOf<LinearLayout>()
        val rowTexts = mutableListOf<TextView>()
        val rowDots = mutableListOf<ThemeColorDotView>()
        val rowH = LinearLayout.LayoutParams.WRAP_CONTENT
        val dotSize = dpPx(18f * scale)

        fun refreshThemeRows(activeIdx: Int) {
            val ct = currentTheme()
            for (i in rowViews.indices) {
                val isHighlight = (i == activeIdx)
                val isCurrent = (i == themeIndex)
                val row = rowViews[i]
                val tv = rowTexts[i]
                val dot = rowDots[i]
                dot.showCheck = isCurrent
                dot.isOnAccentBg = isHighlight
                if (isHighlight) {
                    row.setBackgroundDrawable(GradientDrawable().apply {
                        setColor(ct.accentColor)
                        cornerRadius = themeInnerCornerRadiusPx(scale)
                        setStroke(dpPx(2f), themeFocusStrokeColor())
                    })
                    tv.setTextColor(ct.textOnAccent)
                    tv.typeface = ct.boldFont
                } else {
                    row.setBackgroundDrawable(null)
                    tv.setTextColor(ct.textPrimaryColor)
                    tv.typeface = if (isCurrent) ct.boldFont else ct.normalFont
                }
            }
            livePreview.themeStyle = themes[activeIdx]
            previewCaption.text = if (activeIdx == themeIndex) "${themes[activeIdx].name}  •  Активна" else themes[activeIdx].name
        }

        for (idx in themes.indices) {
            val th = themes[idx]
            val dotView = ThemeColorDotView(this, th).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    rightMargin = (14 * scale).toInt().coerceAtLeast(10)
                }
            }
            val nameTv = TextView(this).apply {
                text = th.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f * scale)
                isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val row = LinearLayout(this).apply {
                id = 8400 + idx
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true; isFocusableInTouchMode = false; isClickable = true
                minimumHeight = dpPx(38f * scale)
                setPadding(dpPx(12f * scale), dpPx(7f * scale), dpPx(12f * scale), dpPx(7f * scale))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH).apply {
                    val mv = dpPx(3f * scale)
                    setMargins(2, mv, 2, mv)
                }
                addView(dotView)
                addView(nameTv)
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        resetMenuInactivityTimer()
                        previewedIdx = idx
                        refreshThemeRows(idx)
                    }
                }
                setOnClickListener {
                    resetMenuInactivityTimer()
                    if (controlMode == 1 && previewedIdx != idx) {
                        previewedIdx = idx
                        refreshThemeRows(idx)
                    } else {
                        themeIndex = idx
                        saveState()
                        applyCurrentThemeToAllPanels()
                        navigateBackInMenu()
                    }
                }
            }
            rowViews.add(row)
            rowTexts.add(nameTv)
            rowDots.add(dotView)
            leftList.addView(row)
        }

        for (i in rowViews.indices) {
            rowViews[i].nextFocusUpId = rowViews[(i - 1 + rowViews.size) % rowViews.size].id
            rowViews[i].nextFocusDownId = rowViews[(i + 1) % rowViews.size].id
        }

        subModalThemesGrid.addView(leftScroll)
        subModalThemesGrid.addView(rightContainer)
        refreshThemeRows(previewedIdx)

        animateMenuAppear(subModalOverlay)
        (rowViews.getOrNull(previewedIdx) ?: rowViews.firstOrNull())?.requestFocus()
    }

    private fun openTopManagerSubModal(inPlace: Boolean = false, keepIndex: Int = -1) {
        setSubModalHeaderTitle("ТОП каналы", "ТП")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalThemesGrid.visibility = View.GONE
        subModalCustomBody.removeAllViews()
        subModalCustomBody.visibility = View.VISIBLE
        subModalCustomBody.orientation = LinearLayout.VERTICAL
        subModalCustomBody.setBackgroundDrawable(makeThemedSubBg())
        subModalCustomBody.setPadding(16, 14, 16, 14)
        subModalCustomBody.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)
        val topChans = allChannels.filter { topUrls.contains(it.url) }
        if (keepIndex in topChans.indices) selectedTopModalIdx = keepIndex
        else if (selectedTopModalIdx !in topChans.indices) selectedTopModalIdx = 0

        val rowViews = mutableListOf<LinearLayout>()
        var btnRemoveSelected: TextView? = null

        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val listCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(listCol)
        subModalCustomBody.addView(scroll)

        if (topChans.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Нет закреплённых каналов\nДобавляйте любимые каналы в ТОП из меню плеера (OK → В ТОП)"
                setTextColor(t.textPrimaryColor)
                alpha = 0.78f
                typeface = t.normalFont
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f * scale)
                gravity = Gravity.CENTER
                setLineSpacing(6f, 1.0f)
                setPadding(24, 54, 24, 54)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            listCol.addView(emptyTv)
        } else {
            val rowH = LinearLayout.LayoutParams.WRAP_CONTENT
            topChans.forEachIndexed { idx, ch ->
                val badge = TextView(this).apply {
                    text = "▲"
                    setTextColor(Color.parseColor("#FBBF24"))
                    typeface = t.boldFont
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f * scale)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        rightMargin = (12 * scale).toInt()
                    }
                }
                val titleTv = TextView(this).apply {
                    text = "${ch.name}  •  ${effectiveCategory(ch)}"
                    setTextColor(t.textPrimaryColor)
                    typeface = t.normalFont
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.8f * scale)
                    isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val removeX = TextView(this).apply {
                    text = "✕"
                    setTextColor(t.textPrimaryColor)
                    alpha = 0.62f
                    typeface = t.boldFont
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale)
                    setPadding((10 * scale).toInt(), 0, 2, 0)
                }
                fun applyRowStyle(row: LinearLayout, focused: Boolean) {
                    val curT = currentTheme()
                    if (focused) {
                        row.setBackgroundDrawable(GradientDrawable().apply {
                            setColor(curT.accentColor)
                            cornerRadius = themeInnerCornerRadiusPx(scale)
                            setStroke(dpPx(2f), themeFocusStrokeColor())
                        })
                        titleTv.setTextColor(curT.textOnAccent)
                        titleTv.typeface = curT.boldFont
                        removeX.setTextColor(curT.textOnAccent)
                        removeX.alpha = 0.95f
                    } else {
                        row.setBackgroundDrawable(GradientDrawable().apply {
                            setColor(if (dark) Color.argb(62, 0, 0, 0) else Color.argb(24, 0, 0, 0))
                            cornerRadius = themeInnerCornerRadiusPx(scale)
                            setStroke(dpPx(1f), themeCardBorderColor())
                        })
                        titleTv.setTextColor(curT.textPrimaryColor)
                        titleTv.typeface = curT.normalFont
                        removeX.setTextColor(curT.textPrimaryColor)
                        removeX.alpha = 0.62f
                    }
                }
                val row = LinearLayout(this).apply {
                    id = View.generateViewId()
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isFocusable = true; isFocusableInTouchMode = false; isClickable = true
                    minimumHeight = dpPx(40f * scale)
                    setPadding(dpPx(12f * scale), dpPx(8f * scale), dpPx(12f * scale), dpPx(8f * scale))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH).apply {
                        val mv = dpPx(3f * scale)
                        setMargins(2, mv, 2, mv)
                    }
                    addView(badge); addView(titleTv); addView(removeX)
                    applyRowStyle(this, false)
                    setOnFocusChangeListener { _, hasFocus ->
                        applyRowStyle(this, hasFocus)
                        if (hasFocus) {
                            selectedTopModalIdx = idx
                            resetMenuInactivityTimer()
                        }
                    }
                    setOnClickListener {
                        resetMenuInactivityTimer()
                        topUrls.remove(ch.url)
                        saveState(); selectCategory(currentCategoryIndex)
                        openTopManagerSubModal(inPlace = true, keepIndex = idx.coerceAtMost((topChans.size - 2).coerceAtLeast(0)))
                    }
                }
                rowViews.add(row)
                listCol.addView(row)
            }
        }

        subModalBottomButtons.removeAllViews()
        subModalBottomButtons.visibility = if (topChans.isNotEmpty()) View.VISIBLE else View.GONE
        if (topChans.isNotEmpty()) {
            btnRemoveSelected = makeUnifiedActionButton("Убрать из ТОП") {
                val ch = topChans.getOrNull(selectedTopModalIdx) ?: return@makeUnifiedActionButton
                topUrls.remove(ch.url)
                saveState(); selectCategory(currentCategoryIndex)
                openTopManagerSubModal(inPlace = true, keepIndex = selectedTopModalIdx.coerceAtMost((topChans.size - 2).coerceAtLeast(0)))
            }.apply { id = 8991 }
            val btnClearAll = makeUnifiedActionButton("Очистить ТОП", isDanger = true) {
                topUrls.clear()
                saveState(); selectCategory(currentCategoryIndex)
                openTopManagerSubModal(inPlace = true, keepIndex = 0)
            }.apply { id = 8992 }
            val rmBtn = btnRemoveSelected
            if (rmBtn != null) {
                rmBtn.nextFocusRightId = btnClearAll.id
                btnClearAll.nextFocusLeftId = rmBtn.id
                if (rowViews.isNotEmpty()) {
                    rmBtn.nextFocusUpId = rowViews.last().id
                    btnClearAll.nextFocusUpId = rowViews.last().id
                    rowViews.last().nextFocusDownId = rmBtn.id
                }
                subModalBottomButtons.addView(rmBtn)
            }
            subModalBottomButtons.addView(btnClearAll)
        }

        if (!inPlace) animateMenuAppear(subModalOverlay)
        (rowViews.getOrNull(selectedTopModalIdx) ?: rowViews.firstOrNull() ?: btnRemoveSelected)?.requestFocus()
    }

    private fun openFullScreenChannelEpg() {
        setPlayerMiniPreviewMode(false)
        val ch = currentChannel
        val progs = getChannelEpgList(ch)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        val chTitle = ch?.name?.take(26)?.ifBlank { null }
        setSubModalHeaderTitle(if (chTitle != null) "Телегид  •  $chTitle" else "Телегид", "ТГ")

        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalThemesGrid.visibility = View.GONE
        subModalBottomButtons.visibility = View.GONE
        subModalCustomBody.removeAllViews()
        subModalCustomBody.visibility = View.VISIBLE
        subModalCustomBody.orientation = LinearLayout.HORIZONTAL
        subModalCustomBody.setBackgroundDrawable(makeThemedSubBg())
        subModalCustomBody.setPadding(14, 12, 14, 12)
        subModalCustomBody.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)

        val leftScroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.57f).apply {
                rightMargin = (14 * scale).toInt()
            }
        }
        val leftList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        leftScroll.addView(leftList)

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.43f)
        }

        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(if (dark) Color.argb(66, 0, 0, 0) else Color.argb(22, 0, 0, 0))
                cornerRadius = 16f * scale
                setStroke(1, if (dark) Color.argb(38, 255, 255, 255) else Color.argb(36, 0, 0, 0))
            })
            setPadding((14 * scale).toInt(), (12 * scale).toInt(), (14 * scale).toInt(), (12 * scale).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                bottomMargin = (10 * scale).toInt()
            }
        }
        val detailTitleTv = TextView(this).apply {
            setTextColor(t.textPrimaryColor)
            typeface = t.boldFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
            maxLines = 3; ellipsize = TextUtils.TruncateAt.END
        }
        val detailTimeTv = TextView(this).apply {
            setTextColor(t.accentColor)
            typeface = t.boldFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale)
            setPadding(0, (6 * scale).toInt(), 0, (6 * scale).toInt())
        }
        val detailMetaTv = TextView(this).apply {
            setTextColor(t.textPrimaryColor)
            alpha = 0.80f
            typeface = t.normalFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale)
            setLineSpacing(4f, 1.0f)
        }
        infoCard.addView(detailTitleTv)
        infoCard.addView(detailTimeTv)
        infoCard.addView(detailMetaTv)
        rightCol.addView(infoCard)

        val btnRefresh = makeUnifiedActionButton("↻  Обновить EPG", fillWidth = true) {
            Toast.makeText(this, "Обновление телепрограммы...", Toast.LENGTH_SHORT).show()
            loadEpgOncePerDayInBackground(forceRefresh = true)
        }.apply { id = 8751 }
        val btnXmlUrl = makeUnifiedActionButton("Ссылка XMLTV", fillWidth = true) {
            navigateToMenu("EPG")
        }.apply { id = 8752 }
        val btnEpgFile = makeUnifiedActionButton("Файл EPG", fillWidth = true) {
            launchSystemEpgPicker()
        }.apply { id = 8753 }

        btnRefresh.nextFocusDownId = btnXmlUrl.id
        btnXmlUrl.nextFocusUpId = btnRefresh.id
        btnXmlUrl.nextFocusDownId = btnEpgFile.id
        btnEpgFile.nextFocusUpId = btnXmlUrl.id
        rightCol.addView(btnRefresh)
        rightCol.addView(btnXmlUrl)
        rightCol.addView(btnEpgFile)

        fun updateDetailBox(prog: EpgProgram?) {
            if (prog == null) {
                detailTitleTv.text = ch?.name ?: "Прямой эфир"
                detailTimeTv.text = if (ch != null) effectiveCategory(ch) else "Нет данных EPG"
                detailMetaTv.text = "Расписание передач для этого канала пока не загружено. Нажмите «Обновить EPG» или укажите свой источник."
                return
            }
            detailTitleTv.text = prog.title
            val startStr = timeFmt.format(Date(prog.startMs))
            val stopStr = timeFmt.format(Date(prog.stopMs))
            detailTimeTv.text = "$startStr — $stopStr"
            val totalMin = ((prog.stopMs - prog.startMs) / 60000L).coerceAtLeast(1L)
            val statusLine = when {
                now in prog.startMs..prog.stopMs -> {
                    val passed = ((now - prog.startMs) / 60000L).coerceIn(0L, totalMin)
                    val left = (totalMin - passed).coerceAtLeast(0L)
                    "Идёт сейчас • прошло $passed мин, осталось $left мин"
                }
                prog.startMs > now -> {
                    val waitMin = ((prog.startMs - now) / 60000L).coerceAtLeast(1L)
                    "Скоро (через $waitMin мин) • длительность $totalMin мин"
                }
                else -> "Передача завершилась • длительность $totalMin мин"
            }
            val catPart = if (ch != null) "\nКанал: ${ch.name} (${effectiveCategory(ch)})" else ""
            detailMetaTv.text = statusLine + catPart
        }

        val rowViews = mutableListOf<LinearLayout>()
        var activeIdx = 0
        if (progs.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Программа передач обновляется или отсутствует для этого канала"
                setTextColor(t.textPrimaryColor); alpha = 0.78f
                typeface = t.normalFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale)
                gravity = Gravity.CENTER; setPadding(16, 44, 16, 44)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            leftList.addView(emptyTv)
            updateDetailBox(null)
        } else {
            progs.forEachIndexed { idx, p ->
                val isNow = now in p.startMs..p.stopMs
                val isPast = p.stopMs < now
                if (isNow) activeIdx = idx

                val topLine = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val timeTv = TextView(this).apply {
                    text = timeFmt.format(Date(p.startMs))
                    typeface = t.boldFont
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.8f * scale)
                    setPadding(0, 0, (10 * scale).toInt(), 0)
                }
                val titleTv = TextView(this).apply {
                    text = p.title
                    typeface = if (isNow) t.boldFont else t.normalFont
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.2f * scale)
                    isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                topLine.addView(timeTv)
                topLine.addView(titleTv)
                if (isNow) {
                    topLine.addView(makeStatusPillView("СЕЙЧАС", 3).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            leftMargin = (8 * scale).toInt()
                        }
                    })
                }

                val progressTrack: FrameLayout? = if (isNow) {
                    val pct = ((now - p.startMs).toFloat() / (p.stopMs - p.startMs).coerceAtLeast(1L).toFloat()).coerceIn(0.03f, 1f)
                    val trackCol = if (dark) Color.argb(72, 255, 255, 255) else Color.argb(56, 0, 0, 0)
                    val fillCol = if (t.id == 3) Color.parseColor("#27272A") else if (t.id == 2) Color.WHITE else t.accentColor
                    FrameLayout(this).apply {
                        setBackgroundDrawable(GradientDrawable().apply {
                            setColor(trackCol)
                            cornerRadius = 999f
                        })
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpPx(4f * scale)).apply {
                            topMargin = dpPx(5f * scale)
                        }
                        val fillRow = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            weightSum = 1f
                            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                            addView(View(this@MainActivity).apply {
                                setBackgroundDrawable(GradientDrawable().apply {
                                    setColor(fillCol)
                                    cornerRadius = 999f
                                })
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct)
                            })
                            addView(View(this@MainActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (1f - pct).coerceAtLeast(0.001f))
                            })
                        }
                        addView(fillRow)
                    }
                } else null

                fun applyEpgRowStyle(row: LinearLayout, focused: Boolean) {
                    val curT = currentTheme()
                    if (focused) {
                        row.setBackgroundDrawable(GradientDrawable().apply {
                            setColor(curT.accentColor)
                            cornerRadius = themeInnerCornerRadiusPx(scale)
                            setStroke(dpPx(2f), themeFocusStrokeColor())
                        })
                        timeTv.setTextColor(curT.textOnAccent)
                        titleTv.setTextColor(curT.textOnAccent)
                        titleTv.alpha = 1f; timeTv.alpha = 1f
                    } else {
                        row.setBackgroundDrawable(GradientDrawable().apply {
                            val bg = if (isNow) Color.argb(58, Color.red(curT.accentColor), Color.green(curT.accentColor), Color.blue(curT.accentColor))
                            else if (dark) Color.argb(54, 0, 0, 0) else Color.argb(20, 0, 0, 0)
                            setColor(bg)
                            cornerRadius = 14f * scale
                            setStroke(if (isNow) 1 else 0, if (isNow) curT.accentColor else Color.TRANSPARENT)
                        })
                        timeTv.setTextColor(if (isNow) curT.accentColor else curT.textPrimaryColor)
                        titleTv.setTextColor(curT.textPrimaryColor)
                        val a = if (isPast) 0.56f else 1f
                        timeTv.alpha = a; titleTv.alpha = a
                    }
                }

                val row = LinearLayout(this).apply {
                    id = View.generateViewId()
                    orientation = LinearLayout.VERTICAL
                    isFocusable = true; isFocusableInTouchMode = false; isClickable = true
                    val ph = (14 * scale).toInt()
                    val pv = (10 * scale).toInt()
                    setPadding(ph, pv, ph, pv)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(2, (3 * scale).toInt(), 2, (3 * scale).toInt())
                    }
                    addView(topLine)
                    if (progressTrack != null) addView(progressTrack)
                    nextFocusRightId = btnRefresh.id
                    applyEpgRowStyle(this, false)
                    setOnFocusChangeListener { _, hasFocus ->
                        applyEpgRowStyle(this, hasFocus)
                        if (hasFocus) {
                            resetMenuInactivityTimer()
                            updateDetailBox(p)
                        }
                    }
                    setOnClickListener {
                        resetMenuInactivityTimer()
                        updateDetailBox(p)
                    }
                }
                rowViews.add(row)
                leftList.addView(row)
            }
            updateDetailBox(progs.getOrNull(activeIdx))
            val focusBackRow = rowViews.getOrNull(activeIdx) ?: rowViews.first()
            btnRefresh.nextFocusLeftId = focusBackRow.id
            btnXmlUrl.nextFocusLeftId = focusBackRow.id
            btnEpgFile.nextFocusLeftId = focusBackRow.id
        }

        subModalCustomBody.addView(leftScroll)
        subModalCustomBody.addView(rightCol)
        animateMenuAppear(subModalOverlay)

        val targetFocus = rowViews.getOrNull(activeIdx) ?: btnRefresh
        targetFocus.requestFocus()
        if (rowViews.isNotEmpty()) {
            leftScroll.post {
                val targetView = rowViews.getOrNull(activeIdx) ?: return@post
                val scrollY = (targetView.top - (leftScroll.height / 3)).coerceAtLeast(0)
                leftScroll.scrollTo(0, scrollY)
            }
        }
    }

    private fun openMoreSettingsSubModal(inPlace: Boolean = false, keepIndex: Int = -1, focusTabs: Boolean = false) {
        setSubModalHeaderTitle("Общие настройки", "ОБ")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalThemesGrid.visibility = View.GONE
        subModalBottomButtons.visibility = View.GONE
        subModalCustomBody.removeAllViews()
        subModalCustomBody.visibility = View.VISIBLE
        subModalCustomBody.orientation = LinearLayout.HORIZONTAL
        subModalCustomBody.setBackgroundDrawable(makeThemedSubBg())
        subModalCustomBody.setPadding(14, 12, 14, 12)
        subModalCustomBody.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)
        selectedMoreSettingsTab = selectedMoreSettingsTab.coerceIn(0, 2)

        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(2, dpPx(2f), dpPx(8f * scale), dpPx(2f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.28f)
        }
        val rightScroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.72f)
        }
        val rightList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        rightScroll.addView(rightList)

        val tabTitles = listOf("Интерфейс", "Видео и сеть", "Сервис\nи обновление")
        val tabViews = mutableListOf<TextView>()
        val rightRowViews = mutableListOf<LinearLayout>()

        fun styleTab(tv: TextView, idx: Int, focused: Boolean) {
            val curT = currentTheme()
            val isActive = (idx == selectedMoreSettingsTab)
            val bg = when {
                focused -> curT.accentColor
                isActive -> Color.argb(66, Color.red(curT.accentColor), Color.green(curT.accentColor), Color.blue(curT.accentColor))
                else -> Color.TRANSPARENT
            }
            val strokeCol = when {
                focused -> themeFocusStrokeColor()
                isActive -> curT.accentColor
                else -> Color.TRANSPARENT
            }
            tv.setBackgroundDrawable(GradientDrawable().apply {
                setColor(bg)
                cornerRadius = themeInnerCornerRadiusPx(scale)
                setStroke(if (focused) dpPx(2f) else if (isActive) dpPx(1f) else 0, strokeCol)
            })
            tv.setTextColor(if (focused) curT.textOnAccent else curT.textPrimaryColor)
            tv.typeface = if (focused || isActive) curT.boldFont else curT.normalFont
        }

        tabTitles.forEachIndexed { idx, label ->
            val tv = TextView(this).apply {
                id = 8600 + idx
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.2f * scale)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                isFocusable = true; isFocusableInTouchMode = false; isClickable = true
                val ph = dpPx(10f * scale)
                val pv = dpPx(9f * scale)
                setPadding(ph, pv, ph, pv)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpPx(5f * scale)
                }
                styleTab(this, idx, false)
                setOnFocusChangeListener { _, hasFocus ->
                    styleTab(this, idx, hasFocus)
                    if (hasFocus) {
                        resetMenuInactivityTimer()
                        if (selectedMoreSettingsTab != idx) {
                            selectedMoreSettingsTab = idx
                            openMoreSettingsSubModal(inPlace = true, keepIndex = 0, focusTabs = true)
                        }
                    }
                }
                setOnClickListener {
                    resetMenuInactivityTimer()
                    if (selectedMoreSettingsTab != idx) {
                        selectedMoreSettingsTab = idx
                        openMoreSettingsSubModal(inPlace = true, keepIndex = 0, focusTabs = false)
                    } else {
                        rightRowViews.firstOrNull()?.requestFocus()
                    }
                }
            }
            tabViews.add(tv)
            leftCol.addView(tv)
        }
        for (i in tabViews.indices) {
            tabViews[i].nextFocusUpId = tabViews[(i - 1 + tabViews.size) % tabViews.size].id
            tabViews[i].nextFocusDownId = tabViews[(i + 1) % tabViews.size].id
        }

        val scaleText = when (uiScaleStep) { 0 -> "Смартфон"; 1 -> "Крупный ТВ"; else -> "Огромный ТВ" }
        val alphaText = when (uiAlphaStep) { 0 -> "96%"; 1 -> "84%"; else -> "65%" }
        val ambiText = when (ambilightMode) { 0 -> "ВЫКЛ"; 1 -> "Группа"; 2 -> "Спектр"; else -> "Кадр" }
        val aspectText = when (screenAspectMode) { 0 -> "По экрану"; 1 -> "16:9"; else -> "Кинозум" }
        val updLabel = availableUpdateInfo?.let { upd ->
            "Доступна ${upd.versionLabel.ifBlank { upd.tagName }.ifBlank { "новая" }}"
        } ?: "Проверить"

        data class SettingSpec(val title: String, val pill: String, val kind: Int, val action: () -> Unit)
        val rowsSpec: List<SettingSpec> = when (selectedMoreSettingsTab) {
            0 -> listOf(
                SettingSpec("Размер текста и меню", scaleText, 0) {
                    uiScaleStep = (uiScaleStep + 1) % 3; saveState(); applyCurrentThemeToAllPanels()
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 0)
                },
                SettingSpec("Прозрачность меню", alphaText, 0) {
                    uiAlphaStep = (uiAlphaStep + 1) % 3; saveState(); animatePanelAlphaTransition()
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 1)
                },
                SettingSpec("Подсветка Ambilight", ambiText, if (ambilightMode == 0) 2 else 0) {
                    ambilightMode = (ambilightMode + 1) % 4; saveState(); applyCurrentThemeToAllPanels()
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 2)
                },
                SettingSpec("Баннер названия канала", if (showChannelBannerOnSwitch) "ВКЛ" else "ВЫКЛ", if (showChannelBannerOnSwitch) 1 else 2) {
                    showChannelBannerOnSwitch = !showChannelBannerOnSwitch; saveState()
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 3)
                },
                SettingSpec("Автоскрытие меню", "$menuHideDelaySec сек", 0) {
                    menuHideDelaySec = when (menuHideDelaySec) { 4 -> 8; 8 -> 12; else -> 4 }
                    saveState(); openMoreSettingsSubModal(inPlace = true, keepIndex = 4)
                },
                SettingSpec("Отступ под камеру", if (cameraCutoutSafeOffset) "ВКЛ" else "ВЫКЛ", if (cameraCutoutSafeOffset) 1 else 2) {
                    cameraCutoutSafeOffset = !cameraCutoutSafeOffset; saveState(); applyCurrentThemeToAllPanels()
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 5)
                },
                SettingSpec("Жесты яркости и громкости", if (touchEdgeGesturesEnabled) "ВКЛ" else "ВЫКЛ", if (touchEdgeGesturesEnabled) 1 else 2) {
                    touchEdgeGesturesEnabled = !touchEdgeGesturesEnabled; saveState()
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 6)
                }
            )
            1 -> listOf(
                SettingSpec("Формат изображения", aspectText, 0) {
                    screenAspectMode = (screenAspectMode + 1) % 3; applyScreenAspectMode(); saveState()
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 0)
                },
                SettingSpec("Двойной эфир (PiP)", if (isDualStreamActive) "ВКЛ" else "ВЫКЛ", if (isDualStreamActive) 1 else 2) {
                    toggleDualStreamWithCurrentOrNeighbor()
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 1)
                },
                SettingSpec("Мульти-DoH (5 серверов)", if (dohEnabled) "ВКЛ" else "ВЫКЛ", if (dohEnabled) 1 else 2) {
                    dohEnabled = !dohEnabled; saveState(); rebuildNetworkClients(); initPlayer()
                    currentChannel?.let { playChannelObj(it, immediate = true, silentSwap = true, updateSavedUrl = false) }
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 2)
                },
                SettingSpec("DPI-обход (HTTP/HTTPS)", if (dpiBypassEnabled) "ВКЛ" else "ВЫКЛ", if (dpiBypassEnabled) 1 else 2) {
                    dpiBypassEnabled = !dpiBypassEnabled; saveState(); rebuildNetworkClients(); initPlayer()
                    currentChannel?.let { playChannelObj(it, immediate = true, silentSwap = true, updateSavedUrl = false) }
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 3)
                },
                SettingSpec(
                    "Прокси (SOCKS5 / HTTP)",
                    if (networkProxyUrl.isNotBlank()) networkProxyUrl.removePrefix("socks5://").removePrefix("http://").take(18) else "ВЫКЛ",
                    if (networkProxyUrl.isNotBlank()) 1 else 2
                ) {
                    navigateToMenu("PROXY_INPUT")
                },
                SettingSpec("При обрыве потока", if (onDropBehavior == 0) "Перезапуск" else "След. канал", 0) {
                    onDropBehavior = (onDropBehavior + 1) % 2; saveState()
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 5)
                },
                SettingSpec("Яркость экрана", "$brightnessPercent%", 0) {
                    navigateToMenu("BRIGHTNESS_MENU")
                },
                SettingSpec("Машина времени", "-30 сек", 0) {
                    rewindLiveBufferSeconds(30); closeAllMenus()
                }
            )
            else -> listOf(
                SettingSpec(
                    "Таймер сна и Выключение",
                    if (sleepTimerMinutesLeft > 0) "${sleepTimerMinutesLeft} мин" else "Открыть",
                    if (sleepTimerMinutesLeft > 0) 1 else 0
                ) {
                    navigateToMenu("POWER_AND_SLEEP_MENU")
                },
                SettingSpec("Телепрограмма EPG", "Обновить", 0) {
                    Toast.makeText(this, "Обновление телепрограммы...", Toast.LENGTH_SHORT).show()
                    loadEpgOncePerDayInBackground(forceRefresh = true); navigateBackInMenu()
                },
                SettingSpec("Диагностический файл", formatDiagFileSize(), 0) {
                    openDiagLogInSystemFileManager()
                },
                SettingSpec("Бэкап настроек в файл", "Сохранить", 0) {
                    exportFullBackupToFile()
                },
                SettingSpec("Восстановить из бэкапа", "Загрузить", 0) {
                    importFullBackupFromFile()
                },
                SettingSpec(
                    "Автопроверка обновлений",
                    if (autoCheckUpdatesEnabled) "ВКЛ" else "ВЫКЛ",
                    if (autoCheckUpdatesEnabled) 1 else 2
                ) {
                    autoCheckUpdatesEnabled = !autoCheckUpdatesEnabled
                    saveState()
                    if (autoCheckUpdatesEnabled) scheduleSilentStartupUpdateCheck(initialDelayMs = 1200L)
                    openMoreSettingsSubModal(inPlace = true, keepIndex = 5)
                },
                SettingSpec("Обновление TiViLook", updLabel, if (availableUpdateInfo != null) 3 else 0) {
                    checkAndInstallAppUpdateFromGithub()
                }
            )
        }

        rowsSpec.forEachIndexed { idx, spec ->
            val row = makeUnifiedSettingRow(spec.title, spec.pill, spec.kind, onClick = spec.action).apply {
                id = 8620 + idx
                nextFocusLeftId = tabViews[selectedMoreSettingsTab].id
            }
            rightRowViews.add(row)
            rightList.addView(row)
        }
        if (rightRowViews.isNotEmpty()) {
            for (i in rightRowViews.indices) {
                rightRowViews[i].nextFocusUpId = rightRowViews[(i - 1 + rightRowViews.size) % rightRowViews.size].id
                rightRowViews[i].nextFocusDownId = rightRowViews[(i + 1) % rightRowViews.size].id
            }
            tabViews.forEach { it.nextFocusRightId = rightRowViews.first().id }
        }

        val dividerV = View(this).apply {
            setBackgroundColor(if (dark) Color.argb(36, 255, 255, 255) else Color.argb(32, 0, 0, 0))
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                rightMargin = (12 * scale).toInt()
            }
        }
        subModalCustomBody.addView(leftCol)
        subModalCustomBody.addView(dividerV)
        subModalCustomBody.addView(rightScroll)

        if (!inPlace) animateMenuAppear(subModalOverlay)
        if (focusTabs) {
            tabViews[selectedMoreSettingsTab].requestFocus()
        } else {
            val targetIdx = keepIndex.coerceIn(0, (rightRowViews.size - 1).coerceAtLeast(0))
            (rightRowViews.getOrNull(targetIdx) ?: tabViews[selectedMoreSettingsTab]).requestFocus()
        }
    }

    private fun formatByteSizeRu(bytes: Long): String {
        if (bytes <= 0L) return "0 КБ"
        return when {
            bytes < 1024L -> "$bytes Б"
            bytes < 1024L * 1024L -> "${(bytes / 1024L).coerceAtLeast(1L)} КБ"
            else -> String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
        }
    }

    private fun formatDiagFileSize(): String {
        try {
            val liveFile = CoreEngines.resolveDiagLogFile(this)
            if (liveFile.isFile && liveFile.length() > 0L) {
                return formatByteSizeRu(liveFile.length())
            }
        } catch (_: Exception) {}
        val status = CoreEngines.getDiagLogStatusText()
        val m = Regex("\\d+(?:[.,]\\d+)?\\s*(?:КБ|МБ|ГБ|Б|KB|MB|B)", RegexOption.IGNORE_CASE).find(status)
        return m?.value?.trim() ?: "0 КБ"
    }

    private fun findDiagLogSourceFile(): File? {
        try {
            val liveFile = CoreEngines.resolveDiagLogFile(this)
            if (liveFile.isFile && liveFile.length() > 0L) return liveFile
        } catch (_: Exception) {}
        val dirs = listOfNotNull(
            getExternalFilesDir(null),
            filesDir,
            cacheDir,
            externalCacheDir
        )
        var best: File? = null
        for (d in dirs) {
            try {
                val exact = File(d, "TiViLook_diag.txt")
                if (exact.isFile && exact.length() > (best?.length() ?: -1L)) best = exact
            } catch (_: Exception) {}
        }
        return best
    }

    private fun buildCurrentDiagnosticHeader(): String {
        val eng = if (isVlcEngineActive()) "LibVLC" else "Media3"
        val dec = when (decoderMode) { 0 -> "HW"; 2 -> "SW"; else -> "HW+FFmpeg" }
        val buf = when (bufferLevel) { 0 -> "Стандарт"; 1 -> "Оптимальный"; 2 -> "Анти-фриз"; else -> "Максимум" }
        return backupManager.buildDiagnosticHeader(
            engineLabel = eng,
            decoderLabel = dec,
            bufferLabel = buf,
            themeLabel = currentTheme().name,
            playlistsCount = playlists.size,
            channelsCount = allChannels.size,
            dohEnabled = dohEnabled,
            dpiBypassEnabled = dpiBypassEnabled,
            proxyUrl = networkProxyUrl,
            preferredAudioLang = preferredAudioLang
        )
    }

    private fun readDiagLogBytesForExport(): ByteArray? {
        val rawBody: ByteArray? = CoreEngines.readDiagLogBytes(this)?.takeIf { it.isNotEmpty() }
            ?: run {
                val srcFile = findDiagLogSourceFile()
                if (srcFile != null && srcFile.exists() && srcFile.length() > 0L) {
                    runCatching { srcFile.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
                } else null
            }
            ?: runCatching {
                val lines = CoreEngines.readDiagLogTail(500)
                if (lines.isNotEmpty()) lines.joinToString("\n").toByteArray(Charsets.UTF_8) else null
            }.getOrNull()
        val bodyText = rawBody?.toString(Charsets.UTF_8).orEmpty()
        val full = buildCurrentDiagnosticHeader() + bodyText.ifBlank { "(Событий пока нет)\n" }
        return full.toByteArray(Charsets.UTF_8)
    }

    private fun buildBackupStateJson(): JSONObject {
        val plArr = JSONArray()
        for (p in playlists) {
            if (p.source == "BUILTIN_VIP_PROVIDER") continue
            plArr.put(JSONObject().apply {
                put("title", p.title)
                put("source", p.source)
                put("isLocal", p.isLocal)
            })
        }
        val customCatObj = JSONObject()
        for ((u, c) in customCategoryMap) customCatObj.put(u, c)
        return JSONObject().apply {
            put("playlists", plArr)
            put("activePlaylistIndex", activePlaylistIndex)
            put("favorites", JSONArray(favoriteUrls.toList()))
            put("topUrls", JSONArray(topUrls.toList()))
            put("manualDisabledUrls", JSONArray(manualDisabledUrls.toList()))
            put("autoDisabledUrls", JSONArray(autoDisabledUrls.toList()))
            put("disabledCategories", JSONArray(disabledCategories.toList()))
            put("customCategoryMap", customCatObj)
            put("themeIndex", themeIndex)
            put("uiScaleStep", uiScaleStep)
            put("uiAlphaStep", uiAlphaStep)
            put("ambilightMode", ambilightMode)
            put("screenAspectMode", screenAspectMode)
            put("brightnessPercent", brightnessPercent)
            put("cameraCutoutSafeOffset", cameraCutoutSafeOffset)
            put("bufferLevel", bufferLevel)
            put("decoderMode", decoderMode)
            put("playerEngineMode", playerEngineMode)
            put("afrEnabled", afrEnabled)
            put("pixelOrbiterEnabled", pixelOrbiterEnabled)
            put("drcNightAudioEnabled", drcNightAudioEnabled)
            put("audioBoostPercent", audioBoostPercent)
            put("preferredAudioLang", preferredAudioLang)
            put("subtitlesEnabled", subtitlesEnabled)
            put("dohEnabled", dohEnabled)
            put("dpiBypassEnabled", dpiBypassEnabled)
            put("networkProxyUrl", networkProxyUrl)
            put("touchEdgeGesturesEnabled", touchEdgeGesturesEnabled)
            put("onDropBehavior", onDropBehavior)
            put("menuHideDelaySec", menuHideDelaySec)
            put("showChannelBannerOnSwitch", showChannelBannerOnSwitch)
            put("autoCheckUpdatesEnabled", autoCheckUpdatesEnabled)
            put("epgUrl", epgUrl)
        }
    }

    private fun applyBackupStateJson(st: JSONObject) {
        st.optJSONArray("playlists")?.let { arr ->
            val loaded = mutableListOf<PlaylistEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val src = o.optString("source", "").trim()
                if (src.isBlank() || src == "BUILTIN_VIP_PROVIDER") continue
                val title = o.optString("title", "Плейлист").trim().ifBlank { "Плейлист" }
                val isLocal = o.optBoolean("isLocal", false)
                if (isLocal && !File(src).exists()) continue
                loaded.add(PlaylistEntry(if (src == "BUILTIN_OPEN_RU") "Основной" else title, src, isLocal))
            }
            if (loaded.isNotEmpty()) {
                playlists.clear()
                playlists.addAll(loaded)
                ensureDefaultPlaylists()
            }
        }
        activePlaylistIndex = st.optInt("activePlaylistIndex", 0).coerceIn(0, (playlists.size - 1).coerceAtLeast(0))
        fun readSet(key: String, target: MutableSet<String>) {
            st.optJSONArray(key)?.let { arr ->
                target.clear()
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "").trim()
                    if (s.isNotEmpty()) target.add(s)
                }
            }
        }
        readSet("favorites", favoriteUrls)
        readSet("topUrls", topUrls)
        readSet("manualDisabledUrls", manualDisabledUrls)
        readSet("autoDisabledUrls", autoDisabledUrls)
        readSet("disabledCategories", disabledCategories)
        st.optJSONObject("customCategoryMap")?.let { obj ->
            customCategoryMap.clear()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "")
                if (k.isNotBlank() && v.isNotBlank()) customCategoryMap[k] = v
            }
        }
        themeIndex = st.optInt("themeIndex", themeIndex).coerceIn(0, themes.lastIndex)
        uiScaleStep = st.optInt("uiScaleStep", uiScaleStep).coerceIn(0, 2)
        uiAlphaStep = st.optInt("uiAlphaStep", uiAlphaStep).coerceIn(0, 2)
        ambilightMode = st.optInt("ambilightMode", ambilightMode).coerceIn(0, 3)
        screenAspectMode = st.optInt("screenAspectMode", screenAspectMode).coerceIn(0, 2)
        brightnessPercent = st.optInt("brightnessPercent", brightnessPercent).coerceIn(25, 100)
        cameraCutoutSafeOffset = st.optBoolean("cameraCutoutSafeOffset", cameraCutoutSafeOffset)
        bufferLevel = st.optInt("bufferLevel", bufferLevel).coerceIn(0, 3)
        decoderMode = st.optInt("decoderMode", decoderMode).coerceIn(0, 2)
        playerEngineMode = st.optInt("playerEngineMode", playerEngineMode).coerceIn(0, 1)
        afrEnabled = st.optBoolean("afrEnabled", afrEnabled)
        pixelOrbiterEnabled = st.optBoolean("pixelOrbiterEnabled", pixelOrbiterEnabled)
        drcNightAudioEnabled = st.optBoolean("drcNightAudioEnabled", drcNightAudioEnabled)
        audioBoostPercent = st.optInt("audioBoostPercent", audioBoostPercent)
        preferredAudioLang = st.optString("preferredAudioLang", preferredAudioLang).ifBlank { "rus" }
        subtitlesEnabled = st.optBoolean("subtitlesEnabled", subtitlesEnabled)
        dohEnabled = st.optBoolean("dohEnabled", dohEnabled)
        dpiBypassEnabled = st.optBoolean("dpiBypassEnabled", dpiBypassEnabled)
        networkProxyUrl = st.optString("networkProxyUrl", networkProxyUrl).trim()
        touchEdgeGesturesEnabled = st.optBoolean("touchEdgeGesturesEnabled", touchEdgeGesturesEnabled)
        onDropBehavior = st.optInt("onDropBehavior", onDropBehavior).coerceIn(0, 1)
        menuHideDelaySec = st.optInt("menuHideDelaySec", menuHideDelaySec).coerceIn(4, 12)
        showChannelBannerOnSwitch = st.optBoolean("showChannelBannerOnSwitch", showChannelBannerOnSwitch)
        autoCheckUpdatesEnabled = st.optBoolean("autoCheckUpdatesEnabled", autoCheckUpdatesEnabled)
        val restoredEpg = st.optString("epgUrl", "").trim()
        if (restoredEpg.isNotEmpty()) epgUrl = restoredEpg
    }

    private fun exportFullBackupToFile() {
        ensurePublicStoragePermission {
            val res = backupManager.exportBackup(buildBackupStateJson())
            Toast.makeText(this, res.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun importFullBackupFromFile() {
        ensurePublicStoragePermission {
            val (res, stateObj) = backupManager.importBackup()
            if (!res.ok || stateObj == null) {
                Toast.makeText(this, res.message, Toast.LENGTH_LONG).show()
                return@ensurePublicStoragePermission
            }
            applyBackupStateJson(stateObj)
            saveState(syncCommit = true)
            rebuildNetworkClients()
            currentAnimatedAlphaInt = targetPanelAlphaInt()
            applyCurrentThemeToAllPanels()
            applyScreenAspectMode()
            applyBrightnessLevel()
            initPlayer()
            loadActivePlaylist(autoPlayLast = false)
            Toast.makeText(this, "${res.message} (плейлистов: ${playlists.size}, избр: ${favoriteUrls.size})", Toast.LENGTH_LONG).show()
            closeAllMenus()
        }
    }

    private fun hasPublicDownloadWritePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePublicStoragePermission(action: () -> Unit) {
        if (hasPublicDownloadWritePermission()) {
            action()
            return
        }
        pendingStoragePermissionAction = action
        try {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        } catch (e: Exception) {
            pendingStoragePermissionAction = null
            CoreEngines.appendDiagLog("PERM_ERR", "Ошибка запроса разрешений: ${e.message}")
            action()
        }
    }

    private fun exportDiagLogToDownloadsSafely(): Pair<Boolean, String> {
        val logBytes = readDiagLogBytesForExport() ?: return Pair(false, "")
        val fileName = "TiViLook_diag.txt"

        // 1. Android 10–16: сохраняем строго ОДИН файл TiViLook_diag.txt в публичный Download через MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Чистим старые дубли (TiViLook_diag_TV.txt и TiViLook_diag (1).txt), созданные нашим приложением
                try {
                    contentResolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Downloads.DISPLAY_NAME}=? OR ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                        arrayOf("TiViLook_diag_TV.txt", "TiViLook_diag (%).txt")
                    )
                } catch (_: Exception) {}

                // Если наш TiViLook_diag.txt уже есть в Download — перезаписываем его без создания копий
                var existingUri: Uri? = null
                try {
                    contentResolver.query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Downloads._ID),
                        "${MediaStore.Downloads.DISPLAY_NAME}=?",
                        arrayOf(fileName),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(0)
                            existingUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                        }
                    }
                } catch (_: Exception) {}

                if (existingUri != null) {
                    try {
                        contentResolver.openOutputStream(existingUri!!, "wt")?.use { output ->
                            output.write(logBytes)
                            output.flush()
                        } ?: throw IOException("openOutputStream returned null")
                        return Pair(true, "Download/$fileName")
                    } catch (_: Exception) {
                        try { contentResolver.delete(existingUri!!, null, null) } catch (_: Exception) {}
                    }
                }

                // Если на диске остался старый файл от прямой записи прошлых версий — пробуем убрать его до вставки в MediaStore
                try {
                    @Suppress("DEPRECATION")
                    val legacyPubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (legacyPubDir != null) {
                        val legacyFile = File(legacyPubDir, fileName)
                        if (legacyFile.exists()) legacyFile.delete()
                        val legacyTv = File(legacyPubDir, "TiViLook_diag_TV.txt")
                        if (legacyTv.exists()) legacyTv.delete()
                    }
                } catch (_: Exception) {}

                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.write(logBytes)
                        output.flush()
                    } ?: throw IOException("openOutputStream returned null")
                    val doneValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    contentResolver.update(uri, doneValues, null, null)
                    val actualName = resolveUriDisplayName(uri).ifBlank { fileName }
                    return Pair(true, "Download/$actualName")
                }
            } catch (e: Exception) {
                CoreEngines.appendDiagLog("DIAG_EXPORT_ERR", "MediaStore export fail: ${e.message}")
            }
        } else {
            // 2. Android 9 и ниже (Mi Box TV): прямой путь /storage/emulated/0/Download
            try {
                @Suppress("DEPRECATION")
                val pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (pubDir != null && (pubDir.exists() || pubDir.mkdirs())) {
                    try { File(pubDir, "TiViLook_diag_TV.txt").delete() } catch (_: Exception) {}
                    val dst = File(pubDir, fileName)
                    FileOutputStream(dst, false).use { output ->
                        output.write(logBytes)
                        output.flush()
                    }
                    if (dst.exists() && dst.length() > 0L) {
                        try {
                            MediaScannerConnection.scanFile(this, arrayOf(dst.absolutePath), arrayOf("text/plain"), null)
                        } catch (_: Exception) {}
                        return Pair(true, "Download/$fileName")
                    }
                }
            } catch (e: Exception) {
                CoreEngines.appendDiagLog("DIAG_EXPORT_ERR", "Public Download write fail: ${e.message}")
            }
        }

        // 3. Резерв во внутреннюю папку программы, только если запись в публичный Download не удалась
        return try {
            val fallbackDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: getExternalFilesDir(null) ?: filesDir
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            val outFile = File(fallbackDir, fileName)
            FileOutputStream(outFile, false).use { output ->
                output.write(logBytes)
                output.flush()
            }
            Pair(false, outFile.absolutePath)
        } catch (_: Exception) {
            Pair(false, "")
        }
    }

    private fun launchSystemFileManagerForDownloads(): Boolean {
        val downloadDocUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")

        // 1. Системный файловый менеджер (DocumentsUI) прямо на папке Download
        try {
            val dirIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(downloadDocUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(dirIntent)
            return true
        } catch (_: Exception) {}

        // 2. Системное приложение «Загрузки / Файлы»
        try {
            val dlIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(dlIntent)
            return true
        } catch (_: Exception) {}

        // 3. Системный выбор документов с открытой папкой Download
        try {
            val docIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadDocUri)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(docIntent)
            return true
        } catch (_: Exception) {}

        // 4. Популярные файловые менеджеры Android TV / Android (Mi Box TV часто не содержит DocumentsUI)
        val knownManagers = listOf(
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.lonelycatgames.Xplore",
            "com.alphainventor.filemanager",
            "com.mobisystems.fileman",
            "com.mixplorer",
            "com.mixplorer.silver",
            "pl.solidexplorer2",
            "com.google.android.apps.nbu.files",
            "nextapp.fx",
            "com.ghisler.android.TotalCommander",
            "com.estrongs.android.pop",
            "com.amaze.filemanager",
            "dev.dworks.apps.anexplorer",
            "com.mi.android.globalFileexplorer",
            "com.sec.android.app.myfiles"
        )
        for (pkg in knownManagers) {
            try {
                val launch = packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launch)
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun saveDiagLogToDownloadsAndOpenFileManager() {
        val now = System.currentTimeMillis()
        if (now - lastDiagExportTriggerMs < 1500L) return
        lastDiagExportTriggerMs = now

        ensurePublicStoragePermission {
            val (savedToPublic, exportedInfo) = exportDiagLogToDownloadsSafely()
            val toastMsg = when {
                savedToPublic -> "Сохранено в Загрузки: $exportedInfo"
                exportedInfo.isNotBlank() -> "Нет прав на Download (сохранено: $exportedInfo)"
                else -> "Файл лога пока пуст"
            }
            Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show()
            val opened = launchSystemFileManagerForDownloads()
            if (!opened) {
                Toast.makeText(this, "Файловый менеджер не найден на устройстве", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openDiagLogInSystemFileManager() {
        saveDiagLogToDownloadsAndOpenFileManager()
    }

    private fun prepareDiagShareFileUri(): Uri? {
        val bytes = readDiagLogBytesForExport() ?: return null
        val shareDir = File(externalCacheDir ?: cacheDir, "shared_logs").apply { mkdirs() }
        val shareCopy = File(shareDir, "TiViLook_diag.txt")
        FileOutputStream(shareCopy, false).use { output ->
            output.write(bytes)
            output.flush()
        }
        return FileProvider.getUriForFile(this, "com.example.miboxiptv.fileprovider", shareCopy)
    }

    private fun buildDiagSendIntent(uri: Uri, mimeType: String = "text/plain"): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TiViLook_diag.txt")
            clipData = ClipData.newRawUri("TiViLook_diag.txt", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun shareDiagLogFile() {
        try {
            val uri = prepareDiagShareFileUri()
            if (uri == null) {
                Toast.makeText(this, "Файл лога пока пуст", Toast.LENGTH_SHORT).show()
                return
            }
            val found = LinkedHashMap<String, ResolveInfo>()
            for (mime in listOf("text/plain", "*/*")) {
                try {
                    val probe = buildDiagSendIntent(uri, mime)
                    @Suppress("DEPRECATION")
                    val list = packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                    for (ri in list) {
                        val ai = ri.activityInfo ?: continue
                        if (ai.packageName == packageName) continue
                        if (!ai.exported) continue
                        val key = "${ai.packageName}/${ai.name}"
                        if (!found.containsKey(key)) found[key] = ri
                    }
                } catch (_: Exception) {}
            }

            val targets = found.values.sortedBy {
                try { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) } catch (_: Exception) { "" }
            }

            if (targets.isNotEmpty()) {
                cachedShareTargets = targets
                cachedShareFileUri = uri
                navigateToMenu("DIAG_SHARE_PICKER")
            } else {
                // Фолбэк для Android 11+ без <queries> в манифесте
                val fallbackIntent = buildDiagSendIntent(uri, "text/plain")
                startActivity(Intent.createChooser(fallbackIntent, "Отправить диагностический файл"))
            }
        } catch (e: Exception) {
            CoreEngines.appendDiagLog("SHARE_ERR", "Share fail: ${e.message}")
            Toast.makeText(this, "Не найдено приложение для отправки файла", Toast.LENGTH_LONG).show()
        }
    }

    private fun openDiagSharePickerModal(firstOpen: Boolean = false) {
        setSubModalHeaderTitle("Поделиться файлом")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.VISIBLE
        subModalTopButtons.visibility = View.VISIBLE
        subModalTopButtons.removeAllViews()
        subModalTopButtons.addView(makeModernButton("◀ Назад к логу") {
            navigateBackInMenu()
        })

        val targets = cachedShareTargets
        val uri = cachedShareFileUri
        if (targets.isEmpty() || uri == null) {
            Toast.makeText(this, "Нет доступных приложений для отправки", Toast.LENGTH_LONG).show()
            navigateBackInMenu()
            return
        }

        // Определяем повторяющиеся названия приложений, чтобы уточнять имя активности только при совпадении
        val labelCounts = HashMap<String, Int>()
        val rawLabels = targets.map { ri ->
            try { ri.loadLabel(packageManager).toString().trim() } catch (_: Exception) { ri.activityInfo.packageName }
        }
        for (lbl in rawLabels) {
            labelCounts[lbl] = (labelCounts[lbl] ?: 0) + 1
        }

        val rows = targets.mapIndexed { idx, ri ->
            val lbl = rawLabels[idx].ifBlank { ri.activityInfo.packageName }
            if ((labelCounts[lbl] ?: 0) > 1) {
                val appName = try {
                    ri.activityInfo.applicationInfo.loadLabel(packageManager).toString().trim()
                } catch (_: Exception) { "" }
                val suffix = if (appName.isNotEmpty() && !appName.equals(lbl, ignoreCase = true)) appName else ri.activityInfo.name.substringAfterLast('.')
                "[>]   $lbl  •  $suffix"
            } else {
                "[>]   $lbl"
            }
        }.toMutableList()

        val hasSystemChooserRow = (controlMode != 2)
        if (hasSystemChooserRow) {
            rows.add("[>]   Системное меню Android")
        }

        updateSubModalListItems(rows, smoothToggle = !firstOpen, preservePositionIndex = 0)
        subModalList.setOnItemClickListener { _, _, which, _ ->
            if (which in targets.indices) {
                val chosen = targets[which]
                val pkg = chosen.activityInfo.packageName
                val cls = chosen.activityInfo.name
                try {
                    grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                try {
                    val direct = buildDiagSendIntent(uri, "text/plain").apply {
                        setClassName(pkg, cls)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(direct)
                } catch (e: Exception) {
                    CoreEngines.appendDiagLog("SHARE_ERR", "Direct share $pkg fail: ${e.message}")
                    Toast.makeText(this, "Не удалось открыть ${rawLabels[which]}", Toast.LENGTH_LONG).show()
                }
            } else if (hasSystemChooserRow && which == targets.size) {
                try {
                    startActivity(Intent.createChooser(buildDiagSendIntent(uri, "text/plain"), "Отправить TiViLook_diag.txt"))
                } catch (_: Exception) {}
            }
        }
        if (firstOpen) {
            animateMenuAppear(subModalOverlay)
            subModalList.requestFocus()
        }
    }

    private fun cancelActiveUpdateDownloadIfAny() {
        activeUpdateDownloadHandle?.cancel()
        activeUpdateDownloadHandle = null
        isUpdateDownloading = false
    }

    private fun showPendingUpdateIfReady() {
        val pending = pendingUpdateRelease ?: return
        if (isFinishing || isDestroyed) return
        if (splashOverlay.visibility == View.VISIBLE || firstRunModeOverlay.visibility == View.VISIBLE) return
        if (menuHistory.isNotEmpty()) return
        val relKey = updateManager.releaseUniqueKey(pending)
        if (relKey == lastPromptedUpdateKey) {
            pendingUpdateRelease = null
            return
        }
        pendingUpdateRelease = null
        lastPromptedUpdateKey = relKey
        updateModalRelease = pending
        updateModalManualCheck = false
        navigateToMenu("UPDATE_PROMPT")
    }

    private fun scheduleSilentStartupUpdateCheck(initialDelayMs: Long = 3500L) {
        if (!autoCheckUpdatesEnabled) return
        if (isUpdateCheckInFlight) return
        val nowMs = System.currentTimeMillis()
        val cooldownMs = 15L * 60L * 1000L
        if (lastSuccessfulUpdateCheckMs > 0L && (nowMs - lastSuccessfulUpdateCheckMs) < cooldownMs) return

        isUpdateCheckInFlight = true
        uiHandler.postDelayed({
            if (isFinishing || isDestroyed || !autoCheckUpdatesEnabled) {
                isUpdateCheckInFlight = false
                return@postDelayed
            }
            thread(priority = Thread.MIN_PRIORITY, name = "TiViLook-AutoUpdate") {
                try {
                    var result = updateManager.checkLatestReleaseDetailed()
                    if (result.release == null) {
                        // Одна тихая повторная попытка через 6 сек (например, если Wi-Fi на приставке ещё поднимался)
                        try { Thread.sleep(6000L) } catch (_: Exception) {}
                        if (isFinishing || isDestroyed || !autoCheckUpdatesEnabled) return@thread
                        result = updateManager.checkLatestReleaseDetailed()
                    }
                    val release = result.release ?: return@thread
                    lastSuccessfulUpdateCheckMs = System.currentTimeMillis()

                    if (!updateManager.isNewerRelease(release)) {
                        availableUpdateInfo = null
                        return@thread
                    }

                    availableUpdateInfo = release
                    val relKey = updateManager.releaseUniqueKey(release)
                    val prefs = getSharedPreferences("iptv_v4_prefs", Context.MODE_PRIVATE)
                    val skippedKey = prefs.getString("skipped_update_tag_v37", "") ?: ""
                    if (relKey == skippedKey || relKey == lastPromptedUpdateKey) return@thread

                    runOnUiThread {
                        if (isFinishing || isDestroyed || !autoCheckUpdatesEnabled) return@runOnUiThread
                        val busy = splashOverlay.visibility == View.VISIBLE ||
                            firstRunModeOverlay.visibility == View.VISIBLE ||
                            menuHistory.isNotEmpty()
                        if (busy) {
                            pendingUpdateRelease = release
                        } else {
                            lastPromptedUpdateKey = relKey
                            updateModalRelease = release
                            updateModalManualCheck = false
                            navigateToMenu("UPDATE_PROMPT")
                        }
                    }
                } catch (_: Exception) {
                    // Тихая проверка никогда не показывает ошибок пользователю
                } finally {
                    isUpdateCheckInFlight = false
                }
            }
        }, initialDelayMs)
    }

    private fun openUpdateAvailablePromptModal() {
        val info = updateModalRelease ?: availableUpdateInfo
        if (info == null) {
            navigateBackInMenu()
            return
        }
        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)
        val isNewer = updateManager.isNewerRelease(info)
        val localVer = updateManager.getInstalledVersionName().ifBlank { "текущая" }
        val remoteVer = info.versionLabel.ifBlank { info.tagName }.ifBlank { info.apkName }

        downloadedUpdateApkFile = null
        onUpdatePermissionResume = null
        cancelActiveUpdateDownloadIfAny()

        setSubModalHeaderTitle("Обновление TiViLook", "ОБ")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalThemesGrid.visibility = View.GONE
        subModalCustomBody.removeAllViews()
        subModalCustomBody.visibility = View.VISIBLE
        subModalCustomBody.orientation = LinearLayout.VERTICAL
        subModalCustomBody.setBackgroundDrawable(makeThemedSubBg())
        val pBox = dpPx(14f)
        subModalCustomBody.setPadding(pBox, pBox, pBox, pBox)
        subModalCustomBody.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

        val bodyScroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val bodyInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        bodyScroll.addView(bodyInner)
        subModalCustomBody.addView(bodyScroll)

        val badgePill = makeStatusPillView(
            if (isNewer) "НОВАЯ ВЕРСИЯ" else "АКТУАЛЬНАЯ ВЕРСИЯ",
            if (isNewer) 1 else 0
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpPx(10f * scale)
            }
        }
        bodyInner.addView(badgePill)

        val versionsTv = TextView(this).apply {
            text = "Установлена: $localVer  →  GitHub: $remoteVer"
            setTextColor(t.textPrimaryColor)
            typeface = t.boldFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.2f * scale)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        bodyInner.addView(versionsTv)

        val sizePart = if (info.apkSizeBytes > 0L) "  •  ${UpdateManager.formatBytes(info.apkSizeBytes)}" else ""
        val metaTv = TextView(this).apply {
            text = "${info.apkName}$sizePart  (${info.tagName})"
            setTextColor(t.textPrimaryColor)
            alpha = 0.72f
            typeface = t.normalFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale)
            gravity = Gravity.CENTER
            setPadding(0, dpPx(4f * scale), 0, dpPx(10f * scale))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        bodyInner.addView(metaTv)

        val notesCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(if (dark) Color.argb(66, 0, 0, 0) else Color.argb(22, 0, 0, 0))
                cornerRadius = themeInnerCornerRadiusPx(scale)
                setStroke(dpPx(1f), themeCardBorderColor())
            })
            val np = dpPx(12f * scale)
            setPadding(np, dpPx(10f * scale), np, dpPx(10f * scale))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpPx(10f * scale)
            }
        }
        val notesTv = TextView(this).apply {
            text = info.body.ifBlank { "Описание изменений не указано." }
            setTextColor(t.textPrimaryColor)
            alpha = 0.88f
            typeface = t.normalFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.2f * scale)
            setLineSpacing(4f, 1.0f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        notesCard.addView(notesTv)
        bodyInner.addView(notesCard)

        val progressWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpPx(4f * scale)
                bottomMargin = dpPx(4f * scale)
            }
        }
        val progressStatusTv = TextView(this).apply {
            text = "Подготовка загрузки..."
            setTextColor(t.accentColor)
            typeface = t.boldFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.8f * scale)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpPx(6f * scale))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val progressTrack = FrameLayout(this).apply {
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(if (dark) Color.argb(65, 255, 255, 255) else Color.argb(48, 0, 0, 0))
                cornerRadius = 999f
            })
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpPx(9f * scale))
        }
        val progressFillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 100f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val progressFillView = View(this).apply {
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(themeOnPillBgColor())
                cornerRadius = 999f
            })
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0f)
        }
        val progressRemainView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 100f)
        }
        progressFillRow.addView(progressFillView)
        progressFillRow.addView(progressRemainView)
        progressTrack.addView(progressFillRow)

        val permHintTv = TextView(this).apply {
            text = "Для автоматической установки разрешите установку неизвестных приложений для TiViLook и вернитесь назад."
            setTextColor(Color.parseColor("#FBBF24"))
            typeface = t.normalFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f * scale)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dpPx(6f), dpPx(8f * scale), dpPx(6f), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        progressWrap.addView(progressStatusTv)
        progressWrap.addView(progressTrack)
        progressWrap.addView(permHintTv)
        bodyInner.addView(progressWrap)

        fun setProgressBarFraction(pct: Int) {
            val clamped = pct.coerceIn(0, 100).toFloat()
            progressFillView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, clamped)
            progressRemainView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100f - clamped).coerceAtLeast(0.01f))
            progressFillRow.requestLayout()
        }

        subModalBottomButtons.removeAllViews()
        subModalBottomButtons.visibility = View.VISIBLE

        lateinit var btnPrimary: TextView
        lateinit var btnSecondary: TextView
        var btnSkip: TextView? = null

        fun tryInstallOrRequestPermission(apkFile: File) {
            if (!updateManager.canInstallUnknownApps()) {
                permHintTv.visibility = View.VISIBLE
                progressStatusTv.text = "Обновление загружено — нужно разрешение на установку"
                progressStatusTv.setTextColor(Color.parseColor("#FBBF24"))
                btnPrimary.text = "Разрешить установку"
                btnPrimary.isEnabled = true
                btnPrimary.alpha = 1f
                btnSecondary.text = "Закрыть"
                btnPrimary.requestFocus()
                onUpdatePermissionResume = {
                    if (menuHistory.lastOrNull() == "UPDATE_PROMPT" && downloadedUpdateApkFile != null && updateManager.canInstallUnknownApps()) {
                        permHintTv.visibility = View.GONE
                        progressStatusTv.text = "Разрешение выдано — запуск установки..."
                        progressStatusTv.setTextColor(themeOnPillBgColor())
                        btnPrimary.text = "Установить"
                        onUpdatePermissionResume = null
                        updateManager.launchPackageInstaller(this, apkFile)
                    }
                }
                return
            }
            permHintTv.visibility = View.GONE
            onUpdatePermissionResume = null
            progressStatusTv.text = "Запуск установщика Android..."
            progressStatusTv.setTextColor(themeOnPillBgColor())
            btnPrimary.text = "Установить"
            updateManager.launchPackageInstaller(this, apkFile)
        }

        btnPrimary = makeUnifiedActionButton(if (isNewer) "Скачать и установить" else "Установить заново") {
            if (isUpdateDownloading) return@makeUnifiedActionButton

            val readyApk = downloadedUpdateApkFile
            if (readyApk != null && readyApk.exists() && readyApk.length() > 0L) {
                if (!updateManager.canInstallUnknownApps()) {
                    updateManager.openUnknownSourcesSettings(this)
                } else {
                    tryInstallOrRequestPermission(readyApk)
                }
                return@makeUnifiedActionButton
            }

            isUpdateDownloading = true
            uiHandler.removeCallbacks(autoHideMenuRunnable)
            progressWrap.visibility = View.VISIBLE
            permHintTv.visibility = View.GONE
            setProgressBarFraction(0)
            progressStatusTv.setTextColor(t.accentColor)
            progressStatusTv.text = "Подключение к GitHub..."
            btnPrimary.isEnabled = false
            btnPrimary.alpha = 0.5f
            btnSkip?.visibility = View.GONE
            btnSecondary.text = "Отмена"
            btnSecondary.requestFocus()

            activeUpdateDownloadHandle = updateManager.startApkDownload(
                release = info,
                onProgress = { downBytes, totalBytes, pct ->
                    if (menuHistory.lastOrNull() != "UPDATE_PROMPT") return@startApkDownload
                    if (pct >= 0 && totalBytes > 0L) {
                        setProgressBarFraction(pct)
                        progressStatusTv.text = "Загрузка: $pct%  (${UpdateManager.formatBytes(downBytes)} / ${UpdateManager.formatBytes(totalBytes)})"
                    } else {
                        setProgressBarFraction(((downBytes / 65536L) % 100L).toInt().coerceAtLeast(8))
                        progressStatusTv.text = "Загрузка: ${UpdateManager.formatBytes(downBytes)}"
                    }
                },
                onSuccess = { readyFile ->
                    isUpdateDownloading = false
                    activeUpdateDownloadHandle = null
                    downloadedUpdateApkFile = readyFile
                    if (menuHistory.lastOrNull() != "UPDATE_PROMPT") return@startApkDownload
                    setProgressBarFraction(100)
                    btnPrimary.isEnabled = true
                    btnPrimary.alpha = 1f
                    btnSecondary.text = "Закрыть"
                    tryInstallOrRequestPermission(readyFile)
                },
                onCancelled = {
                    isUpdateDownloading = false
                    activeUpdateDownloadHandle = null
                    if (menuHistory.lastOrNull() != "UPDATE_PROMPT") return@startApkDownload
                    setProgressBarFraction(0)
                    progressStatusTv.setTextColor(Color.parseColor("#FBBF24"))
                    progressStatusTv.text = "Загрузка отменена"
                    btnPrimary.isEnabled = true
                    btnPrimary.alpha = 1f
                    btnPrimary.text = if (isNewer) "Скачать и установить" else "Установить заново"
                    btnSecondary.text = "Закрыть"
                    if (isNewer && !updateModalManualCheck) btnSkip?.visibility = View.VISIBLE
                    btnPrimary.requestFocus()
                },
                onFailure = { errMsg ->
                    isUpdateDownloading = false
                    activeUpdateDownloadHandle = null
                    if (menuHistory.lastOrNull() != "UPDATE_PROMPT") return@startApkDownload
                    setProgressBarFraction(0)
                    progressStatusTv.setTextColor(Color.parseColor("#F87171"))
                    progressStatusTv.text = "Ошибка загрузки: $errMsg"
                    btnPrimary.isEnabled = true
                    btnPrimary.alpha = 1f
                    btnPrimary.text = "Повторить загрузку"
                    btnSecondary.text = "Закрыть"
                    btnPrimary.requestFocus()
                }
            )
        }.apply { id = 8801 }

        btnSecondary = makeUnifiedActionButton(if (isNewer && !updateModalManualCheck) "Позже" else "Закрыть") {
            if (isUpdateDownloading) {
                cancelActiveUpdateDownloadIfAny()
            } else {
                navigateBackInMenu()
            }
        }.apply { id = 8802 }

        subModalBottomButtons.addView(btnPrimary)
        subModalBottomButtons.addView(btnSecondary)

        if (isNewer && !updateModalManualCheck) {
            btnSkip = makeUnifiedActionButton("Пропустить") {
                getSharedPreferences("iptv_v4_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("skipped_update_tag_v37", updateManager.releaseUniqueKey(info))
                    .apply()
                navigateBackInMenu()
            }.apply { id = 8803 }
            subModalBottomButtons.addView(btnSkip)

            btnPrimary.nextFocusLeftId = btnSkip!!.id
            btnPrimary.nextFocusRightId = btnSecondary.id
            btnSecondary.nextFocusLeftId = btnPrimary.id
            btnSecondary.nextFocusRightId = btnSkip!!.id
            btnSkip!!.nextFocusLeftId = btnSecondary.id
            btnSkip!!.nextFocusRightId = btnPrimary.id
        } else {
            btnPrimary.nextFocusLeftId = btnSecondary.id
            btnPrimary.nextFocusRightId = btnSecondary.id
            btnSecondary.nextFocusLeftId = btnPrimary.id
            btnSecondary.nextFocusRightId = btnPrimary.id
        }

        animateMenuAppear(subModalOverlay)
        if (isNewer) btnPrimary.requestFocus() else btnSecondary.requestFocus()
    }

    private fun checkAndInstallAppUpdateFromGithub() {
        Toast.makeText(this, "Проверка новой сборки TiViLook...", Toast.LENGTH_SHORT).show()
        thread(name = "TiViLook-ManualUpdate") {
            val result = updateManager.checkLatestReleaseDetailed()
            val release = result.release
            if (release == null) {
                runOnUiThread {
                    val err = result.error?.take(60) ?: "нет ответа"
                    Toast.makeText(this, "Не удалось проверить обновления: $err", Toast.LENGTH_LONG).show()
                }
                return@thread
            }

            lastSuccessfulUpdateCheckMs = System.currentTimeMillis()
            val isNewer = updateManager.isNewerRelease(release)
            availableUpdateInfo = if (isNewer) release else null

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                updateModalRelease = release
                updateModalManualCheck = true
                navigateToMenu("UPDATE_PROMPT")
            }
        }
    }

    private fun launchSystemPlaylistPicker() {
        val openDoc = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            systemPlaylistPickerLauncher.launch(openDoc)
            return
        } catch (_: Exception) {}
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            systemPlaylistPickerLauncher.launch(getContent)
        } catch (_: Exception) {
            Toast.makeText(this, "Системный выбор файла недоступен на этом устройстве", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchSystemEpgPicker() {
        val openDoc = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            systemEpgPickerLauncher.launch(openDoc)
            return
        } catch (_: Exception) {}
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            systemEpgPickerLauncher.launch(getContent)
        } catch (_: Exception) {
            Toast.makeText(this, "Системный выбор файла недоступен на этом устройстве", Toast.LENGTH_LONG).show()
        }
    }

    private fun resolveUriDisplayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name.trim()
                    }
                }
            }
        } catch (_: Exception) {}
        val seg = uri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast(":")
        return if (!seg.isNullOrBlank()) seg.trim() else "playlist.m3u8"
    }

    private fun importPlaylistFromUri(uri: Uri) {
        try {
            val displayName = resolveUriDisplayName(uri)
            val safeName = displayName.replace(Regex("[^a-zA-Zа-яА-Я0-9._ -]"), "_").ifBlank { "playlist.m3u8" }
            val destDir = File(filesDir, "user_playlists").apply { mkdirs() }
            val destFile = File(destDir, "${System.currentTimeMillis()}_$safeName")
            val input = contentResolver.openInputStream(uri)
            if (input == null) {
                Toast.makeText(this, "Не удалось открыть выбранный файл", Toast.LENGTH_LONG).show()
                return
            }
            input.use { src ->
                FileOutputStream(destFile).use { dst ->
                    src.copyTo(dst)
                }
            }
            if (!destFile.exists() || destFile.length() == 0L) {
                try { destFile.delete() } catch (_: Exception) {}
                Toast.makeText(this, "Выбранный файл пуст", Toast.LENGTH_LONG).show()
                return
            }
            playlists.add(PlaylistEntry(displayName, destFile.absolutePath, true))
            activePlaylistIndex = playlists.size - 1
            saveState()
            closeAllMenus()
            Toast.makeText(this, "Плейлист добавлен: $displayName", Toast.LENGTH_SHORT).show()
            loadActivePlaylist(autoPlayLast = false)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка импорта файла: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importEpgFromUri(uri: Uri) {
        try {
            val displayName = resolveUriDisplayName(uri)
            val safeName = displayName.replace(Regex("[^a-zA-Zа-яА-Я0-9._ -]"), "_").ifBlank { "epg.xml" }
            val destDir = File(filesDir, "user_epg").apply { mkdirs() }
            destDir.listFiles()?.forEach { try { it.delete() } catch (_: Exception) {} }
            val destFile = File(destDir, safeName)
            val input = contentResolver.openInputStream(uri)
            if (input == null) {
                Toast.makeText(this, "Не удалось открыть файл ТВ программы", Toast.LENGTH_LONG).show()
                return
            }
            input.use { src ->
                FileOutputStream(destFile).use { dst ->
                    src.copyTo(dst)
                }
            }
            if (!destFile.exists() || destFile.length() == 0L) {
                try { destFile.delete() } catch (_: Exception) {}
                Toast.makeText(this, "Выбранный файл ТВ программы пуст", Toast.LENGTH_LONG).show()
                return
            }
            localEpgFilePath = destFile.absolutePath
            saveState()
            closeAllMenus()
            Toast.makeText(this, "Загрузка ТВ программы: $displayName", Toast.LENGTH_SHORT).show()
            loadEpgOncePerDayInBackground(forceRefresh = true)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка импорта ТВ программы: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteUserPlaylistAt(index: Int): Boolean {
        if (index !in 1 until playlists.size) return false
        if (playlists[index].source == "BUILTIN_OPEN_RU") return false
        val removed = playlists.removeAt(index)
        try {
            val internalDir = File(filesDir, "user_playlists").absolutePath
            if (removed.isLocal && removed.source.startsWith(internalDir)) {
                File(removed.source).delete()
            }
        } catch (_: Exception) {}
        val wasActive = (activePlaylistIndex == index)
        if (wasActive) {
            activePlaylistIndex = 0
        } else if (activePlaylistIndex > index) {
            activePlaylistIndex--
        }
        if (activePlaylistIndex !in playlists.indices) activePlaylistIndex = 0
        saveState()
        Toast.makeText(this, "Плейлист удалён: ${removed.title}", Toast.LENGTH_SHORT).show()
        if (wasActive) {
            closeAllMenus()
            loadActivePlaylist(autoPlayLast = false)
        }
        return wasActive
    }

    private fun openDeletePlaylistSubModal() {
        ensureDefaultPlaylists()
        val userEntries = playlists.mapIndexedNotNull { idx, p ->
            if (idx >= 1 && p.source != "BUILTIN_OPEN_RU") Pair(idx, p) else null
        }
        if (userEntries.isEmpty()) {
            navigateBackInMenu()
            return
        }
        setSubModalHeaderTitle("Удалить плейлист")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.VISIBLE
        val items = userEntries.map { "[-]   ${it.second.title}" }
        updateSubModalListItems(items, preservePositionIndex = 0)

        subModalList.setOnItemClickListener { _, _, which, _ ->
            val target = userEntries.getOrNull(which) ?: return@setOnItemClickListener
            val reloaded = deleteUserPlaylistAt(target.first)
            if (!reloaded) {
                val hasMore = playlists.any { it.source != "BUILTIN_OPEN_RU" }
                if (hasMore) openDeletePlaylistSubModal() else navigateBackInMenu()
            }
        }
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
    }

    private fun isExportableUrlPlaylist(p: PlaylistEntry): Boolean {
        if (p.isLocal) return false
        if (p.source == "BUILTIN_OPEN_RU" || p.source == "BUILTIN_STRICT_RU_ONLY") return false
        return p.source.startsWith("http://", ignoreCase = true) || p.source.startsWith("https://", ignoreCase = true)
    }

    private fun buildSafePlaylistFileName(title: String, sourceUrl: String): String {
        val cleaned = title
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(64)
            .ifBlank { "TiViLook_playlist" }
        val lowName = cleaned.lowercase(Locale.US)
        if (lowName.endsWith(".m3u8") || lowName.endsWith(".m3u")) return cleaned
        val urlPath = sourceUrl.substringBefore("?").substringBefore("#").lowercase(Locale.US)
        val ext = if (urlPath.endsWith(".m3u")) ".m3u" else ".m3u8"
        return cleaned + ext
    }

    private fun writePlaylistBytesToDownloadsSafely(fileName: String, contentBytes: ByteArray): Pair<Boolean, String> {
        val ext = fileName.substringAfterLast('.', "m3u8").lowercase(Locale.US)
        val mimeType = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"

        // 1. Android 10–16: сохраняем в публичный Download через MediaStore с перезаписью без дублей
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                var existingUri: Uri? = null
                try {
                    contentResolver.query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Downloads._ID),
                        "${MediaStore.Downloads.DISPLAY_NAME}=?",
                        arrayOf(fileName),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(0)
                            existingUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                        }
                    }
                } catch (_: Exception) {}

                if (existingUri != null) {
                    try {
                        contentResolver.openOutputStream(existingUri!!, "wt")?.use { output ->
                            output.write(contentBytes)
                            output.flush()
                        } ?: throw IOException("openOutputStream returned null")
                        return Pair(true, "Download/$fileName")
                    } catch (_: Exception) {
                        try { contentResolver.delete(existingUri!!, null, null) } catch (_: Exception) {}
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.write(contentBytes)
                        output.flush()
                    } ?: throw IOException("openOutputStream returned null")
                    val doneValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    contentResolver.update(uri, doneValues, null, null)
                    val actualName = resolveUriDisplayName(uri).ifBlank { fileName }
                    return Pair(true, "Download/$actualName")
                }
            } catch (e: Exception) {
                CoreEngines.appendDiagLog("PLAYLIST_EXPORT_ERR", "MediaStore fail ($fileName): ${e.message}")
            }
        } else {
            // 2. Android 9 и ниже (Mi Box TV): прямой путь /storage/emulated/0/Download
            try {
                @Suppress("DEPRECATION")
                val pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (pubDir != null && (pubDir.exists() || pubDir.mkdirs())) {
                    val dst = File(pubDir, fileName)
                    FileOutputStream(dst, false).use { output ->
                        output.write(contentBytes)
                        output.flush()
                    }
                    if (dst.exists() && dst.length() > 0L) {
                        try {
                            MediaScannerConnection.scanFile(this, arrayOf(dst.absolutePath), arrayOf(mimeType), null)
                        } catch (_: Exception) {}
                        return Pair(true, "Download/$fileName")
                    }
                }
            } catch (e: Exception) {
                CoreEngines.appendDiagLog("PLAYLIST_EXPORT_ERR", "Public Download fail ($fileName): ${e.message}")
            }
        }

        // 3. Резерв во внутреннюю папку программы, если ОС отказала в доступе к Download
        return try {
            val fallbackDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: getExternalFilesDir(null) ?: filesDir
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            val outFile = File(fallbackDir, fileName)
            FileOutputStream(outFile, false).use { output ->
                output.write(contentBytes)
                output.flush()
            }
            Pair(false, outFile.absolutePath)
        } catch (_: Exception) {
            Pair(false, "")
        }
    }

    private fun exportUrlPlaylistToFile(entry: PlaylistEntry) {
        ensurePublicStoragePermission {
            val fileName = buildSafePlaylistFileName(entry.title, entry.source)
            Toast.makeText(this, "Скачивание плейлиста «${entry.title}»...", Toast.LENGTH_SHORT).show()
            thread {
                try {
                    val rawBytes = CoreEngines.openStreamWithBypass(entry.source, userAgentIndex, dohEnabled, 15000).use { input ->
                        input.readBytes()
                    }
                    if (rawBytes.isEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this, "Плейлист по ссылке оказался пустым", Toast.LENGTH_LONG).show()
                        }
                        return@thread
                    }
                    val finalBytes = if (rawBytes.size >= 2 && rawBytes[0] == 0x1F.toByte() && rawBytes[1] == 0x8B.toByte()) {
                        try {
                            GZIPInputStream(java.io.ByteArrayInputStream(rawBytes)).use { it.readBytes() }
                        } catch (_: Exception) {
                            rawBytes
                        }
                    } else {
                        rawBytes
                    }
                    val previewHead = String(finalBytes, 0, finalBytes.size.coerceAtMost(512), Charsets.UTF_8)
                    if (previewHead.contains("<html", ignoreCase = true) || previewHead.contains("<!doctype html", ignoreCase = true)) {
                        CoreEngines.appendDiagLog("PLAYLIST_EXPORT_ERR", "Сервер вернул HTML вместо плейлиста для '${entry.title}'")
                        runOnUiThread {
                            Toast.makeText(this, "Сервер вернул страницу ошибки вместо плейлиста", Toast.LENGTH_LONG).show()
                        }
                        return@thread
                    }
                    val (savedToPublic, savedPath) = writePlaylistBytesToDownloadsSafely(fileName, finalBytes)
                    val sizeLabel = formatByteSizeRu(finalBytes.size.toLong())
                    CoreEngines.appendDiagLog("PLAYLIST_EXPORT", "title='${entry.title}' size=$sizeLabel public=$savedToPublic path='$savedPath'")
                    runOnUiThread {
                        val msg = when {
                            savedToPublic -> "Плейлист сохранён: $savedPath ($sizeLabel)"
                            savedPath.isNotBlank() -> "Нет прав на Download (сохранено: $savedPath)"
                            else -> "Не удалось сохранить файл плейлиста"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    CoreEngines.appendDiagLog("PLAYLIST_EXPORT_ERR", "Ошибка сохранения '${entry.title}': ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка скачивания плейлиста: ${e.message ?: "нет ответа"}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun openExportPlaylistSubModal() {
        ensureDefaultPlaylists()
        val urlEntries = playlists.filter { isExportableUrlPlaylist(it) }
        if (urlEntries.isEmpty()) {
            navigateBackInMenu()
            return
        }
        setSubModalHeaderTitle("Сохранить плейлист в файл")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.VISIBLE
        val items = urlEntries.map { "[>]   ${it.title}" }
        updateSubModalListItems(items, preservePositionIndex = 0)

        subModalList.setOnItemClickListener { _, _, which, _ ->
            val target = urlEntries.getOrNull(which) ?: return@setOnItemClickListener
            closeAllMenus()
            exportUrlPlaylistToFile(target)
        }
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
    }

    private fun openRenamePlaylistSubModal() {
        ensureDefaultPlaylists()
        val idx = renamingPlaylistIdx
        if (idx !in 1 until playlists.size || playlists[idx].source == "BUILTIN_OPEN_RU") {
            navigateBackInMenu()
            return
        }
        val entry = playlists[idx]
        setSubModalHeaderTitle("Переименовать плейлист", "ПЛ")
        subModalList.visibility = View.GONE
        subModalCustomBody.visibility = View.GONE
        subModalBottomButtons.visibility = View.GONE
        subModalInputContainer.removeAllViews()
        subModalInputContainer.visibility = View.VISIBLE
        val input = EditText(this).apply {
            setText(entry.title)
            setSelection(entry.title.length)
            setTextColor(currentTheme().textPrimaryColor)
            setSingleLine()
        }
        subModalInputContainer.addView(input)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 0)
        }
        btnRow.addView(makeUnifiedActionButton("Сохранить") {
            val newTitle = input.text.toString().trim()
            if (newTitle.isNotEmpty() && idx in playlists.indices) {
                val old = playlists[idx]
                playlists[idx] = PlaylistEntry(newTitle, old.source, old.isLocal)
                saveState()
                Toast.makeText(this, "Переименовано: $newTitle", Toast.LENGTH_SHORT).show()
            }
            navigateBackInMenu()
        })
        btnRow.addView(makeUnifiedActionButton("Назад") { navigateBackInMenu() })
        subModalInputContainer.addView(btnRow)
        animateMenuAppear(subModalOverlay)
        input.requestFocus()
    }

    private fun openPlaylistsSubModal(inPlace: Boolean = false, keepIndex: Int = -1) {
        ensureDefaultPlaylists()
        setSubModalHeaderTitle("Плейлисты", "ПЛ")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalThemesGrid.visibility = View.GONE
        subModalCustomBody.removeAllViews()
        subModalCustomBody.visibility = View.VISIBLE
        subModalCustomBody.orientation = LinearLayout.HORIZONTAL
        subModalCustomBody.setBackgroundDrawable(makeThemedSubBg())
        subModalCustomBody.setPadding(14, 12, 14, 12)
        subModalCustomBody.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

        val snapshot = playlists.toList()
        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)
        selectedPlaylistModalIdx = when {
            keepIndex in snapshot.indices -> keepIndex
            selectedPlaylistModalIdx in snapshot.indices && inPlace -> selectedPlaylistModalIdx
            else -> activePlaylistIndex.coerceIn(0, (snapshot.size - 1).coerceAtLeast(0))
        }

        val leftColWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.56f).apply {
                rightMargin = (14 * scale).toInt()
            }
        }
        val leftHeader = TextView(this).apply {
            text = "Список"
            setTextColor(t.textPrimaryColor); alpha = 0.70f
            typeface = t.boldFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale)
            setPadding((10 * scale).toInt(), 2, 4, (8 * scale).toInt())
        }
        val leftScroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val leftList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        leftScroll.addView(leftList)
        leftColWrap.addView(leftHeader)
        leftColWrap.addView(leftScroll)

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.44f)
        }
        val rightHeader = TextView(this).apply {
            text = "Действия"
            setTextColor(t.textPrimaryColor); alpha = 0.70f
            typeface = t.boldFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale)
            setPadding((6 * scale).toInt(), 2, 4, (8 * scale).toInt())
        }
        val rightScroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val rightScrollInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        val actionsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val selectedHintTv = TextView(this).apply {
            setTextColor(t.textPrimaryColor); alpha = 0.68f
            typeface = t.normalFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale)
            setPadding(dpPx(6f * scale), dpPx(6f * scale), dpPx(4f), 0)
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        rightScrollInner.addView(actionsBox)
        rightScrollInner.addView(selectedHintTv)
        rightScroll.addView(rightScrollInner)
        rightCol.addView(rightHeader)
        rightCol.addView(rightScroll)

        val rowViews = mutableListOf<LinearLayout>()
        val rowTexts = mutableListOf<TextView>()
        val rowChecks = mutableListOf<TextView>()

        var btnAddUrl: TextView? = null
        var btnAddFile: TextView? = null

        fun rebuildActionButtons(selIdx: Int) {
            actionsBox.removeAllViews()
            val entry = snapshot.getOrNull(selIdx) ?: return
            val isBuiltin = (selIdx == 0 || entry.source == "BUILTIN_OPEN_RU")
            val canExport = isExportableUrlPlaylist(entry)
            selectedHintTv.text = when {
                isBuiltin -> "Встроенный плейлист (только выбор)"
                canExport -> "Сетевой плейлист по ссылке"
                else -> "Локальный файл на устройстве"
            }

            val firstRowId = rowViews.getOrNull(selIdx)?.id ?: View.NO_ID
            val actBtns = mutableListOf<TextView>()

            val bSelect = makeUnifiedActionButton("▶  Выбрать", fillWidth = true) {
                activePlaylistIndex = selIdx
                saveState(); closeAllMenus(); loadActivePlaylist(autoPlayLast = false)
            }.apply { id = 8551 }
            actBtns.add(bSelect)
            actionsBox.addView(bSelect)

            val bExport = makeUnifiedActionButton("↓  В файл .m3u8", isEnabledState = canExport, fillWidth = true) {
                closeAllMenus()
                exportUrlPlaylistToFile(entry)
            }.apply { id = 8552 }
            if (canExport) actBtns.add(bExport)
            actionsBox.addView(bExport)

            val bRename = makeUnifiedActionButton("✎  Переименовать", isEnabledState = !isBuiltin, fillWidth = true) {
                renamingPlaylistIdx = selIdx
                navigateToMenu("RENAME_PLAYLIST")
            }.apply { id = 8553 }
            if (!isBuiltin) actBtns.add(bRename)
            actionsBox.addView(bRename)

            val bDelete = makeUnifiedActionButton("✕  Удалить", isDanger = true, isEnabledState = !isBuiltin, fillWidth = true) {
                val reloaded = deleteUserPlaylistAt(selIdx)
                if (!reloaded) {
                    openPlaylistsSubModal(inPlace = true, keepIndex = (selIdx - 1).coerceAtLeast(0))
                }
            }.apply { id = 8554 }
            if (!isBuiltin) actBtns.add(bDelete)
            actionsBox.addView(bDelete)

            for (i in actBtns.indices) {
                actBtns[i].nextFocusLeftId = firstRowId
                if (i > 0) actBtns[i].nextFocusUpId = actBtns[i - 1].id
                if (i + 1 < actBtns.size) actBtns[i].nextFocusDownId = actBtns[i + 1].id
                else btnAddFile?.let { actBtns[i].nextFocusDownId = it.id }
            }
            rowViews.forEach { rv -> rv.nextFocusRightId = bSelect.id }
        }

        fun refreshPlaylistRows(selIdx: Int) {
            val curT = currentTheme()
            for (i in rowViews.indices) {
                val row = rowViews[i]
                val tv = rowTexts[i]
                val chk = rowChecks[i]
                val isFocused = row.hasFocus()
                val isSelected = (i == selIdx)
                val isActive = (i == activePlaylistIndex)
                chk.text = if (isActive) "✓" else ""
                when {
                    isFocused -> {
                        row.setBackgroundDrawable(GradientDrawable().apply {
                            setColor(curT.accentColor)
                            cornerRadius = themeInnerCornerRadiusPx(scale)
                            setStroke(dpPx(2f), themeFocusStrokeColor())
                        })
                        tv.setTextColor(curT.textOnAccent); tv.typeface = curT.boldFont
                        chk.setTextColor(curT.textOnAccent)
                    }
                    isSelected -> {
                        row.setBackgroundDrawable(GradientDrawable().apply {
                            setColor(Color.argb(66, Color.red(curT.accentColor), Color.green(curT.accentColor), Color.blue(curT.accentColor)))
                            cornerRadius = themeInnerCornerRadiusPx(scale)
                            setStroke(dpPx(1f), curT.accentColor)
                        })
                        tv.setTextColor(curT.textPrimaryColor); tv.typeface = curT.boldFont
                        chk.setTextColor(themeOnPillBgColor())
                    }
                    else -> {
                        row.setBackgroundDrawable(GradientDrawable().apply {
                            setColor(if (dark) Color.argb(56, 0, 0, 0) else Color.argb(20, 0, 0, 0))
                            cornerRadius = themeInnerCornerRadiusPx(scale)
                        })
                        tv.setTextColor(curT.textPrimaryColor); tv.typeface = if (isActive) curT.boldFont else curT.normalFont
                        chk.setTextColor(themeOnPillBgColor())
                    }
                }
            }
        }

        val rowH = LinearLayout.LayoutParams.WRAP_CONTENT
        snapshot.forEachIndexed { idx, entry ->
            val tv = TextView(this).apply {
                text = entry.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.8f * scale)
                isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val chk = TextView(this).apply {
                typeface = t.boldFont
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
                setPadding((8 * scale).toInt(), 0, 2, 0)
            }
            val row = LinearLayout(this).apply {
                id = View.generateViewId()
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true; isFocusableInTouchMode = false; isClickable = true
                minimumHeight = dpPx(40f * scale)
                setPadding(dpPx(12f * scale), dpPx(8f * scale), dpPx(12f * scale), dpPx(8f * scale))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH).apply {
                    val mv = dpPx(3f * scale)
                    setMargins(2, mv, 2, mv)
                }
                addView(tv); addView(chk)
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        resetMenuInactivityTimer()
                        if (selectedPlaylistModalIdx != idx) {
                            selectedPlaylistModalIdx = idx
                            rebuildActionButtons(idx)
                        }
                    }
                    refreshPlaylistRows(selectedPlaylistModalIdx)
                }
                setOnClickListener {
                    resetMenuInactivityTimer()
                    if (controlMode == 1 && selectedPlaylistModalIdx != idx) {
                        selectedPlaylistModalIdx = idx
                        rebuildActionButtons(idx)
                        refreshPlaylistRows(idx)
                    } else {
                        activePlaylistIndex = idx
                        saveState(); closeAllMenus(); loadActivePlaylist(autoPlayLast = false)
                    }
                }
            }
            rowViews.add(row); rowTexts.add(tv); rowChecks.add(chk)
            leftList.addView(row)
        }

        subModalBottomButtons.removeAllViews()
        subModalBottomButtons.visibility = View.VISIBLE
        val addUrlBtn = makeUnifiedActionButton("+  По ссылке") { navigateToMenu("ADD_URL") }.apply { id = 8581 }
        val addFileBtn = makeUnifiedActionButton("+  Из файла") { launchSystemPlaylistPicker() }.apply { id = 8582 }
        btnAddUrl = addUrlBtn
        btnAddFile = addFileBtn
        addUrlBtn.nextFocusRightId = addFileBtn.id
        addFileBtn.nextFocusLeftId = addUrlBtn.id
        if (rowViews.isNotEmpty()) {
            for (i in rowViews.indices) {
                if (i > 0) rowViews[i].nextFocusUpId = rowViews[i - 1].id
                if (i + 1 < rowViews.size) rowViews[i].nextFocusDownId = rowViews[i + 1].id
                else rowViews[i].nextFocusDownId = addUrlBtn.id
            }
            addUrlBtn.nextFocusUpId = rowViews.last().id
            addFileBtn.nextFocusUpId = 8551
        }
        subModalBottomButtons.addView(addUrlBtn)
        subModalBottomButtons.addView(addFileBtn)

        val dividerV = View(this).apply {
            setBackgroundColor(if (dark) Color.argb(36, 255, 255, 255) else Color.argb(32, 0, 0, 0))
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                rightMargin = (14 * scale).toInt()
            }
        }
        subModalCustomBody.addView(leftColWrap)
        subModalCustomBody.addView(dividerV)
        subModalCustomBody.addView(rightCol)

        rebuildActionButtons(selectedPlaylistModalIdx)
        refreshPlaylistRows(selectedPlaylistModalIdx)

        if (!inPlace) animateMenuAppear(subModalOverlay)
        (rowViews.getOrNull(selectedPlaylistModalIdx) ?: btnAddUrl)?.requestFocus()
    }

    private fun openChannelEditorGroupsModal(inPlace: Boolean = false, keepIndex: Int = -1) {
        setSubModalHeaderTitle("Редактор", "РД")
        subModalInputContainer.visibility = View.GONE
        subModalCustomBody.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalList.visibility = View.VISIBLE

        val snapshot = allChannels.toList()
        val editableCats = activeCategoryList().drop(2)
        val rows = editableCats.map { cat ->
            val isCatEnabled = !disabledCategories.contains(cat)
            val totalInCat = snapshot.count { effectiveCategory(it) == cat }
            val activeInCat = snapshot.count { effectiveCategory(it) == cat && isChannelActive(it) }
            StatsModalRowData(
                isAnalyzerMode = false,
                isEnabled = isCatEnabled,
                title = cat,
                primaryCount = activeInCat,
                secondaryCount = totalInCat,
                totalCount = totalInCat
            )
        }
        updateSubModalStatsRows(rows, smoothToggle = inPlace, preservePositionIndex = keepIndex)
        subModalList.setOnItemClickListener { _, _, idx, _ ->
            if (idx in editableCats.indices) {
                selectedEditorCategory = editableCats[idx]
                navigateToMenu("CHANNEL_EDITOR_ITEMS")
            }
        }

        subModalBottomButtons.removeAllViews()
        subModalBottomButtons.visibility = View.VISIBLE
        subModalBottomButtons.addView(makeUnifiedActionButton("Включить все") {
            disabledCategories.clear(); manualDisabledUrls.clear(); autoDisabledUrls.clear()
            saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
            openChannelEditorGroupsModal(inPlace = true, keepIndex = subModalHighlightedPos)
        })
        subModalBottomButtons.addView(makeUnifiedActionButton("Вкл / Выкл группу") {
            val cat = editableCats.getOrNull(subModalHighlightedPos) ?: return@makeUnifiedActionButton
            if (disabledCategories.contains(cat)) disabledCategories.remove(cat) else disabledCategories.add(cat)
            saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
            openChannelEditorGroupsModal(inPlace = true, keepIndex = subModalHighlightedPos)
        })
        subModalBottomButtons.addView(makeUnifiedActionButton("Каналы группы") {
            val cat = editableCats.getOrNull(subModalHighlightedPos) ?: return@makeUnifiedActionButton
            selectedEditorCategory = cat
            navigateToMenu("CHANNEL_EDITOR_ITEMS")
        })

        if (!inPlace) { animateMenuAppear(subModalOverlay); subModalList.requestFocus() }
    }

    private fun openChannelEditorItemsModal(catName: String, inPlace: Boolean = false, keepIndex: Int = -1) {
        val catChannels = allChannels.filter { effectiveCategory(it) == catName }.sortedWith(safeChannelComparator)
        val isCatDisabled = disabledCategories.contains(catName)
        setSubModalHeaderTitle(catName, "РД")
        subModalInputContainer.visibility = View.GONE
        subModalCustomBody.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalList.visibility = View.VISIBLE

        subModalBottomButtons.removeAllViews()
        subModalBottomButtons.visibility = View.VISIBLE
        subModalBottomButtons.addView(makeUnifiedActionButton(if (isCatDisabled) "Включить группу" else "Отключить группу") {
            if (disabledCategories.contains(catName)) disabledCategories.remove(catName) else disabledCategories.add(catName)
            saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
            openChannelEditorItemsModal(catName, inPlace = true, keepIndex = subModalHighlightedPos)
        })
        subModalBottomButtons.addView(makeUnifiedActionButton("Включить все") {
            catChannels.forEach { manualDisabledUrls.remove(it.url); autoDisabledUrls.remove(it.url) }
            disabledCategories.remove(catName); saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
            openChannelEditorItemsModal(catName, inPlace = true, keepIndex = subModalHighlightedPos)
        })
        updateSubModalListItems(
            catChannels.map { ch ->
                val isManualOff = manualDisabledUrls.contains(ch.url)
                val isAutoOff = autoDisabledUrls.contains(ch.url)
                val enabled = !isManualOff && !isAutoOff && !isCatDisabled
                val note = if (isManualOff) "   •   Скрыт" else if (isAutoOff) "   •   Тест" else ""
                "${if (enabled) "[+]" else "[-]"}   ${ch.name}$note"
            },
            smoothToggle = inPlace,
            preservePositionIndex = keepIndex
        )
        subModalList.setOnItemClickListener { _, _, idx, _ ->
            if (idx in catChannels.indices) {
                val ch = catChannels[idx]
                if (manualDisabledUrls.contains(ch.url) || autoDisabledUrls.contains(ch.url)) {
                    manualDisabledUrls.remove(ch.url); autoDisabledUrls.remove(ch.url)
                } else manualDisabledUrls.add(ch.url)
                saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
                openChannelEditorItemsModal(catName, inPlace = true, keepIndex = idx)
            }
        }
        if (!inPlace) { animateMenuAppear(subModalOverlay); subModalList.requestFocus() }
    }

    private fun openAnalyzerGroupsModal(firstOpen: Boolean = false) {
        val snapshot = allChannels.filter { !manualDisabledUrls.contains(it.url) }
        val hiddenChannels = snapshot.filter { autoDisabledUrls.contains(it.url) }
        val hiddenCount = hiddenChannels.size
        setSubModalHeaderTitle("Тест каналов", "ТС")
        subModalInputContainer.visibility = View.GONE
        subModalCustomBody.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalList.visibility = View.VISIBLE

        if (firstOpen || subModalBottomButtons.childCount == 0 || !isAnalyzerRunning) {
            subModalBottomButtons.visibility = View.VISIBLE
            subModalBottomButtons.removeAllViews()
            subModalBottomButtons.addView(makeUnifiedActionButton(if (isAnalyzerRunning) "■ Стоп" else "▶ Запустить тест", isDanger = isAnalyzerRunning) {
                if (isAnalyzerRunning) { isAnalyzerRunning = false; analyzerStatusBanner.visibility = View.GONE; openAnalyzerGroupsModal(firstOpen = true) }
                else { selectedAnalyzerCategory = "Все каналы"; startBackgroundChannelAnalyzer(snapshot, isRescanHidden = false); openAnalyzerGroupsModal(firstOpen = true) }
            })
            if (isAnalyzerRunning) {
                subModalBottomButtons.addView(makeUnifiedActionButton("▲ В фон") { collapseAnalyzerToTopBar() })
            }
            if (!isAnalyzerRunning && hiddenCount > 0) {
                subModalBottomButtons.addView(makeUnifiedActionButton("↻ Повтор ($hiddenCount)") {
                    selectedAnalyzerCategory = "Все каналы"
                    startBackgroundChannelAnalyzer(hiddenChannels, isRescanHidden = true)
                    openAnalyzerGroupsModal(firstOpen = true)
                })
                subModalBottomButtons.addView(makeUnifiedActionButton("↺ Вернуть ($hiddenCount)") {
                    hiddenChannels.forEach { ch ->
                        autoDisabledUrls.remove(ch.url)
                        if (channelHealthMap[ch.url] == false) channelHealthMap.remove(ch.url)
                    }
                    autoDisabledUrls.clear()
                    saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
                    openAnalyzerGroupsModal(firstOpen = false)
                })
            }
        }
        val groupNames = listOf("Все каналы") + activeCategoryList().drop(2)
        val rows = groupNames.map { gName ->
            val groupChans = if (gName == "Все каналы") snapshot else snapshot.filter { effectiveCategory(it) == gName }
            val aliveCnt = groupChans.count { channelHealthMap[it.url] == true }
            val deadCnt = groupChans.count { channelHealthMap[it.url] == false || (channelHealthMap[it.url] == null && autoDisabledUrls.contains(it.url)) }
            StatsModalRowData(
                isAnalyzerMode = true,
                isEnabled = true,
                title = gName,
                primaryCount = aliveCnt,
                secondaryCount = deadCnt,
                totalCount = groupChans.size
            )
        }
        updateSubModalStatsRows(rows, smoothToggle = !firstOpen, preservePositionIndex = subModalHighlightedPos)
        subModalList.setOnItemClickListener { _, _, idx, _ ->
            if (idx in groupNames.indices) { selectedAnalyzerCategory = groupNames[idx]; navigateToMenu("ANALYZER_GROUP_VIEW") }
        }
        if (firstOpen) { animateMenuAppear(subModalOverlay); subModalList.requestFocus(); resetMenuInactivityTimer() }
    }

    private fun openAnalyzerGroupChannelsModal(catName: String, firstOpen: Boolean = false) {
        val snapshot = allChannels.filter { !manualDisabledUrls.contains(it.url) }
        val groupChans = (if (catName == "Все каналы") snapshot else snapshot.filter { effectiveCategory(it) == catName }).sortedWith(safeChannelComparator)
        val groupHidden = groupChans.filter { autoDisabledUrls.contains(it.url) }
        val groupHiddenCount = groupHidden.size
        setSubModalHeaderTitle(catName, "ТС")
        subModalInputContainer.visibility = View.GONE
        subModalCustomBody.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalList.visibility = View.VISIBLE

        if (firstOpen || subModalBottomButtons.childCount == 0 || !isAnalyzerRunning) {
            subModalBottomButtons.visibility = View.VISIBLE
            subModalBottomButtons.removeAllViews()
            subModalBottomButtons.addView(makeUnifiedActionButton(if (isAnalyzerRunning) "■ Стоп" else "▶ Тест группы", isDanger = isAnalyzerRunning) {
                if (isAnalyzerRunning) { isAnalyzerRunning = false; analyzerStatusBanner.visibility = View.GONE; openAnalyzerGroupChannelsModal(catName, firstOpen = true) }
                else { startBackgroundChannelAnalyzer(groupChans, isRescanHidden = false); openAnalyzerGroupChannelsModal(catName, firstOpen = true) }
            })
            if (isAnalyzerRunning) {
                subModalBottomButtons.addView(makeUnifiedActionButton("▲ В фон") { collapseAnalyzerToTopBar() })
            }
            if (!isAnalyzerRunning && groupHiddenCount > 0) {
                subModalBottomButtons.addView(makeUnifiedActionButton("↻ Повтор ($groupHiddenCount)") {
                    startBackgroundChannelAnalyzer(groupHidden, isRescanHidden = true)
                    openAnalyzerGroupChannelsModal(catName, firstOpen = true)
                })
                subModalBottomButtons.addView(makeUnifiedActionButton("↺ Вернуть ($groupHiddenCount)") {
                    groupHidden.forEach { ch ->
                        autoDisabledUrls.remove(ch.url)
                        if (channelHealthMap[ch.url] == false) channelHealthMap.remove(ch.url)
                    }
                    saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
                    openAnalyzerGroupChannelsModal(catName, firstOpen = false)
                })
            }
        }
        updateSubModalListItems(groupChans.map { ch ->
            val status = when {
                channelHealthMap[ch.url] == true -> "[+]   Работает"
                channelHealthMap[ch.url] == false || autoDisabledUrls.contains(ch.url) -> "[-]   Скрыт"
                else -> "[?]   Очередь"
            }
            "$status   •   ${ch.name}"
        }, preservePositionIndex = subModalHighlightedPos)
        subModalList.setOnItemClickListener { _, _, idx, _ -> if (idx in groupChans.indices) { playChannelObj(groupChans[idx], immediate = true); closeAllMenus() } }
        if (firstOpen) { animateMenuAppear(subModalOverlay); subModalList.requestFocus(); resetMenuInactivityTimer() }
    }

    private fun startBackgroundChannelAnalyzer(rawTargetChannels: List<Channel>, isRescanHidden: Boolean = false) {
        val targetChannels = rawTargetChannels.filter { !manualDisabledUrls.contains(it.url) }
        if (isAnalyzerRunning || targetChannels.isEmpty()) return
        isAnalyzerRunning = true
        targetChannels.forEach { channelHealthMap.remove(it.url) }
        aliveChannelsAtomic.set(0)
        deadChannelsAtomic.set(0)

        val snapshot = targetChannels.toList()
        val total = snapshot.size
        val counter = AtomicInteger(0)

        analyzerStatusBanner.text = colorizeStatusText(if (isRescanHidden) "Проверка скрытых: 0 / $total" else "Быстрый тест: 0 / $total")
        analyzerStatusBanner.visibility = View.VISIBLE
        resetMenuInactivityTimer()

        thread(priority = Thread.NORM_PRIORITY) {
            val pool = Executors.newFixedThreadPool(6)
            var lastUiUpdateMs = 0L
            for (ch in snapshot) {
                pool.submit {
                    if (!isAnalyzerRunning) return@submit
                    val activeSrc = playlists.getOrNull(activePlaylistIndex)?.source
                    val isBuiltinCatalog = (activeSrc == "BUILTIN_OPEN_RU" || activeSrc == "BUILTIN_STRICT_RU_ONLY")
                    val isProtectedVipToken = !isBuiltinCatalog && CoreEngines.isSingleSessionTokenUrl(ch.url)
                    val ok = if (isProtectedVipToken) true else checkStreamAliveDeep(ch.url)
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
                            if (isFinishing || isDestroyed) return@runOnUiThread
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
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
                    val curMenu = menuHistory.lastOrNull()
                    if (curMenu == "ANALYZER_GROUP_VIEW") openAnalyzerGroupChannelsModal(selectedAnalyzerCategory, firstOpen = false)
                    else if (curMenu == "ANALYZER_GROUPS") openAnalyzerGroupsModal(firstOpen = false)
                    analyzerStatusBanner.text = colorizeStatusText(if (isRescanHidden) "Пересканировано: [+] вернулось $aliveCount  |  [-] скрыто: $deadCount" else "Готово! Рабочих: [+] $aliveCount  |  Скрыто нерабочих: [-] $deadCount")
                    uiHandler.postDelayed({ analyzerStatusBanner.visibility = View.GONE }, 5000L)
                }
            }
        }
    }

    private fun probeMediaBytesOkHttp(targetUrl: String, minBytes: Int = 188): Boolean {
        return try {
            val clean = targetUrl.substringBefore("#").trim()
            val hdrs = CoreEngines.headersForStreamUrl(clean)
            val reqBuilder = Request.Builder()
                .url(clean)
                .header("User-Agent", CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size])
            for ((k, v) in hdrs) reqBuilder.header(k, v)
            analyzerOkHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val cType = (resp.header("Content-Type") ?: "").lowercase(Locale.US)
                if (cType.contains("text/html") || cType.contains("application/json")) return false
                val stream = resp.body?.byteStream() ?: return false
                val buf = ByteArray(4096)
                var totalRead = 0
                val firstChunk = ByteArray(256)
                var firstLen = 0
                while (totalRead < minBytes) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    if (firstLen < firstChunk.size) {
                        val copyLen = minOf(n, firstChunk.size - firstLen)
                        System.arraycopy(buf, 0, firstChunk, firstLen, copyLen)
                        firstLen += copyLen
                    }
                    totalRead += n
                }
                if (totalRead < minBytes) return false
                val headStr = String(firstChunk, 0, firstLen)
                if (headStr.contains("<html", true) || headStr.contains("<!doctype html", true) ||
                    headStr.contains("404 not found", true) || headStr.contains("access denied", true)
                ) {
                    return false
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchPlaylistTextOkHttp(targetUrl: String): Pair<String, String>? {
        return try {
            val clean = targetUrl.substringBefore("#").trim()
            val hdrs = CoreEngines.headersForStreamUrl(clean)
            val reqBuilder = Request.Builder()
                .url(clean)
                .header("User-Agent", CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size])
            for ((k, v) in hdrs) reqBuilder.header(k, v)
            analyzerOkHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val cType = (resp.header("Content-Type") ?: "").lowercase(Locale.US)
                if (cType.contains("text/html")) return null
                val finalUrl = resp.request.url.toString()
                val stream = resp.body?.byteStream() ?: return null
                val out = ByteArray(65536)
                var total = 0
                while (total < out.size) {
                    val n = stream.read(out, total, out.size - total)
                    if (n <= 0) break
                    total += n
                }
                if (total <= 0) return null
                Pair(finalUrl, String(out, 0, total, Charsets.UTF_8))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFirstHlsUri(playlistText: String, baseUrl: String): String? {
        for (rawLine in playlistText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            return try {
                URL(URL(baseUrl), line).toString()
            } catch (_: Exception) {
                line
            }
        }
        return null
    }

    private fun checkStreamAliveDeep(urlStr: String): Boolean {
        return try {
            val cleanUrl = urlStr.substringBefore("#").trim()
            val lowerUrl = cleanUrl.lowercase(Locale.US)
            if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) return false

            val isLikelyHls = lowerUrl.contains(".m3u") || lowerUrl.contains("/hls/") || lowerUrl.contains("playlist") || lowerUrl.contains("mono.") || lowerUrl.contains("index.")
            val isDash = lowerUrl.contains(".mpd")

            if (isLikelyHls || isDash) {
                val (finalUrl, body) = fetchPlaylistTextOkHttp(cleanUrl) ?: return false
                if (body.contains("<html", true) || body.contains("<!doctype html", true) ||
                    body.contains("404 not found", true) || body.contains("access denied", true)
                ) {
                    return false
                }
                if (isDash || body.contains("<MPD", true)) {
                    if (body.contains("edef8ba9", true) || body.contains("widevine", true) || body.contains("playready", true)) return false
                    return body.contains("<Segment", true) || body.contains("<BaseURL", true) || body.contains("<AdaptationSet", true)
                }
                if (body.contains("#EXTM3U", true)) {
                    // Отбрасываем DRM-потоки, которые не воспроизводятся без лицензии
                    if (body.contains("SAMPLE-AES", true) || body.contains("widevine", true) || body.contains("edef8ba9", true)) {
                        return false
                    }
                    // Если это master-плейлист — спускаемся в первый медиа-плейлист и проверяем реальный видео-сегмент
                    var mediaUrl = finalUrl
                    var mediaBody = body
                    if (mediaBody.contains("#EXT-X-STREAM-INF", true)) {
                        val variantUrl = extractFirstHlsUri(mediaBody, finalUrl) ?: return false
                        val fetchedVariant = fetchPlaylistTextOkHttp(variantUrl) ?: return false
                        mediaUrl = fetchedVariant.first
                        mediaBody = fetchedVariant.second
                        if (!mediaBody.contains("#EXTM3U", true)) return false
                        if (mediaBody.contains("SAMPLE-AES", true) || mediaBody.contains("widevine", true)) return false
                    }
                    val firstSegUrl = extractFirstHlsUri(mediaBody, mediaUrl) ?: return false
                    return probeMediaBytesOkHttp(firstSegUrl, minBytes = 188)
                }
                // Некоторые URL с "index" отдают сразу бинарный поток (MPEG-TS/MP3/AAC)
                return probeMediaBytesOkHttp(cleanUrl, minBytes = 1024)
            }

            // Прямой поток (TS / MP4 / MP3 / Icecast) — проверяем реальную отдачу байт через тот же OkHttp
            probeMediaBytesOkHttp(cleanUrl, minBytes = 1024)
        } catch (_: Exception) {
            false
        }
    }

    private fun openDiagLogViewerModal(firstOpen: Boolean = false) {
        setSubModalHeaderTitle("Лог ошибок")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.VISIBLE
        subModalTopButtons.visibility = View.VISIBLE
        subModalTopButtons.removeAllViews()
        subModalTopButtons.addView(makeModernButton("↻ Обновить") {
            openDiagLogViewerModal(firstOpen = false)
        })
        subModalTopButtons.addView(makeModernButton("📥 В Download") {
            saveDiagLogToDownloadsAndOpenFileManager()
        })
        subModalTopButtons.addView(makeModernButton("📤 Поделиться") {
            shareDiagLogFile()
        })
        subModalTopButtons.addView(makeModernButton("🗑 Очистить") {
            val path = CoreEngines.clearDiagLog()
            Toast.makeText(this, "Лог очищен: $path", Toast.LENGTH_SHORT).show()
            openDiagLogViewerModal(firstOpen = false)
        })
        val lines = CoreEngines.readDiagLogTail(45)
        updateSubModalListItems(lines, smoothToggle = !firstOpen, preservePositionIndex = 0)
        subModalList.setOnItemClickListener { _, _, _, _ ->
            openDiagLogViewerModal(firstOpen = false)
        }
        if (firstOpen) {
            animateMenuAppear(subModalOverlay)
            subModalList.requestFocus()
        }
    }

    private fun openBufferSubModal(inPlace: Boolean = false, keepIndex: Int = -1) {
        setSubModalHeaderTitle("Буфер", "БФ")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalThemesGrid.visibility = View.GONE
        subModalCustomBody.removeAllViews()
        subModalCustomBody.visibility = View.VISIBLE
        subModalCustomBody.orientation = LinearLayout.VERTICAL
        subModalCustomBody.setBackgroundDrawable(makeThemedSubBg())
        val pBox = dpPx(12f)
        subModalCustomBody.setPadding(pBox, pBox, pBox, pBox)
        subModalCustomBody.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        val bufScroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val bufInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        bufScroll.addView(bufInner)
        subModalCustomBody.addView(bufScroll)

        val t = currentTheme()
        val scale = uiScaleFactor()
        val presets = listOf(
            Triple("Стандарт", "3–8 сек", "Быстрое переключение каналов при стабильном соединении."),
            Triple("Оптимальный", "5–12 сек", "Сбалансированный режим для плавного эфира в большинстве сетей."),
            Triple("Анти-фриз", "8–18 сек", "Увеличенный запас буфера против подвисаний и потерь пакетов."),
            Triple("Максимум", "12–25 сек", "Максимальная устойчивость при нестабильном интернете.")
        )

        val hintTv = TextView(this).apply {
            text = presets[bufferLevel.coerceIn(0, 3)].third
            setTextColor(t.textPrimaryColor)
            alpha = 0.78f
            typeface = t.normalFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f * scale)
            gravity = Gravity.CENTER
            setPadding(10, (12 * scale).toInt(), 10, 4)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val cardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val cardViews = mutableListOf<LinearLayout>()
        presets.forEachIndexed { idx, (name, range, desc) ->
            val card = makeUnifiedPresetCard(
                titleText = name,
                subtitleText = range,
                isSelected = (idx == bufferLevel),
                onFocused = { hintTv.text = desc }
            ) {
                bufferLevel = idx
                CoreEngines.appendDiagLog("BUFFER_MODE", "Установлен уровень буфера: $idx")
                saveState(); initPlayer()
                openBufferSubModal(inPlace = true, keepIndex = idx)
            }.apply { id = 8300 + idx }
            cardViews.add(card)
            cardsRow.addView(card)
        }

        bufInner.addView(cardsRow)
        bufInner.addView(hintTv)

        subModalBottomButtons.removeAllViews()
        subModalBottomButtons.visibility = View.VISIBLE
        val b20 = makeUnifiedActionButton("⏪  Отмотать -20с") { rewindLiveBufferSeconds(20); closeAllMenus() }.apply { id = 8311 }
        val b45 = makeUnifiedActionButton("⏪  Отмотать -45с") { rewindLiveBufferSeconds(45); closeAllMenus() }.apply { id = 8312 }
        val bLog = makeUnifiedActionButton("Лог ошибок") { navigateToMenu("DIAG_LOG_VIEW") }.apply { id = 8313 }
        b20.nextFocusRightId = b45.id; b45.nextFocusLeftId = b20.id
        b45.nextFocusRightId = bLog.id; bLog.nextFocusLeftId = b45.id
        val focusCardId = cardViews[bufferLevel.coerceIn(0, cardViews.lastIndex)].id
        b20.nextFocusUpId = cardViews[0].id
        b45.nextFocusUpId = focusCardId
        bLog.nextFocusUpId = cardViews.last().id
        for (i in cardViews.indices) {
            cardViews[i].nextFocusLeftId = cardViews[(i - 1 + cardViews.size) % cardViews.size].id
            cardViews[i].nextFocusRightId = cardViews[(i + 1) % cardViews.size].id
            cardViews[i].nextFocusDownId = when (i) { 0 -> b20.id; 3 -> bLog.id; else -> b45.id }
        }
        subModalBottomButtons.addView(b20)
        subModalBottomButtons.addView(b45)
        subModalBottomButtons.addView(bLog)

        if (!inPlace) animateMenuAppear(subModalOverlay)
        val targetIdx = if (keepIndex in cardViews.indices) keepIndex else bufferLevel.coerceIn(0, cardViews.lastIndex)
        cardViews[targetIdx].requestFocus()
    }

    private fun openDecoderSubModal(inPlace: Boolean = false, keepIndex: Int = -1) {
        setSubModalHeaderTitle("Декодер", "ДК")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalThemesGrid.visibility = View.GONE
        subModalBottomButtons.visibility = View.GONE
        subModalCustomBody.removeAllViews()
        subModalCustomBody.visibility = View.VISIBLE
        subModalCustomBody.orientation = LinearLayout.VERTICAL
        subModalCustomBody.setBackgroundDrawable(makeThemedSubBg())
        val pBox = dpPx(12f)
        subModalCustomBody.setPadding(pBox, pBox, pBox, pBox)
        subModalCustomBody.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        val decScroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val decInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        decScroll.addView(decInner)
        subModalCustomBody.addView(decScroll)

        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)

        val engineLabel = TextView(this).apply {
            text = "Движок плеера"
            setTextColor(t.textPrimaryColor); alpha = 0.74f
            typeface = t.boldFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f * scale)
            setPadding(dpPx(6f), 0, dpPx(4f), dpPx(4f))
        }
        val engines = listOf(
            Triple("Media3", "Основной + DPI", "Быстрый системный движок с поддержкой DoH, DPI-обхода, прокси и DV → HEVC."),
            Triple("LibVLC", "Резервный", "Альтернативный движок VLC для сложных потоков MPEG-TS и нестандартных кодеков.")
        )
        val modes = listOf(
            Triple("HW", "Аппаратный", "Минимальная нагрузка на процессор при стандартных кодеках."),
            Triple("HW + FFmpeg", "Авто", "Рекомендуется для большинства устройств и аудиодорожек AC3 / MP2."),
            Triple("SW", "Программный", "Резервный режим, если аппаратный декодер даёт артефакты.")
        )

        val hintTv = TextView(this).apply {
            text = if (isVlcEngineActive()) engines[1].third else modes[decoderMode.coerceIn(0, 2)].third
            setTextColor(t.textPrimaryColor); alpha = 0.78f
            typeface = t.normalFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f * scale)
            gravity = Gravity.CENTER
            setPadding(dpPx(8f), dpPx(8f * scale), dpPx(8f), dpPx(8f * scale))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val engineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val engineCards = mutableListOf<LinearLayout>()
        engines.forEachIndexed { idx, (name, sub, desc) ->
            val card = makeUnifiedPresetCard(name, sub, idx == playerEngineMode, onFocused = { hintTv.text = desc }) {
                if (playerEngineMode != idx) {
                    playerEngineMode = idx
                    saveState()
                    initPlayer()
                    currentChannel?.let { playChannelObj(it, immediate = true, silentSwap = true, updateSavedUrl = false) }
                }
                openDecoderSubModal(inPlace = true, keepIndex = idx)
            }.apply { id = 8320 + idx }
            engineCards.add(card)
            engineRow.addView(card)
        }

        val decLabel = TextView(this).apply {
            text = "Режим декодирования"
            setTextColor(t.textPrimaryColor); alpha = 0.74f
            typeface = t.boldFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f * scale)
            setPadding(dpPx(6f), dpPx(8f * scale), dpPx(4f), dpPx(4f))
        }
        val cardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val cardViews = mutableListOf<LinearLayout>()
        modes.forEachIndexed { idx, (name, sub, desc) ->
            val card = makeUnifiedPresetCard(name, sub, idx == decoderMode, onFocused = { hintTv.text = desc }) {
                decoderMode = idx; saveState(); initPlayer()
                currentChannel?.let { playChannelObj(it, immediate = true, silentSwap = true, updateSavedUrl = false) }
                openDecoderSubModal(inPlace = true, keepIndex = 2 + idx)
            }.apply { id = 8330 + idx }
            cardViews.add(card)
            cardsRow.addView(card)
        }

        val divider = View(this).apply {
            setBackgroundColor(if (dark) Color.argb(36, 255, 255, 255) else Color.argb(32, 0, 0, 0))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                setMargins(4, dpPx(4f * scale), 4, dpPx(6f * scale))
            }
        }

        val rowOrbiter = makeUnifiedSettingRow(
            "Сдвиг пикселей (защита экрана)",
            if (pixelOrbiterEnabled) "ВКЛ" else "ВЫКЛ",
            if (pixelOrbiterEnabled) 1 else 2
        ) {
            pixelOrbiterEnabled = !pixelOrbiterEnabled; saveState(); stepPixelOrbiter()
            openDecoderSubModal(inPlace = true, keepIndex = 5)
        }.apply { id = 8335 }

        val rowAfr = makeUnifiedSettingRow(
            "Авточастота кадров (AFR)",
            if (afrEnabled) "ВКЛ" else "ВЫКЛ",
            if (afrEnabled) 1 else 2
        ) {
            afrEnabled = !afrEnabled; saveState()
            openDecoderSubModal(inPlace = true, keepIndex = 6)
        }.apply { id = 8336 }

        val activeDecCardId = cardViews[decoderMode.coerceIn(0, 2)].id
        val activeEngCardId = engineCards[playerEngineMode.coerceIn(0, 1)].id
        engineCards[0].nextFocusRightId = engineCards[1].id; engineCards[0].nextFocusLeftId = engineCards[1].id
        engineCards[1].nextFocusLeftId = engineCards[0].id; engineCards[1].nextFocusRightId = engineCards[0].id
        engineCards.forEach { it.nextFocusDownId = activeDecCardId }
        for (i in cardViews.indices) {
            cardViews[i].nextFocusLeftId = cardViews[(i - 1 + cardViews.size) % cardViews.size].id
            cardViews[i].nextFocusRightId = cardViews[(i + 1) % cardViews.size].id
            cardViews[i].nextFocusUpId = activeEngCardId
            cardViews[i].nextFocusDownId = rowOrbiter.id
        }
        rowOrbiter.nextFocusUpId = activeDecCardId
        rowOrbiter.nextFocusDownId = rowAfr.id
        rowAfr.nextFocusUpId = rowOrbiter.id

        decInner.addView(engineLabel)
        decInner.addView(engineRow)
        decInner.addView(decLabel)
        decInner.addView(cardsRow)
        decInner.addView(hintTv)
        decInner.addView(divider)
        decInner.addView(rowOrbiter)
        decInner.addView(rowAfr)

        if (!inPlace) animateMenuAppear(subModalOverlay)
        val focusTarget: View = when (keepIndex) {
            0, 1 -> engineCards[keepIndex]
            in 2..4 -> cardViews[keepIndex - 2]
            5 -> rowOrbiter
            6 -> rowAfr
            else -> engineCards[playerEngineMode.coerceIn(0, 1)]
        }
        focusTarget.requestFocus()
    }

    private fun openAudioSubModal(inPlace: Boolean = false, keepIndex: Int = -1) {
        setSubModalHeaderTitle("Звук и DRC", "ЗВ")
        subModalInputContainer.visibility = View.GONE
        subModalList.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalThemesGrid.visibility = View.GONE
        subModalBottomButtons.visibility = View.GONE
        subModalCustomBody.removeAllViews()
        subModalCustomBody.visibility = View.VISIBLE
        subModalCustomBody.orientation = LinearLayout.VERTICAL
        subModalCustomBody.setBackgroundDrawable(makeThemedSubBg())
        val pBox = dpPx(12f)
        subModalCustomBody.setPadding(pBox, pBox, pBox, pBox)
        subModalCustomBody.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        val audScroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val audInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        audScroll.addView(audInner)
        subModalCustomBody.addView(audScroll)

        val t = currentTheme()
        val scale = uiScaleFactor()
        val rowDrc = makeUnifiedSettingRow(
            "Выравнивание DRC (ночной режим)",
            if (drcNightAudioEnabled) "ВКЛ" else "ВЫКЛ",
            if (drcNightAudioEnabled) 1 else 2
        ) {
            drcNightAudioEnabled = !drcNightAudioEnabled; saveState(); applyAudioEnhancements()
            openAudioSubModal(inPlace = true, keepIndex = 0)
        }.apply { id = 8350 }
        audInner.addView(rowDrc)

        val langDisplay = when (preferredAudioLang.lowercase(Locale.US)) {
            "rus", "ru" -> "Русский"
            "eng", "en" -> "Оригинал (ENG)"
            else -> "Авто"
        }
        val rowPrefLang = makeUnifiedSettingRow("Язык аудио по умолчанию", langDisplay, 0) {
            preferredAudioLang = when (preferredAudioLang.lowercase(Locale.US)) {
                "rus", "ru" -> "eng"
                "eng", "en" -> "auto"
                else -> "rus"
            }
            saveState()
            if (isVlcEngineActive()) {
                vlcAppliedSavedTracks = false
                applyVlcRememberedTracks()
            } else {
                media3AppliedSavedTracks = false
                player?.currentTracks?.let { applyMedia3RememberedTracks(it) }
            }
            openAudioSubModal(inPlace = true, keepIndex = 1)
        }.apply { id = 8349 }
        audInner.addView(rowPrefLang)

        val boostLabel = TextView(this).apply {
            text = "Усиление громкости"
            setTextColor(t.textPrimaryColor); alpha = 0.74f
            typeface = t.boldFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f * scale)
            setPadding(dpPx(6f), dpPx(8f * scale), dpPx(4f), dpPx(4f))
        }
        audInner.addView(boostLabel)

        val boostSteps = listOf(100, 130, 160, 200)
        val boostRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val boostCards = mutableListOf<LinearLayout>()
        boostSteps.forEachIndexed { idx, pct ->
            val card = makeUnifiedPresetCard("$pct%", "", audioBoostPercent == pct) {
                audioBoostPercent = pct; saveState(); applyAudioEnhancements()
                openAudioSubModal(inPlace = true, keepIndex = 2 + idx)
            }.apply { id = 8351 + idx }
            boostCards.add(card)
            boostRow.addView(card)
        }
        audInner.addView(boostRow)

        val tracksLabel = TextView(this).apply {
            text = "Аудиодорожки"
            setTextColor(t.textPrimaryColor); alpha = 0.74f
            typeface = t.boldFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f * scale)
            setPadding(dpPx(6f), dpPx(8f * scale), dpPx(4f), dpPx(4f))
        }
        audInner.addView(tracksLabel)

        val extraRows = mutableListOf<LinearLayout>()
        if (isVlcEngineActive()) {
            val vlc = vlcPlayer
            val vlcTracks = vlc?.getAudioTracks()?.filter { it.id != -1 }.orEmpty()
            val activeAudioId = vlc?.getAudioTrack() ?: -1
            vlcTracks.forEach { td ->
                val rowIdx = 6 + extraRows.size
                val isSel = td.id == activeAudioId
                val r = makeUnifiedSettingRow(
                    td.name.ifBlank { "Дорожка ${td.id}" },
                    if (isSel) "АКТИВНА" else "ВЫБРАТЬ",
                    if (isSel) 1 else 0
                ) {
                    runCatching { vlc?.setAudioTrack(td.id) }
                    val inferred = inferLangCodeFromTrackName(td.name)
                    if (inferred.isNotBlank()) { preferredAudioLang = inferred; saveState() }
                    applyVlcAudioBoost()
                    openAudioSubModal(inPlace = true, keepIndex = rowIdx)
                }.apply { id = View.generateViewId() }
                extraRows.add(r)
                audInner.addView(r)
            }
            if (vlcTracks.isEmpty()) {
                audInner.addView(TextView(this).apply {
                    text = "Используется основная аудиодорожка потока (LibVLC)"
                    setTextColor(t.textPrimaryColor); alpha = 0.65f
                    typeface = t.normalFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale)
                    setPadding(dpPx(8f), dpPx(4f), dpPx(8f), dpPx(4f))
                })
            }

            // Subtitles section for LibVLC
            val spuTracks = vlc?.getSpuTracks()?.filter { it.id != -1 }.orEmpty()
            val activeSpuId = vlc?.getSpuTrack() ?: -1
            val subLabel = TextView(this).apply {
                text = "Субтитры"
                setTextColor(t.textPrimaryColor); alpha = 0.74f
                typeface = t.boldFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f * scale)
                setPadding(dpPx(6f), dpPx(8f * scale), dpPx(4f), dpPx(4f))
            }
            audInner.addView(subLabel)
            val offRowIdx = 6 + extraRows.size
            val offRow = makeUnifiedSettingRow("Выключить субтитры", if (activeSpuId == -1) "АКТИВНО" else "ВЫКЛ", if (activeSpuId == -1) 1 else 2) {
                subtitlesEnabled = false; saveState()
                runCatching { vlc?.setSpuTrack(-1) }
                openAudioSubModal(inPlace = true, keepIndex = offRowIdx)
            }.apply { id = View.generateViewId() }
            extraRows.add(offRow); audInner.addView(offRow)
            spuTracks.forEach { td ->
                val rIdx = 6 + extraRows.size
                val isSel = td.id == activeSpuId
                val r = makeUnifiedSettingRow(td.name.ifBlank { "Субтитры ${td.id}" }, if (isSel) "ВКЛ" else "ВЫБРАТЬ", if (isSel) 1 else 0) {
                    subtitlesEnabled = true; saveState()
                    runCatching { vlc?.setSpuTrack(td.id) }
                    openAudioSubModal(inPlace = true, keepIndex = rIdx)
                }.apply { id = View.generateViewId() }
                extraRows.add(r); audInner.addView(r)
            }
        } else {
            val p = player
            val groups = p?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO }.orEmpty()
            var audioCount = 0
            for (g in groups) {
                for (i in 0 until g.length) {
                    val fmt = g.getTrackFormat(i)
                    val codec = formatAudioCodecLabel(fmt.sampleMimeType, fmt.codecs)
                    val chLabel = formatAudioChannelsLabel(fmt.channelCount)
                    val langRaw = fmt.language?.trim().orEmpty()
                    val lang = fmt.label?.takeIf { it.isNotBlank() } ?: langRaw.uppercase(Locale.US).ifBlank { "Дорожка ${audioCount + 1}" }
                    val meta = listOf(codec, chLabel).filter { it.isNotBlank() }.joinToString(" ")
                    val isSel = g.isTrackSelected(i)
                    val ov = TrackSelectionOverride(g.mediaTrackGroup, i)
                    val rowIdx = 6 + extraRows.size
                    val r = makeUnifiedSettingRow(
                        if (meta.isNotBlank()) "$lang  •  $meta" else lang,
                        if (isSel) "АКТИВНА" else "ВЫБРАТЬ",
                        if (isSel) 1 else 0
                    ) {
                        p?.trackSelectionParameters = p?.trackSelectionParameters?.buildUpon()
                            ?.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            ?.setOverrideForType(ov)
                            ?.build() ?: return@makeUnifiedSettingRow
                        val normLang = normalizeAudioLangCode(langRaw.ifBlank { inferLangCodeFromTrackName(lang) })
                        if (normLang.isNotBlank()) { preferredAudioLang = normLang; saveState() }
                        openAudioSubModal(inPlace = true, keepIndex = rowIdx)
                    }.apply { id = View.generateViewId() }
                    extraRows.add(r); audInner.addView(r)
                    audioCount++
                }
            }
            if (audioCount == 0) {
                audInner.addView(TextView(this).apply {
                    text = "Используется основная аудиодорожка потока"
                    setTextColor(t.textPrimaryColor); alpha = 0.65f
                    typeface = t.normalFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale)
                    setPadding(dpPx(8f), dpPx(4f), dpPx(8f), dpPx(4f))
                })
            }

            // Subtitles section for Media3
            val subGroups = p?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT }.orEmpty()
            val subLabel = TextView(this).apply {
                text = "Субтитры"
                setTextColor(t.textPrimaryColor); alpha = 0.74f
                typeface = t.boldFont; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f * scale)
                setPadding(dpPx(6f), dpPx(8f * scale), dpPx(4f), dpPx(4f))
            }
            audInner.addView(subLabel)
            val subDisabled = p?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true || !subtitlesEnabled
            val offRowIdx = 6 + extraRows.size
            val offRow = makeUnifiedSettingRow("Выключить субтитры", if (subDisabled) "АКТИВНО" else "ВЫКЛ", if (subDisabled) 1 else 2) {
                subtitlesEnabled = false; saveState()
                p?.trackSelectionParameters = p?.trackSelectionParameters?.buildUpon()
                    ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    ?.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    ?.build() ?: return@makeUnifiedSettingRow
                openAudioSubModal(inPlace = true, keepIndex = offRowIdx)
            }.apply { id = View.generateViewId() }
            extraRows.add(offRow); audInner.addView(offRow)
            for (sg in subGroups) {
                for (i in 0 until sg.length) {
                    val fmt = sg.getTrackFormat(i)
                    val sName = fmt.label?.takeIf { it.isNotBlank() } ?: fmt.language?.uppercase(Locale.US) ?: "Субтитры"
                    val isSel = !subDisabled && sg.isTrackSelected(i)
                    val ov = TrackSelectionOverride(sg.mediaTrackGroup, i)
                    val rIdx = 6 + extraRows.size
                    val r = makeUnifiedSettingRow(sName, if (isSel) "ВКЛ" else "ВЫБРАТЬ", if (isSel) 1 else 0) {
                        subtitlesEnabled = true; saveState()
                        p?.trackSelectionParameters = p?.trackSelectionParameters?.buildUpon()
                            ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            ?.setOverrideForType(ov)
                            ?.build() ?: return@makeUnifiedSettingRow
                        openAudioSubModal(inPlace = true, keepIndex = rIdx)
                    }.apply { id = View.generateViewId() }
                    extraRows.add(r); audInner.addView(r)
                }
            }
        }

        val activeBoostIdx = boostSteps.indexOf(audioBoostPercent).coerceAtLeast(0)
        rowDrc.nextFocusDownId = rowPrefLang.id
        rowPrefLang.nextFocusUpId = rowDrc.id
        rowPrefLang.nextFocusDownId = boostCards[activeBoostIdx].id
        for (i in boostCards.indices) {
            boostCards[i].nextFocusLeftId = boostCards[(i - 1 + boostCards.size) % boostCards.size].id
            boostCards[i].nextFocusRightId = boostCards[(i + 1) % boostCards.size].id
            boostCards[i].nextFocusUpId = rowPrefLang.id
            if (extraRows.isNotEmpty()) boostCards[i].nextFocusDownId = extraRows.first().id
        }
        if (extraRows.isNotEmpty()) {
            extraRows.first().nextFocusUpId = boostCards[activeBoostIdx].id
            for (i in extraRows.indices) {
                if (i > 0) extraRows[i].nextFocusUpId = extraRows[i - 1].id
                if (i + 1 < extraRows.size) extraRows[i].nextFocusDownId = extraRows[i + 1].id
            }
        }

        if (!inPlace) animateMenuAppear(subModalOverlay)
        val focusTarget: View = when {
            keepIndex == 0 -> rowDrc
            keepIndex == 1 -> rowPrefLang
            keepIndex in 2..5 -> boostCards[(keepIndex - 2).coerceIn(0, boostCards.lastIndex)]
            keepIndex >= 6 -> extraRows.getOrNull(keepIndex - 6) ?: rowDrc
            else -> rowDrc
        }
        focusTarget.requestFocus()
    }

    private fun openInputSubModal(title: String, initialValue: String, onSave: (String) -> Unit) {
        setSubModalHeaderTitle(title); subModalList.visibility = View.GONE
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
        setSubModalHeaderTitle("Плейлист по ссылке"); subModalList.visibility = View.GONE
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
            setOnClickListener {
                resetMenuInactivityTimer()
                if (!isLeftRailVisible) {
                    showLeftIconRailFromGroups()
                } else {
                    onClick()
                }
            }
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
            0 -> {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                runCatching { vlcPlayer?.setVideoScale(VlcMediaPlayer.ScaleType.SURFACE_BEST_FIT) }
            }
            1 -> {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                runCatching { vlcPlayer?.setVideoScale(VlcMediaPlayer.ScaleType.SURFACE_FILL) }
            }
            else -> {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                player?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                runCatching { vlcPlayer?.setVideoScale(VlcMediaPlayer.ScaleType.SURFACE_FIT_SCREEN) }
            }
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
        Thread.setDefaultUncaughtExceptionHandler { t, _ ->
            if (t == Looper.getMainLooper().thread) {
                while (true) {
                    try {
                        Looper.loop()
                    } catch (_: Throwable) {}
                }
            }
        }

        CoreEngines.diagAppContext = applicationContext
        CoreEngines.installTrustAllSsl()
        CoreEngines.appendDiagLog("APP_START", "Запуск TiViLook v2.7 (SDK=${Build.VERSION.SDK_INT}, Device=${Build.MODEL})")
        loadSavedState()
        rebuildNetworkClients()
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
        vlcVideoLayout = VLCVideoLayout(this).apply {
            visibility = View.GONE
            isFocusable = false; isClickable = false
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        }
        playerContainerFrame.addView(vlcVideoLayout)
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
            clipChildren = true; clipToPadding = true
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(sidePanelWidthPx(), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START).apply { setMargins(leftSafeMarginPx(), 16, 0, 16) }
        }
        applySidePanelBackgroundAndPadding()

        allGridTiles.clear(); allGridCaptions.clear()

        singleColumnFrame = FrameLayout(this).apply {
            clipChildren = true; clipToPadding = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        leftIconRail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundDrawable(makeThemedSubBg()); visibility = View.VISIBLE; setPadding(10, 16, 10, 16)
            layoutParams = FrameLayout.LayoutParams(108, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL)
            setOnClickListener {
                resetMenuInactivityTimer()
                if (!isLeftRailVisible) showLeftIconRailFromGroups()
            }
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

        categoryListView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_NONE; divider = null; dividerHeight = 0
            overScrollMode = View.OVER_SCROLL_NEVER; isScrollingCacheEnabled = false
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            selector = ColorDrawable(Color.TRANSPARENT); isSmoothScrollbarEnabled = false
            setBackgroundDrawable(makeThemedSubBg()); setPadding(8, 8, 8, 8); visibility = View.VISIBLE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL)
        }

        channelListView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_NONE; divider = null; dividerHeight = 0
            overScrollMode = View.OVER_SCROLL_NEVER; isScrollingCacheEnabled = false
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            selector = ColorDrawable(Color.TRANSPARENT); isSmoothScrollbarEnabled = false
            setBackgroundDrawable(makeThemedSubBg()); setPadding(8, 8, 8, 8); visibility = View.VISIBLE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL)
        }

        singleColumnFrame.addView(leftIconRail)
        singleColumnFrame.addView(categoryListView)
        singleColumnFrame.addView(channelListView)
        sidePanel.addView(singleColumnFrame)
        rootFrame.addView(sidePanel)

        infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundDrawable(makeThemedPanelBg()); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(620, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, 68, 24, 0) }
            setPadding(28, 22, 28, 22)
        }
        infoTitleView = TextView(this).apply {
            text = "Info"; setTextColor(currentTheme().accentColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f)
            typeface = themeBoldFont(); gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 0, 0, 12)
            isSingleLine = true; maxLines = 1
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        infoPanel.addView(infoTitleView)
        infoRowViews.clear()
        for (rIdx in 0 until 7) {
            val rowTv = TextView(this).apply {
                setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f); typeface = themeFont()
                isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(4, if (rIdx == 2) 18 else 5, 4, 5)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            infoRowViews.add(rowTv)
            infoPanel.addView(rowTv)
        }
        infoTextView = infoRowViews[0]
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
            setPadding(30, 22, 30, 22)
            layoutParams = FrameLayout.LayoutParams(1200, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
                setMargins(28, 72, 28, 24)
            }
        }
        fullEpgTitle = TextView(this).apply {
            setTextColor(currentTheme().accentColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 19.5f)
            typeface = themeBoldFont(); gravity = Gravity.CENTER_HORIZONTAL; setPadding(4, 2, 4, 14)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fullEpgOverlay.addView(fullEpgTitle)

        fullEpgListView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_NONE; divider = null; dividerHeight = 0; overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = true; isScrollbarFadingEnabled = false
            selector = ColorDrawable(Color.TRANSPARENT); setBackgroundDrawable(makeThemedSubBg())
            layoutParams = FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
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
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(960, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(24, 16, 24, 16)
            }
        }
        applySubModalBackgroundAndPadding()
        subModalHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(4, 2, 4, 14)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        subModalBadgeView = TextView(this).apply {
            text = "TV"
            styleModalMonogramBadge(this)
        }
        subModalTitle = TextView(this).apply {
            setTextColor(currentTheme().textPrimaryColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = themeBoldFont(); gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        subModalHeaderRow.addView(subModalBadgeView)
        subModalHeaderRow.addView(subModalTitle)
        subModalOverlay.addView(subModalHeaderRow)

        subModalTopButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            visibility = View.GONE; setPadding(0, 0, 0, 12)
        }
        subModalOverlay.addView(subModalTopButtons)

        subModalInputContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        subModalOverlay.addView(subModalInputContainer)

        subModalCustomBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        subModalOverlay.addView(subModalCustomBody)

        subModalThemesGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            visibility = View.GONE
            setPadding(4, 4, 4, 4)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        subModalOverlay.addView(subModalThemesGrid)

        subModalList = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_NONE
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = (7 * uiScaleFactor()).toInt().coerceAtLeast(6)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            isSmoothScrollbarEnabled = false
            selector = ColorDrawable(Color.TRANSPARENT); setBackgroundDrawable(makeThemedSubBg()); setPadding(12, 10, 14, 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        subModalAdapter = createRoundedSelectorAdapter(subModalItems, 15.5f, true, fixedRowHeight = true) { pos -> subModalHighlightedPos == pos }
        subModalStatsAdapter = object : ArrayAdapter<StatsModalRowData>(this@MainActivity, 0, subModalStatsItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val rowView = (convertView as? StructuredStatsRowView) ?: StructuredStatsRowView(this@MainActivity)
                val item = if (position in 0 until count) getItem(position) else null
                val rowH = exactModalRowHeightPx()
                rowView.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH)
                if (item != null) {
                    rowView.bindData(item, currentTheme(), uiScaleFactor(), subModalHighlightedPos == position)
                }
                return rowView
            }
        }
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
        subModalBottomButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            visibility = View.GONE; setPadding(0, 12, 0, 2)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        subModalOverlay.addView(subModalBottomButtons)
        rootFrame.addView(subModalOverlay)

        currentChannelBanner = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); typeface = themeBoldFont()
            setBackgroundDrawable(makeThemedPanelBg()); setPadding(22, 12, 22, 12); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply { setMargins(26, 0, 26, 26) }
        }
        rootFrame.addView(currentChannelBanner)
        gestureHudView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            typeface = themeBoldFont()
            gravity = Gravity.CENTER
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.argb(215, 16, 20, 30))
                cornerRadius = 999f
                setStroke(2, Color.argb(120, 255, 255, 255))
            })
            val ph = dpPx(20f); val pv = dpPx(10f)
            setPadding(ph, pv, ph, pv)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        rootFrame.addView(gestureHudView)

        firstRunModeOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E608070D")); visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        buildFirstRunControlPickerView()
        rootFrame.addView(firstRunModeOverlay)

        splashOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0B2742"))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        splashEyeView = SunbeamEyeSplashView(this).apply {
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

        // 1. Адаптер и обработчики списка ГРУПП (categoryListView)
        categoryAdapter = createRoundedSelectorAdapter(categoryDisplayItems, 15.5f, true, fixedRowHeight = true) { pos ->
            !isSideShowingChannels && !isLeftRailVisible && pos == focusedCategoryPos
        }
        categoryListView.adapter = categoryAdapter

        categoryListView.setOnItemClickListener { _, _, position, _ ->
            resetMenuInactivityTimer()
            if (isSideShowingChannels) {
                showSideGroupsMode()
                return@setOnItemClickListener
            }
            if (isLeftRailVisible) {
                hideLeftIconRailToGroups()
                return@setOnItemClickListener
            }
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

        // 2. Адаптер и обработчики отдельного списка КАНАЛОВ (channelListView с цветными кубиками)
        channelListAdapter = createRoundedSelectorAdapter(channelDisplayItems, 15.5f, true, fixedRowHeight = true) { pos ->
            isSideShowingChannels && pos == focusedChannelPos
        }
        channelListView.adapter = channelListAdapter

        channelListView.setOnItemClickListener { _, _, position, _ ->
            resetMenuInactivityTimer()
            if (!isSideShowingChannels) {
                showSideChannelsMode(focusCurrentChannelIfSameGroup = false)
                return@setOnItemClickListener
            }
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
        scheduleSilentStartupUpdateCheck()
    }

    internal fun setSubModalHeaderTitle(baseTitle: String, badgeCode: String = "") {
        subModalBaseTitleText = baseTitle
        if (::subModalBadgeView.isInitialized) {
            subModalBadgeView.text = badgeCode.ifBlank { resolveMonogramForTitle(baseTitle) }
            styleModalMonogramBadge(subModalBadgeView)
        }
        updateSubModalTitleWithCounter()
    }

    internal fun updateSubModalTitleWithCounter() {
        subModalTitle.text = colorizeStatusText(subModalBaseTitleText)
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
        isSideShowingChannels = false
        updateSideSlidingTiles(animate = true)
        refreshSimpleListHighlightInPlace(categoryListView, -1)
        refreshSimpleListHighlightInPlace(channelListView, -1)
        railTilePlaylists.requestFocus()
    }

    internal fun hideLeftIconRailToGroups() {
        isLeftRailVisible = false
        isSideShowingChannels = false
        updateSideSlidingTiles(animate = true)
        categoryListView.requestFocus()
        refreshSimpleListHighlightInPlace(categoryListView, focusedCategoryPos)
        refreshSimpleListHighlightInPlace(channelListView, -1)
    }

    internal fun refreshSimpleListHighlightInPlace(listView: ListView, activeAdapterPos: Int) {
        try {
            val adapter = listView.adapter ?: return
            val totalCount = adapter.count
            val t = currentTheme()
            val scale = uiScaleFactor()
            val firstVis = listView.firstVisiblePosition
            for (i in 0 until listView.childCount) {
                val rawChild = listView.getChildAt(i)
                val itemPos = firstVis + i
                if (itemPos !in 0 until totalCount) continue
                val isHighlighted = (itemPos == activeAdapterPos)
                if (rawChild is StructuredStatsRowView) {
                    rawChild.applyThemeAndHighlight(t, scale, isHighlighted)
                    continue
                }
                val child = rawChild as? MarqueeRowTextView ?: continue
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
            }
        } catch (_: Exception) {}
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
                val rawTxt = if (position in 0 until count) (getItem(position) ?: "") else ""
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

        val curIdx = findActiveChannelIndex(list, currentChannel, focusedChannelPos).takeIf { it >= 0 } ?: 0
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

        if (subModalList.adapter !== subModalAdapter) {
            subModalList.adapter = subModalAdapter
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


    internal fun updateSubModalStatsRows(newRows: List<StatsModalRowData>, smoothToggle: Boolean = false, preservePositionIndex: Int = -1) {
        val targetPos = when {
            preservePositionIndex in newRows.indices -> preservePositionIndex
            subModalHighlightedPos in newRows.indices -> subModalHighlightedPos
            else -> 0
        }
        val wasStatsAdapter = (subModalList.adapter === subModalStatsAdapter)
        val firstVis = subModalList.firstVisiblePosition
        val selectedChildIdx = targetPos - firstVis
        val selectedRowTopPx = if (wasStatsAdapter && smoothToggle && selectedChildIdx in 0 until subModalList.childCount) {
            subModalList.getChildAt(selectedChildIdx)?.top ?: 8
        } else {
            subModalList.getChildAt(0)?.top ?: 8
        }

        subModalHighlightedPos = targetPos
        subModalStatsItems.clear()
        subModalStatsItems.addAll(newRows)
        if (!wasStatsAdapter) {
            subModalList.adapter = subModalStatsAdapter
        }
        fitSubModalListToWholeRows(newRows.size)
        subModalStatsAdapter.notifyDataSetChanged()
        updateSubModalTitleWithCounter()

        subModalList.post {
            if (newRows.isNotEmpty()) {
                if (wasStatsAdapter && smoothToggle) {
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
        val divH = (7 * uiScaleFactor()).toInt().coerceAtLeast(6)
        subModalList.dividerHeight = divH
        val rowH = exactModalRowHeightPx() + divH
        val availListH = (dm.heightPixels - 140).coerceAtLeast(320)
        val fitsWithoutScroll = (itemCount * rowH + 20) <= availListH
        subModalList.isVerticalScrollBarEnabled = !fitsWithoutScroll
        subModalList.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    }

    private fun makeSymmetricDeviceButton(
        isTv: Boolean,
        labelText: String,
        subtitleText: String,
        isActiveMode: Boolean,
        onSelect: () -> Unit
    ): LinearLayout {
        val t = currentTheme()
        val scale = uiScaleFactor()
        val dark = isThemeDarkSurface(t.panelBgColor)
        val iconSize = dpPx(44f * scale)

        val checkBadge = TextView(this).apply {
            text = "✓"
            setTextColor(themeOnPillTextColor())
            typeface = t.boldFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale)
            gravity = Gravity.CENTER
            includeFontPadding = false
            visibility = if (isActiveMode) View.VISIBLE else View.INVISIBLE
            val sz = dpPx(24f * scale)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                gravity = Gravity.END
                bottomMargin = dpPx(4f * scale)
            }
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(themeOnPillBgColor())
                cornerRadius = if (t.id == 3) dpPx(5f).toFloat() else 999f
            })
        }
        val iconView = DeviceGlyphIconView(this, isTv).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { bottomMargin = dpPx(8f * scale) }
        }
        val labelView = TextView(this).apply {
            text = labelText
            setTextColor(t.textPrimaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f * scale)
            typeface = t.boldFont
            gravity = Gravity.CENTER
            isSingleLine = true; maxLines = 1
        }
        val subView = TextView(this).apply {
            text = subtitleText
            setTextColor(t.textPrimaryColor)
            alpha = 0.76f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.6f * scale)
            typeface = t.normalFont
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(4, (6 * scale).toInt(), 4, 0)
        }
        fun applyCardStyle(container: LinearLayout, focused: Boolean) {
            val curT = currentTheme()
            val bgCol = when {
                focused -> curT.accentColor
                isActiveMode -> Color.argb(66, Color.red(curT.accentColor), Color.green(curT.accentColor), Color.blue(curT.accentColor))
                else -> if (dark) Color.argb(68, 0, 0, 0) else Color.argb(24, 0, 0, 0)
            }
            val strokeCol = when {
                focused -> themeFocusStrokeColor()
                isActiveMode -> curT.accentColor
                else -> themeCardBorderColor()
            }
            container.setBackgroundDrawable(GradientDrawable().apply {
                setColor(bgCol)
                cornerRadius = themeInnerCornerRadiusPx(scale)
                setStroke(if (focused) dpPx(2f) else dpPx(1f), strokeCol)
            })
            val fg = if (focused) curT.textOnAccent else curT.textPrimaryColor
            iconView.glyphColor = fg
            labelView.setTextColor(fg)
            subView.setTextColor(fg)
            subView.alpha = if (focused) 0.92f else 0.74f
            container.animate().scaleX(if (focused) 1.03f else 1f).scaleY(if (focused) 1.03f else 1f).setDuration(140L).start()
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            val p = dpPx(14f * scale)
            minimumHeight = dpPx(140f * scale)
            setPadding(p, dpPx(10f * scale), p, p)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                val m = dpPx(8f * scale)
                setMargins(m, dpPx(4f), m, dpPx(4f))
            }
            addView(checkBadge)
            addView(iconView)
            addView(labelView)
            addView(subView)
            applyCardStyle(this, false)
            setOnFocusChangeListener { _, hasFocus -> applyCardStyle(this, hasFocus) }
            setOnClickListener { onSelect() }
        }
    }

    internal fun buildFirstRunControlPickerView() {
        firstRunModeOverlay.removeAllViews()
        val scale = uiScaleFactor()
        val t = currentTheme()
        val cardW = computeCenteredModalWidth(0.74f)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundDrawable(makeThemedPanelBg())
            val cph = dpPx(18f).coerceAtLeast(24)
            val cpv = dpPx(14f).coerceAtLeast(20)
            setPadding(cph, cpv, cph, cpv)
            layoutParams = FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(4, 2, 4, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val badge = TextView(this).apply {
            text = "УП"
            styleModalMonogramBadge(this)
        }
        val title = TextView(this).apply {
            text = "Управление"
            setTextColor(t.textPrimaryColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 19.5f * scale)
            typeface = themeBoldFont(); gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(badge); headerRow.addView(title)
        card.addView(headerRow)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; weightSum = 2f
            setBackgroundDrawable(makeThemedSubBg())
            setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val btnRemoteMode = makeSymmetricDeviceButton(
            isTv = true,
            labelText = "Пульт ТВ",
            subtitleText = "Крупный шрифт и фокус стрелками",
            isActiveMode = (controlMode == 2)
        ) {
            controlMode = 2; uiScaleStep = 1; cameraCutoutSafeOffset = false
            applyCurrentThemeToAllPanels(); saveState()
            enableTrueFullScreenImmersion(); firstRunModeOverlay.visibility = View.GONE
        }.apply { id = 8451 }

        val btnTouchMode = makeSymmetricDeviceButton(
            isTv = false,
            labelText = "Смартфон / Планшет",
            subtitleText = "Свайпы, касания и отступ под камеру",
            isActiveMode = (controlMode == 1)
        ) {
            controlMode = 1; uiScaleStep = 0; cameraCutoutSafeOffset = true; screenAspectMode = 0
            applyScreenAspectMode(); applyCurrentThemeToAllPanels(); saveState()
            enableTrueFullScreenImmersion(); firstRunModeOverlay.visibility = View.GONE
        }.apply { id = 8452 }

        btnRemoteMode.nextFocusRightId = btnTouchMode.id
        btnTouchMode.nextFocusLeftId = btnRemoteMode.id

        row.addView(btnRemoteMode); row.addView(btnTouchMode)
        card.addView(row); firstRunModeOverlay.addView(card)
    }

    internal fun showFirstRunControlPicker(forceShow: Boolean = false) {
        if (controlMode == 0 || forceShow) {
            closeAllMenus(); buildFirstRunControlPickerView()
            animateMenuAppear(firstRunModeOverlay, 0f, 20f)
            val card = firstRunModeOverlay.getChildAt(0) as? LinearLayout
            val row = card?.getChildAt(1) as? LinearLayout
            row?.getChildAt(if (controlMode == 1) 1 else 0)?.requestFocus()
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
                lastSwipeLeftFromIdleMs = 0L
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
                lastSwipeLeftFromIdleMs = 0L
                if (firstRunModeOverlay.visibility == View.VISIBLE || menuHistory.isNotEmpty()) return
                navigateToMenu("SETTINGS")
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                lastSwipeLeftFromIdleMs = 0L
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

                val nowMs = System.currentTimeMillis()
                if (abs(dx) > abs(dy) * 1.45f && abs(dx) > 95f) {
                    if (curMenu == "SIDE") {
                        lastSwipeLeftFromIdleMs = 0L
                        if (dx > 0) {
                            if (isSideShowingChannels) showSideGroupsMode()
                            else if (!isLeftRailVisible) showLeftIconRailFromGroups()
                        } else {
                            if (isLeftRailVisible) hideLeftIconRailToGroups()
                            else if (!isSideShowingChannels) showSideChannelsMode(focusCurrentChannelIfSameGroup = false)
                            else closeAllMenus()
                        }
                        return true
                    } else if (curMenu == "INFO") {
                        if (dx < 0 && lastSwipeLeftFromIdleMs > 0L && (nowMs - lastSwipeLeftFromIdleMs) in 80L..1050L) {
                            lastSwipeLeftFromIdleMs = 0L
                            powerOffAppWithCrtEffect()
                            return true
                        }
                        lastSwipeLeftFromIdleMs = 0L
                        navigateBackInMenu()
                        return true
                    } else if (curMenu != null) {
                        lastSwipeLeftFromIdleMs = 0L
                        if (dx < 0) {
                            navigateBackInMenu()
                            return true
                        }
                    } else {
                        if (dx > 0) {
                            lastSwipeLeftFromIdleMs = 0L
                            navigateToMenu("SIDE")
                        } else {
                            lastSwipeLeftFromIdleMs = nowMs
                            navigateToMenu("INFO")
                        }
                        return true
                    }
                } else if (abs(dy) > abs(dx) * 1.35f && abs(dy) > 85f) {
                    lastSwipeLeftFromIdleMs = 0L
                    val touchedEmptySpace = !isTouchInsideAnyActiveMenu(e1)
                    if (curMenu == null || curMenu == "INFO") {
                        switchChannelByOffset(if (dy < 0) +1 else -1)
                        return true
                    } else if (curMenu == "OSD") {
                        if (touchedEmptySpace) {
                            resetMenuInactivityTimer()
                            switchChannelByOffset(if (dy < 0) +1 else -1)
                            return true
                        } else if (dy > 0) {
                            buildAndShowZappingBar()
                            return true
                        }
                    } else if (curMenu == "SIDE" && touchedEmptySpace) {
                        resetMenuInactivityTimer()
                        switchChannelByOffset(if (dy < 0) +1 else -1)
                        return true
                    }
                }
                return false
            }
        })
    }

    private val regexCleanTwoLetters: Regex = Regex("[^a-zA-Zа-яА-Я0-9 ]")
    private val regexSplitSpaces: Regex = Regex("\\s+")

    internal fun extractTwoLetters(name: String): String {
        val clean = name.replace(regexCleanTwoLetters, " ").trim()
        val parts = clean.split(regexSplitSpaces).filter { it.isNotEmpty() }
        val raw = if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}" else (parts.firstOrNull() ?: "TV").take(2)
        return raw.uppercase().padEnd(2, 'T').take(2)
    }

    internal fun fitSideListsExactRows(repositionTiles: Boolean = true) {
        val rowH = exactModalRowHeightPx().coerceAtLeast(42)
        val divH = dpPx(2f)
        val stepH = rowH + divH
        val pad = sidePanelPadPx()
        val maxH = (resources.displayMetrics.heightPixels - pad * 2 - dpPx(24f)).coerceAtLeast(dpPx(220f))
        val exactRows = (maxH / stepH).coerceAtLeast(4)
        val exactH = (exactRows * rowH + (exactRows - 1).coerceAtLeast(0) * divH + dpPx(12f)).coerceAtMost(maxH)

        val innerW = (sidePanelWidthPx() - pad * 2).coerceAtLeast(dpPx(260f))
        val gap = dpPx(8f)
        val peekW = (innerW * 0.14f).toInt().coerceIn(dpPx(36f), dpPx(78f))
        val railW = dpPx(64f * uiScaleFactor()).coerceIn(dpPx(54f), dpPx(92f))
        val groupsW = (innerW - 2 * peekW - 2 * gap).coerceAtLeast(dpPx(170f))
        val channelsW = (innerW - peekW - gap).coerceAtLeast(dpPx(190f))

        categoryListView.isVerticalScrollBarEnabled = (!isSideShowingChannels && !isLeftRailVisible && activeCategoryList().size > exactRows)
        channelListView.isVerticalScrollBarEnabled = (isSideShowingChannels && displayedChannels.size > exactRows)

        fun applySizeIfChanged(v: View, w: Int, h: Int) {
            val lp = v.layoutParams as? FrameLayout.LayoutParams
            if (lp == null || lp.width != w || lp.height != h) {
                v.layoutParams = FrameLayout.LayoutParams(w, h, Gravity.START or Gravity.CENTER_VERTICAL)
            }
        }
        applySizeIfChanged(leftIconRail, railW, exactH)
        applySizeIfChanged(categoryListView, groupsW, exactH)
        applySizeIfChanged(channelListView, channelsW, exactH)
        if (repositionTiles) {
            updateSideSlidingTiles(animate = false)
        }
    }

    internal fun updateSideSlidingTiles(animate: Boolean) {
        val pad = sidePanelPadPx()
        val innerW = (sidePanelWidthPx() - pad * 2).coerceAtLeast(dpPx(260f))
        val gap = dpPx(8f)
        val peekW = (innerW * 0.14f).toInt().coerceIn(dpPx(36f), dpPx(78f))
        val railW = dpPx(64f * uiScaleFactor()).coerceIn(dpPx(54f), dpPx(92f))
        val groupsW = (innerW - 2 * peekW - 2 * gap).coerceAtLeast(dpPx(170f))

        leftIconRail.visibility = View.VISIBLE
        categoryListView.visibility = View.VISIBLE
        channelListView.visibility = View.VISIBLE

        val railX: Float; val railAlpha: Float; val railScaleY: Float
        val groupsX: Float; val groupsAlpha: Float; val groupsScaleY: Float
        val channelsX: Float; val channelsAlpha: Float; val channelsScaleY: Float

        when {
            isLeftRailVisible -> {
                railX = 0f; railAlpha = 1f; railScaleY = 1f
                groupsX = (railW + gap).toFloat(); groupsAlpha = 0.60f; groupsScaleY = 0.96f
                channelsX = (railW + gap + groupsW + gap).toFloat(); channelsAlpha = 0f; channelsScaleY = 0.94f
                leftIconRail.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                categoryListView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                channelListView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                categoryListView.isFocusable = false
                channelListView.isFocusable = false
            }
            !isSideShowingChannels -> {
                railX = -(railW - peekW).toFloat(); railAlpha = 0.76f; railScaleY = 0.96f
                groupsX = (peekW + gap).toFloat(); groupsAlpha = 1f; groupsScaleY = 1f
                channelsX = (innerW - peekW).toFloat(); channelsAlpha = 0.84f; channelsScaleY = 0.96f
                leftIconRail.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                categoryListView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                channelListView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                categoryListView.isFocusable = true
                channelListView.isFocusable = false
            }
            else -> {
                railX = -(railW + groupsW).toFloat(); railAlpha = 0f; railScaleY = 0.94f
                groupsX = -(groupsW - peekW).toFloat(); groupsAlpha = 0.78f; groupsScaleY = 0.96f
                channelsX = (peekW + gap).toFloat(); channelsAlpha = 1f; channelsScaleY = 1f
                leftIconRail.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                categoryListView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                channelListView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                categoryListView.isFocusable = false
                channelListView.isFocusable = true
            }
        }

        fun applyTileTransform(v: View, tx: Float, al: Float, sy: Float) {
            v.animate().cancel()
            if (animate) {
                v.animate()
                    .translationX(tx)
                    .alpha(al)
                    .scaleY(sy)
                    .setDuration(240L)
                    .setInterpolator(DecelerateInterpolator(1.4f))
                    .start()
            } else {
                v.translationX = tx
                v.alpha = al
                v.scaleY = sy
            }
        }

        applyTileTransform(leftIconRail, railX, railAlpha, railScaleY)
        applyTileTransform(categoryListView, groupsX, groupsAlpha, groupsScaleY)
        applyTileTransform(channelListView, channelsX, channelsAlpha, channelsScaleY)
    }

    internal fun showSideChannelsMode(focusCurrentChannelIfSameGroup: Boolean = true) {
        val wasVisible = (sidePanel.visibility == View.VISIBLE && !isSideShowingChannels)
        isSideShowingChannels = true
        isLeftRailVisible = false
        fitSideListsExactRows(repositionTiles = !wasVisible)

        val list = displayedChannels.toList()
        val activeIdx = if (focusCurrentChannelIfSameGroup) findActiveChannelIndex(list, currentChannel, focusedChannelPos) else focusedChannelPos
        val targetPos = if (activeIdx in list.indices) activeIdx else 0
        focusedChannelPos = targetPos

        if (categoryDisplayItems.isEmpty()) updateCategoriesList()
        refreshSimpleListHighlightInPlace(categoryListView, -1)
        refreshChannelAdapter()
        updateSideSlidingTiles(animate = wasVisible)

        channelListView.post {
            try {
                val safeCount = channelListAdapter.count
                if (safeCount > 0) {
                    val clampedPos = targetPos.coerceIn(0, safeCount - 1)
                    focusedChannelPos = clampedPos
                    channelListView.setSelectionFromTop(clampedPos, (channelListView.height / 3).coerceAtLeast(70))
                    channelListView.requestFocus()
                    refreshSimpleListHighlightInPlace(channelListView, clampedPos)
                }
            } catch (_: Exception) {}
        }
    }

    internal fun showSideGroupsMode() {
        val wasVisible = (sidePanel.visibility == View.VISIBLE && (isSideShowingChannels || isLeftRailVisible))
        isSideShowingChannels = false
        isLeftRailVisible = false
        fitSideListsExactRows(repositionTiles = !wasVisible)

        val catsCount = activeCategoryList().size
        val targetPos = currentCategoryIndex.coerceIn(0, (catsCount - 1).coerceAtLeast(0))
        focusedCategoryPos = targetPos

        if (categoryDisplayItems.isEmpty()) updateCategoriesList()
        if (channelDisplayItems.isEmpty() && allChannels.isNotEmpty()) refreshChannelAdapter()
        refreshSimpleListHighlightInPlace(channelListView, -1)
        refreshSimpleListHighlightInPlace(categoryListView, targetPos)
        updateSideSlidingTiles(animate = wasVisible)

        categoryListView.post {
            try {
                val safeCount = categoryAdapter.count
                if (safeCount > 0) {
                    val clampedPos = targetPos.coerceIn(0, safeCount - 1)
                    focusedCategoryPos = clampedPos
                    categoryListView.setSelectionFromTop(clampedPos, (categoryListView.height / 3).coerceAtLeast(60))
                    categoryListView.requestFocus()
                    refreshSimpleListHighlightInPlace(categoryListView, clampedPos)
                }
            } catch (_: Exception) {}
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
                        uiHandler.postDelayed({ showPendingUpdateIfReady() }, 400L)
                    }.start()
                }
            }
        }
        uiHandler.post(animRunnable)
    }



    internal fun hideAllMenuViews() {
        setPlayerMiniPreviewMode(false)
        sidePanel.visibility = View.GONE
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
        if (menuHistory.lastOrNull() == "UPDATE_PROMPT") {
            cancelActiveUpdateDownloadIfAny()
            onUpdatePermissionResume = null
        }
        menuHistory.clear()
        uiHandler.removeCallbacks(autoHideMenuRunnable)
        hideAllMenuViews()
        uiHandler.postDelayed({ showPendingUpdateIfReady() }, 350L)
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
        if (leaving == "UPDATE_PROMPT") {
            cancelActiveUpdateDownloadIfAny()
            onUpdatePermissionResume = null
        }
        if ((leaving == "ANALYZER_GROUP_VIEW" || leaving == "ANALYZER_GROUPS") && isAnalyzerRunning) {
            analyzerStatusBanner.visibility = View.VISIBLE
        }
        val prev = menuHistory.lastOrNull()
        if (prev == null) {
            uiHandler.removeCallbacks(autoHideMenuRunnable)
            hideAllMenuViews()
            uiHandler.postDelayed({ showPendingUpdateIfReady() }, 350L)
        } else {
            subModalHighlightedPos = 0
            renderMenuById(prev)
            resetMenuInactivityTimer()
        }
        return true
    }

    internal fun renderMenuById(menuId: String) {
        hideAllMenuViews()
        applySidePanelBackgroundAndPadding()
        centerOsdPanel.setBackgroundDrawable(makeThemedPanelBg())
        applySubModalBackgroundAndPadding()
        settingsTileOverlay.setBackgroundDrawable(makeThemedPanelBg())
        fullEpgOverlay.setBackgroundDrawable(makeThemedPanelBg())

        currentChannelBanner.animate().cancel()
        currentChannelBanner.visibility = View.GONE
        subModalTopButtons.visibility = View.GONE
        subModalTopButtons.removeAllViews()
        if (::subModalInputContainer.isInitialized) {
            subModalInputContainer.visibility = View.GONE
            subModalInputContainer.removeAllViews()
        }
        if (::subModalCustomBody.isInitialized) {
            subModalCustomBody.visibility = View.GONE
            subModalCustomBody.removeAllViews()
        }
        if (::subModalBottomButtons.isInitialized) {
            subModalBottomButtons.visibility = View.GONE
            subModalBottomButtons.removeAllViews()
        }
        if (::subModalThemesGrid.isInitialized) subModalThemesGrid.visibility = View.GONE
        if (::subModalList.isInitialized) {
            subModalList.visibility = View.GONE
            subModalList.onItemLongClickListener = null
        }

        applyCenteredModalDimensions(menuId)

        when (menuId) {
            "SIDE" -> {
                sidePanel.layoutParams = FrameLayout.LayoutParams(
                    sidePanelWidthPx(),
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.START
                ).apply { setMargins(leftSafeMarginPx(), 16, 0, 16) }
                applySidePanelBackgroundAndPadding()
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
            "EXPORT_PLAYLIST" -> openExportPlaylistSubModal()
            "DELETE_PLAYLIST" -> openDeletePlaylistSubModal()
            "CHANNEL_EDITOR_GROUPS" -> openChannelEditorGroupsModal(inPlace = false)
            "CHANNEL_EDITOR_ITEMS" -> openChannelEditorItemsModal(selectedEditorCategory, inPlace = false)
            "ANALYZER_GROUPS" -> openAnalyzerGroupsModal(firstOpen = true)
            "ANALYZER_GROUP_VIEW" -> openAnalyzerGroupChannelsModal(selectedAnalyzerCategory, firstOpen = true)
            "BUFFER" -> openBufferSubModal(inPlace = false)
            "DECODER" -> openDecoderSubModal(inPlace = false)
            "AUDIO" -> openAudioSubModal(inPlace = false)
            "DIAG_LOG_VIEW" -> openDiagLogViewerModal(firstOpen = true)
            "DIAG_SHARE_PICKER" -> openDiagSharePickerModal(firstOpen = true)
            "EPG" -> openInputSubModal("Ссылка на телепрограмму XMLTV", epgUrl) { newVal ->
                if (newVal.isNotEmpty()) { epgUrl = newVal; saveState(); loadEpgOncePerDayInBackground(forceRefresh = true) }
            }
            "ADD_URL" -> openAddUrlSubModal()
            "RENAME_PLAYLIST" -> openRenamePlaylistSubModal()
            "PROXY_INPUT" -> openInputSubModal("Прокси (socks5:// или http://, пусто = выкл)", networkProxyUrl) { newVal ->
                networkProxyUrl = newVal.trim()
                saveState()
                rebuildNetworkClients()
                initPlayer()
                currentChannel?.let { playChannelObj(it, immediate = true, silentSwap = true, updateSavedUrl = false) }
            }
            "UPDATE_PROMPT" -> openUpdateAvailablePromptModal()
        }
    }

    internal fun rewindLiveBufferSeconds(sec: Int) {
        if (isVlcEngineActive()) {
            val vlc = vlcPlayer ?: return
            val cur = runCatching { vlc.time }.getOrDefault(0L)
            if (cur > 0L) runCatching { vlc.setTime((cur - sec * 1000L).coerceAtLeast(0L)) }
            return
        }
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
            val curIdx = findActiveChannelIndex(list, currentChannel, focusedChannelPos).takeIf { it >= 0 } ?: 0
            val secondCh = previousChannel ?: list[(curIdx + 1) % list.size]
            startDualStreamChannel(secondCh)
        }
    }

    internal fun startDualStreamChannel(ch: Channel) {
        try { dualPipPlayer?.stop(); dualPipPlayer?.release() } catch (_: Exception) {}
        dualStreamChannel = ch
        isDualStreamActive = true
        val cleanUrl = ch.url.substringBefore("#")
        val pipAgent = if (CoreEngines.isSingleSessionTokenUrl(cleanUrl)) {
            CoreEngines.russianSmartTvAgents[0]
        } else {
            CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size]
        }
        val pipDsFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(pipAgent)
            .setDefaultRequestProperties(CoreEngines.headersForStreamUrl(cleanUrl))
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(12000)
            .setReadTimeoutMs(12000)
        val pipMediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(pipDsFactory)
        val dp = ExoPlayer.Builder(this)
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

    internal fun effectiveCategory(ch: Channel): String {
        if (dynamicCategories.isNotEmpty()) return ch.category
        return customCategoryMap[ch.url] ?: ch.category
    }

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
                if (p != null && currentChannel != null && !streamRetriesExhausted) {
                    val state = p.playbackState
                    val curPos = p.currentPosition
                    val bufMs = p.totalBufferedDuration
                    val prevPos = lastRenderedFramePositionMs
                    lastRenderedFramePositionMs = curPos

                    val sinceSwitchMs = System.currentTimeMillis() - lastChannelSwitchMs
                    if (state == Player.STATE_BUFFERING && sinceSwitchMs >= 9000L) {
                        continuousBufferingTicks++
                    } else {
                        continuousBufferingTicks = 0
                    }

                    // Даём новому каналу 9 секунд на первичный старт; в STATE_BUFFERING ловим также
                    // зависание с уезжающим в минус curPos при полном буфере (HLS sliding window stall)
                    val isBufferingStalled = (state == Player.STATE_BUFFERING && (curPos == prevPos || curPos <= 0L || bufMs < 1500L || continuousBufferingTicks >= 4))
                    val isReadyStalled = (state == Player.STATE_READY && p.isPlaying && curPos == prevPos && bufMs < 1500L)
                    val isTrulyStalled = sinceSwitchMs >= 9000L && (state == Player.STATE_IDLE || isBufferingStalled || isReadyStalled)

                    if (isTrulyStalled) {
                        lowSignalStuckSeconds += 2
                        if (lowSignalStuckSeconds == 6 || lowSignalStuckSeconds >= 12) {
                            CoreEngines.appendDiagLog(
                                "WATCHDOG_STALL",
                                "ch='${currentChannel?.name}' state=$state bufMs=$bufMs curPos=$curPos prevPos=$prevPos stuckSec=$lowSignalStuckSeconds drops=$consecutiveDropFailures"
                            )
                        }
                        if (lowSignalStuckSeconds == 6 && state == Player.STATE_BUFFERING && bufMs >= 8000L && curPos < 0L) {
                            try {
                                p.seekToDefaultPosition()
                                p.play()
                            } catch (_: Exception) {}
                        }
                        if (lowSignalStuckSeconds >= 12) {
                            lowSignalStuckSeconds = 0
                            continuousBufferingTicks = 0
                            consecutiveDropFailures++
                            val mirrorsCount = currentChannel?.let { collectMirrorsForChannel(it).size } ?: 0
                            val maxRetries = (mirrorsCount + 2).coerceIn(3, 5)
                            if (consecutiveDropFailures > maxRetries) {
                                streamRetriesExhausted = true
                                CoreEngines.appendDiagLog("RECONNECT_STOP", "ch='${currentChannel?.name}' watchdog остановлен после $consecutiveDropFailures попыток")
                                if (onDropBehavior == 1) {
                                    consecutiveDropFailures = 0
                                    switchChannelByOffset(+1)
                                }
                            } else if (onDropBehavior == 1 && consecutiveDropFailures >= 3) {
                                consecutiveDropFailures = 0
                                switchChannelByOffset(+1)
                            } else {
                                val curUrl = currentChannel?.url ?: ""
                                if (antiBlockEnabled && !CoreEngines.isSingleSessionTokenUrl(curUrl)) {
                                    userAgentIndex = (userAgentIndex + 1) % CoreEngines.russianSmartTvAgents.size
                                }
                                reloadIconIndicator.visibility = View.VISIBLE
                                uiHandler.postDelayed({ reloadIconIndicator.visibility = View.GONE }, 2200L)
                                reconnectCurrentStreamSilently(forceMirror = (consecutiveDropFailures >= 2))
                            }
                        }
                    } else {
                        lowSignalStuckSeconds = 0
                        if (state == Player.STATE_READY && p.isPlaying && curPos > 0L && curPos != prevPos) {
                            consecutiveDropFailures = 0
                            streamRetriesExhausted = false
                        }
                    }
                } else if (isVlcEngineActive() && vlcPlayer != null && currentChannel != null && !streamRetriesExhausted) {
                    val sinceSwitchMs = System.currentTimeMillis() - lastChannelSwitchMs
                    if (sinceSwitchMs >= 10_000L && (!vlcIsPlayingNow || vlcIsBufferingNow)) {
                        lowSignalStuckSeconds += 2
                        if (lowSignalStuckSeconds >= 14) {
                            lowSignalStuckSeconds = 0
                            consecutiveDropFailures++
                            if (consecutiveDropFailures > 4) {
                                streamRetriesExhausted = true
                                if (onDropBehavior == 1) { consecutiveDropFailures = 0; switchChannelByOffset(+1) }
                            } else {
                                reloadIconIndicator.visibility = View.VISIBLE
                                uiHandler.postDelayed({ reloadIconIndicator.visibility = View.GONE }, 2200L)
                                reconnectCurrentStreamSilently(forceMirror = (consecutiveDropFailures >= 2))
                            }
                        }
                    } else {
                        lowSignalStuckSeconds = 0
                    }
                }

                if (infoPanel.visibility == View.VISIBLE) updateStreamInfoText()
                if (centerOsdPanel.visibility == View.VISIBLE) {
            updateCenterOsdContent()
            if (zappingRowContainer.visibility == View.VISIBLE) buildAndShowZappingBar()
        }
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
        val activeAgent = if (CoreEngines.isSingleSessionTokenUrl(urlStr)) {
            CoreEngines.russianSmartTvAgents[0]
        } else {
            CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size]
        }
        val hdrs = CoreEngines.headersForStreamUrl(urlStr)
        httpDataSourceFactory?.setUserAgent(activeAgent)
        httpDataSourceFactory?.setDefaultRequestProperties(hdrs)
    }

    private var pendingForceMirror: Boolean = false
    private var pendingPreferHttpFallback: Boolean = false
    private var mirrorCycleIndex: Int = 0
    private val pendingErrorReconnectRunnable = Runnable {
        val useMirrorNow = pendingForceMirror
        val preferHttpNow = pendingPreferHttpFallback
        pendingForceMirror = false
        pendingPreferHttpFallback = false
        reconnectCurrentStreamSilently(forceMirror = useMirrorNow, preferHttpFallback = preferHttpNow)
    }

    internal fun collectMirrorsForChannel(ch: Channel): List<String> {
        val cleanUrl = ch.url.substringBefore("#")
        val normTarget = CoreEngines.normalizeName(ch.name.replace(Regex("\\s+orig$", RegexOption.IGNORE_CASE), "").trim())
        val canonTarget = canonicalDedupKey(ch.name, effectiveCategory(ch))
        return LinkedHashSet<String>().apply {
            for (k in listOf(canonTarget, normTarget).distinct()) {
                if (k.isEmpty()) continue
                backupMirrorsMap[k]?.forEach { m ->
                    if (m != cleanUrl && !autoDisabledUrls.contains(m) && !isBlockedCastChannel("", m, "")) add(m)
                }
            }
            allChannels.forEach { cand ->
                val u = cand.url.substringBefore("#")
                if (u != cleanUrl && !autoDisabledUrls.contains(cand.url) && !isBlockedCastChannel(cand.name, u, cand.category, cand.tvgId) &&
                    (canonicalDedupKey(cand.name, effectiveCategory(cand)) == canonTarget || CoreEngines.normalizeName(cand.name) == normTarget)
                ) {
                    add(u)
                }
            }
        }.toList()
    }

    internal fun reconnectCurrentStreamSilently(forceMirror: Boolean = false, preferHttpFallback: Boolean = false) {
        val ch = currentChannel ?: return
        if (isVlcEngineActive()) {
            val mirrors = collectMirrorsForChannel(ch)
            val nextUrl = if ((forceMirror || consecutiveDropFailures >= 2) && mirrors.isNotEmpty()) {
                val picked = mirrors[mirrorCycleIndex % mirrors.size]; mirrorCycleIndex++; picked
            } else ch.url
            playChannelInVlc(nextUrl)
            return
        }
        val p = player ?: return
        try {
            uiHandler.removeCallbacks(pendingErrorReconnectRunnable)
            val cleanUrl = ch.url.substringBefore("#")
            val rawMirrors = collectMirrorsForChannel(ch)
            val allMirrors = if (preferHttpFallback) {
                rawMirrors.sortedBy { if (it.startsWith("http://", ignoreCase = true)) 0 else 1 }
            } else {
                rawMirrors
            }
            val httpEquivalent = if (preferHttpFallback && cleanUrl.startsWith("https://", ignoreCase = true)) {
                "http://" + cleanUrl.removePrefix("https://")
            } else {
                ""
            }
            val useMirror = (forceMirror || consecutiveDropFailures >= 2 || (preferHttpFallback && allMirrors.any { it.startsWith("http://", ignoreCase = true) })) && allMirrors.isNotEmpty()
            val targetUrl = when {
                preferHttpFallback && allMirrors.any { it.startsWith("http://", ignoreCase = true) } -> {
                    silentFallbackAttemptedForUrl = ch.url
                    val chosen = allMirrors[mirrorCycleIndex % allMirrors.size]
                    mirrorCycleIndex++
                    chosen
                }
                preferHttpFallback && httpEquivalent.isNotEmpty() && consecutiveDropFailures == 1 -> {
                    httpEquivalent
                }
                useMirror -> {
                    silentFallbackAttemptedForUrl = ch.url
                    val chosen = allMirrors[mirrorCycleIndex % allMirrors.size]
                    mirrorCycleIndex++
                    chosen
                }
                else -> cleanUrl
            }
            CoreEngines.appendDiagLog(
                "RECONNECT",
                "ch='${ch.name}' drops=$consecutiveDropFailures forceMirror=$forceMirror useMirror=$useMirror url='$targetUrl'"
            )
            lastChannelSwitchMs = System.currentTimeMillis()
            if (p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)) {
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .build()
            }
            p.stop()
            p.clearMediaItems()
            applyHeadersToDataSourceForUrl(targetUrl)
            p.setMediaItem(createLiveMediaItem(targetUrl), true)
            p.prepare()
            p.play()
        } catch (_: Exception) {}
    }

    internal fun createLiveMediaItem(url: String): MediaItem {
        val cleanUrl = url.substringBefore("#")
        // Не навязываем жёсткий сдвиг live-окна: ExoPlayer сам берёт безопасную точку
        // из HLS-манифеста (актуально для коротких 12–15 сек окон музыкальных каналов)
        // и плавно держит синхронизацию в диапазоне 0.98–1.02x без скачков.
        return MediaItem.Builder()
            .setUri(cleanUrl)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMinPlaybackSpeed(0.98f)
                    .setMaxPlaybackSpeed(1.02f)
                    .build()
            )
            .build()
    }

    internal fun updateSignalIndicator(): Int {
        val (activeBars, activeColor) = if (isVlcEngineActive()) {
            when {
                vlcIsPlayingNow && !vlcIsBufferingNow -> Pair(4, Color.parseColor("#00E676"))
                vlcIsBufferingNow && vlcBufferPercent >= 40 -> Pair(2, Color.parseColor("#FFD740"))
                vlcIsBufferingNow -> Pair(1, Color.parseColor("#FF5252"))
                else -> Pair(1, Color.parseColor("#FF5252"))
            }
        } else {
            val p = player
            val state = p?.playbackState ?: Player.STATE_IDLE
            val bufMs = p?.totalBufferedDuration ?: 0L
            when {
                state == Player.STATE_IDLE -> Pair(1, Color.parseColor("#FF5252"))
                state == Player.STATE_BUFFERING && bufMs < 150L -> Pair(1, Color.parseColor("#FF5252"))
                state == Player.STATE_BUFFERING -> Pair(2, Color.parseColor("#FFD740"))
                state == Player.STATE_READY && bufMs >= 2000L -> Pair(4, Color.parseColor("#00E676"))
                state == Player.STATE_READY && bufMs >= 600L -> Pair(3, Color.parseColor("#69F0AE"))
                state == Player.STATE_READY -> Pair(2, Color.parseColor("#FFD740"))
                else -> Pair(1, Color.parseColor("#FF5252"))
            }
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

    private fun buildSmartCodecSelector(preferHevcForDv: Boolean): MediaCodecSelector {
        return object : MediaCodecSelector {
            override fun getDecoderInfos(
                mimeType: String,
                requiresSecureDecoder: Boolean,
                requiresTunnelingDecoder: Boolean
            ): MutableList<MediaCodecInfo> {
                if (mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
                    val hevcDecoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
                        MimeTypes.VIDEO_H265,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                    if (preferHevcForDv && hevcDecoders.isNotEmpty()) {
                        return hevcDecoders.toMutableList()
                    }
                    val dvDecoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
                        mimeType,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                    val combined = ArrayList<MediaCodecInfo>(dvDecoders.size + hevcDecoders.size)
                    combined.addAll(dvDecoders)
                    for (hevc in hevcDecoders) {
                        if (combined.none { it.name == hevc.name }) combined.add(hevc)
                    }
                    return combined
                }
                return MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                ).toMutableList()
            }
        }
    }

    private fun normalizeAudioLangCode(raw: String): String {
        val c = raw.trim().lowercase(Locale.US)
        return when {
            c.startsWith("ru") -> "rus"
            c.startsWith("en") -> "eng"
            c.startsWith("uk") || c.startsWith("ua") -> "ukr"
            c == "auto" -> "auto"
            else -> c
        }
    }

    private fun langAliases(code: String): List<String> {
        return when (normalizeAudioLangCode(code)) {
            "rus" -> listOf("rus", "ru", "рус", "russian")
            "eng" -> listOf("eng", "en", "english", "ориг", "orig")
            "ukr" -> listOf("ukr", "uk", "укр", "ukrainian")
            else -> listOf(code.lowercase(Locale.US)).filter { it.isNotBlank() }
        }
    }

    private fun inferLangCodeFromTrackName(name: String?): String {
        val low = name?.lowercase(Locale.US).orEmpty()
        return when {
            low.contains("рус") || low.contains("rus") || low.contains("[ru]") -> "rus"
            low.contains("eng") || low.contains("англ") || low.contains("ориг") -> "eng"
            low.contains("укр") || low.contains("ukr") -> "ukr"
            else -> ""
        }
    }

    private fun applyMedia3RememberedTracks(tracks: Tracks) {
        val p = player ?: return
        var builder = p.trackSelectionParameters.buildUpon()
        var changed = false

        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isNotEmpty()) {
            val pref = normalizeAudioLangCode(preferredAudioLang)
            if (pref.isNotBlank() && pref != "auto" && !media3AppliedSavedTracks) {
                val aliases = langAliases(pref)
                var matchedGroup: Tracks.Group? = null
                var matchedIdx = -1
                outer@ for (g in audioGroups) {
                    for (i in 0 until g.length) {
                        val fmt = g.getTrackFormat(i)
                        val hay = "${fmt.language.orEmpty()} ${fmt.label.orEmpty()}".lowercase(Locale.US)
                        if (aliases.any { hay.contains(it) }) {
                            matchedGroup = g; matchedIdx = i; break@outer
                        }
                    }
                }
                if (matchedGroup != null && matchedIdx >= 0) {
                    builder = builder
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(TrackSelectionOverride(matchedGroup.mediaTrackGroup, matchedIdx))
                    changed = true
                    media3AppliedSavedTracks = true
                }
            }
            if (!changed && audioGroups.none { it.isSelected }) {
                val firstGroup = audioGroups.first()
                if (firstGroup.length > 0) {
                    builder = builder.setOverrideForType(TrackSelectionOverride(firstGroup.mediaTrackGroup, 0))
                    changed = true
                }
            }
        }

        val curSubDisabled = p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        if (!subtitlesEnabled && !curSubDisabled) {
            builder = builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            changed = true
        } else if (subtitlesEnabled && curSubDisabled) {
            builder = builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            changed = true
        }

        if (changed) p.trackSelectionParameters = builder.build()
    }

    private fun vlcNetworkCachingMs(): Int {
        return when (bufferLevel) {
            0 -> 2500
            1 -> 4500
            2 -> 7000
            else -> 10000
        }
    }

    private fun releaseVlcPlayer() {
        val vlc = vlcPlayer
        vlcPlayer = null
        vlcIsPlayingNow = false
        vlcIsBufferingNow = false
        vlcBufferPercent = 0
        if (vlc != null) {
            runCatching { vlc.setEventListener(null) }
            runCatching { vlc.stop() }
            if (vlcViewsAttached) {
                runCatching { vlc.detachViews() }
                vlcViewsAttached = false
            }
            runCatching { vlc.release() }
        }
        val lib = libVlc
        libVlc = null
        if (lib != null) {
            runCatching { lib.release() }
        }
    }

    private fun initVlcPlayer() {
        releaseVlcPlayer()
        val cacheMs = vlcNetworkCachingMs()
        val options = ArrayList<String>().apply {
            add("--network-caching=$cacheMs")
            add("--file-caching=$cacheMs")
            add("--live-caching=$cacheMs")
            add("--http-reconnect")
            add("--audio-time-stretch")
            add("--drop-late-frames")
            add("--skip-frames")
        }
        val vlcInstance = runCatching { LibVLC(this, options) }.getOrElse { err ->
            CoreEngines.appendDiagLog("VLC_ERR", "Ошибка инициализации LibVLC: ${err.message}")
            Toast.makeText(this, "LibVLC недоступен, возврат на Media3", Toast.LENGTH_SHORT).show()
            playerEngineMode = 0
            saveState()
            initPlayer()
            return
        }
        val mp = VlcMediaPlayer(vlcInstance)
        libVlc = vlcInstance
        vlcPlayer = mp

        playerView.visibility = View.GONE
        vlcVideoLayout.visibility = View.VISIBLE
        runCatching {
            mp.attachViews(vlcVideoLayout, null, true, false)
            vlcViewsAttached = true
        }.onFailure { err ->
            CoreEngines.appendDiagLog("VLC_ERR", "VLC attachViews fail: ${err.message}")
        }
        applyScreenAspectMode()
        applyVlcAudioBoost()

        mp.setEventListener { event ->
            if (!isVlcEngineActive() || vlcPlayer !== mp) return@setEventListener
            when (event.type) {
                VlcMediaPlayer.Event.Opening -> {
                    vlcIsBufferingNow = true
                    vlcBufferPercent = 0
                    uiHandler.post { updateSignalIndicator() }
                }
                VlcMediaPlayer.Event.Buffering -> {
                    val pct = event.buffering.toInt().coerceIn(0, 100)
                    vlcBufferPercent = pct
                    vlcIsBufferingNow = (pct < 99)
                    uiHandler.post { updateSignalIndicator() }
                }
                VlcMediaPlayer.Event.Playing -> {
                    vlcIsPlayingNow = true
                    vlcIsBufferingNow = false
                    vlcBufferPercent = 100
                    lowSignalStuckSeconds = 0
                    consecutiveDropFailures = 0
                    uiHandler.post {
                        applyVlcAudioBoost()
                        applyScreenAspectMode()
                        if (!vlcAppliedSavedTracks) applyVlcRememberedTracks()
                        updateSignalIndicator()
                        if (infoPanel.visibility == View.VISIBLE) updateStreamInfoText()
                    }
                }
                VlcMediaPlayer.Event.EncounteredError, VlcMediaPlayer.Event.EndReached -> {
                    vlcIsPlayingNow = false
                    vlcIsBufferingNow = false
                    uiHandler.post {
                        updateSignalIndicator()
                        reconnectCurrentStreamSilently()
                    }
                }
            }
        }
    }

    private fun playChannelInVlc(rawUrl: String) {
        if (libVlc == null || vlcPlayer == null) initVlcPlayer()
        val lib = libVlc ?: return
        val mp = vlcPlayer ?: return
        val cleanUrl = rawUrl.substringBefore("#").trim()
        vlcAppliedSavedTracks = false
        vlcIsPlayingNow = false
        vlcIsBufferingNow = true
        vlcBufferPercent = 0
        runCatching { mp.stop() }
        val cacheMs = vlcNetworkCachingMs()
        val media = Media(lib, Uri.parse(cleanUrl))
        val hwEnabled = (decoderMode != 2)
        media.setHWDecoderEnabled(hwEnabled, false)
        media.addOption(":network-caching=$cacheMs")
        media.addOption(":live-caching=$cacheMs")
        media.addOption(":http-reconnect")
        media.addOption(":drop-late-frames")
        media.addOption(":skip-frames")
        val agent = CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size]
        media.addOption(":http-user-agent=$agent")
        val hdrs = CoreEngines.headersForStreamUrl(cleanUrl)
        hdrs["Referer"]?.takeIf { it.isNotBlank() }?.let { media.addOption(":http-referrer=$it") }
        if (!subtitlesEnabled) media.addOption(":no-spu")
        mp.setMedia(media)
        media.release()
        mp.play()
        applyVlcAudioBoost()
    }

    private fun applyVlcRememberedTracks() {
        val vlc = vlcPlayer ?: return
        val pref = normalizeAudioLangCode(preferredAudioLang)
        if (pref.isNotBlank() && pref != "auto") {
            val aliases = langAliases(pref)
            val tracks = vlc.getAudioTracks()?.filter { it.id != -1 }.orEmpty()
            val match = tracks.firstOrNull { td ->
                val low = td.name.lowercase(Locale.US)
                aliases.any { low.contains(it) }
            }
            if (match != null) {
                runCatching { vlc.setAudioTrack(match.id) }
            }
        }
        if (!subtitlesEnabled) {
            runCatching { vlc.setSpuTrack(-1) }
        }
        vlcAppliedSavedTracks = true
    }

    internal fun initPlayer() {
        val currentUrl = currentChannel?.url
        try { loudnessEnhancer?.release(); loudnessEnhancer = null; lastAudioSessionId = C.AUDIO_SESSION_ID_UNSET } catch (_: Exception) {}
        try { player?.stop(); player?.release(); player = null } catch (_: Exception) {}
        if (isVlcEngineActive()) {
            initVlcPlayer()
            return
        }
        releaseVlcPlayer()
        if (::vlcVideoLayout.isInitialized) vlcVideoLayout.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        // Сетка буферов под короткие окна HLS (12–15 сек) без ухода позиции в минус и перескоков
        val (minBuf, maxBuf, startBuf, rebuf) = when (bufferLevel) {
            0 -> listOf(3000, 8000, 1000, 1500)
            1 -> listOf(5000, 12000, 1200, 1800)
            2 -> listOf(8000, 18000, 1500, 2200)
            else -> listOf(12000, 25000, 1800, 2500)
        }
        val targetBytes = when (bufferLevel) {
            0 -> 16 * 1024 * 1024
            1 -> 24 * 1024 * 1024
            2 -> 32 * 1024 * 1024
            else -> 44 * 1024 * 1024
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuf, maxBuf, startBuf, rebuf)
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(0, false)
            .build()

        val extensionMode = when (decoderMode) {
            0 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            2 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        }
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(extensionMode)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(buildSmartCodecSelector(preferHevcForDv = (decoderMode != 0)))

        val initUrl = currentUrl ?: "https://vgtrkregion-reg.cdnvideo.ru"
        val activeAgent = if (CoreEngines.isSingleSessionTokenUrl(initUrl)) {
            CoreEngines.russianSmartTvAgents[0]
        } else {
            CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size]
        }
        val dsFactory = OkHttpDataSource.Factory(streamCallFactory)
            .setUserAgent(activeAgent)
            .setDefaultRequestProperties(CoreEngines.headersForStreamUrl(initUrl))
        httpDataSourceFactory = dsFactory

        val smartRetryPolicy = object : DefaultLoadErrorHandlingPolicy(3) {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val errMsg = loadErrorInfo.exception.message ?: ""
                val isManifest = (loadErrorInfo.mediaLoadData.dataType == C.DATA_TYPE_MANIFEST)
                if (errMsg.contains("Response code: 403") ||
                    errMsg.contains("Response code: 410") ||
                    errMsg.contains("SSLException") ||
                    errMsg.contains("Unable to parse TLS")
                ) {
                    return C.TIME_UNSET
                }
                if (isManifest) {
                    if (errMsg.contains("Response code: 404") || loadErrorInfo.errorCount > 2) return C.TIME_UNSET
                    return if (loadErrorInfo.errorCount == 1) 450L else 900L
                }
                // Для отдельных видео-сегментов (.ts/.m4s) разрешаем 3 быстрых повтора без перезапуска потока
                if (loadErrorInfo.errorCount > 3) return C.TIME_UNSET
                return when (loadErrorInfo.errorCount) {
                    1 -> 350L
                    2 -> 750L
                    else -> 1300L
                }
            }
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dsFactory)
            .setLoadErrorHandlingPolicy(smartRetryPolicy)

        // Стабильный выбор битрейта: предотвращает дёрганье между вариантами HLS, из-за которого музыка прыгает кусками
        val stableAdaptiveFactory = AdaptiveTrackSelection.Factory(
            25_000,
            25_000,
            25_000,
            0.75f
        )
        val trackSelector = DefaultTrackSelector(this, stableAdaptiveFactory)

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val newPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttrs, true)
            .build()

        newPlayer.volume = if (::splashOverlay.isInitialized && splashOverlay.visibility == View.VISIBLE) 0f else 1.0f
        newPlayer.trackSelectionParameters = newPlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(3840, 2160)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()

        newPlayer.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                lowSignalStuckSeconds = 0
                consecutiveDropFailures = 0
                applyAudioEnhancements()
                updateSignalIndicator()
            }
            override fun onTracksChanged(tracks: Tracks) {
                applyMedia3RememberedTracks(tracks)
                if (infoPanel.visibility == View.VISIBLE) updateStreamInfoText()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateSignalIndicator()
                val stName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                val bufMs = newPlayer.totalBufferedDuration
                if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    CoreEngines.appendDiagLog(
                        "STATE_$stName",
                        "ch='${currentChannel?.name}' bufMs=$bufMs pos=${newPlayer.currentPosition} drops=$consecutiveDropFailures"
                    )
                }
                if (playbackState == Player.STATE_ENDED) reconnectCurrentStreamSilently()
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_REMOVE) return
                val sinceSwitchMs = System.currentTimeMillis() - lastChannelSwitchMs
                if (sinceSwitchMs < 3500L) return
                val deltaMs = newPosition.positionMs - oldPosition.positionMs
                if (abs(deltaMs) >= 1200L) {
                    val reasonName = when (reason) {
                        Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
                        Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
                        Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
                        Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
                        Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
                        else -> "REASON($reason)"
                    }
                    CoreEngines.appendDiagLog(
                        "POS_JUMP",
                        "ch='${currentChannel?.name}' reason=$reasonName oldPos=${oldPosition.positionMs} newPos=${newPosition.positionMs} deltaMs=$deltaMs bufMs=${newPlayer.totalBufferedDuration}"
                    )
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                val causeMsg = error.cause?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "none"
                CoreEngines.appendDiagLog(
                    "PLAYER_ERROR",
                    "ch='${currentChannel?.name}' code=${error.errorCodeName}(${error.errorCode}) msg='${error.message}' cause='$causeMsg' bufMs=${newPlayer.totalBufferedDuration}"
                )
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    try {
                        newPlayer.seekToDefaultPosition()
                        newPlayer.prepare()
                        newPlayer.play()
                    } catch (_: Exception) {
                        reconnectCurrentStreamSilently()
                    }
                    return
                }
                val isDecoderError = (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED)
                val isBadHttpStatus = (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
                consecutiveDropFailures++
                val chNow = currentChannel
                val errUrl = chNow?.url ?: ""
                if (antiBlockEnabled && !CoreEngines.isSingleSessionTokenUrl(errUrl)) {
                    userAgentIndex = (userAgentIndex + 1) % CoreEngines.russianSmartTvAgents.size
                }
                if (isDecoderError && silentFallbackAttemptedForUrl == errUrl) {
                    streamRetriesExhausted = true
                    CoreEngines.appendDiagLog("DECODER_STOP", "Поток '${chNow?.name}' требует FFmpeg-кодек MP2/AC3 (нет AAC-зеркала)")
                    return
                }
                val mirrorsCount = chNow?.let { collectMirrorsForChannel(it).size } ?: 0
                val maxAttempts = if (isBadHttpStatus) {
                    (mirrorsCount + 1).coerceIn(2, 4)
                } else {
                    (mirrorsCount + 2).coerceIn(3, 5)
                }
                if (consecutiveDropFailures > maxAttempts) {
                    streamRetriesExhausted = true
                    uiHandler.removeCallbacks(pendingErrorReconnectRunnable)
                    CoreEngines.appendDiagLog(
                        "RECONNECT_STOP",
                        "ch='${chNow?.name}' drops=$consecutiveDropFailures code=${error.errorCodeName} — лимит попыток ($maxAttempts) исчерпан"
                    )
                    if (onDropBehavior == 1) {
                        consecutiveDropFailures = 0
                        switchChannelByOffset(+1)
                    }
                    return
                }
                val isTlsError = causeMsg.contains("SSLException", ignoreCase = true) ||
                    causeMsg.contains("Unable to parse TLS", ignoreCase = true) ||
                    causeMsg.contains("SSLHandshake", ignoreCase = true)
                val tryMirrorNow = (isDecoderError || isBadHttpStatus || isTlsError) && mirrorsCount > 0
                val delayMs = when {
                    (tryMirrorNow || isTlsError) && consecutiveDropFailures == 1 -> 350L
                    consecutiveDropFailures == 1 -> 650L
                    consecutiveDropFailures == 2 -> 1800L
                    else -> 3200L
                }
                pendingForceMirror = (isDecoderError || isBadHttpStatus || isTlsError)
                pendingPreferHttpFallback = isTlsError
                uiHandler.removeCallbacks(pendingErrorReconnectRunnable)
                uiHandler.postDelayed(pendingErrorReconnectRunnable, delayMs)
            }
        })

        newPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onLoadError(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
                error: IOException,
                wasCanceled: Boolean
            ) {
                if (!wasCanceled) {
                    CoreEngines.appendDiagLog(
                        "HTTP_LOAD_ERR",
                        "ch='${currentChannel?.name}' dataType=${mediaLoadData.dataType} trackType=${mediaLoadData.trackType} durMs=${loadEventInfo.loadDurationMs} uri='${loadEventInfo.uri}' err='${error.javaClass.simpleName}: ${error.message}'"
                    )
                }
            }
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                if (droppedFrames >= 6) {
                    CoreEngines.appendDiagLog(
                        "DROPPED_FRAMES",
                        "ch='${currentChannel?.name}' dropped=$droppedFrames in ${elapsedMs}ms bufMs=${newPlayer.totalBufferedDuration}"
                    )
                }
            }
            override fun onDownstreamFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                mediaLoadData: MediaLoadData
            ) {
                if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO) {
                    val fmt = mediaLoadData.trackFormat
                    if (fmt != null) {
                        CoreEngines.appendDiagLog(
                            "VIDEO_FORMAT",
                            "ch='${currentChannel?.name}' ${fmt.width}x${fmt.height} fps=${fmt.frameRate} mime=${fmt.sampleMimeType} br=${fmt.bitrate}"
                        )
                    }
                }
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
        val p = player ?: return
        if (p.playbackState != Player.STATE_READY || p.totalBufferedDuration < 8000L) return
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

            val epgCandidates = mutableListOf<String>()
            if (localEpgFilePath.isNotBlank() && File(localEpgFilePath).exists()) {
                epgCandidates.add("LOCAL_FILE::$localEpgFilePath")
            }
            for (u in (listOf(epgUrl) + epgUrls).distinct()) {
                if (u.isNotBlank()) epgCandidates.add(u)
            }

            for (candidateUrl in epgCandidates) {
                try {
                    val rawStream: InputStream = if (candidateUrl.startsWith("LOCAL_FILE::")) {
                        File(candidateUrl.removePrefix("LOCAL_FILE::")).inputStream()
                    } else {
                        CoreEngines.openStreamWithBypass(candidateUrl, userAgentIndex, dohEnabled, 25000)
                    }
                    val buffered = rawStream.buffered()
                    buffered.mark(4)
                    val b0 = buffered.read()
                    val b1 = buffered.read()
                    buffered.reset()
                    val isGzip = (b0 == 0x1f && b1 == 0x8b) || candidateUrl.endsWith(".gz", true)
                    val stream: InputStream = if (isGzip) GZIPInputStream(buffered) else buffered
                    val tempById = HashMap<String, MutableList<EpgProgram>>()
                    val tempIdToNames = HashMap<String, MutableList<String>>()

                    stream.use { activeStream ->
                        val parser = XmlPullParserFactory.newInstance().newPullParser()
                        parser.setInput(activeStream, "UTF-8")

                        val minTime = now - 2 * 3600_000L; val maxTime = now + 18 * 3600_000L

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
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (forceRefresh) { epgById.clear(); epgByName.clear() }
                            epgById.putAll(mergedById); epgByName.putAll(mergedByName)
                            epgLoadedCount = totalProgramsCount
                            if (centerOsdPanel.visibility == View.VISIBLE) updateCenterOsdContent()
                            if (menuHistory.lastOrNull() == "FULL_EPG") openFullScreenChannelEpg()
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

    private fun formatVideoCodecLabel(mime: String?, codecs: String?): String {
        val m = mime?.lowercase(Locale.US).orEmpty()
        val c = codecs?.lowercase(Locale.US).orEmpty()
        return when {
            m.contains("dolby-vision") || c.contains("dvhe") || c.contains("dvh1") -> "Dolby Vision"
            m.contains("hevc") || m.contains("h265") || c.contains("hevc") || c.contains("hvc1") || c.contains("hev1") -> "HEVC"
            m.contains("avc") || m.contains("h264") || c.contains("avc") || c.contains("h264") -> "H.264"
            m.contains("av01") || c.contains("av01") || c.contains("av1") -> "AV1"
            m.contains("vp9") || c.contains("vp9") -> "VP9"
            m.contains("mpeg2") || c.contains("mpgv") -> "MPEG-2"
            else -> codecs?.substringBefore('.')?.uppercase(Locale.US).orEmpty()
        }
    }

    private fun formatAudioCodecLabel(mime: String?, codecs: String?): String {
        val m = mime?.lowercase(Locale.US).orEmpty()
        val c = codecs?.lowercase(Locale.US).orEmpty()
        return when {
            m.contains("eac3-joc") -> "E-AC3 Atmos"
            m.contains("eac3") || c.contains("eac3") || c.contains("ec-3") -> "E-AC3"
            m.contains("ac3") || c.contains("a52") || c.contains("ac3") -> "AC3"
            m.contains("dts") || c.contains("dts") -> "DTS"
            m.contains("flac") || c.contains("flac") -> "FLAC"
            m.contains("opus") || c.contains("opus") -> "Opus"
            m.contains("mp4a") || m.contains("aac") || c.contains("mp4a") || c.contains("aac") -> "AAC"
            m.contains("mpeg-l2") || m.contains("mp2") -> "MP2"
            m.contains("mpeg") || c.contains("mpga") || c.contains("mp3") -> "MP3"
            else -> codecs?.substringBefore('.')?.uppercase(Locale.US).orEmpty()
        }
    }

    private fun formatAudioChannelsLabel(count: Int): String {
        return when (count) {
            1 -> "1.0"
            2 -> "2.0"
            6 -> "5.1"
            8 -> "7.1"
            in 3..16 -> "${count}ch"
            else -> ""
        }
    }

    internal fun updateStreamInfoText() {
        val ch = currentChannel
        var resText = "Анализ..."
        var codecsText = "Определение..."
        var vBitrate = "Адаптивный HLS"
        var bufText = "-"
        val decTag = when (decoderMode) { 0 -> "HW"; 2 -> "SW"; else -> "HW+FFmpeg" }
        val netTag = NetworkHelper.formatNetworkSummary()
        val engineText = if (isVlcEngineActive()) "LibVLC ($decTag)  •  $netTag" else "Media3 ($decTag)  •  $netTag"

        if (isVlcEngineActive()) {
            val vlc = vlcPlayer
            val mediaRef = runCatching { vlc?.media }.getOrNull()
            if (mediaRef != null) {
                try {
                    val count = mediaRef.trackCount
                    var vCodec = ""
                    var aCodec = ""
                    var aCh = ""
                    val activeAudioId = vlc?.getAudioTrack() ?: -1
                    var firstAudio: IMedia.AudioTrack? = null
                    var matchedAudio: IMedia.AudioTrack? = null
                    for (i in 0 until count) {
                        val tr = mediaRef.getTrack(i)
                        if (tr is IMedia.VideoTrack && vCodec.isEmpty()) {
                            val fpsVal = if (tr.frameRateDen > 0) tr.frameRateNum.toFloat() / tr.frameRateDen.toFloat() else 0f
                            val fpsStr = if (fpsVal > 1f) String.format(Locale.US, " %.0f fps", fpsVal) else ""
                            if (tr.width > 0 && tr.height > 0) resText = "${tr.width}x${tr.height}$fpsStr"
                            vCodec = formatVideoCodecLabel(null, tr.codec ?: tr.originalCodec)
                            if (tr.bitrate > 0) vBitrate = String.format(Locale.US, "%.2f Мбит/с", tr.bitrate / 1_000_000f)
                        } else if (tr is IMedia.AudioTrack) {
                            if (firstAudio == null) firstAudio = tr
                            if (tr.id == activeAudioId) matchedAudio = tr
                        }
                    }
                    val at = matchedAudio ?: firstAudio
                    if (at != null) {
                        aCodec = formatAudioCodecLabel(null, at.codec ?: at.originalCodec)
                        aCh = formatAudioChannelsLabel(at.channels)
                    }
                    val aFull = listOf(aCodec, if (aCh.isNotEmpty()) "($aCh)" else "").filter { it.isNotEmpty() }.joinToString(" ")
                    val combined = listOf(vCodec, aFull).filter { it.isNotEmpty() }.joinToString("  •  ")
                    if (combined.isNotEmpty()) codecsText = combined
                } finally {
                    runCatching { mediaRef.release() }
                }
            }
            bufText = "${vlcBufferPercent}% (${vlcNetworkCachingMs() / 1000}с)"
        } else {
            val p = player ?: return
            val vFormat = p.videoFormat
            val aFormat = p.audioFormat

            val width = vFormat?.width ?: p.videoSize.width
            val height = vFormat?.height ?: p.videoSize.height
            val fps = if (vFormat != null && vFormat.frameRate > 0) String.format(Locale.US, " %.0f fps", vFormat.frameRate) else ""
            if (width > 0 && height > 0) resText = "${width}x${height}$fps"

            val vCodec = formatVideoCodecLabel(vFormat?.sampleMimeType, vFormat?.codecs)
            val hdr = when (vFormat?.colorInfo?.colorTransfer) {
                C.COLOR_TRANSFER_ST2084 -> "HDR10"
                C.COLOR_TRANSFER_HLG -> "HLG"
                else -> ""
            }
            val vFull = listOf(vCodec, hdr).filter { it.isNotEmpty() }.joinToString(" ")
            val aCodec = formatAudioCodecLabel(aFormat?.sampleMimeType, aFormat?.codecs)
            val aCh = formatAudioChannelsLabel(aFormat?.channelCount ?: 0)
            val aFull = listOf(aCodec, if (aCh.isNotEmpty()) "($aCh)" else "").filter { it.isNotEmpty() }.joinToString(" ")
            val combined = listOf(vFull, aFull).filter { it.isNotEmpty() }.joinToString("  •  ")
            if (combined.isNotEmpty()) codecsText = combined

            if (vFormat != null && vFormat.bitrate > 0) {
                vBitrate = String.format(Locale.US, "%.2f Мбит/с", vFormat.bitrate / 1_000_000f)
            }
            bufText = String.format(Locale.US, "%.1f сек", (p.totalBufferedDuration / 1000f))
        }

        val lines = listOf(
            "Канал: " + (ch?.name ?: "-"),
            "Группа: " + (if (ch != null) effectiveCategory(ch) else "-"),
            "Разрешение: $resText",
            "Кодеки: $codecsText",
            "Поток: $vBitrate",
            "Буфер: $bufText",
            "Движок: $engineText"
        )
        for (i in infoRowViews.indices) {
            val txt = lines.getOrElse(i) { "" }
            val rv = infoRowViews[i]
            if (txt.isEmpty()) {
                rv.visibility = View.GONE
            } else {
                rv.visibility = View.VISIBLE
                rv.text = txt
            }
        }
    }

    private val regexSafeCastWords = Regex("broadcast[a-z]*", RegexOption.IGNORE_CASE)
    private val regexBracketQualityOrDup = Regex(
        "[\\(\\[]\\s*(?:\\d{1,2}|2160p?|1440p?|1080[pi]?|720p?|576p?|540p?|480p?|360p?|240p?|4k|2k|uhd|fhd|full\\s*hd|hd|хд|hq|sd|orig|ориг(?:инал)?|резерв|основн\\w*|прямой(?:\\s+cdn)?|geo[- ]?blocked|not\\s*24/7)\\s*[\\)\\]]",
        RegexOption.IGNORE_CASE
    )
    private val regexQualityToken = Regex(
        "(?<![a-zа-яё0-9])(?:2160p?|1440p?|1080[pi]?|720p?|576p?|540p?|480p?|360p?|240p?|4k|2k|uhd|fhd|full\\s*hd|fullhd|hd|хд|hq|sd|orig|оригинал|резерв)(?![a-zа-яё0-9])",
        RegexOption.IGNORE_CASE
    )
    private val regexTrailingDupNumber = Regex("\\s*[\\(\\[]\\s*\\d{1,2}\\s*[\\)\\]]\\s*$")

    private fun normalizeCastHomoglyphs(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    'с' -> 'c'
                    'а' -> 'a'
                    'е' -> 'e'
                    'о' -> 'o'
                    'т' -> 't'
                    'к' -> 'k'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    internal fun isBlockedCastChannel(name: String, url: String, group: String = "", tvgId: String = ""): Boolean {
        val combined = "$name $url $group $tvgId".lowercase(Locale.getDefault())
        if (combined.contains("catcast") || combined.contains("cat-cast") || combined.contains("cat_cast") ||
            combined.contains("каст") || combined.contains("кэст") || combined.contains("kast")
        ) {
            return true
        }
        val cleaned = regexSafeCastWords.replace(combined, " ")
        if (cleaned.contains("cast")) return true
        val homoglyphCleaned = regexSafeCastWords.replace(normalizeCastHomoglyphs(combined), " ")
        return homoglyphCleaned.contains("cast")
    }

    private fun stripCastEntriesFromM3uReader(reader: BufferedReader): BufferedReader {
        val out = StringBuilder(16384)
        out.append("#EXTM3U\n")
        var pendingInf = ""
        var pendingGrp = ""
        var line = reader.readLine()
        while (line != null) {
            val l = line.trim()
            when {
                l.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingInf = l
                    pendingGrp = ""
                }
                l.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGrp = l
                }
                l.startsWith("http://", ignoreCase = true) || l.startsWith("https://", ignoreCase = true) || l.startsWith("rtmp://", ignoreCase = true) -> {
                    if (!isBlockedCastChannel(pendingInf, l, pendingGrp, "")) {
                        if (pendingInf.isNotEmpty()) out.append(pendingInf).append('\n')
                        if (pendingGrp.isNotEmpty()) out.append(pendingGrp).append('\n')
                        out.append(l).append('\n')
                    }
                    pendingInf = ""
                    pendingGrp = ""
                }
            }
            line = reader.readLine()
        }
        return BufferedReader(StringReader(out.toString()))
    }

    private val regexSportCountryToken = Regex(
        "(?<![a-zа-яё0-9])(?:" +
            "czechia|czech|germany|deutschland|hungary|italy|italia|romania|spain|espana|españa|" +
            "united\\s+kingdom|england|britain|bulgaria|poland|polska|france|netherlands|holland|" +
            "greece|portugal|turkey|turkiye|türkiye|serbia|croatia|slovenia|slovakia|sweden|" +
            "norway|denmark|finland|austria|switzerland|belgium|ireland|ukraine|belarus|" +
            "kazakhstan|georgia|armenia|azerbaijan|estonia|latvia|lithuania|moldova|israel|" +
            "usa|canada|australia|brazil|mexico|argentina|india|arabia|arabic|mena|europe|" +
            "international|intl|asia|global|latam|baltic|nordic|balkans|cis|" +
            "uk|de|pl|it|es|fr|ro|hu|bg|cz|sk|tr|nl|se|no|dk|fi|gr|pt|at|ch|be|ie|ua|by|kz|en|rs|hr|eu|" +
            "чехия|германия|венгрия|италия|румыния|испания|болгария|польша|франция|турция|сербия|хорватия|" +
            "греция|португалия|швеция|норвегия|дания|финляндия|австрия|бельгия|украина|беларусь|казахстан|европа" +
            ")(?![a-zа-яё0-9])",
        RegexOption.IGNORE_CASE
    )

    private val politicalKeywordsOther = listOf(
        "дума", "госдума", "совет федерации", "вместе-рф", "вместе рф", "парламент",
        "красная линия", "кпрф", "лдпр", "единая россия", "справедливая", "полит",
        "соловьев", "соловьёв", "царьград", "день тв", "россия 24", "вести", "известия",
        "russia today", "rt ", "rt-", "sputnik", "спутник", "звезда", "рада", "свобода",
        "настоящее время", "дождь", "belarus 24", "беларусь 24", "euronews", "евроньюс",
        "bbc", "cnn", "al jazeera", "france 24", "deutsche welle", "bloomberg", "cnbc",
        "fox news", "sky news", "новости", "news", "инфо 24", "инфоканал", "мир 24",
        "рбк", "отр", "партия", "выборы", "кремль", "правительство", "президент", "агит"
    )

    private val regionalKeywordsOther = listOf(
        "область", "обл.", " край", "республика", "респ.", "автоном", "округ",
        "гтрк", "чгтрк", "нвк", "бст", "тнв", "отв", "губерния", "регион", "местн",
        "городск", "муниципал", "телерадиокомпания", "трк ", "ктв", "якт",
        "москва", "подмосков", "петербург", "лентв", "питер", "воронеж", "белгород",
        "курган", "астрахан", "башкортостан", "уфа", "татарстан", "казан", "челны",
        "самара", "тольятти", "саратов", "волгоград", "ростов", "дон 24", "краснодар",
        "кубань", "сочи", "ставропол", "крым", "севастопол", "екатеринбург", "свердлов",
        "урал", "челябинск", "тюмень", "югра", "ямал", "янао", "хмао", "сургут",
        "омск", "новосибирск", "томск", "кемерово", "кузбасс", "барнаул", "алтай",
        "красноярск", "енисей", "иркутск", "байкал", "бурятия", "чита", "забайкал",
        "якутия", "саха", "хабаровск", "примор", "владивосток", "сахалин", "камчат",
        "магадан", "чукот", "перм", "киров", "ижевск", "удмурт", "оренбург", "пенза",
        "ульяновск", "чуваш", "чебоксар", "марий", "йошкар", "мордови", "саранск",
        "нижний новгород", "нижегород", "владимир", "иваново", "ярославл", "костром",
        "тверь", "тверск", "смоленск", "калуга", "тула", "рязань", "тамбов", "липецк",
        "орел", "орёл", "курск", "брянск", "псков", "новгород", "вологда", "вологод",
        "череповец", "архангельск", "мурманск", "карел", "петрозаводск", "коми",
        "сыктывкар", "калининград", "кавказ", "дагестан", "чечня", "грозный", "ингуш",
        "осетия", "владикавказ", "кабардин", "нальчик", "карачаев", "черкес", "адыгея",
        "калмык", "тыва", "хакасия", "абакан", "приоскол", "белогор", "катунь",
        "шадринск", "магнитогорск", "норильск", "ноябрьск", "нижневартовск", "нефтеюганск",
        "армавир", "анапа", "новороссийск", "таганрог", "шахты", "волжский", "энгельс",
        "сызрань", "димитровград", "дзержинск", "рыбинск", "обнинск", "подольск",
        "химки", "балашиха", "королев", "мытищи", "люберцы", "одинцово", "серпухов",
        "коломна", "долгопрудный", "красногорск", "раменск", "щелково", "пушкино",
        "ногинск", "сергиев", "дмитров", "клин", "дубна", "сигма"
    )

    internal fun isFilteredOtherChannel(ch: Channel): Boolean {
        if (ch.category != "Прочее") return false
        val n = ch.name.lowercase(Locale.getDefault())
        val u = ch.url.substringBefore("#").lowercase(Locale.getDefault())
        if (n.contains("|")) return true
        for (kw in politicalKeywordsOther) {
            if (n.contains(kw)) return true
        }
        for (kw in regionalKeywordsOther) {
            if (n.contains(kw) || u.contains(kw)) return true
        }
        return false
    }

    internal fun canonicalDedupKey(rawName: String, category: String = ""): String {
        val low = rawName.lowercase(Locale.getDefault()).trim()
        val noBrackets = regexBracketQualityOrDup.replace(low, " ")
        val noQuality = regexQualityToken.replace(noBrackets, " ")
        val cleaned = if (category == "Спорт") regexSportCountryToken.replace(noQuality, " ") else noQuality
        val canon = CoreEngines.normalizeName(cleaned)
        return canon.ifEmpty { CoreEngines.normalizeName(rawName) }
    }

    internal fun estimateChannelQualityScore(ch: Channel, isCurated: Boolean): Int {
        val n = ch.name.lowercase(Locale.getDefault())
        val u = ch.url.substringBefore("#").lowercase(Locale.getDefault())
        var score = 0

        score += when {
            n.contains("4k") || n.contains("uhd") || n.contains("2160") || u.contains("2160") || u.contains("uhd") || u.contains("4k") -> 500
            n.contains("2k") || n.contains("1440") || u.contains("1440") -> 420
            n.contains("1080") || n.contains("fhd") || n.contains("full hd") || n.contains("fullhd") || u.contains("1080") || u.contains("fhd") -> 340
            n.contains("hd") || n.contains("хд") || n.contains("720") || u.contains("hdhigh") || u.contains("_hd") || u.contains("/hd") || u.contains("720") -> 220
            n.contains("hq") || n.contains("576") || n.contains("540") -> 120
            n.contains("480") || u.contains("sdhigh") -> 60
            n.contains("360") || n.contains("240") || u.contains("_low") || u.contains("low/") || n.endsWith(" sd") -> -40
            else -> 100
        }

        // Для «Спорт»: отдаём приоритет русским / нейтральным версиям (например Eurosport 1 HD / Eurosport 2 1080p)
        // перед локализованными версиями по странам (Czechia, Hungary, Romania, Spain, Italy и т.д.)
        if (ch.category == "Спорт") {
            val hasCountryTag = regexSportCountryToken.containsMatchIn(n)
            val isNeutralOrRu = !hasCountryTag || n.contains("рус") || n.contains("russia") || n.contains(" ru")
            val isEnglish = n.contains(" uk") || n.contains("(uk)") || n.contains("international") || n.contains(" en")
            score += when {
                isNeutralOrRu -> 180
                isEnglish -> 65
                else -> -45
            }
        }

        if (u.contains(".m3u8") || u.contains("hls")) score += 35
        if (u.startsWith("https://")) score += 10
        if (isCurated) score += 25
        if (regexTrailingDupNumber.containsMatchIn(n)) score -= 15

        // Штраф за часто отдающие 403/ошибки хосты, чтобы при наличии рабочего зеркала побеждало оно
        if (u.contains("176.118.197.101") || u.contains("31.210.208.171") || u.contains("bonus-tv.ru") || u.contains("stitch.teletarget.ru")) {
            score -= 80
        }
        return score
    }

    private fun cleanDisplayDupSuffix(ch: Channel): Channel {
        val trimmed = regexTrailingDupNumber.replace(ch.name, "").trim()
        return if (trimmed.isNotEmpty() && trimmed != ch.name) {
            Channel(trimmed, ch.url, ch.category, ch.tvgId)
        } else {
            ch
        }
    }

    internal fun deduplicateBuiltinChannelsByQuality(candidates: List<Pair<Channel, Boolean>>): List<Channel> {
        data class Winner(val channel: Channel, val score: Int, val order: Int)
        val bestByKey = LinkedHashMap<String, Winner>()
        val seenUrls = HashSet<String>()
        val mirrorScoreList = HashMap<String, MutableList<Pair<String, Int>>>()
        var orderCounter = 0

        for ((rawCh, isCurated) in candidates) {
            if (isBlockedCastChannel(rawCh.name, rawCh.url, rawCh.category, rawCh.tvgId)) continue
            if (isFilteredOtherChannel(rawCh)) continue
            val cleanMirrorUrl = rawCh.url.substringBefore("#").trim()
            val cleanUrlKey = cleanMirrorUrl.lowercase(Locale.getDefault())
            if (cleanUrlKey.isEmpty() || !seenUrls.add(cleanUrlKey)) continue

            val canonKey = canonicalDedupKey(rawCh.name, rawCh.category)
            val normKey = CoreEngines.normalizeName(rawCh.name)
            val score = estimateChannelQualityScore(rawCh, isCurated)

            if (canonKey.isNotEmpty()) {
                mirrorScoreList.getOrPut(canonKey) { mutableListOf() }.add(Pair(cleanMirrorUrl, score))
            }
            if (normKey.isNotEmpty() && normKey != canonKey) {
                mirrorScoreList.getOrPut(normKey) { mutableListOf() }.add(Pair(cleanMirrorUrl, score))
            }

            val dedupKey = "${canonKey}_${rawCh.category}"
            val cleanCh = cleanDisplayDupSuffix(rawCh)
            val existing = bestByKey[dedupKey]
            if (existing == null) {
                bestByKey[dedupKey] = Winner(cleanCh, score, orderCounter++)
            } else if (score > existing.score) {
                bestByKey[dedupKey] = Winner(cleanCh, score, existing.order)
            }
        }

        // Сохраняем отсортированные по качеству резервные зеркала (лучшие первыми, до 8 на канал)
        for ((k, scoredUrls) in mirrorScoreList) {
            val sortedUrls = scoredUrls.sortedByDescending { it.second }.map { it.first }.distinct().take(8)
            val targetMirrors = backupMirrorsMap.getOrPut(k) { CopyOnWriteArrayList() }
            for (u in sortedUrls) {
                if (!targetMirrors.contains(u) && targetMirrors.size < 8) {
                    targetMirrors.add(u)
                }
            }
        }

        return bestByKey.values.sortedBy { it.order }.map { it.channel }
    }

    internal fun ensureDefaultPlaylists() {
        playlists.removeAll { it.source == "BUILTIN_VIP_PROVIDER" || it.source == "BUILTIN_STRICT_RU_ONLY" }
        val mainIdx = playlists.indexOfFirst { it.source == "BUILTIN_OPEN_RU" }
        if (mainIdx == -1) {
            playlists.add(0, PlaylistEntry("Основной", "BUILTIN_OPEN_RU", false))
        } else if (playlists[mainIdx].title != "Основной") {
            playlists[mainIdx] = PlaylistEntry("Основной", "BUILTIN_OPEN_RU", false)
        }
        if (activePlaylistIndex !in playlists.indices) activePlaylistIndex = 0
    }

    internal fun isChannelActive(ch: Channel): Boolean {
        if (manualDisabledUrls.contains(ch.url)) return false
        if (disabledCategories.contains(effectiveCategory(ch))) return false
        if (autoDisabledUrls.contains(ch.url)) return false
        val activeSrc = playlists.getOrNull(activePlaylistIndex)?.source
        if ((activeSrc == "BUILTIN_OPEN_RU" || activeSrc == "BUILTIN_STRICT_RU_ONLY") &&
            (isBlockedCastChannel(ch.name, ch.url, ch.category, ch.tvgId) || isFilteredOtherChannel(ch))
        ) {
            return false
        }
        return true
    }

    internal fun loadActivePlaylist(autoPlayLast: Boolean) {
        ensureDefaultPlaylists()
        val entry = playlists[activePlaylistIndex]
        val myGen = ++playlistLoadGeneration
        val isBuiltin = entry.source == "BUILTIN_OPEN_RU" || entry.source == "BUILTIN_STRICT_RU_ONLY"
        val strictRuOnly = entry.source == "BUILTIN_STRICT_RU_ONLY"
        val rawStartupUrl = if (autoPlayLast) lastSavedChannelUrl else ""
        val requestedStartupUrl = if (isBuiltin && (rawStartupUrl.contains("8fupzrusy2skcm", ignoreCase = true) || rawStartupUrl.contains("rostelekom.xyz", ignoreCase = true))) "" else rawStartupUrl

        if (isBuiltin) {
            for ((normKey, mirrorUrl) in BuiltinCatalog.origBackupPairs) {
                if (isBlockedCastChannel("", mirrorUrl, "")) continue
                val list = backupMirrorsMap.getOrPut(normKey) { CopyOnWriteArrayList() }
                if (!list.contains(mirrorUrl)) list.add(mirrorUrl)
            }
            val curatedRaw = mutableListOf<Pair<Channel, Boolean>>()
            for (extra in BuiltinCatalog.extraOpenStreams) {
                if (CoreEngines.isArabicOrIndianChannel(extra.name, extra.category, extra.url)) continue
                if (strictRuOnly && !CoreEngines.isStrictlyRussianChannel(extra.name, extra.url, extra.category)) continue
                val cleanTitle = CoreEngines.cleanChannelDisplayName(extra.name)
                val cat = customCategoryMap[extra.url] ?: extra.category
                if (isBlockedCastChannel(cleanTitle, extra.url, cat, extra.tvgId)) continue
                curatedRaw.add(Pair(Channel(cleanTitle, extra.url, cat, extra.tvgId), true))
            }

            // Используем свежий кэш v19, но при первом запуске подстраховываемся старым кэшем до успешной загрузки сети
            val cacheFile = File(filesDir, "open_catalog_cache_v19.m3u")
            val fallbackReadCache = listOf(
                cacheFile,
                File(filesDir, "open_catalog_cache_v18.m3u"),
                File(filesDir, "open_catalog_cache_v17.m3u"),
                File(filesDir, "open_catalog_cache_v16.m3u")
            ).firstOrNull { it.exists() && it.length() > 100L }
            val startupCandidates = ArrayList<Pair<Channel, Boolean>>(curatedRaw)
            var cachedRawCount = 0
            if (fallbackReadCache != null) {
                try {
                    val cachedMap = LinkedHashMap<String, Channel>()
                    fallbackReadCache.bufferedReader().use { rawBr ->
                        stripCastEntriesFromM3uReader(rawBr).use { br ->
                            CoreEngines.parseM3uReaderIntoMap(
                                reader = br,
                                targetMap = cachedMap,
                                customCategoryMap = customCategoryMap,
                                filterBuiltinRules = true,
                                onlyRussianAllCategories = strictRuOnly,
                                keepOriginalGroups = false
                            )
                        }
                    }
                    for (ch in cachedMap.values) {
                        if (isBlockedCastChannel(ch.name, ch.url, ch.category, ch.tvgId)) continue
                        startupCandidates.add(Pair(ch, false))
                        cachedRawCount++
                    }
                    CoreEngines.appendDiagLog("CACHE_LOAD", "Из кэша v19 загружено кандидатов: $cachedRawCount")
                } catch (_: Exception) {}
            }

            val startupList = deduplicateBuiltinChannelsByQuality(startupCandidates).toMutableList()

            if (requestedStartupUrl.isNotEmpty() && !isBlockedCastChannel(lastSavedChannelName, requestedStartupUrl, lastSavedChannelGroup) &&
                startupList.none { it.url == requestedStartupUrl }
            ) {
                val restoredName = lastSavedChannelName.ifEmpty { "Канал" }
                val restoredCat = customCategoryMap[requestedStartupUrl] ?: lastSavedChannelGroup.ifEmpty { "Новости и общие" }
                if (!manualDisabledUrls.contains(requestedStartupUrl)) {
                    startupList.add(0, Channel(restoredName, requestedStartupUrl, restoredCat, ""))
                }
            }
            allChannels.clear()
            allChannels.addAll(startupList)
            dynamicCategories.clear()
            updateCategoriesList()
            val currentList = allChannels.filter { isChannelActive(it) }
            val cats = activeCategoryList()
            val matchedInBuiltin = if (requestedStartupUrl.isNotEmpty()) {
                currentList.firstOrNull { it.url == requestedStartupUrl }
                    ?: if (lastSavedChannelName.isNotEmpty()) {
                        val canonSaved = canonicalDedupKey(lastSavedChannelName)
                        currentList.firstOrNull { canonicalDedupKey(it.name) == canonSaved }
                    } else null
            } else null

            if (currentList.isNotEmpty()) {
                val targetCh = matchedInBuiltin ?: currentList[0]
                selectCategory(cats.indexOf(effectiveCategory(targetCh)).takeIf { it >= 2 } ?: 1)
                openChannelsDirectlyWithCurrentFocus = true
                playChannelObj(targetCh, immediate = true, updateSavedUrl = (matchedInBuiltin != null || requestedStartupUrl.isEmpty()))
            } else {
                selectCategory(1)
            }

            thread(priority = Thread.MIN_PRIORITY) {
                try { Thread.sleep(350L) } catch (_: Exception) {}
                if (myGen != playlistLoadGeneration) return@thread
                val perSourceResults = arrayOfNulls<List<Channel>>(openRusSources.size)
                val loaderPool = Executors.newFixedThreadPool(6)
                for (srcIdx in openRusSources.indices) {
                    val srcUrl = openRusSources[srcIdx]
                    loaderPool.submit {
                        if (myGen != playlistLoadGeneration) return@submit
                        val shortName = srcUrl.substringAfter("://").take(44)
                        try {
                            val localMap = LinkedHashMap<String, Channel>()
                            CoreEngines.openStreamWithBypass(srcUrl, userAgentIndex, dohEnabled, 7500).bufferedReader().use { rawReader ->
                                stripCastEntriesFromM3uReader(rawReader).use { reader ->
                                    CoreEngines.parseM3uReaderIntoMap(
                                        reader = reader,
                                        targetMap = localMap,
                                        customCategoryMap = customCategoryMap,
                                        filterBuiltinRules = true,
                                        onlyRussianAllCategories = strictRuOnly,
                                        keepOriginalGroups = false
                                    )
                                }
                            }
                            perSourceResults[srcIdx] = localMap.values.toList()
                            CoreEngines.appendDiagLog("SRC_LOAD", "OK $shortName -> ${localMap.size} кан.")
                        } catch (e: Exception) {
                            CoreEngines.appendDiagLog("SRC_LOAD", "FAIL $shortName -> ${e.javaClass.simpleName}: ${e.message}")
                        }
                    }
                }
                loaderPool.shutdown()
                try { loaderPool.awaitTermination(24, TimeUnit.SECONDS) } catch (_: Exception) {}
                loaderPool.shutdownNow()
                if (myGen != playlistLoadGeneration) return@thread

                val allFetchedCandidates = ArrayList<Pair<Channel, Boolean>>(curatedRaw)
                var fetchedTotal = 0
                var castBlockedTotal = 0

                for (list in perSourceResults) {
                    if (list == null) continue
                    for (ch in list) {
                        fetchedTotal++
                        if (isBlockedCastChannel(ch.name, ch.url, ch.category, ch.tvgId)) {
                            castBlockedTotal++
                            continue
                        }
                        allFetchedCandidates.add(Pair(ch, false))
                    }
                }

                if (fetchedTotal == 0) return@thread
                val freshCombined = deduplicateBuiltinChannelsByQuality(allFetchedCandidates)
                val cacheChannels = freshCombined.take(2500)

                CoreEngines.appendDiagLog(
                    "OPEN_CATALOG",
                    "Загружено: $fetchedTotal потоков (cast скрыто: $castBlockedTotal) -> без дублей (лучшее качество): ${freshCombined.size}"
                )

                // Сохраняем свежий очищенный каталог в локальный кэш v17
                try {
                    val tmp = File(filesDir, "open_catalog_cache_v19.tmp")
                    tmp.bufferedWriter().use { bw ->
                        bw.write("#EXTM3U\n")
                        for (c in cacheChannels) {
                            val safeGrp = c.category.replace("\"", "")
                            val safeName = c.name.replace("\n", " ").replace("\r", " ")
                            bw.write("#EXTINF:-1 group-title=\"$safeGrp\",$safeName\n")
                            bw.write("${c.url}\n")
                        }
                    }
                    if (tmp.renameTo(cacheFile)) {
                        try { File(filesDir, "open_catalog_cache_v16.m3u").delete() } catch (_: Exception) {}
                        try { File(filesDir, "open_catalog_cache_v17.m3u").delete() } catch (_: Exception) {}
                        try { File(filesDir, "open_catalog_cache_v18.m3u").delete() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}

                runOnUiThread {
                    if (isFinishing || isDestroyed || myGen != playlistLoadGeneration) return@runOnUiThread
                    allChannels.clear()
                    allChannels.addAll(freshCombined)
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
                    // Всегда обновляем текущий список каналов/категорий, даже если боковое меню сейчас открыто
                    selectCategory(currentCategoryIndex)
                    if (sidePanel.visibility == View.VISIBLE) {
                        fitSideListsExactRows()
                        if (isSideShowingChannels) {
                            refreshChannelAdapter()
                        } else {
                            refreshSimpleListHighlightInPlace(categoryListView, focusedCategoryPos)
                        }
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
                reader.use { br ->
                    CoreEngines.parseM3uReaderIntoMap(
                        reader = br,
                        targetMap = targetMap,
                        customCategoryMap = customCategoryMap,
                        filterBuiltinRules = false,
                        onlyRussianAllCategories = false,
                        keepOriginalGroups = true
                    )
                }
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
            } catch (e: Exception) {
                CoreEngines.appendDiagLog("PLAYLIST_ERR", "Ошибка чтения '${entry.title}': ${e.message}")
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this@MainActivity, "Не удалось прочитать плейлист: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
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
            firstChar in 'а'..'я' || firstChar in 'А'..'Я' ||
                firstChar == 'ё' || firstChar == 'Ё' ||
                firstChar in 'А'..'я' || firstChar in listOf('і', 'І', 'ї', 'Ї', 'є', 'Є', 'ў', 'Ў') -> 0
            firstChar in 'a'..'z' || firstChar in 'A'..'Z' || firstChar.isLetter() -> 1
            firstChar.isDigit() -> 2
            else -> 3
        }
    }

    private val safeChannelComparator: Comparator<Channel> = Comparator { a, b ->
        val gCmp = charAlphabetGroup(a.name).compareTo(charAlphabetGroup(b.name))
        if (gCmp != 0) return@Comparator gCmp
        val keyA = a.name.trimStart { !it.isLetterOrDigit() }.ifEmpty { a.name.trim() }
        val keyB = b.name.trimStart { !it.isLetterOrDigit() }.ifEmpty { b.name.trim() }
        val colCmp = synchronized(ruCollator) { ruCollator.compare(keyA, keyB) }
        if (colCmp != 0) return@Comparator colCmp
        val lowCmp = keyA.lowercase(Locale.ROOT).compareTo(keyB.lowercase(Locale.ROOT))
        if (lowCmp != 0) return@Comparator lowCmp
        a.url.compareTo(b.url)
    }

    internal fun selectCategory(index: Int) {
        try {
            val cats = activeCategoryList()
            if (index !in cats.indices) return
            if (index != topBrowseAnchorCategoryIndex) clearTopBrowseAnchor()
            currentCategoryIndex = index; focusedCategoryPos = index
            val catName = cats[index]
            val activeAll = allChannels.filter { isChannelActive(it) }
            val rawFiltered = when (catName) {
                "★ Избранное" -> activeAll.filter { favoriteUrls.contains(it.url) }
                "Все каналы" -> activeAll
                else -> activeAll.filter { effectiveCategory(it) == catName }
            }
            // Единая сортировка для всех плейлистов (встроенного и пользовательских):
            // ТОП сверху, далее по алфавиту: русские (А–Я) -> иностранные (A–Z) -> цифры (0–9)
            val topPart = rawFiltered.filter { topUrls.contains(it.url) }.sortedWith(safeChannelComparator)
            val regularPart = rawFiltered.filter { !topUrls.contains(it.url) }.sortedWith(safeChannelComparator)

            displayedChannels.clear()
            displayedChannels.addAll(topPart)
            displayedChannels.addAll(regularPart)

            val activePosInGroup = findActiveChannelIndex(displayedChannels, currentChannel, focusedChannelPos)
            focusedChannelPos = if (activePosInGroup >= 0) activePosInGroup else 0
            refreshChannelAdapter()
        } catch (_: Exception) {}
    }

    internal fun refreshChannelAdapter() {
        if (!::channelListAdapter.isInitialized) return
        try {
            val list = displayedChannels.toList()
            val activePlayIdx = findActiveChannelIndex(list, currentChannel, focusedChannelPos)
            val newItems = if (list.isEmpty()) {
                listOf("[-]   В этой категории пока нет каналов")
            } else {
                list.mapIndexed { idx, ch ->
                    val mono = extractTwoLetters(ch.name)
                    val isPlaying = (idx == activePlayIdx)
                    val playOrb = if (isPlaying) "[+] " else ""
                    val topBadge = if (topUrls.contains(ch.url)) "▲ " else ""
                    val favBadge = if (favoriteUrls.contains(ch.url)) "★ " else ""
                    "[B:$mono] $playOrb$topBadge$favBadge${ch.name}"
                }
            }
            focusedChannelPos = focusedChannelPos.coerceIn(0, (newItems.size - 1).coerceAtLeast(0))
            channelDisplayItems.clear()
            channelDisplayItems.addAll(newItems)
            channelListAdapter.notifyDataSetChanged()
            if (sidePanel.visibility == View.VISIBLE) {
                channelListView.post {
                    refreshSimpleListHighlightInPlace(channelListView, if (isSideShowingChannels) focusedChannelPos else -1)
                }
            }
        } catch (_: Exception) {}
    }

    internal fun toggleTopChannel(ch: Channel) {
        val wasTop = topUrls.contains(ch.url)
        if (!wasTop) {
            val snapshot = displayedChannels.toList()
            val oldIdx = findActiveChannelIndex(snapshot, ch, focusedChannelPos)
            if (oldIdx >= 0 && snapshot.size > 1) {
                val prevCh = snapshot[(oldIdx - 1 + snapshot.size) % snapshot.size]
                val nextCh = snapshot[(oldIdx + 1) % snapshot.size]
                topBrowseAnchorUrl = ch.url
                topBrowseAnchorName = ch.name
                topBrowseAnchorPrevUrl = prevCh.url
                topBrowseAnchorPrevName = prevCh.name
                topBrowseAnchorNextUrl = nextCh.url
                topBrowseAnchorNextName = nextCh.name
                topBrowseAnchorCategoryIndex = currentCategoryIndex
                topBrowseAnchorFallbackIndex = oldIdx
            } else {
                clearTopBrowseAnchor()
            }
            topUrls.add(ch.url)
        } else {
            topUrls.remove(ch.url)
            if (ch.url == topBrowseAnchorUrl) clearTopBrowseAnchor()
        }
        saveState(); selectCategory(currentCategoryIndex)
    }

    internal fun toggleFavorite(ch: Channel) {
        if (!favoriteUrls.remove(ch.url)) favoriteUrls.add(ch.url)
        saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex)
    }

    internal val executeDebouncedPlayRunnable = Runnable {
        val target = pendingDebouncedChannel ?: return@Runnable
        try {
            uiHandler.removeCallbacks(pendingErrorReconnectRunnable)
            lastChannelSwitchMs = System.currentTimeMillis()
            CoreEngines.appendDiagLog(
                "PLAY_CHANNEL",
                "name='${target.name}' group='${effectiveCategory(target)}' bufLvl=$bufferLevel dec=$decoderMode url='${target.url.substringBefore("#")}'"
            )
            media3AppliedSavedTracks = false
            if (isVlcEngineActive()) {
                playChannelInVlc(target.url)
            } else {
                val p = player
                if (p != null) {
                    if (p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)) {
                        p.trackSelectionParameters = p.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .build()
                    }
                    p.stop()
                    p.clearMediaItems()
                    applyHeadersToDataSourceForUrl(target.url)
                    p.setMediaItem(createLiveMediaItem(target.url), true)
                    p.prepare()
                    p.play()
                }
            }
        } catch (_: Exception) {}
        uiHandler.removeCallbacks(preloadNeighborsRunnable)
        if (!isAnalyzerRunning) uiHandler.postDelayed(preloadNeighborsRunnable, 8000L)
    }

    internal fun playChannelObj(channel: Channel, immediate: Boolean = false, silentSwap: Boolean = false, updateSavedUrl: Boolean = true) {
        val prevPlayingUrl = currentChannel?.url
        if (!silentSwap && currentChannel != null && prevPlayingUrl != channel.url) {
            previousChannel = currentChannel
        }
        if (topBrowseAnchorUrl != null && (channel.url != topBrowseAnchorUrl || channel.name != topBrowseAnchorName)) {
            clearTopBrowseAnchor()
        }
        currentChannel = channel
        pendingDebouncedChannel = channel
        if (updateSavedUrl) {
            lastSavedChannelUrl = channel.url
            lastSavedChannelName = channel.name
            lastSavedChannelGroup = effectiveCategory(channel)
            saveLastChannelOnly(syncCommit = true)
        }
        silentFallbackAttemptedForUrl = ""
        mirrorCycleIndex = 0
        lowSignalStuckSeconds = 0
        continuousBufferingTicks = 0
        consecutiveDropFailures = 0
        streamRetriesExhausted = false

        refreshChannelAdapter()

        uiHandler.removeCallbacks(pendingErrorReconnectRunnable)
        lastChannelSwitchMs = System.currentTimeMillis()
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
        if (menuHistory.lastOrNull() == "FULL_EPG") openFullScreenChannelEpg()
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
            .putString("last_channel_name", lastSavedChannelName)
            .putString("last_channel_group", lastSavedChannelGroup)
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
            .putInt("buffer_level_v34", bufferLevel)
            .putInt("decoder_mode", decoderMode)
            .putInt("theme_index", themeIndex.coerceIn(0, themes.lastIndex))
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
            .putBoolean("dpi_bypass_v38", dpiBypassEnabled)
            .putString("net_proxy_url_v38", networkProxyUrl)
            .putInt("player_engine_v38", playerEngineMode)
            .putString("pref_audio_lang_v38", preferredAudioLang)
            .putBoolean("subtitles_enabled_v38", subtitlesEnabled)
            .putBoolean("edge_gestures_v38", touchEdgeGesturesEnabled)
            .putInt("on_drop_v23", onDropBehavior)
            .putInt("menu_hide_sec_v23", menuHideDelaySec)
            .putBoolean("show_banner_v32", showChannelBannerOnSwitch)
            .putBoolean("auto_check_updates_v37", autoCheckUpdatesEnabled)
            .putBoolean("anti_block_ru", antiBlockEnabled)
            .putInt("ua_index", userAgentIndex)
            .putString("epg_url", epgUrl)
            .putString("local_epg_path", localEpgFilePath)
            .putString("last_channel_url", lastSavedChannelUrl)
            .putString("last_channel_name", lastSavedChannelName)
            .putString("last_channel_group", lastSavedChannelGroup)
            .putStringSet("favorites", favoriteUrls.toSet())
            .putStringSet("top_urls", topUrls.toSet())
            .putStringSet("manual_disabled_urls_v19", manualDisabledUrls.toSet())
            .putStringSet("auto_disabled_urls_v33", autoDisabledUrls.toSet())
            .putStringSet("disabled_cats", disabledCategories.toSet())
        if (syncCommit) editor.commit() else editor.apply()
    }

    internal fun loadSavedState() {
        val prefs = getSharedPreferences("iptv_v4_prefs", Context.MODE_PRIVATE)
        bufferLevel = prefs.getInt("buffer_level_v34", 1)
        decoderMode = prefs.getInt("decoder_mode", 1)
        themeIndex = prefs.getInt("theme_index", 0).coerceIn(0, themes.lastIndex)
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
        dpiBypassEnabled = prefs.getBoolean("dpi_bypass_v38", true)
        networkProxyUrl = prefs.getString("net_proxy_url_v38", "")?.trim().orEmpty()
        playerEngineMode = prefs.getInt("player_engine_v38", 0).coerceIn(0, 1)
        preferredAudioLang = prefs.getString("pref_audio_lang_v38", "rus")?.trim().orEmpty().ifBlank { "rus" }
        subtitlesEnabled = prefs.getBoolean("subtitles_enabled_v38", false)
        touchEdgeGesturesEnabled = prefs.getBoolean("edge_gestures_v38", false)
        onDropBehavior = prefs.getInt("on_drop_v23", 0)
        menuHideDelaySec = prefs.getInt("menu_hide_sec_v23", 8)
        showChannelBannerOnSwitch = prefs.getBoolean("show_banner_v32", true)
        autoCheckUpdatesEnabled = prefs.getBoolean("auto_check_updates_v37", true)
        antiBlockEnabled = prefs.getBoolean("anti_block_ru", true)
        userAgentIndex = prefs.getInt("ua_index", 0)
        activePlaylistIndex = prefs.getInt("active_playlist", 0)
        epgUrl = prefs.getString("epg_url", epgUrl) ?: epgUrl
        localEpgFilePath = prefs.getString("local_epg_path", "") ?: ""
        lastSavedChannelUrl = prefs.getString("last_channel_url", "") ?: ""
        lastSavedChannelName = prefs.getString("last_channel_name", "") ?: ""
        lastSavedChannelGroup = prefs.getString("last_channel_group", "") ?: ""
        if (lastSavedChannelUrl.contains("sc.id-tv.kz") || lastSavedChannelUrl.contains("ott-cdn.ucom.am")) {
            lastSavedChannelUrl = ""
            lastSavedChannelName = ""
            lastSavedChannelGroup = ""
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
                    if (src != "BUILTIN_VIP_PROVIDER" && src != "BUILTIN_STRICT_RU_ONLY") playlists.add(PlaylistEntry(if (src == "BUILTIN_OPEN_RU") "Основной" else o.getString("title"), src, o.getBoolean("isLocal")))
                }
            } catch (_: Exception) {}
        }
        ensureDefaultPlaylists()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastSwipeLeftFromIdleMs = 0L
            if (menuHistory.isNotEmpty()) resetMenuInactivityTimer()
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showEdgeGestureHud(message: String) {
        if (!::gestureHudView.isInitialized) return
        uiHandler.removeCallbacks(hideGestureHudRunnable)
        gestureHudView.animate().cancel()
        gestureHudView.text = message
        gestureHudView.alpha = 1f
        gestureHudView.visibility = View.VISIBLE
        uiHandler.postDelayed(hideGestureHudRunnable, 1100L)
    }

    private fun handleOptionalEdgeTouchGesture(ev: MotionEvent): Boolean {
        if (!touchEdgeGesturesEnabled || menuHistory.isNotEmpty() ||
            (::firstRunModeOverlay.isInitialized && firstRunModeOverlay.visibility == View.VISIBLE) ||
            (::splashOverlay.isInitialized && splashOverlay.visibility == View.VISIBLE)
        ) {
            edgeGestureActiveMode = 0
            return false
        }
        val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1).toFloat()
        val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1).toFloat()
        val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val edgeGuard = dpPx(28f).toFloat()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeGestureActiveMode = 0
                edgeGestureStartX = ev.rawX
                edgeGestureStartY = ev.rawY
                val curB = window.attributes.screenBrightness
                edgeGestureStartBrightness = if (curB > 0f) curB.coerceIn(0.05f, 1f) else (brightnessPercent / 100f).coerceIn(0.2f, 1f)
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                edgeGestureMaxVolume = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
                edgeGestureStartVolume = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - edgeGestureStartX
                val dy = ev.rawY - edgeGestureStartY
                if (edgeGestureActiveMode == 0) {
                    val inSafeY = edgeGestureStartY > edgeGuard && edgeGestureStartY < (screenH - edgeGuard)
                    val inLeftZone = edgeGestureStartX < screenW * 0.25f
                    val inRightZone = edgeGestureStartX > screenW * 0.75f
                    if (inSafeY && (inLeftZone || inRightZone) && abs(dy) > slop * 1.4f && abs(dy) > abs(dx) * 1.4f) {
                        edgeGestureActiveMode = if (inLeftZone) 1 else 2
                    }
                }
                if (edgeGestureActiveMode == 1) {
                    val newB = (edgeGestureStartBrightness - dy / (screenH * 0.65f)).coerceIn(0.05f, 1.0f)
                    val lp = window.attributes
                    lp.screenBrightness = newB
                    window.attributes = lp
                    val pct = (newB * 100f).toInt().coerceIn(5, 100)
                    showEdgeGestureHud("☀ Яркость: $pct%")
                    return true
                } else if (edgeGestureActiveMode == 2) {
                    val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val deltaSteps = (-dy / (screenH * 0.65f)) * edgeGestureMaxVolume
                    val targetVol = (edgeGestureStartVolume + deltaSteps).toInt().coerceIn(0, edgeGestureMaxVolume)
                    runCatching { am?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0) }
                    val pct = (targetVol * 100) / edgeGestureMaxVolume.coerceAtLeast(1)
                    showEdgeGestureHud("🔊 Громкость: $pct%")
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasActive = edgeGestureActiveMode != 0
                edgeGestureActiveMode = 0
                return wasActive
            }
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && menuHistory.isNotEmpty()) resetMenuInactivityTimer()
        if (handleOptionalEdgeTouchGesture(ev)) return true
        if (::gestureDetector.isInitialized && gestureDetector.onTouchEvent(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    internal fun switchChannelByOffset(offset: Int) {
        val list = displayedChannels.toList()
        if (list.isEmpty()) return
        val cur = currentChannel
        val targetIdx = if (
            topBrowseAnchorUrl != null &&
            currentCategoryIndex == topBrowseAnchorCategoryIndex &&
            cur != null &&
            cur.url == topBrowseAnchorUrl &&
            (topBrowseAnchorName == null || cur.name == topBrowseAnchorName)
        ) {
            val anchorIdx = if (offset >= 0) {
                val nextIdx = list.indexOfFirst {
                    it.url == topBrowseAnchorNextUrl && (topBrowseAnchorNextName == null || it.name == topBrowseAnchorNextName)
                }.takeIf { it >= 0 } ?: list.indexOfFirst { it.url == topBrowseAnchorNextUrl }
                if (nextIdx >= 0) {
                    (nextIdx + (offset - 1) + list.size) % list.size
                } else {
                    (topBrowseAnchorFallbackIndex.coerceAtLeast(0) + (offset - 1) + list.size) % list.size
                }
            } else {
                val prevIdx = list.indexOfFirst {
                    it.url == topBrowseAnchorPrevUrl && (topBrowseAnchorPrevName == null || it.name == topBrowseAnchorPrevName)
                }.takeIf { it >= 0 } ?: list.indexOfFirst { it.url == topBrowseAnchorPrevUrl }
                if (prevIdx >= 0) {
                    (prevIdx + (offset + 1) + list.size) % list.size
                } else {
                    ((topBrowseAnchorFallbackIndex - 1).coerceAtLeast(0) + (offset + 1) + list.size) % list.size
                }
            }
            clearTopBrowseAnchor()
            anchorIdx
        } else {
            val idx = findActiveChannelIndex(list, cur, focusedChannelPos)
            if (idx == -1) 0 else (idx + offset + list.size) % list.size
        }
        focusedChannelPos = targetIdx
        playChannelObj(list[targetIdx], immediate = false)
        if (sidePanel.visibility == View.VISIBLE) {
            channelListView.post {
                val firstVis = channelListView.firstVisiblePosition
                val lastVis = channelListView.lastVisiblePosition
                if (targetIdx < firstVis || targetIdx > lastVis) {
                    channelListView.setSelection(targetIdx)
                }
                refreshSimpleListHighlightInPlace(channelListView, if (isSideShowingChannels) targetIdx else -1)
            }
        }
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
        if ((!isVlcEngineActive() && player == null) || (isVlcEngineActive() && vlcPlayer == null)) {
            initPlayer()
            if (wasStoppedInBackground) {
                wasStoppedInBackground = false
                currentChannel?.let { playChannelObj(it, immediate = true, silentSwap = true, updateSavedUrl = false) }
            }
        } else if (wasStoppedInBackground) {
            wasStoppedInBackground = false
            val ch = currentChannel
            if (ch != null) {
                playChannelObj(ch, immediate = true, silentSwap = true, updateSavedUrl = false)
            }
            if (isDualStreamActive) {
                dualStreamChannel?.let { startDualStreamChannel(it) }
            }
        } else {
            if (isVlcEngineActive()) runCatching { vlcPlayer?.play() } else player?.play()
        }
        if (allChannels.isEmpty()) loadActivePlaylist(autoPlayLast = true)
        onUpdatePermissionResume?.invoke()
        showPendingUpdateIfReady()
        scheduleSilentStartupUpdateCheck(initialDelayMs = 1500L)
    }

    override fun onStop() {
        super.onStop()
        wasStoppedInBackground = true
        saveLastChannelOnly(syncCommit = true)
        stopClockAndWatchdogUpdater()
        uiHandler.removeCallbacks(pendingErrorReconnectRunnable)
        uiHandler.removeCallbacks(preloadNeighborsRunnable)
        try {
            player?.stop()
            player?.clearMediaItems()
        } catch (_: Exception) {}
        try { vlcPlayer?.stop() } catch (_: Exception) {}
        try {
            dualPipPlayer?.stop()
            dualPipPlayer?.clearMediaItems()
        } catch (_: Exception) {}
        try {
            streamOkHttpClient.connectionPool.evictAll()
            tokenStreamOkHttpClient.connectionPool.evictAll()
        } catch (_: Exception) {}
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
        try { if (!ambilightSampleBitmap.isRecycled) ambilightSampleBitmap.recycle() } catch (_: Exception) {}
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        try { dualPipPlayer?.stop(); dualPipPlayer?.release() } catch (_: Exception) {}
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        releaseVlcPlayer()
    }
}

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
