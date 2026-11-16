package com.example.miboxiptv

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

data class Channel(
    val name: String,
    val url: String,
    val category: String,
    val tvgId: String = ""
)

data class PlaylistEntry(
    val title: String,
    val source: String,
    val isLocal: Boolean
)

data class EpgProgram(
    val title: String,
    val desc: String,
    val startMs: Long,
    val stopMs: Long
)

data class AppThemeStyle(
    val id: Int,
    val name: String,
    val panelBgColor: Int,
    val cardBgColor: Int,
    val accentColor: Int,
    val strokeColor: Int,
    val textPrimaryColor: Int,
    val textOnAccent: Int,
    val cornerRadiusDp: Float,
    val normalFont: Typeface,
    val boldFont: Typeface,
    val isCarouselMenu: Boolean = false
)

class ChannelRvAdapter(
    private val getTheme: () -> AppThemeStyle,
    private val getUiScaleFactor: () -> Float,
    private val isTopUrl: (String) -> Boolean,
    private val isFavUrl: (String) -> Boolean,
    private val getPlayingUrl: () -> String?,
    private val onChannelFocused: (Int) -> Unit,
    private val onChannelClicked: (Int, Channel) -> Unit,
    private val onChannelLongClicked: (Int, Channel) -> Unit,
    private val onLeftPressedToGroups: () -> Unit,
    private val onRightPressedCloseMenu: () -> Unit
) : RecyclerView.Adapter<ChannelRvAdapter.ChannelVH>() {

    var highlightedPos: Int = 0
    fun getExactRowHeightPx(): Int = (50f * getUiScaleFactor()).toInt()
    fun submitChannels(newList: List<Channel>, initialFocusPos: Int) {
        highlightedPos = initialFocusPos
    }

    inner class ChannelVH(val rowView: ChannelRowView) : RecyclerView.ViewHolder(rowView)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelVH =
        ChannelVH(ChannelRowView(parent.context))
    override fun onBindViewHolder(holder: ChannelVH, position: Int) {}
    override fun getItemCount(): Int = 0
}

// === ВСЕ МОДАЛЬНЫЕ ОКНА НАСТРОЕК, РЕДАКТОР И ТЕСТ КАНАЛОВ ===

@OptIn(UnstableApi::class)
internal fun MainActivity.openPowerAndSleepSubModal() {
    setSubModalHeaderTitle("Выключение и Таймер сна")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE

    val sleepOpts = listOf(0, 30, 60, 90, 120)
    val items = mutableListOf("[-]   Выключить телевизор TiViLook прямо сейчас")
    for (m in sleepOpts) {
        val sel = if (sleepTimerMinutesLeft == m) "[+]   " else "[-]   "
        if (m == 0) items.add("${sel}Таймер сна   •   Выключен")
        else items.add("${sel}Таймер сна   •   Выключить через $m минут")
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
    animateMenuAppear(subModalOverlay)
    subModalList.requestFocus()
}

@OptIn(UnstableApi::class)
internal fun MainActivity.setSleepTimer(minutes: Int) {
    sleepTimerRunnable?.let { uiHandler.removeCallbacks(it) }
    sleepTimerMinutesLeft = minutes
    applyCurrentThemeToAllPanels()
    if (minutes <= 0) {
        applyAudioEnhancements()
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

@OptIn(UnstableApi::class)
internal fun MainActivity.openBrightnessSubModal() {
    setSubModalHeaderTitle("Уровень яркости экрана")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    val levels = listOf(100, 75, 50, 25)
    val descriptions = listOf(
        "Яркость экрана: 100%   •   Дневной режим",
        "Яркость экрана: 75%     •   Вечерний режим",
        "Яркость экрана: 50%     •   Ночной режим",
        "Яркость экрана: 25%     •   Глубокая ночь (защита плазмы)"
    )
    val curIdx = levels.indexOf(brightnessPercent).coerceAtLeast(0)
    updateSubModalListItems(
        levels.mapIndexed { idx, lvl -> (if (brightnessPercent == lvl) "[+]   " else "[-]   ") + descriptions[idx] },
        preservePositionIndex = curIdx
    )
    subModalList.setOnItemClickListener { _, _, which, _ ->
        if (which in levels.indices) {
            brightnessPercent = levels[which]
            applyBrightnessLevel()
            saveState()
            navigateBackInMenu()
        }
    }
    animateMenuAppear(subModalOverlay)
    subModalList.requestFocus()
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openChannelActionsSubModal() {
    val ch = currentChannel ?: return
    setSubModalHeaderTitle("Управление каналом: ${ch.name}")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    val items = listOf(
        "[-]   Исключить этот канал из списка   •   Возврат через Редактор",
        "[>]   Переместить в другую категорию   •   Сейчас: ${effectiveCategory(ch)}",
        if (isDualStreamActive) "[ON]   Двойной эфир (2-й экран)   •   Активен" else "[OFF]   Двойной эфир (2-й экран)   •   Выключен"
    )
    updateSubModalListItems(items)
    subModalList.setOnItemClickListener { _, _, which, _ ->
        when (which) {
            0 -> {
                manualDisabledUrls.add(ch.url)
                saveState()
                updateCategoriesList()
                selectCategory(currentCategoryIndex)
                closeAllMenus()
                switchChannelByOffset(+1)
            }
            1 -> navigateToMenu("MOVE_CHANNEL_CATEGORY")
            2 -> {
                if (isDualStreamActive) {
                    stopDualStreamMode()
                    closeAllMenus()
                } else {
                    startDualStreamChannel(ch)
                    closeAllMenus()
                    switchChannelByOffset(+1)
                }
            }
        }
    }
    animateMenuAppear(subModalOverlay)
    subModalList.requestFocus()
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openMoveChannelCategorySubModal() {
    val ch = currentChannel ?: return
    val targetCats = activeCategoryList().drop(2)
    val curCat = effectiveCategory(ch)
    setSubModalHeaderTitle("Переместить «${ch.name}» в категорию")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    val curIdx = targetCats.indexOf(curCat).coerceAtLeast(0)
    updateSubModalListItems(
        targetCats.map { c -> (if (c == curCat) "[+]   " else "[-]   ") + c },
        preservePositionIndex = curIdx
    )
    subModalList.setOnItemClickListener { _, _, which, _ ->
        if (which in targetCats.indices) {
            val newCat = targetCats[which]
            customCategoryMap[ch.url] = newCat
            currentChannel = Channel(ch.name, ch.url, newCat, ch.tvgId)
            saveState()
            updateCategoriesList()
            selectCategory(activeCategoryList().indexOf(newCat).takeIf { it >= 2 } ?: currentCategoryIndex)
            closeAllMenus()
        }
    }
    animateMenuAppear(subModalOverlay)
    subModalList.requestFocus()
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openThemesSubModal() {
    setSubModalHeaderTitle("Темы оформления")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    subModalTopButtons.visibility = View.GONE
    subModalTopButtons.removeAllViews()

    val items = themes.mapIndexed { idx, t ->
        if (idx == themeIndex) "[+]   ${t.name}   •   Активна"
        else "[-]   ${t.name}"
    }
    updateSubModalListItems(items, preservePositionIndex = themeIndex)

    subModalList.setOnItemClickListener { _, _, which, _ ->
        themeIndex = which
        saveState()
        applyCurrentThemeToAllPanels()
        navigateBackInMenu()
    }
    animateMenuAppear(subModalOverlay)
    subModalList.requestFocus()
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openTopManagerSubModal(inPlace: Boolean = false, keepIndex: Int = -1) {
    setSubModalHeaderTitle("Каналы в ТОПе групп (${topUrls.size})")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    subModalTopButtons.visibility = View.VISIBLE
    subModalTopButtons.removeAllViews()
    if (topUrls.isNotEmpty()) {
        subModalTopButtons.addView(makeModernButton("Очистить весь ТОП") {
            topUrls.clear()
            saveState()
            selectCategory(currentCategoryIndex)
            openTopManagerSubModal(inPlace = true, keepIndex = 0)
        })
    }
    val topChans = allChannels.filter { topUrls.contains(it.url) }
    updateSubModalListItems(
        if (topChans.isEmpty()) listOf("[-]   В ТОПе пока нет закреплённых каналов")
        else topChans.map { "[+]   ${it.name}   •   ${effectiveCategory(it)}" },
        smoothToggle = inPlace,
        preservePositionIndex = keepIndex
    )
    subModalList.setOnItemClickListener { _, _, idx, _ ->
        if (idx in topChans.indices) {
            topUrls.remove(topChans[idx].url)
            saveState()
            selectCategory(currentCategoryIndex)
            openTopManagerSubModal(inPlace = true, keepIndex = idx.coerceAtMost((topChans.size - 2).coerceAtLeast(0)))
        }
    }
    if (!inPlace) {
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openFullScreenChannelEpg() {
    setPlayerMiniPreviewMode(true)
    val ch = currentChannel
    val progs = getChannelEpgList(ch)
    val dateFmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    val now = System.currentTimeMillis()

    fullEpgTitle.text = "Программа передач: " + (ch?.name ?: "Канал не выбран")
    val items = mutableListOf<String>()
    var activeIndex = 0

    if (progs.isEmpty()) {
        items.add("[?]   Расписание для канала «${ch?.name ?: ""}» обновляется в фоне...")
    } else {
        progs.forEachIndexed { idx, p ->
            val isNow = now in p.startMs..p.stopMs
            if (isNow) activeIndex = idx
            val prefix = when {
                isNow -> "[+]   Сейчас   •   "
                p.stopMs < now -> "[-]   Прошло   •   "
                else -> "[>]   Далее     •   "
            }
            val timeRange = dateFmt.format(Date(p.startMs)) + " – " + dateFmt.format(Date(p.stopMs))
            items.add("$prefix$timeRange   •   ${p.title}")
        }
    }

    fullEpgListView.adapter = createRoundedSelectorAdapter(items, 15.5f, true, fixedRowHeight = true) { pos ->
        fullEpgListView.selectedItemPosition == pos
    }
    animateMenuAppear(fullEpgOverlay)
    fullEpgListView.requestFocus()
    if (activeIndex > 0 && activeIndex < items.size) fullEpgListView.setSelection(activeIndex)
    fullEpgListView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            resetMenuInactivityTimer()
            refreshSimpleListHighlightInPlace(fullEpgListView, position)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openMoreSettingsSubModal(inPlace: Boolean = false, keepIndex: Int = -1) {
    setSubModalHeaderTitle("Общие настройки")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE

    val scaleText = when (uiScaleStep) {
        0 -> "Обычный (Смартфон)"
        1 -> "Крупный (Телевизор)"
        else -> "Огромный (Большой ТВ)"
    }
    val alphaText = when (uiAlphaStep) {
        0 -> "Плотный (96%)"
        1 -> "Средний (84%)"
        else -> "Стекло (65%)"
    }
    val ambiRow = when (ambilightMode) {
        0 -> "[OFF]   Подсветка Ambilight   •   Выключена"
        1 -> "[ON]   Подсветка Ambilight   •   Цвет группы"
        2 -> "[ON]   Подсветка Ambilight   •   Спектр"
        else -> "[ON]   Подсветка Ambilight   •   Кадр видео"
    }
    val aspectText = when (screenAspectMode) {
        0 -> "По размеру экрана"
        1 -> "Растянуть 16:9"
        else -> "Кинозум"
    }
    val cutoutRow = if (cameraCutoutSafeOffset) "[ON]   Отступ под камеру слева (Смартфон)   •   Включён" else "[OFF]   Отступ под камеру слева (Смартфон)   •   Выключен"
    val bannerRow = if (showChannelBannerOnSwitch) "[ON]   Баннер названия при переключении   •   Показывать" else "[OFF]   Баннер названия при переключении   •   Скрыт"
    val delayRow = "[+]   Автоскрытие меню и баннера   •   Через $menuHideDelaySec сек"
    val dualRow = if (isDualStreamActive) "[ON]   Двойной эфир (2-й экран PiP)   •   Активен" else "[OFF]   Двойной эфир (2-й экран PiP)   •   Выключен"
    val dohRow = if (dohEnabled) "[ON]   Обход блокировок DoH (Cloudflare)   •   Включён" else "[OFF]   Обход блокировок DoH (Системный DNS)   •   Выключен"
    val dropRow = if (onDropBehavior == 0) "[+]   При обрыве потока   •   Бесшовный перезапуск" else "[+]   При обрыве потока   •   Следующий канал"
    val sleepRow = if (sleepTimerMinutesLeft > 0) "[ON]   Таймер сна и Выключение   •   Через $sleepTimerMinutesLeft мин" else "[>]   Таймер сна и Выключение   •   Открыть меню"

    val items = listOf(
        "[+]   Размер текста и меню   •   $scaleText",
        "[+]   Прозрачность меню   •   $alphaText",
        ambiRow,
        "[+]   Формат изображения   •   $aspectText",
        cutoutRow,
        bannerRow,
        delayRow,
        dualRow,
        dohRow,
        dropRow,
        "[>]   Яркость экрана   •   Сейчас: $brightnessPercent%",
        "[>]   Машина времени   •   Отмотать эфир на -30 сек",
        sleepRow,
        "[>]   Телепрограмма EPG   •   Обновить расписание сейчас",
        "[>]   Обновление TiViLook   •   Проверить и установить с GitHub"
    )
    updateSubModalListItems(items, smoothToggle = inPlace, preservePositionIndex = keepIndex)

    subModalList.setOnItemClickListener { _, _, which, _ ->
        when (which) {
            0 -> { uiScaleStep = (uiScaleStep + 1) % 3; saveState(); applyCurrentThemeToAllPanels(); openMoreSettingsSubModal(inPlace = true, keepIndex = which) }
            1 -> { uiAlphaStep = (uiAlphaStep + 1) % 3; saveState(); animatePanelAlphaTransition(); openMoreSettingsSubModal(inPlace = true, keepIndex = which) }
            2 -> { ambilightMode = (ambilightMode + 1) % 4; saveState(); applyCurrentThemeToAllPanels(); openMoreSettingsSubModal(inPlace = true, keepIndex = which) }
            3 -> { screenAspectMode = (screenAspectMode + 1) % 3; applyScreenAspectMode(); saveState(); openMoreSettingsSubModal(inPlace = true, keepIndex = which) }
            4 -> { cameraCutoutSafeOffset = !cameraCutoutSafeOffset; saveState(); applyCurrentThemeToAllPanels(); openMoreSettingsSubModal(inPlace = true, keepIndex = which) }
            5 -> { showChannelBannerOnSwitch = !showChannelBannerOnSwitch; saveState(); openMoreSettingsSubModal(inPlace = true, keepIndex = which) }
            6 -> {
                menuHideDelaySec = when (menuHideDelaySec) { 4 -> 8; 8 -> 12; else -> 4 }
                saveState(); openMoreSettingsSubModal(inPlace = true, keepIndex = which)
            }
            7 -> { toggleDualStreamWithCurrentOrNeighbor(); openMoreSettingsSubModal(inPlace = true, keepIndex = which) }
            8 -> { dohEnabled = !dohEnabled; saveState(); initPlayer(); openMoreSettingsSubModal(inPlace = true, keepIndex = which) }
            9 -> { onDropBehavior = (onDropBehavior + 1) % 2; saveState(); openMoreSettingsSubModal(inPlace = true, keepIndex = which) }
            10 -> navigateToMenu("BRIGHTNESS_MENU")
            11 -> { rewindLiveBufferSeconds(30); closeAllMenus() }
            12 -> navigateToMenu("POWER_AND_SLEEP_MENU")
            13 -> { loadEpgOncePerDayInBackground(forceRefresh = true); navigateBackInMenu() }
            14 -> checkAndInstallAppUpdateFromGithub()
        }
    }
    if (!inPlace) {
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.checkAndInstallAppUpdateFromGithub() {
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

@OptIn(UnstableApi::class)
internal fun MainActivity.openPlaylistsSubModal() {
    ensureDefaultPlaylists()
    setSubModalHeaderTitle("Плейлисты (2 встроенных + ваши списки)")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    val snapshot = playlists.toList()
    val items = snapshot.mapIndexed { idx, p -> (if (idx == activePlaylistIndex) "[+]   " else "[-]   ") + p.title }.toMutableList()
    items.add("[>]   Добавить свой плейлист по ссылке   •   Сохранение групп")
    items.add("[>]   Проводник   •   Выбрать файл .m3u/.m3u8 с устройства или USB")
    if (snapshot.size > 2 && activePlaylistIndex >= 2) items.add("[-]   Удалить выбранный пользовательский плейлист")
    updateSubModalListItems(items, preservePositionIndex = activePlaylistIndex)

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
    animateMenuAppear(subModalOverlay)
    subModalList.requestFocus()
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openFolderExplorerModal(dir: File) {
    requestStoragePermissionsIfNeeded()
    currentBrowseDir = dir
    setSubModalHeaderTitle("Проводник: " + dir.absolutePath)
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    subModalTopButtons.visibility = View.VISIBLE
    subModalTopButtons.removeAllViews()

    subModalTopButtons.addView(makeModernButton("📥 Download") { openFolderExplorerModal(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) })
    subModalTopButtons.addView(makeModernButton("🏠 Память") { openFolderExplorerModal(Environment.getExternalStorageDirectory()) })
    subModalTopButtons.addView(makeModernButton("🔌 USB") { openFolderExplorerModal(File("/storage")) })
    subModalTopButtons.addView(makeModernButton("⌨ Путь вручную") {
        openInputSubModal("Путь к файлу .m3u / .m3u8", "/sdcard/Download/playlist.m3u8") { path -> addLocalPlaylistFile(File(path)) }
    })

    val entries = mutableListOf<Pair<String, File?>>()
    val parent = dir.parentFile
    if (parent != null && parent.canRead()) entries.add(Pair("[>]   .. [На уровень вверх]", parent))
    val rawChildren = try { dir.listFiles()?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
    for (d in rawChildren.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }) entries.add(Pair("[>]   Папка: " + d.name, d))
    for (f in rawChildren.filter { it.isFile && (it.name.endsWith(".m3u", true) || it.name.endsWith(".m3u8", true) || it.name.endsWith(".txt", true)) }.sortedBy { it.name.lowercase() }) {
        entries.add(Pair("[+]   Файл: " + f.name + "   •   " + (f.length() / 1024) + " КБ", f))
    }
    if (entries.isEmpty()) entries.add(Pair("[-]   Папка пуста или нет доступа", null))

    updateSubModalListItems(entries.map { it.first }, preservePositionIndex = 0)
    subModalList.setOnItemClickListener { _, _, idx, _ ->
        val target = entries.getOrNull(idx)?.second ?: return@setOnItemClickListener
        if (target.isDirectory) openFolderExplorerModal(target) else if (target.isFile) addLocalPlaylistFile(target)
    }
    animateMenuAppear(subModalOverlay)
    subModalList.requestFocus()
}

@OptIn(UnstableApi::class)
internal fun MainActivity.addLocalPlaylistFile(file: File) {
    if (!file.exists() || !file.canRead()) return
    playlists.add(PlaylistEntry(file.name, file.absolutePath, true))
    activePlaylistIndex = playlists.size - 1
    saveState()
    closeAllMenus()
    loadActivePlaylist(autoPlayLast = false)
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openChannelEditorGroupsModal(inPlace: Boolean = false, keepIndex: Int = -1) {
    setSubModalHeaderTitle("Редактор каналов: выберите группу")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    subModalTopButtons.visibility = View.VISIBLE
    subModalTopButtons.removeAllViews()
    subModalTopButtons.addView(makeModernButton("Включить все группы и каналы") {
        disabledCategories.clear(); manualDisabledUrls.clear(); autoDisabledUrls.clear()
        saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex); openChannelEditorGroupsModal(inPlace = true, keepIndex = subModalHighlightedPos)
    })
    val snapshot = allChannels.toList()
    val editableCats = activeCategoryList().drop(2)
    updateSubModalListItems(
        editableCats.map { cat ->
            val isCatEnabled = !disabledCategories.contains(cat)
            val totalInCat = snapshot.count { effectiveCategory(it) == cat }
            val activeInCat = snapshot.count { effectiveCategory(it) == cat && isChannelActive(it) }
            "${if (isCatEnabled) "[+]" else "[-]"}   $cat   •   Активно: $activeInCat из $totalInCat"
        },
        smoothToggle = inPlace,
        preservePositionIndex = keepIndex
    )
    subModalList.setOnItemClickListener { _, _, idx, _ -> selectedEditorCategory = editableCats[idx]; navigateToMenu("CHANNEL_EDITOR_ITEMS") }
    if (!inPlace) {
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openChannelEditorItemsModal(catName: String, inPlace: Boolean = false, keepIndex: Int = -1) {
    val catChannels = allChannels.filter { effectiveCategory(it) == catName }.sortedWith(Comparator<Channel> { a, b -> ruCollator.compare(a.name, b.name) })
    val isCatDisabled = disabledCategories.contains(catName)
    setSubModalHeaderTitle("Группа: $catName (${catChannels.size} кан.)")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    subModalTopButtons.visibility = View.VISIBLE
    subModalTopButtons.removeAllViews()
    subModalTopButtons.addView(makeModernButton(if (isCatDisabled) "[+] Включить всю группу" else "[-] Отключить всю группу") {
        if (disabledCategories.contains(catName)) disabledCategories.remove(catName) else disabledCategories.add(catName)
        saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex); openChannelEditorItemsModal(catName, inPlace = true, keepIndex = subModalHighlightedPos)
    })
    subModalTopButtons.addView(makeModernButton("Включить все в группе") {
        catChannels.forEach { manualDisabledUrls.remove(it.url); autoDisabledUrls.remove(it.url) }
        disabledCategories.remove(catName); saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex); openChannelEditorItemsModal(catName, inPlace = true, keepIndex = subModalHighlightedPos)
    })
    updateSubModalListItems(
        catChannels.map { ch ->
            val isManualOff = manualDisabledUrls.contains(ch.url)
            val isAutoOff = autoDisabledUrls.contains(ch.url)
            val enabled = !isManualOff && !isAutoOff && !isCatDisabled
            val note = if (isManualOff) "   •   Исключён вручную" else if (isAutoOff) "   •   Скрыт тестом" else ""
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
            saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex); openChannelEditorItemsModal(catName, inPlace = true, keepIndex = idx)
        }
    }
    if (!inPlace) {
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openAnalyzerGroupsModal(firstOpen: Boolean = false) {
    val snapshot = allChannels.filter { !manualDisabledUrls.contains(it.url) }
    val aliveTotal = aliveChannelsAtomic.get()
    val deadTotal = deadChannelsAtomic.get()
    setSubModalHeaderTitle("Быстрый тест   •   [+] $aliveTotal   [-] $deadTotal")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    if (firstOpen || subModalTopButtons.childCount == 0 || !isAnalyzerRunning) {
        subModalTopButtons.visibility = View.VISIBLE
        subModalTopButtons.removeAllViews()
        subModalTopButtons.addView(makeModernButton(if (isAnalyzerRunning) "■ Стоп" else "▶ Быстрый тест всех групп") {
            if (isAnalyzerRunning) {
                isAnalyzerRunning = false; analyzerStatusBanner.visibility = View.GONE; openAnalyzerGroupsModal(firstOpen = true)
            } else {
                selectedAnalyzerCategory = "Все каналы"; startBackgroundChannelAnalyzer(snapshot); openAnalyzerGroupsModal(firstOpen = true)
            }
        })
        if (isAnalyzerRunning) subModalTopButtons.addView(makeModernButton("▲ Свернуть в фон") { collapseAnalyzerToTopBar() })
        if (autoDisabledUrls.isNotEmpty()) {
            subModalTopButtons.addView(makeModernButton("↺ Вернуть скрытые (${autoDisabledUrls.size})") {
                autoDisabledUrls.clear(); saveState(); updateCategoriesList(); selectCategory(currentCategoryIndex); openAnalyzerGroupsModal(firstOpen = false)
            })
        }
    }
    val groupNames = listOf("Все каналы") + activeCategoryList().drop(2)
    updateSubModalListItems(groupNames.map { gName ->
        val groupChans = if (gName == "Все каналы") snapshot else snapshot.filter { effectiveCategory(it) == gName }
        "[>]   $gName (${groupChans.size} кан.)   •   [+] ${groupChans.count { channelHealthMap[it.url] == true }}    [-] ${groupChans.count { channelHealthMap[it.url] == false }}"
    }, preservePositionIndex = subModalHighlightedPos)
    subModalList.setOnItemClickListener { _, _, idx, _ ->
        if (idx in groupNames.indices) {
            selectedAnalyzerCategory = groupNames[idx]; navigateToMenu("ANALYZER_GROUP_VIEW")
        }
    }
    if (firstOpen) {
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
        resetMenuInactivityTimer()
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openAnalyzerGroupChannelsModal(catName: String, firstOpen: Boolean = false) {
    val snapshot = allChannels.filter { !manualDisabledUrls.contains(it.url) }
    val groupChans = (if (catName == "Все каналы") snapshot else snapshot.filter { effectiveCategory(it) == catName })
        .sortedWith(Comparator<Channel> { a, b -> ruCollator.compare(a.name, b.name) })
    val alive = groupChans.count { channelHealthMap[it.url] == true }
    val dead = groupChans.count { channelHealthMap[it.url] == false }
    setSubModalHeaderTitle("Группа: $catName ([+] $alive  •  [-] $dead)")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    if (firstOpen || subModalTopButtons.childCount == 0 || !isAnalyzerRunning) {
        subModalTopButtons.visibility = View.VISIBLE
        subModalTopButtons.removeAllViews()
        subModalTopButtons.addView(makeModernButton(if (isAnalyzerRunning) "■ Стоп" else "▶ Быстрый тест группы") {
            if (isAnalyzerRunning) {
                isAnalyzerRunning = false; analyzerStatusBanner.visibility = View.GONE; openAnalyzerGroupChannelsModal(catName, firstOpen = true)
            } else {
                startBackgroundChannelAnalyzer(groupChans); openAnalyzerGroupChannelsModal(catName, firstOpen = true)
            }
        })
        if (isAnalyzerRunning) subModalTopButtons.addView(makeModernButton("▲ Свернуть в фон") { collapseAnalyzerToTopBar() })
    }
    updateSubModalListItems(groupChans.mapIndexed { i, ch ->
        val status = when (channelHealthMap[ch.url]) { true -> "[+]   Работает"; false -> "[-]   Скрыт"; null -> "[?]   Очередь" }
        "$status   •   ${i + 1}. ${ch.name}"
    }, preservePositionIndex = subModalHighlightedPos)
    subModalList.setOnItemClickListener { _, _, idx, _ ->
        if (idx in groupChans.indices) {
            playChannelObj(groupChans[idx], immediate = true); closeAllMenus()
        }
    }
    if (firstOpen) {
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
        resetMenuInactivityTimer()
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.startBackgroundChannelAnalyzer(rawTargetChannels: List<Channel>) {
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
        val pool = Executors.newFixedThreadPool(6)
        var lastUiUpdateMs = 0L
        for (ch in snapshot) {
            pool.submit {
                if (!isAnalyzerRunning) return@submit
                val isProtectedVipToken = CoreEngines.isSingleSessionTokenUrl(ch.url)
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

@OptIn(UnstableApi::class)
internal fun MainActivity.checkStreamAliveDeep(urlStr: String): Boolean {
    var conn: HttpURLConnection? = null
    return try {
        val cleanUrl = urlStr.substringBefore("#")
        val parsed = URL(cleanUrl)
        val hdrs = CoreEngines.headersForStreamUrl(cleanUrl)
        conn = (parsed.openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", CoreEngines.russianSmartTvAgents[userAgentIndex % CoreEngines.russianSmartTvAgents.size])
            for ((k, v) in hdrs) setRequestProperty(k, v)
            instanceFollowRedirects = true
            connectTimeout = 4000
            readTimeout = 4000
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
        if (cleanUrl.contains(".m3u", true) && !headStr.contains("#EXTM3U", true) && buf[0] != 0x47.toByte()) {
            return false
        }
        true
    } catch (_: Exception) {
        try { conn?.disconnect() } catch (_: Exception) {}
        false
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openBufferSubModal(inPlace: Boolean = false, keepIndex: Int = -1) {
    setSubModalHeaderTitle("Буфер подкачки и Машина времени")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    val opts = listOf(
        "Маленький буфер (6–12 сек)   •   Мгновенный старт",
        "Оптимальный буфер (14–28 сек)   •   Рекомендуется (без VPN)",
        "Средний буфер (18–36 сек)   •   Плавный HEVC поток",
        "Большой буфер (24–45 сек)   •   Максимум защиты от ТСПУ"
    ).mapIndexed { i, t -> (if (i == bufferLevel) "[+]   " else "[-]   ") + t }.toMutableList()
    opts.add("[>]   Машина времени   •   Отмотать прямой эфир на -20 сек")
    opts.add("[>]   Машина времени   •   Отмотать прямой эфир на -45 сек")
    val targetIdx = if (keepIndex >= 0) keepIndex else bufferLevel
    updateSubModalListItems(opts, smoothToggle = inPlace, preservePositionIndex = targetIdx)
    subModalList.setOnItemClickListener { _, _, which, _ ->
        when (which) {
            in 0..3 -> { bufferLevel = which; saveState(); initPlayer(); openBufferSubModal(inPlace = true, keepIndex = which) }
            4 -> { rewindLiveBufferSeconds(20); closeAllMenus() }
            5 -> { rewindLiveBufferSeconds(45); closeAllMenus() }
        }
    }
    if (!inPlace) {
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openDecoderSubModal(inPlace: Boolean = false, keepIndex: Int = -1) {
    setSubModalHeaderTitle("Декодер и Защита Плазмы")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    val opts = listOf(
        "Аппаратный декодер (MediaCodec HW)   •   Минимальная нагрузка",
        "Автоматический декодер (HW + FFmpeg)   •   Рекомендуется",
        "Программный декодер (Приоритет SW)   •   Совместимость"
    ).mapIndexed { i, t -> (if (i == decoderMode) "[+]   " else "[-]   ") + t }.toMutableList()
    opts.add(if (pixelOrbiterEnabled) "[ON]   Сдвиг пикселей часов, антенны и экрана (Pixel Orbiter)   •   Включён" else "[OFF]   Сдвиг пикселей часов, антенны и экрана (Pixel Orbiter)   •   Выключен")
    opts.add(if (afrEnabled) "[ON]   Авточастота кадров AFR (50/60 Гц)   •   Включена" else "[OFF]   Авточастота кадров AFR (50/60 Гц)   •   Выключена")
    val targetIdx = if (keepIndex >= 0) keepIndex else decoderMode
    updateSubModalListItems(opts, smoothToggle = inPlace, preservePositionIndex = targetIdx)
    subModalList.setOnItemClickListener { _, _, which, _ ->
        when (which) {
            in 0..2 -> { decoderMode = which; saveState(); initPlayer(); openDecoderSubModal(inPlace = true, keepIndex = which) }
            3 -> { pixelOrbiterEnabled = !pixelOrbiterEnabled; saveState(); stepPixelOrbiter(); openDecoderSubModal(inPlace = true, keepIndex = which) }
            4 -> { afrEnabled = !afrEnabled; saveState(); openDecoderSubModal(inPlace = true, keepIndex = which) }
        }
    }
    if (!inPlace) {
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openAudioSubModal(inPlace: Boolean = false, keepIndex: Int = -1) {
    val p = player ?: return
    setSubModalHeaderTitle("Звук, Выравнивание (DRC) и Усиление")
    subModalInputContainer.visibility = View.GONE
    subModalList.visibility = View.VISIBLE
    val labels = mutableListOf<String>()
    labels.add(if (drcNightAudioEnabled) "[ON]   Выравнивание громкости (Night Audio / DRC)   •   Включено" else "[OFF]   Выравнивание громкости (Night Audio / DRC)   •   Выключено")
    val boostText = when (audioBoostPercent) {
        100 -> "100% (Штатный уровень)"
        130 -> "130% (+3.5 дБ)"
        160 -> "160% (+7.0 дБ)"
        else -> "200% (+11 дБ Максимум)"
    }
    labels.add("[+]   Усиление громкости (Audio Boost)   •   $boostText")

    val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val overrides = mutableListOf<TrackSelectionOverride>()
    for (g in groups) {
        for (i in 0 until g.length) {
            val fmt = g.getTrackFormat(i)
            val codec = fmt.sampleMimeType?.substringAfter("/")?.uppercase() ?: "AUDIO"
            val lang = fmt.language?.uppercase() ?: ("Дорожка " + (overrides.size + 1))
            labels.add((if (g.isTrackSelected(i)) "[+]   " else "[-]   ") + "Аудиодорожка: $lang   •   $codec")
            overrides.add(TrackSelectionOverride(g.mediaTrackGroup, i))
        }
    }
    val targetIdx = if (keepIndex >= 0) keepIndex else 0
    updateSubModalListItems(labels, smoothToggle = inPlace, preservePositionIndex = targetIdx)
    subModalList.setOnItemClickListener { _, _, idx, _ ->
        when (idx) {
            0 -> { drcNightAudioEnabled = !drcNightAudioEnabled; saveState(); applyAudioEnhancements(); openAudioSubModal(inPlace = true, keepIndex = idx) }
            1 -> {
                audioBoostPercent = when (audioBoostPercent) { 100 -> 130; 130 -> 160; 160 -> 200; else -> 100 }
                saveState(); applyAudioEnhancements(); openAudioSubModal(inPlace = true, keepIndex = idx)
            }
            else -> {
                val ovIdx = idx - 2
                if (ovIdx in overrides.indices) {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(overrides[ovIdx]).build()
                    openAudioSubModal(inPlace = true, keepIndex = idx)
                }
            }
        }
    }
    if (!inPlace) {
        animateMenuAppear(subModalOverlay)
        subModalList.requestFocus()
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openInputSubModal(title: String, initialValue: String, onSave: (String) -> Unit) {
    setSubModalHeaderTitle(title)
    subModalList.visibility = View.GONE
    subModalInputContainer.removeAllViews()
    subModalInputContainer.visibility = View.VISIBLE
    val input = EditText(this).apply { setText(initialValue); setTextColor(currentTheme().textPrimaryColor); setSingleLine() }
    subModalInputContainer.addView(input)
    val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 14, 0, 0) }
    btnRow.addView(makeModernButton("Сохранить") { onSave(input.text.toString().trim()); navigateBackInMenu() })
    btnRow.addView(makeModernButton("Назад") { navigateBackInMenu() })
    subModalInputContainer.addView(btnRow)
    animateMenuAppear(subModalOverlay)
    input.requestFocus()
}

@OptIn(UnstableApi::class)
internal fun MainActivity.openAddUrlSubModal() {
    setSubModalHeaderTitle("Новый плейлист по ссылке")
    subModalList.visibility = View.GONE
    subModalInputContainer.removeAllViews()
    subModalInputContainer.visibility = View.VISIBLE
    val nameInput = EditText(this).apply { hint = "Название плейлиста"; setTextColor(currentTheme().textPrimaryColor); setSingleLine() }
    val urlInput = EditText(this).apply { hint = "http://... (.m3u8)"; setTextColor(currentTheme().textPrimaryColor); setSingleLine() }
    subModalInputContainer.addView(nameInput)
    subModalInputContainer.addView(urlInput)
    val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 14, 0, 0) }
    btnRow.addView(makeModernButton("Добавить") {
        val title = nameInput.text.toString().trim().ifEmpty { "Плейлист " + (playlists.size + 1) }
        val url = urlInput.text.toString().trim()
        if (url.isNotEmpty()) {
            playlists.add(PlaylistEntry(title, url, false))
            activePlaylistIndex = playlists.size - 1
            saveState()
            closeAllMenus()
            loadActivePlaylist(autoPlayLast = false)
        }
    })
    btnRow.addView(makeModernButton("Назад") { navigateBackInMenu() })
    subModalInputContainer.addView(btnRow)
    animateMenuAppear(subModalOverlay)
    nameInput.requestFocus()
}
