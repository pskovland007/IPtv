package com.example.miboxiptv

import org.json.JSONArray
import org.json.JSONObject
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

    val russianSmartTvAgents = listOf(
        "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.5) AppleWebKit/537.36 (KHTML, like Gecko) Version/6.5 TV Safari/537.36",
        "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.211 Safari/537.36 WebAppManager",
        "Wink/1.44.0 (AndroidTV; Android 11; Xiaomi Mi Box S)",
        "Okko/7.22.0 (SmartTV; Linux; Android 11)",
        "VLC/3.0.20 LibVLC/3.0.20"
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

    fun resolveHostViaCloudflareDoH(host: String, dohEnabled: Boolean): String? {
        if (!dohEnabled || host.isEmpty()) return null
        if (host.all { it.isDigit() || it == '.' }) return host
        dohDnsCache[host]?.let { return it }
        try {
            val dohUrl = "https://1.1.1.1/dns-query?name=$host&type=A"
            val conn = (URL(dohUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/dns-json")
                connectTimeout = 2800
                readTimeout = 2800
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

    fun openStreamWithBypass(urlStr: String, userAgentIndex: Int, dohEnabled: Boolean, timeoutMs: Int = 10000): InputStream {
        var currentUrl = urlStr.substringBefore("#")
        var redirects = 0
        while (redirects < 6) {
            val parsed = URL(currentUrl)
            if (dohEnabled) resolveHostViaCloudflareDoH(parsed.host, true)
            val origin = "${parsed.protocol}://${parsed.host}"
            val activeAgent = russianSmartTvAgents[userAgentIndex % russianSmartTvAgents.size]
            val conn = (parsed.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", activeAgent)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.7")
                setRequestProperty("Origin", origin)
                setRequestProperty("Referer", "$origin/")
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
        }
        return URL(currentUrl).openStream()
    }

    fun normalizeName(s: String): String =
        s.lowercase(Locale.getDefault()).replace(Regex("[^a-zа-я0-9]"), "")

    fun cleanChannelDisplayName(rawName: String): String {
        val cleaned = rawName
            .replace(Regex("\\(\\s*\\d+\\s*[pPрР]\\s*\\)"), " ")
            .replace(Regex("\\[\\s*\\d+\\s*[pPрР]\\s*\\]"), " ")
            .replace(Regex("\\b\\d{3,4}[pPрР]\\b"), " ")
            .replace(Regex("\\[[^\\]]*(Geo|Not 24|720|1080|576|480)[^\\]]*\\]", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return cleaned.ifEmpty { rawName.trim() }
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
        val low = combined.lowercase()
        val bannedTokens = listOf(
            "arabic", "arabia", "al jazeera", "aljazeera", "mbc", "rotana", "osn ", "art ", "nile ",
            "dubai", "sharjah", "abu dhabi", "al arabiya", "alarabiya", "iqraa", "majid", "spacetoon",
            "zee ", "sony sab", "star plus", "star bharat", "colors ", "aaj tak", "ndtv", "dd national",
            "sun tv", "asianet", "bollywood", "india", "hindi", "tamil", "telugu", "punjabi", "urdu",
            "bangla", "pakistan", "geo news", "ary digital", "hum tv", "samaa", "kurd", "farsi", "persian"
        )
        return bannedTokens.any { low.contains(it) }
    }

    fun isStrictlyRussianChannel(name: String, url: String, rawGroup: String = ""): Boolean {
        if (name.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }) return true
        val lName = name.lowercase()
        val lUrl = url.lowercase()
        val lGroup = rawGroup.lowercase()
        if (lUrl.contains(".ru/") || lUrl.contains(".su/") || lUrl.contains(".by/") ||
            lUrl.contains("vgtrk") || lUrl.contains("1tv") || lUrl.contains("ntv") || lUrl.contains("smotrim") ||
            lUrl.contains("tricolor") || lUrl.contains("wink") || lUrl.contains("peers") || lUrl.contains("catcast") ||
            lUrl.contains("cdnvideo") || lUrl.contains("ngenix") || lUrl.contains("mediavitrina") ||
            lUrl.contains("uma.media") || lUrl.contains("1hd.ru") || lUrl.contains("gcdn.co")) {
            return true
        }
        if (lGroup.contains("росси") || lGroup.contains("русск") || lGroup.contains("детск") || lGroup.contains("мульт") || lGroup.contains("cccp") || lGroup.contains("ссср")) {
            return true
        }
        val ruLatinWhitelist = listOf(
            "viju", "tv1000", "amedia", "karusel", "mult", "tiji", "gulli", "nickelodeon ru", "nick jr ru",
            "cartoon network ru", "boomerang ru", "jimjam ru", "da vinci", "sts kids", "ani ", "fan hd",
            "bridge tv", "bridge ", "muz tv", "muz-tv", "ru.tv", "rutv", "1hd", "europa plus", "zhara", "shanson",
            "tnt", "sts", "ren tv", "ntv", "match", "khl", "kino", "illuzion", "soyuzmult", "smeshariki",
            "masha", "fixiki", "luntik", "tri kota", "multilandia", "tlum", "unicum", "radost", "deti", "detskiy"
        )
        return ruLatinWhitelist.any { lName.contains(it) }
    }

    fun is2kOrHigherStream(rawLine: String, name: String, rawGroup: String, url: String): Boolean {
        val text = "$rawLine $name $rawGroup $url".lowercase()
        if (text.contains("4k") || text.contains("uhd") || text.contains("2160p") || text.contains("1440p") || text.contains("2k") || text.contains("ultra hd") || text.contains("hevc")) return true
        val resMatch = Regex("(\\d{3,4})x(\\d{3,4})").find(text)
        if (resMatch != null) {
            val w = resMatch.groupValues[1].toIntOrNull() ?: 0
            val h = resMatch.groupValues[2].toIntOrNull() ?: 0
            if (w >= 2560 || h >= 1440) return true
        }
        return false
    }

    fun mapCategory(rawGroup: String, name: String, rawLine: String = "", url: String = ""): String {
        if (is2kOrHigherStream(rawLine, name, rawGroup, url)) return "2K"
        val g = rawGroup.lowercase()
        val n = name.lowercase()
        val combined = "$g $n"
        return when {
            combined.contains("дет") || combined.contains("мульт") || combined.contains("карусель") ||
                combined.contains("сказк") || combined.contains("малыш") || combined.contains("союзмульт") ||
                combined.contains("смешарик") || combined.contains("фиксик") || combined.contains("лунтик") ||
                combined.contains("барбоскин") || combined.contains("три кота") || combined.contains("маша и медведь") ||
                combined.contains("простоквашин") || combined.contains("ну, погоди") || combined.contains("ми-ми-мишк") ||
                combined.contains("тлум") || combined.contains("рыжий") || combined.contains("смайлик") ||
                combined.contains("уникум") || combined.contains("мультиландия") || combined.contains("мультимузыка") ||
                combined.contains("радость моя") || combined.contains("капитан фантастика") || combined.contains("детский") ||
                combined.contains("стс kids") || combined.contains("sts kids") || combined.contains("солнце") ||
                combined.contains("лёва") || combined.contains("лева ") || combined.contains("чижик") ||
                combined.contains("gulli") || combined.contains("tiji") || combined.contains("jimjam") ||
                combined.contains("kids") || combined.contains("cartoon") || combined.contains("nickelodeon") ||
                combined.contains("disney") || combined.contains("nick jr") || combined.contains("boomerang") ||
                combined.contains("anim") || combined.contains("анимац") || combined.contains("аниме") ||
                n == "о!" || n.startsWith("о! ") || n.startsWith("ани ") || n == "ани" -> "Детские"

            combined.contains("музык") || combined.contains("music") || combined.contains("муз") ||
                combined.contains("mtv") || combined.contains("bridge") || combined.contains("шансон") ||
                combined.contains("ru.tv") || combined.contains("rutv") || combined.contains("1hd") ||
                combined.contains("европа плюс") || combined.contains("europa plus") || combined.contains("жара") ||
                combined.contains("mezzo") || combined.contains("vh1") || combined.contains("ля-минор") ||
                combined.contains("mcm") || combined.contains("clubbing") || combined.contains("hit ") ||
                combined.contains("dance") || combined.contains("rock") || combined.contains("pop ") ||
                combined.contains("jazz") || combined.contains("trace") || combined.contains("deluxe music") -> "Музыка"

            combined.contains("кино") || combined.contains("фильм") || combined.contains("сериал") ||
                combined.contains("movie") || combined.contains("cinema") || combined.contains("film") ||
                combined.contains("series") || combined.contains("amedia") || combined.contains("viju") ||
                combined.contains("tv1000") || combined.contains("иллюзион") || combined.contains("комеди") ||
                combined.contains("comedy") || combined.contains("драма") || combined.contains("детектив") ||
                combined.contains("триллер") || combined.contains("ужас") || combined.contains("нст") ||
                combined.contains("фантастик") || combined.contains("sci-fi") || combined.contains("action") ||
                combined.contains("prem") || combined.contains("бестселлер") || combined.contains("роман") ||
                combined.contains("мосфильм") || combined.contains("ленфильм") || combined.contains("кинозал") ||
                combined.contains("hollywood") || combined.contains("red hd") || combined.contains("дом кино") ||
                combined.contains("еврокино") || combined.contains("дорама") || combined.contains("феникс") -> "Кино и сериалы"

            combined.contains("спорт") || combined.contains("sport") || combined.contains("футбол") ||
                combined.contains("матч") || combined.contains("match") || combined.contains("кхл") ||
                combined.contains("khl") || combined.contains("боец") || combined.contains("ufc") ||
                combined.contains("бокс") || combined.contains("теннис") || combined.contains("хоккей") ||
                combined.contains("eurosport") || combined.contains("арена") || combined.contains("старт") -> "Спорт"

            combined.contains("познават") || combined.contains("наука") || combined.contains("discovery") ||
                combined.contains("nature") || combined.contains("history") || combined.contains("истори") ||
                combined.contains("планета") || combined.contains("doc") || combined.contains("док") ||
                combined.contains("animal") || combined.contains("nat geo") || combined.contains("viasat explore") ||
                combined.contains("viasat nature") || combined.contains("viasat history") || combined.contains("техно") ||
                combined.contains("оружие") || combined.contains("охота") || combined.contains("рыбалк") ||
                combined.contains("авто") || combined.contains("усад") || combined.contains("бобёр") ||
                combined.contains("зоо") || combined.contains("travel") || combined.contains("путешеств") -> "Познавательные"

            combined.contains("новост") || combined.contains("news") || combined.contains("инфо") ||
                combined.contains("россия 24") || combined.contains("рбк") || combined.contains("мир 24") ||
                combined.contains("известия") || combined.contains("первый") || combined.contains("россия 1") ||
                combined.contains("нтв") || combined.contains("тв центр") || combined.contains("твц") ||
                combined.contains("пятый") || combined.contains("рен") || combined.contains("звезда") ||
                combined.contains("отр") || combined.contains("спас") || combined.contains("360") ||
                combined.contains("москва 24") || combined.contains("общие") || combined.contains("федерал") -> "Новости и общие"

            combined.contains("развлеч") || combined.contains(" тнт") || n.startsWith("тнт") ||
                combined.contains("стс") || combined.contains("пятница") || combined.contains("суббота") ||
                combined.contains("домашний") || combined.contains("ю ") || n == "ю" ||
                combined.contains("че!") || n == "че" || combined.contains("2x2") || combined.contains("2х2") ||
                combined.contains("квн") || combined.contains("сарафан") || combined.contains("еда") ||
                combined.contains("кухня") || combined.contains("entertain") || combined.contains("fashion") -> "Развлечения"

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
                val groupMatch = Regex("group-title=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(l)
                currentGroup = groupMatch?.groupValues?.get(1)?.trim() ?: ""
                val idMatch = Regex("tvg-id=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(l)
                currentTvgId = idMatch?.groupValues?.get(1)?.trim() ?: ""
            } else if (l.startsWith("http://", ignoreCase = true) || l.startsWith("https://", ignoreCase = true) || l.startsWith("rtmp://", ignoreCase = true)) {
                if (currentName.isNotEmpty() && !l.contains("sc.id-tv.kz", ignoreCase = true)) {
                    var skip = false
                    if (filterBuiltinRules) {
                        if (isArabicOrIndianChannel(currentName, currentGroup, l)) {
                            skip = true
                        } else {
                            val mappedCat = mapCategory(currentGroup, currentName, currentRawInfLine, l)
                            val isStrictRu = isStrictlyRussianChannel(currentName, l, currentGroup)
                            if (onlyRussianAllCategories) {
                                if (!isStrictRu) skip = true
                            } else if (mappedCat == "Детские" || mappedCat == "Новости и общие" || mappedCat == "Развлечения" || mappedCat == "Прочее") {
                                // В Детских — строго только русскоязычные каналы! В Музыке — разрешены и русские, и мировые.
                                if (!isStrictRu) skip = true
                            }
                        }
                    }
                    if (!skip) {
                        val category = customCategoryMap[l] ?: if (keepOriginalGroups) {
                            currentGroup.ifEmpty { mapCategory(currentGroup, currentName, currentRawInfLine, l) }
                        } else {
                            mapCategory(currentGroup, currentName, currentRawInfLine, l)
                        }
                        val hostKey = try { URL(l.substringBefore("#")).host.lowercase() } catch (_: Exception) { "" }
                        val dedupKey = if (category == "Детские" || category == "Музыка") {
                            normalizeName(currentName) + "_" + category + "_" + hostKey
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
