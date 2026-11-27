package com.example.miboxiptv

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object CrashGuard {
    @Volatile private var installed = false

    fun install(appContext: Context) {
        if (installed) return
        installed = true
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CoreEngines.appendCrashLogSync(appContext, thread.name, throwable)
            prevHandler?.uncaughtException(thread, throwable)
        }
    }
}

object AfrController {
    @Volatile var lastAppliedFps: Float = 0f

    fun applyFrameRate(activity: Activity, videoView: View?, fps: Float, enabled: Boolean) {
        if (!enabled) {
            reset(activity, videoView)
            return
        }
        if (fps <= 1f || fps > 125f || fps.isNaN()) return
        lastAppliedFps = fps

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val sv = videoView as? SurfaceView
                val surface = sv?.holder?.surface
                if (surface != null && surface.isValid) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        surface.setFrameRate(
                            fps,
                            Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                            Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                        )
                    } else {
                        surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                    }
                    CoreEngines.appendDiagLog("AFR", "Surface.setFrameRate(${String.format(Locale.US, "%.2f", fps)} fps)")
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val display = getActivityDisplay(activity) ?: return
                val currentMode = display.mode ?: return
                val candidates = display.supportedModes.filter {
                    it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight
                }
                if (candidates.size <= 1) return

                val targetRate = pickBestRefreshRate(fps, candidates) ?: return
                val lp = activity.window.attributes
                if (lp.preferredDisplayModeId != targetRate.modeId) {
                    lp.preferredDisplayModeId = targetRate.modeId
                    activity.window.attributes = lp
                    CoreEngines.appendDiagLog(
                        "AFR",
                        "Display mode -> ${targetRate.physicalWidth}x${targetRate.physicalHeight}@${String.format(Locale.US, "%.2f", targetRate.refreshRate)}Hz (video=${String.format(Locale.US, "%.2f", fps)}fps)"
                    )
                }
            }
        }
    }

    fun reset(activity: Activity, videoView: View?) {
        lastAppliedFps = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val surface = (videoView as? SurfaceView)?.holder?.surface
                if (surface != null && surface.isValid) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        surface.setFrameRate(
                            0f,
                            Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                        )
                    } else {
                        surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val lp = activity.window.attributes
                if (lp.preferredDisplayModeId != 0) {
                    lp.preferredDisplayModeId = 0
                    activity.window.attributes = lp
                    CoreEngines.appendDiagLog("AFR", "Display mode сброшен на системный по умолчанию")
                }
            }
        }
    }

    fun currentDisplayRefreshRateLabel(activity: Activity): String {
        return runCatching {
            val display = getActivityDisplay(activity) ?: return ""
            val hz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                display.mode?.refreshRate ?: display.refreshRate
            } else {
                display.refreshRate
            }
            if (hz > 1f) String.format(Locale.US, "%.0f Гц", hz) else ""
        }.getOrDefault("")
    }

    private fun getActivityDisplay(activity: Activity): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }
    }

    private fun pickBestRefreshRate(videoFps: Float, modes: List<Display.Mode>): Display.Mode? {
        var bestMode: Display.Mode? = null
        var bestPenalty = Float.MAX_VALUE
        for (m in modes) {
            val hz = m.refreshRate
            if (hz < 20f) continue
            val directDiff = abs(hz - videoFps)
            val mult2Diff = abs(hz - videoFps * 2f)
            val mult3Diff = abs(hz - videoFps * 3f)
            val minDiff = minOf(directDiff, mult2Diff + 0.15f, mult3Diff + 0.35f)
            if (minDiff < 0.6f && minDiff < bestPenalty) {
                bestPenalty = minDiff
                bestMode = m
            }
        }
        return bestMode
    }
}

object CatchupHelper {
    fun hasCatchup(ch: Channel?): Boolean {
        if (ch == null) return false
        return ch.catchupType.isNotBlank() || ch.catchupSource.isNotBlank() || ch.catchupDays > 0
    }

    fun isProgramArchived(ch: Channel?, prog: EpgProgram, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (ch == null || !hasCatchup(ch)) return false
        if (prog.stopMs >= nowMs) return false
        val maxDays = if (ch.catchupDays > 0) ch.catchupDays else 7
        val oldestMs = nowMs - maxDays.toLong() * 24L * 3600_000L
        return prog.startMs >= oldestMs
    }

    fun buildArchiveUrl(ch: Channel, prog: EpgProgram): String? {
        val baseUrl = ch.url.substringBefore("#").trim()
        if (baseUrl.isEmpty()) return null
        val startSec = (prog.startMs / 1000L).coerceAtLeast(0L)
        val stopSec = (prog.stopMs / 1000L).coerceAtLeast(startSec + 60L)
        val durSec = (stopSec - startSec).coerceAtLeast(60L)
        val nowSec = System.currentTimeMillis() / 1000L
        val offsetSec = (nowSec - startSec).coerceAtLeast(0L)
        val type = ch.catchupType.trim().lowercase(Locale.US)
        val srcTemplate = ch.catchupSource.trim()

        fun expandPlaceholders(template: String): String {
            val utcDate = Date(startSec * 1000L)
            fun fmtUtc(pattern: String): String {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.format(utcDate)
            }
            return template
                .replace("\${start}", startSec.toString())
                .replace("{utc}", startSec.toString())
                .replace("{lutc}", nowSec.toString())
                .replace("\${end}", stopSec.toString())
                .replace("{utcend}", stopSec.toString())
                .replace("\${duration}", durSec.toString())
                .replace("{duration}", durSec.toString())
                .replace("\${offset}", offsetSec.toString())
                .replace("{offset}", offsetSec.toString())
                .replace("{Y}", fmtUtc("yyyy"))
                .replace("{m}", fmtUtc("MM"))
                .replace("{d}", fmtUtc("dd"))
                .replace("{H}", fmtUtc("HH"))
                .replace("{M}", fmtUtc("mm"))
                .replace("{S}", fmtUtc("ss"))
        }

        return when {
            type == "default" && srcTemplate.isNotEmpty() -> expandPlaceholders(srcTemplate)
            type == "append" && srcTemplate.isNotEmpty() -> baseUrl + expandPlaceholders(srcTemplate)
            type == "flussonic" || type == "flussonic-hls" || type == "fs" -> {
                if (srcTemplate.isNotEmpty()) {
                    expandPlaceholders(srcTemplate)
                } else {
                    val cleanNoQuery = baseUrl.substringBefore("?")
                    val query = baseUrl.substringAfter("?", "")
                    val qSuffix = if (query.isNotEmpty()) "?$query" else ""
                    when {
                        cleanNoQuery.endsWith("/index.m3u8") ->
                            cleanNoQuery.removeSuffix("/index.m3u8") + "/index-$startSec-$durSec.m3u8$qSuffix"
                        cleanNoQuery.endsWith("/mono.m3u8") ->
                            cleanNoQuery.removeSuffix("/mono.m3u8") + "/mono-$startSec-$durSec.m3u8$qSuffix"
                        cleanNoQuery.endsWith("/video.m3u8") ->
                            cleanNoQuery.removeSuffix("/video.m3u8") + "/video-$startSec-$durSec.m3u8$qSuffix"
                        else -> {
                            val sep = if (baseUrl.contains("?")) "&" else "?"
                            "${baseUrl}${sep}utc=$startSec&lutc=$nowSec"
                        }
                    }
                }
            }
            type == "shift" -> {
                val sep = if (baseUrl.contains("?")) "&" else "?"
                "${baseUrl}${sep}utc=$startSec&lutc=$nowSec"
            }
            srcTemplate.isNotEmpty() -> {
                if (srcTemplate.startsWith("http://", true) || srcTemplate.startsWith("https://", true)) {
                    expandPlaceholders(srcTemplate)
                } else {
                    baseUrl + expandPlaceholders(srcTemplate)
                }
            }
            else -> {
                val sep = if (baseUrl.contains("?")) "&" else "?"
                "${baseUrl}${sep}utc=$startSec&lutc=$nowSec"
            }
        }
    }
}
