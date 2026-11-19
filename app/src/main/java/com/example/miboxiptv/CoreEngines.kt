package com.example.miboxiptv

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object CoreEngines {
    private val dohDnsCache = ConcurrentHashMap<String, String>()
    private val diagLock = Any()
    private val diagTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile var diagAppContext: Context? = null

    fun resolveDiagLogFile(ctx: Context? = diagAppContext): File {
        try {
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dlDir != null && (dlDir.exists() || dlDir.mkdirs()) && dlDir.canWrite()) {
                return File(dlDir, "TiViLook_diag.txt")
            }
        } catch (_: Exception) {}
        val extDir = ctx?.getExternalFilesDir(null)
        if (extDir != null) return File(extDir, "TiViLook_diag.txt")
        val intDir = ctx?.filesDir
        if (intDir != null) return File(intDir, "TiViLook_diag.txt")
        return File("/sdcard/Download/TiViLook_diag.txt")
    }

    fun appendDiagLog(tag: String, msg: String) {
        try {
            synchronized(diagLock) {
                val f = resolveDiagLogFile()
                if (f.exists() && f.length() > 1_500_000L) {
                    val tail = f.readText().takeLast(450_000)
                    f.writeText("--- [ЛОГ УСЕЧЁН ПО ЛИМИТУ 1.5 МБ] ---\n" + tail)
                }
                val ts = diagTimeFmt.format(Date())
                FileWriter(f, true).use { fw ->
                    fw.write("$ts [$tag] $msg\n")
                }
            }
        } catch (_: Exception) {}
    }

    fun clearDiagLog(): String {
        return try {
            synchronized(diagLock) {
                val f = resolveDiagLogFile()
                f.writeText("${diagTimeFmt.format(Date())} [INIT] Диагностический лог TiViLook очищен пользователем\n")
                f.absolutePath
            }
        } catch (_: Exception) {
            "/sdcard/Download/TiViLook_diag.txt"
        }
    }

    fun getDiagLogStatusText(): String {
        return try {
            val f = resolveDiagLogFile()
            val kb = if (f.exists()) (f.length() / 1024L).coerceAtLeast(1L) else 0L
            "${f.name} (${kb} КБ)"
        } catch (_: Exception) {
            "TiViLook_diag.txt"
        }
    }

    fun readDiagLogTail(maxLines: Int = 45): List<String> {
        return try {
            synchronized(diagLock) {
                val f = resolveDiagLogFile()
                if (!f.exists()) {
                    listOf("[-]   Лог-файл пока пуст (${f.absolutePath})")
                } else {
                    val allLines = f.readLines().filter { it.isNotBlank() }
                    val header = "[+]   Путь: ${f.absolutePath} (${(f.length() / 1024L).coerceAtLeast(1L)} КБ)"
                    listOf(header) + allLines.takeLast(maxLines).reversed()
                }
            }
        } catch (e: Exception) {
            listOf("[-]   Ошибка чтения лога: ${e.message}")
        }
    }

    fun exportDiagLogToPublicDownloads(ctx: Context): String {
        return try {
            val src = resolveDiagLogFile(ctx)
            val content = synchronized(diagLock) {
                if (src.exists()) src.readText() else "${diagTimeFmt.format(Date())} [INIT] Лог пуст\n"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = ctx.contentResolver
                val fileName = "TiViLook_diag_TV.txt"
                try {
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Downloads.DISPLAY_NAME}=?",
                        arrayOf(fileName)
                    )
                } catch (_: Exception) {}
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    return "Download/$fileName"
                }
            }
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dlDir != null && (dlDir.exists() || dlDir.mkdirs()) && dlDir.canWrite()) {
                val dst = File(dlDir, "TiViLook_diag_TV.txt")
                dst.writeText(content)
                return dst.absolutePath
            }
            src.absolutePath
        } catch (e: Exception) {
            "Ошибка экспорта: ${e.message}"
        }
    }

    private val REGEX_NON_ALNUM: Regex = Regex("[^a-zа-я0-9]")
    private val REGEX_GEO_TAG: Regex = Regex("\\[[^\\]]*(Geo|Not 24)[^\\]]*\\]", RegexOption.IGNORE_CASE)
    private val REGEX_MULTI_SPACE: Regex = Regex("\\s{2,}")
    private val REGEX_PLUS_TZ1: Regex = Regex("\\+[1-9]\\b")
    private val REGEX_PLUS_TZ2: Regex = Regex("\\(\\+[1-9]\\)")
    private val REGEX_RESOLUTION: Regex = Regex("(\\d{3,4})x(\\d{3,4})")
    private val REGEX_GROUP_TITLE: Regex = Regex("group-title=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val REGEX_TVG_ID: Regex = Regex("tvg-id=\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    val russianSmartTvAgents = listOf(
        "VLC/3.0.20 LibVLC/3.0.20",
        "Televizo/1.9.7.14 (Linux; Android 11; Xiaomi Mi Box S)",
        "OTT Navigator/1.6.9.4 (Linux; Android 11; AndroidTV)",
        "Wink/1.44.0 (AndroidTV; Android 11; Xiaomi Mi Box S)",
        "Lavf/60.16.100"
    )

    fun installTrustAllSsl() {
        try {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAll, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (_: Exception) {}
    }

    fun isSingleSessionTokenUrl(urlStr: String): Boolean {
        val l = urlStr.lowercase()
        return l.contains("rostelekom.xyz") || l.contains("/iptv/")
    }

    fun headersForStreamUrl(urlStr: String): Map<String, String> {
        val clean = urlStr.substringBefore("#")
        val lUrl = clean.lowercase()
        if (isSingleSessionTokenUrl(lUrl)) {
            return mapOf(
                "Accept" to "*/*"
            )
        }
        val parsedOrigin = try {
            val u = URL(clean)
            "${u.protocol}://${u.host}"
        } catch (_: Exception) {
            "https://smotrim.ru"
        }
        val (origin, referer) = when {
            lUrl.contains("vgtrk") || lUrl.contains("smotrim") || lUrl.contains("mediavitrina") ->
                Pair("https://smotrim.ru", "https://smotrim.ru/")
            lUrl.contains("1tv") || lUrl.contains("1internet") ->
                Pair("https://www.1tv.ru", "https://www.1tv.ru/")
            lUrl.contains("ntv.ru") ->
                Pair("https://www.ntv.ru", "https://www.ntv.ru/")
            lUrl.contains("catcast") ->
                Pair("https://catcast.tv", "https://catcast.tv/")
            lUrl.contains("peers.tv") || lUrl.contains("cn.ru") ->
                Pair("https://peers.tv", "https://peers.tv/")
            lUrl.contains("1hd.ru") || lUrl.contains("onehdmusic") ->
                Pair("https://www.1hd.ru", "https://www.1hd.ru/")
            lUrl.contains("bonus-tv") ->
                Pair("https://bonus-tv.ru", "https://bonus-tv.ru/")
            else -> Pair(parsedOrigin, "$parsedOrigin/")
        }
        return mapOf(
            "Accept" to "*/*",
            "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.7",
            "Origin" to origin,
            "Referer" to referer,
            "Connection" to "keep-alive"
        )
    }

    fun resolveHostViaCloudflareDoH(host: String, dohEnabled: Boolean): String? {
        if (!dohEnabled || host.isEmpty()) return null
        if (host.all { it.isDigit() || it == '.' }) return host
        dohDnsCache[host]?.let { return it }
        try {
            val dohUrl = "https://1.1.1.1/dns-query?name=$host&type=A"
            val conn = (URL(dohUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/dns-json")
                connectTimeout = 2500
                readTimeout = 2500
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(body)
                val answers = json.optJSONArray("Answer")
                if (answers != null) {
                    for (i in 0 until answers.length()) {
                        val ans = answers.getJSONObject(i)
                        if (ans.optInt("type") == 1) {
                            val ip = ans.optString("data", "")
                            if (ip.isNotEmpty()) {
                                dohDnsCache[host] = ip
                                return ip
                            }
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
        return try { InetAddress.getByName(host).hostAddress } catch (_: Exception) { null }
    }

    fun openStreamWithBypass(urlStr: String, userAgentIndex: Int, dohEnabled: Boolean, timeoutMs: Int = 8000): InputStream {
        var currentUrl = urlStr.substringBefore("#")
        var redirects = 0
        while (redirects < 6) {
            val parsed = URL(currentUrl)
            val originalHost = parsed.host
            val resolvedIp = if (dohEnabled) resolveHostViaCloudflareDoH(originalHost, true) else null
            val activeAgent = if (isSingleSessionTokenUrl(currentUrl)) russianSmartTvAgents[0] else russianSmartTvAgents[userAgentIndex % russianSmartTvAgents.size]
            val hdrs = headersForStreamUrl(currentUrl)
            try {
                val conn = (parsed.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", activeAgent)
                    for ((k, v) in hdrs) setRequestProperty(k, v)
                    instanceFollowRedirects = true
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!loc.isNullOrEmpty()) {
                        currentUrl = if (loc.startsWith("http")) loc else URL(parsed, loc).toString()
                        redirects++
                        continue
                    }
                }
                return conn.inputStream
            } catch (e: Exception) {
                if (dohEnabled && !resolvedIp.isNullOrEmpty() && resolvedIp != originalHost && parsed.protocol.equals("http", ignoreCase = true)) {
                    val ipUrl = URL(parsed.protocol, resolvedIp, parsed.port, parsed.file)
                    val ipConn = (ipUrl.openConnection() as HttpURLConnection).apply {
                        setRequestProperty("Host", originalHost)
                        setRequestProperty("User-Agent", activeAgent)
                        for ((k, v) in hdrs) setRequestProperty(k, v)
                        instanceFollowRedirects = true
                        connectTimeout = timeoutMs
                        readTimeout = timeoutMs
                    }
                    val code = ipConn.responseCode
                    if (code in 300..399) {
                        val loc = ipConn.getHeaderField("Location")
                        ipConn.disconnect()
                        if (!loc.isNullOrEmpty()) {
                            currentUrl = if (loc.startsWith("http")) loc else URL(parsed, loc).toString()
                            redirects++
                            continue
                        }
                    }
                    return ipConn.inputStream
                }
                throw e
            }
        }
        return URL(currentUrl).openStream()
    }

    fun normalizeName(s: String): String =
        s.lowercase(Locale.getDefault()).replace(REGEX_NON_ALNUM, "")

    fun cleanChannelDisplayName(rawName: String): String {
        val cleaned = rawName
            .replace(REGEX_GEO_TAG, " ")
            .replace(REGEX_MULTI_SPACE, " ")
            .trim()
        return cleaned.ifEmpty { rawName.trim() }
    }

    fun isRegionalTimeShiftDuplicate(name: String): Boolean {
        val low = name.lowercase()
        if (REGEX_PLUS_TZ1.containsMatchIn(low) || REGEX_PLUS_TZ2.containsMatchIn(low)) return true
        val regionalMarkers = listOf("(омск", "(томск", "(владивосток", "(уфа", "(алтай", "(канск", "(абакан", "(иркутск", "(братск", "(пермь", "(элиста", "(белгород", "(липецк", "(тамбов", "(нефтекамск", "(белорецк", "(невинномысск")
        return regionalMarkers.any { low.contains(it) }
    }

    fun parseXmltvDate(raw: String): Long {
        if (raw.length < 14) return 0L
        return try {
            val clean = raw.trim()
            val fmt = if (clean.length >= 19 && (clean[14] == ' ' || clean[14] == '+' || clean[14] == '-')) {
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            } else {
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            }
            fmt.parse(clean)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun isArabicOrIndianChannel(name: String, group: String, url: String): Boolean {
        val combined = "$name $group $url"
        for (ch in combined) {
            val code = ch.code
            if (code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x0900..0x0D7F) return true
        }
        val low = " ${name.lowercase()} ${group.lowercase()} "
        val bannedTokens = listOf(
            "arabic", "arabia", "al jazeera", "aljazeera", " mbc", "rotana", " osn ", " nile ",
            "dubai", "sharjah", "abu dhabi", "al arabiya", "alarabiya", "iqraa", "majid", "spacetoon",
            " zee ", "sony sab", "star plus", "star bharat", "colors ", "aaj tak", "ndtv", "dd national",
            "sun tv", "asianet", "bollywood", " hindi", " tamil", " telugu", " punjabi", " urdu",
            "bangla", "pakistan", "geo news", "ary digital", "hum tv", "samaa", "kurd", "farsi", "persian",
            "latino", "viva latino", "3abn", "telemundo", "univision", "azteca", "caracol", "kral ",
            "trt ", "haberturk", "powerturk", "dream turk", "afrobeat", "nollywood", "iran", "afghan",
            "[magenta]", " in ", " ae ", " qa ", " br ", " pl ", " de ", " fr ", " es ", " it ", " nl ", " se ", " dk ", " no ", " fi ", " ro ", " hr ", " cz ", " sk ", " lt ", " lv ", " ee ",
            "(czech", "(hungary)", "(italy)", "(poland)", "(germany)", "(france)", "(spain)", "(romania)",
            "(bulgaria)", "(serbia)", "(croatia)", "(slovenia)", "(slovakia)", "(portugal)", "(netherlands)",
            "(sweden)", "(norway)", "(denmark)", "(finland)", "(greece)", "(turkey)", "(israel)", "(brazil)",
            "(mexico)", "(argentina)", "(colombia)", "(chile)", "(peru)", "(uk)", "(usa)", "(canada)", "international",
            "(japan)", "japan", "brazil", "brasil", "latam", "uzbekistan", "deportes", "verguenza", "polska", "ukraine"
        )
        return bannedTokens.any { low.contains(it) }
    }

    fun isStrictlyRussianChannel(name: String, url: String, rawGroup: String = ""): Boolean {
        if (isArabicOrIndianChannel(name, rawGroup, url)) return false
        if (name.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }) return true
        val lName = name.lowercase().trim()
        val lUrl = url.lowercase()
        if (lUrl.contains(".ru/") || lUrl.contains(".su/") || lUrl.contains(".by/") ||
            lUrl.contains("vgtrk") || lUrl.contains("1tv") || lUrl.contains("ntv") || lUrl.contains("smotrim") ||
            lUrl.contains("tricolor") || lUrl.contains("wink") || lUrl.contains("peers") || lUrl.contains("catcast") ||
            lUrl.contains("cdnvideo") || lUrl.contains("ngenix") || lUrl.contains("mediavitrina") ||
            lUrl.contains("uma.media") || lUrl.contains("1hd.ru") || lUrl.contains("gcdn.co") ||
            lUrl.contains("bonus-tv") || lUrl.contains("rutube") || lUrl.contains("ok.ru")) {
            return true
        }
        val ruPrefixes = listOf(
            "vf ", "uz ", "mm ", "bcu ", "yosso ", "cineman ", "kli ", "z!", "prokop ",
            "veles ", "dosug ", "kinoshka ", "fresh ", "liberty ", "magic ", "eye ", "cobra "
        )
        if (ruPrefixes.any { lName.startsWith(it) }) return true

        val ruLatinWhitelist = listOf(
            "viju", "tv1000", "amedia", "karusel", "mult", "tiji", "gulli", "nickelodeon", "nick jr", "nicktoons",
            "cartoon network", "cartoonito", "boomerang", "jimjam", "jim jam", "da vinci", "sts kids", "ani", "fan hd",
            "bridge", "muz tv", "muz-tv", "ru.tv", "rutv", "ru tv", "1hd", "europa plus", "zhara", "shanson",
            "tnt", "sts", "ren tv", "ntv", "match", "khl", "kino", "illuzion", "soyuzmult", "smeshariki",
            "masha", "fixiki", "luntik", "tri kota", "multilandia", "tlum", "unicum", "radost", "deti", "detskiy",
            "mosfilm", "lenfilm", "kinomiks", "kinoprem", "kinohit", "kinosemya", "kinokomediya", "kinoseriya",
            "kinosvidanie", "kinouzhas", "dom kino", "evrokino", "russkiy", "rodnoe", "nashe", "lyubimoe", "feniks",
            "cinema", "hollywood", ".red", ".sci-fi", ".black", "tv xxi", "shot tv", "retro", "ussr", "vhs",
            "discovery", "national geographic", "nat geo", "animal planet", "history", "h2", "ocean tv", "rtg",
            "outdoor", "travel+adventure", "2x2", "comedy central", "eurosport", "setanta", "mma-tv"
        )
        return ruLatinWhitelist.any { lName.contains(it) }
    }

    fun isAllowedMusicChannel(name: String, url: String, rawGroup: String): Boolean {
        if (isArabicOrIndianChannel(name, rawGroup, url)) return false
        val lName = " ${name.lowercase()} "
        val lUrl = url.lowercase()
        val musicNoiseBan = listOf(
            "nigeria", "australia", "napoli", "hiru", "kayhan", "fusion tv", "impact tv",
            "kn music", "lamore", "yayin.com.tr", "cdni.live", "intv.mk", "nasatv.com.mk",
            "hiruhost", "cloudflarestream", "infomaniak", "streamlock.net"
        )
        if (musicNoiseBan.any { lName.contains(it) || lUrl.contains(it) }) return false
        if (isStrictlyRussianChannel(name, url, rawGroup)) return true
        val topEuroMusicWhitelist = listOf(
            " mtv", " vh1", "mezzo", "deluxe music", " mcm ", "trace urban", "clubbing tv",
            "stingray", "iconcerts", "now 70", "now 80", "now 90", "now rock", "kerrang",
            "kiss tv", "box hits", "the box", "4music", "4 music", "4fun", "4 fun",
            "stars.tv", "stars tv", "eska", "polo tv", "vox music", "rfm tv", "nrj hits",
            "m6 music", "cstar", "vevo", "qello", "classic music", "eurodance",
            "music box russia", "music box gold", "music box classic", "music box dance",
            "music box hits", "v2beat"
        )
        return topEuroMusicWhitelist.any { lName.contains(it) }
    }

    fun is2kOrHigherStream(rawLine: String, name: String, rawGroup: String, url: String): Boolean {
        val text = "$rawLine $name $rawGroup $url".lowercase()
        if (text.contains("4k") || text.contains("uhd") || text.contains("2160p") || text.contains("1440p") || text.contains("2k") || text.contains("ultra hd")) return true
        val resMatch = REGEX_RESOLUTION.find(text)
        if (resMatch != null) {
            val w = resMatch.groupValues[1].toIntOrNull() ?: 0
            val h = resMatch.groupValues[2].toIntOrNull() ?: 0
            if (w >= 2560 || h >= 1440) return true
        }
        return false
    }

    fun mapCategory(rawGroup: String, name: String, rawLine: String = "", url: String = ""): String {
        if (rawGroup.equals("2K", ignoreCase = true) || rawGroup.equals("4K", ignoreCase = true) ||
            (url.contains("rostelekom.xyz", ignoreCase = true) && is2kOrHigherStream(rawLine, name, rawGroup, url))) {
            return "2K"
        }
        val g = rawGroup.lowercase()
        val n = name.lowercase()
        val combined = "$g $n"
        return when {
            g == "детские" || combined.contains("детск") || combined.contains("мульт") || combined.contains("карусель") ||
                combined.contains("сказк") || combined.contains("малыш") || combined.contains("союзмульт") ||
                combined.contains("смешарик") || combined.contains("фиксик") || combined.contains("лунтик") ||
                combined.contains("барбоскин") || combined.contains("три кота") || combined.contains("маша и медведь") ||
                combined.contains("простоквашин") || combined.contains("ну, погоди") || combined.contains("ну погоди") ||
                combined.contains("ми-ми-мишк") || combined.contains("том и джер") || combined.contains("tom and jerry") ||
                combined.contains("скуби-ду") || combined.contains("масяня") || combined.contains("кроха") ||
                combined.contains("тлум") || combined.contains("рыжий") || combined.contains("смайлик") ||
                combined.contains("уникум") || combined.contains("мультиландия") || combined.contains("мультимузыка") ||
                combined.contains("радость моя") || combined.contains("капитан фантастика") || combined.contains("супергерои") ||
                combined.contains("стс kids") || combined.contains("sts kids") || combined.contains("типтоп") ||
                combined.contains("лёва") || combined.contains("лева ") || combined.contains("чижик") ||
                combined.contains("бебимульт") || combined.contains("multserial") || combined.contains("little hd") ||
                combined.contains("gulli") || combined.contains("tiji") || combined.contains("jimjam") || combined.contains("jim jam") ||
                combined.contains("kids") || combined.contains("cartoon") || combined.contains("nickelodeon") ||
                combined.contains("disney") || combined.contains("pixar") || combined.contains("nick jr") || combined.contains("nicktoons") ||
                combined.contains("boomerang") || combined.contains("анимац") ||
                n == "о!" || n.startsWith("о! ") || n.startsWith("ани ") || n == "ани" || n == "ani" -> "Детские"

            g == "музыка" || combined.contains("музык") || combined.contains("music") || combined.contains("муз-тв") ||
                combined.contains("муз тв") || combined.contains("музсоюз") || combined.contains("песни") ||
                combined.contains("mtv") || combined.contains("bridge") || combined.contains("шансон") ||
                combined.contains("ru.tv") || combined.contains("rutv") || combined.contains("ru tv") || combined.contains("1hd") ||
                combined.contains("европа плюс") || combined.contains("europa plus") || combined.contains("жара") ||
                combined.contains("mezzo") || combined.contains("vh1") || combined.contains("ля-минор") ||
                combined.contains("mcm") || combined.contains("clubbing") || combined.contains("eurodance") ||
                combined.contains("страна fm") || combined.contains("ретро фм") || combined.contains("караоке") ||
                combined.contains("trace") || combined.contains("deluxe music") -> "Музыка"

            g == "кино" || combined.contains("кино") || combined.contains("фильм") || combined.contains("сериал") ||
                combined.contains("movie") || combined.contains("cinema") || combined.contains("film") ||
                combined.contains("series") || combined.contains("amedia") || combined.contains("viju") ||
                combined.contains("tv1000") || combined.contains("иллюзион") || combined.contains("комеди") ||
                combined.contains("comedy") || combined.contains("драма") || combined.contains("детектив") ||
                combined.contains("триллер") || combined.contains("ужас") || combined.contains("страшное") || combined.contains("нст") ||
                combined.contains("фантастик") || combined.contains("sci-fi") || combined.contains("action") ||
                combined.contains("prem") || combined.contains("бестселлер") || combined.contains("роман") ||
                combined.contains("мосфильм") || combined.contains("ленфильм") || combined.contains("кинозал") ||
                combined.contains("зал ") || combined.contains("боевик") || combined.contains("вестерн") ||
                combined.contains("hollywood") || combined.contains("red hd") || combined.contains(".red") ||
                combined.contains("дом кино") || combined.contains("еврокино") || combined.contains("дорама") ||
                combined.contains("феникс") || combined.contains("сваты") || combined.contains("интерны") ||
                combined.contains("универ") || combined.contains("воронины") || combined.contains("гайдай") ||
                combined.contains("рязанов") || combined.contains("остросюжетн") || combined.contains("блокбастер") -> "Кино и сериалы"

            g == "спорт" || combined.contains("спорт") || combined.contains("sport") || combined.contains("футбол") ||
                combined.contains("матч") || combined.contains("match") || combined.contains("кхл") ||
                combined.contains("khl") || combined.contains("боец") || combined.contains("ufc") ||
                combined.contains("бокс") || combined.contains("теннис") || combined.contains("хоккей") ||
                combined.contains("eurosport") || combined.contains("арена") || combined.contains("старт") ||
                combined.contains("удар") || combined.contains("mma") -> "Спорт"

            g == "познавательные" || combined.contains("познават") || combined.contains("наука") || combined.contains("discovery") ||
                combined.contains("nature") || combined.contains("history") || combined.contains("истори") ||
                combined.contains("планета") || combined.contains("doc") || combined.contains("доктор") ||
                combined.contains("animal") || combined.contains("nat geo") || combined.contains("viasat explore") ||
                combined.contains("viasat nature") || combined.contains("viasat history") || combined.contains("техно") ||
                combined.contains("оружие") || combined.contains("арсенал") || combined.contains("охота") ||
                combined.contains("рыбалк") || combined.contains("рыболов") || combined.contains("дикий") ||
                combined.contains("авто") || combined.contains("драйв") || combined.contains("усадьба") ||
                combined.contains("бобёр") || combined.contains("загородн") || combined.contains("зоо") ||
                combined.contains("животн") || combined.contains("пёс и ко") || combined.contains("природ") ||
                combined.contains("travel") || combined.contains("путешеств") || combined.contains("поехали") ||
                combined.contains("турист") || combined.contains("rtg") || combined.contains("стихия") ||
                combined.contains("косм") || combined.contains("galaxy") || combined.contains("нано") ||
                combined.contains("тайна") || combined.contains("секретно") || combined.contains("365 дней") ||
                combined.contains("культура") || combined.contains("время") || combined.contains("кухня") ||
                combined.contains("еда") || combined.contains("телекафе") || combined.contains("вкусное") -> "Познавательные"

            g == "развлекательные" || combined.contains("развлеч") || combined.contains(" тнт") || n.startsWith("тнт") ||
                combined.contains("стс") || combined.contains("пятница") || combined.contains("суббота") ||
                combined.contains("домашний") || combined.contains("тв3") || combined.contains("ю ") || n == "ю" ||
                combined.contains("че!") || n == "че" || combined.contains("2x2") || combined.contains("2х2") ||
                combined.contains("солнце") || combined.contains("квн") || combined.contains("сарафан") ||
                combined.contains("анекдот") || combined.contains("пельмени") || combined.contains("6 кадров") ||
                combined.contains("городок") || combined.contains("наша раша") || combined.contains("ностальгия") ||
                combined.contains("модерн") || combined.contains("осп") || combined.contains("юмор") ||
                combined.contains("entertain") || combined.contains("fashion") -> "Развлечения"

            g == "новости" || combined.contains("новост") || combined.contains("news") || combined.contains("инфо") ||
                combined.contains("россия 24") || combined.contains("рбк") || combined.contains("мир 24") ||
                combined.contains("известия") || combined.contains("первый") || combined.contains("россия 1") ||
                combined.contains("нтв") || combined.contains("тв центр") || combined.contains("твц") ||
                combined.contains("пятый") || combined.contains("рен") || combined.contains("звезда") ||
                combined.contains("отр") || combined.contains("спас") || combined.contains("360") ||
                combined.contains("москва 24") || combined.contains("санкт-петербург") || combined.contains("псков") ||
                combined.contains("вместе-рф") || combined.contains("общие") || combined.contains("федерал") -> "Новости и общие"

            else -> "Прочее"
        }
    }

    fun parseM3uReaderIntoMap(
        reader: BufferedReader,
        targetMap: LinkedHashMap<String, Channel>,
        customCategoryMap: Map<String, String>,
        filterBuiltinRules: Boolean,
        onlyRussianAllCategories: Boolean = false,
        keepOriginalGroups: Boolean = false
    ) {
        var currentName = ""
        var currentGroup = ""
        var currentTvgId = ""
        var currentRawInfLine = ""
        var line: String? = reader.readLine()
        while (line != null) {
            val l = line.trim()
            if (l.startsWith("#EXTINF", ignoreCase = true)) {
                currentRawInfLine = l
                currentName = cleanChannelDisplayName(l.substringAfterLast(",", "Канал").trim())
                val groupMatch = REGEX_GROUP_TITLE.find(l)
                currentGroup = groupMatch?.groupValues?.get(1)?.trim() ?: ""
                val idMatch = REGEX_TVG_ID.find(l)
                currentTvgId = idMatch?.groupValues?.get(1)?.trim() ?: ""
            } else if (l.startsWith("#EXTGRP:", ignoreCase = true)) {
                val extGrp = l.substringAfter(":").trim()
                if (extGrp.isNotEmpty() && currentGroup.isEmpty()) {
                    currentGroup = extGrp
                }
            } else if (l.startsWith("http://", ignoreCase = true) || l.startsWith("https://", ignoreCase = true) || l.startsWith("rtmp://", ignoreCase = true)) {
                val isDeadHost = l.contains("sc.id-tv.kz", ignoreCase = true) ||
                    l.contains("ott-cdn.ucom.am", ignoreCase = true) ||
                    l.contains("jmp2.uk", ignoreCase = true) ||
                    l.contains("plutotv.net", ignoreCase = true) ||
                    l.contains("stream.mcquack.net", ignoreCase = true) ||
                    l.contains("iptv-24.net", ignoreCase = true) ||
                    l.contains("tvstitch.com", ignoreCase = true) ||
                    l.contains("freem3u.xyz", ignoreCase = true) ||
                    l.contains("103.255.250.188", ignoreCase = true) ||
                    l.contains("170.83.49.66", ignoreCase = true)
                val isAdultGroup = !keepOriginalGroups && (currentGroup.equals("взрослые", ignoreCase = true) || currentGroup.contains("18+", ignoreCase = true) || currentGroup.contains("adult", ignoreCase = true))
                if (currentName.isNotEmpty() && !isDeadHost && !isAdultGroup) {
                    var skip = false
                    if (filterBuiltinRules) {
                        if (isArabicOrIndianChannel(currentName, currentGroup, l) || isRegionalTimeShiftDuplicate(currentName)) {
                            skip = true
                        } else {
                            val mappedCat = mapCategory(currentGroup, currentName, currentRawInfLine, l)
                            val isStrictRu = isStrictlyRussianChannel(currentName, l, currentGroup)
                            if (onlyRussianAllCategories) {
                                if (!isStrictRu) skip = true
                            } else {
                                when (mappedCat) {
                                    "2K" -> {
                                        if (!l.contains("rostelekom.xyz", ignoreCase = true)) skip = true
                                    }
                                    "Детские", "Кино и сериалы", "Познавательные", "Развлечения", "Новости и общие", "Прочее" -> {
                                        if (!isStrictRu) skip = true
                                    }
                                    "Музыка" -> {
                                        if (!isAllowedMusicChannel(currentName, l, currentGroup)) skip = true
                                    }
                                }
                            }
                        }
                    }
                    if (!skip) {
                        val category = if (keepOriginalGroups) {
                            currentGroup.ifEmpty { "Общие" }
                        } else {
                            customCategoryMap[l] ?: mapCategory(currentGroup, currentName, currentRawInfLine, l)
                        }
                        val streamIdKey = l.substringBefore("?").lowercase()
                        val dedupKey = if (keepOriginalGroups) {
                            "${category}_${normalizeName(currentName)}_${l.hashCode()}"
                        } else if (category == "2K" || category == "Детские" || category == "Кино и сериалы" || category == "Музыка" || category == "Познавательные" || category == "Развлечения") {
                            normalizeName(currentName) + "_" + category + "_" + streamIdKey.hashCode()
                        } else {
                            normalizeName(currentName) + "_" + category
                        }
                        if (!targetMap.containsKey(dedupKey)) {
                            targetMap[dedupKey] = Channel(currentName, l, category, currentTvgId)
                        }
                    }
                    currentName = ""
                    currentGroup = ""
                    currentTvgId = ""
                    currentRawInfLine = ""
                }
            }
            line = reader.readLine()
        }
        reader.close()
    }

    fun saveEpgToStreamCache(cacheFile: File, mapByName: Map<String, List<EpgProgram>>) {
        try {
            val tmpFile = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            BufferedWriter(FileWriter(tmpFile)).use { writer ->
                for ((normName, progs) in mapByName) {
                    if (normName.isEmpty() || progs.isEmpty()) continue
                    val row = JSONObject()
                    row.put("k", normName)
                    val pArr = JSONArray()
                    for (p in progs) {
                        val po = JSONObject()
                        po.put("t", p.title)
                        po.put("s", p.startMs)
                        po.put("e", p.stopMs)
                        pArr.put(po)
                    }
                    row.put("p", pArr)
                    writer.write(row.toString())
                    writer.newLine()
                }
            }
            if (cacheFile.exists()) cacheFile.delete()
            tmpFile.renameTo(cacheFile)
        } catch (_: Exception) {}
    }

    fun restoreEpgFromStreamCache(cacheFile: File, outByName: ConcurrentHashMap<String, MutableList<EpgProgram>>): Int {
        if (!cacheFile.exists()) return 0
        var count = 0
        try {
            val now = System.currentTimeMillis()
            val minTime = now - 2 * 3600_000L
            BufferedReader(FileReader(cacheFile)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (line.isNotBlank()) {
                        val row = JSONObject(line)
                        val key = row.optString("k", "")
                        val pArr = row.optJSONArray("p")
                        if (key.isNotEmpty() && pArr != null) {
                            val list = mutableListOf<EpgProgram>()
                            for (i in 0 until pArr.length()) {
                                val po = pArr.getJSONObject(i)
                                val stop = po.optLong("e", 0L)
                                if (stop >= minTime) {
                                    list.add(EpgProgram(po.optString("t", ""), "", po.optLong("s", 0L), stop))
                                    count++
                                }
                            }
                            if (list.isNotEmpty()) outByName[key] = list
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (_: Exception) {}
        return count
    }
}
