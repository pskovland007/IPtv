package com.example.miboxiptv

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(private val context: Context) {

    data class OpResult(
        val ok: Boolean,
        val message: String,
        val savedPath: String = ""
    )

    fun exportBackup(stateJson: JSONObject): OpResult {
        return runCatching {
            val verName = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            }.getOrDefault("")
            val root = JSONObject().apply {
                put("format", FORMAT_TAG)
                put("appVersion", verName)
                put("exportedAt", System.currentTimeMillis())
                put("state", stateJson)
            }
            val json = root.toString(2)
            saveToAppExternalDir(BACKUP_FILE_NAME, json)
            val savedPath = writeTextToDownloads(BACKUP_FILE_NAME, "application/json", json)
            CoreEngines.appendDiagLog("BACKUP", "Экспорт бэкапа: $savedPath")
            OpResult(
                ok = true,
                message = "Бэкап сохранён: $savedPath",
                savedPath = savedPath
            )
        }.getOrElse { err ->
            CoreEngines.appendDiagLog("BACKUP_ERR", "Ошибка экспорта бэкапа: ${err.message}")
            OpResult(false, "Ошибка экспорта: ${err.message ?: "нет доступа"}")
        }
    }

    fun importBackup(): Pair<OpResult, JSONObject?> {
        return runCatching {
            val loaded = readNewestBackupText()
                ?: return OpResult(
                    ok = false,
                    message = "Файл $BACKUP_FILE_NAME не найден в Download/TiViLook"
                ) to null
            val (sourceLabel, rawJson) = loaded
            val root = JSONObject(rawJson)
            val stateObj = root.optJSONObject("state")
                ?: return OpResult(false, "Неверный формат файла бэкапа") to null
            CoreEngines.appendDiagLog("BACKUP", "Загружен бэкап из $sourceLabel")
            OpResult(true, "Восстановлено из $sourceLabel", sourceLabel) to stateObj
        }.getOrElse { err ->
            CoreEngines.appendDiagLog("BACKUP_ERR", "Ошибка импорта бэкапа: ${err.message}")
            OpResult(false, "Ошибка импорта: ${err.message ?: "повреждён файл"}") to null
        }
    }

    fun buildDiagnosticHeader(
        engineLabel: String,
        decoderLabel: String,
        bufferLabel: String,
        themeLabel: String,
        playlistsCount: Int,
        channelsCount: Int,
        dohEnabled: Boolean,
        dpiBypassEnabled: Boolean,
        proxyUrl: String,
        preferredAudioLang: String
    ): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val (verName, verCode) = runCatching {
            @Suppress("DEPRECATION")
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
            (pi.versionName ?: "?") to code
        }.getOrDefault("?" to 0L)

        return buildString {
            append("=== TiViLook Diagnostic Log ===\n")
            append("Generated: ").append(ts).append('\n')
            append("App: ").append(verName).append(" (code ").append(verCode).append(")\n")
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Player: ").append(engineLabel).append(" | Decoder: ").append(decoderLabel).append('\n')
            append("Buffer: ").append(bufferLabel).append(" | Theme: ").append(themeLabel).append('\n')
            append("Playlists: ").append(playlistsCount).append(" | Channels: ").append(channelsCount).append('\n')
            append("Audio lang: ").append(preferredAudioLang.ifBlank { "auto" }).append('\n')
            append("Network: DoH=").append(dohEnabled)
                .append(" | DPI=").append(dpiBypassEnabled)
                .append(" | Proxy=").append(if (proxyUrl.isNotBlank()) "set" else "none").append('\n')
            append("===============================\n\n")
        }
    }

    private fun writeTextToDownloads(fileName: String, mimeType: String, content: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relPath = Environment.DIRECTORY_DOWNLOADS + "/TiViLook/"
            runCatching {
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
                    arrayOf(fileName, relPath)
                )
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relPath)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                return "Download/TiViLook/$fileName"
            }
        }

        val canWritePublic = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

        if (canWritePublic) {
            @Suppress("DEPRECATION")
            val pubDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TiViLook"
            )
            if (pubDir.exists() || pubDir.mkdirs()) {
                val outFile = File(pubDir, fileName)
                outFile.writeText(content, Charsets.UTF_8)
                return "Download/TiViLook/$fileName"
            }
        }

        val fallbackFile = saveToAppExternalDir(fileName, content)
        return fallbackFile.absolutePath
    }

    private fun saveToAppExternalDir(fileName: String, content: String): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val dir = File(baseDir, "TiViLook").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    private fun readNewestBackupText(): Pair<String, String>? {
        val candidates = mutableListOf<Triple<Long, String, () -> String?>>()

        // 1. Android 10+ MediaStore Download/TiViLook/tivilook_backup.json
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                val relPath = Environment.DIRECTORY_DOWNLOADS + "/TiViLook/"
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DATE_MODIFIED),
                    "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
                    arrayOf(BACKUP_FILE_NAME, relPath),
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(0)
                        val modSec = c.getLong(1)
                        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        candidates.add(
                            Triple(modSec * 1000L, "Download/TiViLook/$BACKUP_FILE_NAME") {
                                resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            }
                        )
                    }
                }
            }
        }

        // 2. Legacy public Download/TiViLook/tivilook_backup.json (and root Download/tivilook_backup.json)
        @Suppress("DEPRECATION")
        val pubRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        for (f in listOf(File(File(pubRoot, "TiViLook"), BACKUP_FILE_NAME), File(pubRoot, BACKUP_FILE_NAME))) {
            if (f.exists() && f.isFile && f.canRead() && f.length() > 0L) {
                candidates.add(Triple(f.lastModified(), "Download/${f.parentFile?.name}/${f.name}") {
                    runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
                })
            }
        }

        // 3. App-external mirror
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val mirror = File(File(baseDir, "TiViLook"), BACKUP_FILE_NAME)
        if (mirror.exists() && mirror.isFile && mirror.length() > 0L) {
            candidates.add(Triple(mirror.lastModified(), "внутренний бэкап") {
                runCatching { mirror.readText(Charsets.UTF_8) }.getOrNull()
            })
        }

        val sorted = candidates.sortedByDescending { it.first }
        for ((_, label, reader) in sorted) {
            val txt = runCatching { reader() }.getOrNull()
            if (!txt.isNullOrBlank()) return label to txt
        }
        return null
    }

    companion object {
        private const val FORMAT_TAG = "tivilook_backup_v1"
        const val BACKUP_FILE_NAME = "tivilook_backup.json"
    }
}
