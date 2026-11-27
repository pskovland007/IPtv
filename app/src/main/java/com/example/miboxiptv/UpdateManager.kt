package com.example.miboxiptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class UpdateManager(private val context: Context) {

    data class ReleaseInfo(
        val tagName: String,
        val title: String,
        val apkUrl: String,
        val apkName: String,
        val apkSizeBytes: Long,
        val body: String,
        val versionLabel: String,
        val updatedAtMs: Long
    )

    data class CheckResult(
        val release: ReleaseInfo?,
        val error: String? = null
    )

    class DownloadHandle(
        private val cancelled: AtomicBoolean,
        @Volatile var activeCall: Call? = null
    ) {
        fun cancel() {
            cancelled.set(true)
            runCatching { activeCall?.cancel() }
        }

        fun isCancelled(): Boolean = cancelled.get()
    }

    private val apiClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkLatestReleaseDetailed(): CheckResult {
        var lastErr: String? = null

        // 1. Primary: GitHub API /releases/latest
        val latestAttempt = fetchReleaseObject(LATEST_RELEASE_URL)
        latestAttempt.first?.let { obj ->
            parseReleaseJson(obj)?.let { rel ->
                CoreEngines.appendDiagLog("UPDATE", "GitHub /latest: ${rel.versionLabel} (${rel.apkName}, tag=${rel.tagName})")
                return CheckResult(rel, null)
            }
        }
        if (latestAttempt.second != null) {
            lastErr = latestAttempt.second
        }

        // 2. Fallback: GitHub API /releases?per_page=8 (covers pre-releases)
        val listReq = Request.Builder()
            .url(ALL_RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()

        val listResult = runCatching {
            apiClient.newCall(listReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    lastErr = "GitHub API HTTP ${resp.code}"
                    return@use null
                }
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) return@use null
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optBoolean("draft", false)) continue
                    val parsed = parseReleaseJson(obj)
                    if (parsed != null) return@use parsed
                }
                null
            }
        }.onFailure { err ->
            lastErr = err.message?.take(60) ?: err.javaClass.simpleName
        }.getOrNull()

        if (listResult != null) {
            CoreEngines.appendDiagLog("UPDATE", "GitHub /releases: ${listResult.versionLabel} (${listResult.apkName})")
            return CheckResult(listResult, null)
        }

        // 3. Fallback without GitHub API rate limits: follow /releases/latest web redirect & expanded_assets
        val webFallback = fetchReleaseViaWebFallback()
        if (webFallback != null) {
            CoreEngines.appendDiagLog("UPDATE", "GitHub web fallback: ${webFallback.versionLabel} (${webFallback.apkName})")
            return CheckResult(webFallback, null)
        }

        val errMsg = lastErr ?: "Нет ответа от GitHub"
        CoreEngines.appendDiagLog("UPDATE_ERR", "Проверка обновлений не удалась: $errMsg")
        return CheckResult(null, errMsg)
    }

    private fun fetchReleaseObject(url: String): Pair<JSONObject?, String?> {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            apiClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null to "GitHub API HTTP ${resp.code}"
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) return@use null to "Пустой ответ GitHub"
                JSONObject(raw) to null
            }
        }.getOrElse { err ->
            null to (err.message?.take(60) ?: err.javaClass.simpleName)
        }
    }

    private fun fetchReleaseViaWebFallback(): ReleaseInfo? {
        return runCatching {
            val latestReq = Request.Builder()
                .url(WEB_LATEST_RELEASE_URL)
                .header("User-Agent", USER_AGENT)
                .build()
            val tag = apiClient.newCall(latestReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                val finalUrl = resp.request.url.toString()
                if (finalUrl.contains("/releases/tag/")) {
                    finalUrl.substringAfter("/releases/tag/").substringBefore('?').trim()
                } else {
                    ""
                }
            }
            if (tag.isBlank()) return@runCatching null

            val assetsReq = Request.Builder()
                .url("$WEB_EXPANDED_ASSETS_PREFIX$tag")
                .header("User-Agent", USER_AGENT)
                .build()
            val html = apiClient.newCall(assetsReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                resp.body?.string().orEmpty()
            }
            val match = WEB_APK_HREF_REGEX.find(html) ?: return@runCatching null
            val relHref = match.groupValues[1].trim()
            val apkUrl = if (relHref.startsWith("http")) relHref else "https://github.com$relHref"
            val apkName = relHref.substringAfterLast('/').ifBlank { "TiViLook-latest.apk" }
            val verLabel = extractVersionLabel(apkName, tag, tag)
            ReleaseInfo(
                tagName = tag,
                title = "TiViLook $verLabel",
                apkUrl = apkUrl,
                apkName = apkName,
                apkSizeBytes = 0L,
                body = "Релиз $tag на GitHub (получено через резервный канал).",
                versionLabel = verLabel,
                updatedAtMs = 0L
            )
        }.getOrNull()
    }

    private fun parseReleaseJson(obj: JSONObject): ReleaseInfo? {
        val tag = obj.optString("tag_name").trim()
        val name = obj.optString("name").trim().ifBlank { tag }
        val body = obj.optString("body").trim()
        val publishedAtMs = parseIso8601UtcMillis(obj.optString("published_at", ""))

        var apkUrl = ""
        var apkName = "TiViLook-latest.apk"
        var apkSize = 0L
        var updatedMs = publishedAtMs
        val assets = obj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name").trim()
                val dlUrl = asset.optString("browser_download_url").trim()
                if ((assetName.endsWith(".apk", ignoreCase = true) || dlUrl.endsWith(".apk", ignoreCase = true)) && dlUrl.isNotBlank()) {
                    apkUrl = dlUrl
                    apkName = assetName.ifBlank { "TiViLook-latest.apk" }
                    apkSize = asset.optLong("size", 0L)
                    val assetUpd = parseIso8601UtcMillis(asset.optString("updated_at", ""))
                    if (assetUpd > 0L) updatedMs = assetUpd
                    break
                }
            }
        }
        if (apkUrl.isBlank() || tag.isBlank()) return null

        val extractedVersion = extractVersionLabel(apkName, tag, name)
        return ReleaseInfo(
            tagName = tag,
            title = name,
            apkUrl = apkUrl,
            apkName = apkName,
            apkSizeBytes = apkSize,
            body = body,
            versionLabel = extractedVersion,
            updatedAtMs = updatedMs
        )
    }

    fun getInstalledVersionName(): String {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName?.trim().orEmpty()
        }.getOrDefault("")
    }

    fun releaseUniqueKey(info: ReleaseInfo): String {
        val ver = info.versionLabel.trim()
        val tagPart = info.tagName.trim()
        return when {
            ver.isNotEmpty() && !ver.equals("latest", ignoreCase = true) -> ver
            tagPart.isNotEmpty() && !tagPart.equals("latest", ignoreCase = true) -> tagPart
            else -> "${tagPart.ifBlank { info.apkName }}_${info.updatedAtMs}"
        }
    }

    fun isNewerRelease(release: ReleaseInfo): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val localVerName = (pInfo.versionName ?: "").trim()
            val localVerCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            val localInstalledMs = pInfo.lastUpdateTime

            val remoteSem = parseSemver(release.versionLabel)
                ?: parseSemver(release.tagName)
                ?: parseSemver(release.title)
                ?: parseSemver(release.apkName)
            val localSem = parseSemver(localVerName)

            val isNewer = when {
                remoteSem != null && localSem != null -> compareVersions(release.versionLabel, localVerName) > 0
                else -> {
                    val remoteBuild = BUILD_NUM_REGEX.find(release.tagName)?.groupValues?.getOrNull(1)?.toLongOrNull()
                    when {
                        remoteBuild != null && localVerCode > 1L -> remoteBuild > localVerCode
                        release.updatedAtMs > 0L -> release.updatedAtMs > (localInstalledMs + 60_000L)
                        else -> false
                    }
                }
            }
            CoreEngines.appendDiagLog(
                "UPDATE",
                "Сравнение версий: local=$localVerName (code=$localVerCode) vs GitHub=${release.versionLabel} (tag=${release.tagName}), новее=$isNewer"
            )
            isNewer
        } catch (e: Exception) {
            CoreEngines.appendDiagLog("UPDATE_ERR", "Не удалось сравнить версии: ${e.message}")
            false
        }
    }

    fun startApkDownload(
        release: ReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long, percent: Int) -> Unit,
        onSuccess: (apkFile: File) -> Unit,
        onCancelled: () -> Unit,
        onFailure: (errorMessage: String) -> Unit
    ): DownloadHandle {
        val cancelled = AtomicBoolean(false)
        val handle = DownloadHandle(cancelled)

        thread(name = "TiViLook-UpdateDownload") {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            cleanOldApks(updatesDir)
            val outFile = File(updatesDir, "TiViLook-latest.apk")

            val req = Request.Builder()
                .url(release.apkUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val call = downloadClient.newCall(req)
            handle.activeCall = call

            CoreEngines.appendDiagLog("UPDATE", "Старт загрузки APK в кэш: ${release.apkName} (${release.apkUrl})")

            val result = runCatching {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("Пустой ответ сервера")
                    val totalBytes = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes
                    var downloaded = 0L
                    var lastUiUpdate = 0L
                    val buf = ByteArray(16_384)

                    body.byteStream().use { input ->
                        FileOutputStream(outFile, false).use { output ->
                            while (true) {
                                if (cancelled.get()) {
                                    throw InterruptedException("CANCELLED")
                                }
                                val read = input.read(buf)
                                if (read == -1) break
                                output.write(buf, 0, read)
                                downloaded += read

                                val now = System.currentTimeMillis()
                                if (now - lastUiUpdate >= 120L || (totalBytes > 0L && downloaded >= totalBytes)) {
                                    lastUiUpdate = now
                                    val snapDown = downloaded
                                    val pct = if (totalBytes > 0L) {
                                        ((snapDown * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    } else {
                                        -1
                                    }
                                    mainHandler.post {
                                        if (!cancelled.get()) {
                                            onProgress(snapDown, totalBytes, pct)
                                        }
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                }
                if (!outFile.exists() || outFile.length() <= 0L) {
                    error("Загруженный файл пуст")
                }
                outFile
            }

            handle.activeCall = null

            mainHandler.post {
                if (cancelled.get()) {
                    runCatching { outFile.delete() }
                    CoreEngines.appendDiagLog("UPDATE", "Загрузка обновления отменена пользователем")
                    onCancelled()
                    return@post
                }

                result.onSuccess { readyApk ->
                    CoreEngines.appendDiagLog("UPDATE", "APK загружен (${formatBytes(readyApk.length())}): ${readyApk.absolutePath}")
                    onSuccess(readyApk)
                }.onFailure { err ->
                    runCatching { outFile.delete() }
                    val msg = err.message?.take(80) ?: err.javaClass.simpleName
                    CoreEngines.appendDiagLog("UPDATE_ERR", "Ошибка загрузки APK: $msg")
                    onFailure(msg)
                }
            }
        }

        return handle
    }

    private fun cleanOldApks(dir: File) {
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".apk", ignoreCase = true)) {
                runCatching { f.delete() }
            }
        }
    }

    fun canInstallUnknownApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings(activityContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activityContext.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                activityContext.startActivity(intent)
            }.onFailure {
                Toast.makeText(
                    activityContext,
                    "Откройте Настройки → Приложения → Специальный доступ → Установка неизвестных приложений",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun launchPackageInstaller(activityContext: Context, apkFile: File): Boolean {
        return runCatching {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(installIntent)
            CoreEngines.appendDiagLog("UPDATE", "Запущен системный установщик APK: ${apkFile.name}")
            true
        }.getOrElse { err ->
            CoreEngines.appendDiagLog("UPDATE_ERR", "Не удалось открыть установщик: ${err.message}")
            Toast.makeText(
                activityContext,
                "Не удалось открыть установщик: ${err.message}",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    companion object {
        private const val USER_AGENT = "TiViLook-Updater"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/pskovland007/IPtv/releases/latest"
        private const val ALL_RELEASES_URL =
            "https://api.github.com/repos/pskovland007/IPtv/releases?per_page=8"
        private const val WEB_LATEST_RELEASE_URL =
            "https://github.com/pskovland007/IPtv/releases/latest"
        private const val WEB_EXPANDED_ASSETS_PREFIX =
            "https://github.com/pskovland007/IPtv/releases/expanded_assets/"

        private val APK_VERSION_REGEX = Regex("v?(\\d+(?:\\.\\d+)+(?:-[A-Za-z0-9.]+)?)", RegexOption.IGNORE_CASE)
        private val SEMVER_REGEX = Regex("(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
        private val STAGE_REGEX = Regex("stage(\\d+)(?:\\.(\\d+))?([a-z])?", RegexOption.IGNORE_CASE)
        private val BUILD_NUM_REGEX = Regex("(?:^|[^0-9])(?:v|r|build[-_]?)(\\d{1,7})(?:$|[^0-9])", RegexOption.IGNORE_CASE)
        private val WEB_APK_HREF_REGEX = Regex("href=\"(/[^\"]+/releases/download/[^\"]+\\.apk)\"", RegexOption.IGNORE_CASE)

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 КБ"
            return when {
                bytes < 1024L -> "$bytes Б"
                bytes < 1024L * 1024L -> "${(bytes / 1024L).coerceAtLeast(1L)} КБ"
                else -> String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
            }
        }

        private fun parseIso8601UtcMillis(raw: String?): Long {
            if (raw.isNullOrBlank()) return 0L
            val clean = raw.trim()
            val formats = listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            for (fmt in formats) {
                try {
                    val sdf = SimpleDateFormat(fmt, Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    val parsed = sdf.parse(clean)
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {}
            }
            return 0L
        }

        fun extractVersionLabel(apkName: String, tagName: String, releaseTitle: String): String {
            val cleanApk = apkName.removeSuffix(".apk").removeSuffix(".APK")
            APK_VERSION_REGEX.find(tagName)?.groupValues?.get(1)?.let { return it }
            APK_VERSION_REGEX.find(cleanApk)?.groupValues?.get(1)?.let { return it }
            APK_VERSION_REGEX.find(releaseTitle)?.groupValues?.get(1)?.let { return it }
            return tagName.removePrefix("v").removePrefix("V").trim()
        }

        fun compareVersions(remote: String, current: String): Int {
            val rSem = parseSemver(remote)
            val cSem = parseSemver(current)
            if (rSem != null && cSem != null) {
                for (i in 0 until 3) {
                    if (rSem[i] != cSem[i]) return rSem[i].compareTo(cSem[i])
                }
            }

            val rStage = parseStage(remote)
            val cStage = parseStage(current)
            if (rStage != null && cStage != null) {
                for (i in 0 until 3) {
                    if (rStage[i] != cStage[i]) return rStage[i].compareTo(cStage[i])
                }
            } else if (rSem != null && cSem != null) {
                if (rStage == null && cStage != null) return 1
                if (rStage != null && cStage == null) return -1
            }

            return 0
        }

        private fun parseSemver(raw: String): IntArray? {
            val cleaned = raw.lowercase(Locale.US).replace(STAGE_REGEX, "")
            val m = SEMVER_REGEX.find(cleaned) ?: return null
            val major = m.groupValues[1].toIntOrNull() ?: 0
            val minor = m.groupValues[2].toIntOrNull() ?: 0
            val patch = m.groupValues[3].toIntOrNull() ?: 0
            return intArrayOf(major, minor, patch)
        }

        private fun parseStage(raw: String): IntArray? {
            val m = STAGE_REGEX.find(raw.lowercase(Locale.US)) ?: return null
            val major = m.groupValues[1].toIntOrNull() ?: 0
            val minor = m.groupValues[2].toIntOrNull() ?: 0
            val letter = m.groupValues[3].firstOrNull()?.let { ch ->
                if (ch in 'a'..'z') (ch - 'a') + 1 else 0
            } ?: 0
            return intArrayOf(major, minor, letter)
        }
    }
}
