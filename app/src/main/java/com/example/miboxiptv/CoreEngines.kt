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
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object CoreEngines {
    private val dohCache = ConcurrentHashMap<String, String>()

    val russianSmartTvAgents = listOf(
        "Mozilla/5.0 (SmartHub; SMART-TV; U; Linux/SmartTV; Maple2012) AppleWebKit/534.7 (KHTML, like Gecko) SmartTV Safari/534.7",
        "Wink/1.38.2 (Linux; Android 11; MiBox) ExoPlayerLib/2.19.1",
        "VLC/3.0.20 LibVLC/3.0.20",
        "Lavf/60.3.100"
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

    fun resolveHostViaCloudflareDoH(host: String): String? {
        dohCache[host]?.let { return it }
        return try {
            InetAddress.getByName(host).hostAddress?.also { dohCache[host] = it }
        } catch (_: Exception) {
            try {
                val conn = URL("https://1.1.1.1/dns-query?name=$host&type=A").openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/dns-json")
                conn.connectTimeout = 3000; conn.readTimeout = 3000
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val ans = json.optJSONArray("Answer")
                    if (ans != null) {
                        for (i in 0 until ans.length()) {
                            val item = ans.getJSONObject(i)
                            if (item.optInt("type") == 1) {
                                val ip = item.optString("data")
                                if (ip.isNotEmpty()) {
                                    dohCache[host] = ip
                                    return ip
                                }
                            }
                        }
                    }
                }
                null
            } catch (_: Exception) { null }
        }
    }

    fun openStreamWithBypass(rawUrl: String, uaIndex: Int, dohEnabled: Boolean, timeoutMs: Int = 7500): InputStream {
        var currentUrl = rawUrl
        for (redirectAttempt in 0..3) {
            val parsed = URL(currentUrl)
            val origin = "${parsed.protocol}://${parsed.host}"
            val conn = try {
                (parsed.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", russianSmartTvAgents[uaIndex % russianSmartTvAgents.size])
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Referer", "$origin/")
                    setRequestProperty("Origin", origin)
                    setRequestProperty("Connection", "keep-alive")
                    instanceFollowRedirects = true
                    connectTimeout = timeoutMs; readTimeout = timeoutMs
                }
            } catch (e: Exception) {
                if (dohEnabled) {
                    val ip = resolveHostViaCloudflareDoH(parsed.host)
                    if (ip != null) {
                        val fallbackUrl = URL(currentUrl.replaceFirst(parsed.host, ip))
                        (fallbackUrl.openConnection() as HttpURLConnection).apply {
                            setRequestProperty("Host", parsed.host)
                            setRequestProperty("User-Agent", russianSmartTvAgents[uaIndex % russianSmartTvAgents.size])
                            connectTimeout = timeoutMs; readTimeout = timeoutMs
                        }
                    } else throw e
                } else throw e
            }

            val code = conn.responseCode
            if (code in 300..308) {
                val loc = conn.getHeaderField("Location")
                if (!loc.isNullOrEmpty()) { currentUrl = loc; continue }
            }
            if (code in 200..299) return conn.inputStream
            throw Exception("HTTP $code")
        }
        throw Exception("Too many redirects")
    }

    fun cleanChannelDisplayName(raw: String): String {
        var s = raw
        s = s.replace(Regex("\\[[^\\]]*\\]"), " ")
        s = s.replace(Regex("\\([^)]*\\)"), " ")
        s = s.replace(Regex("(?i)\\b(1080p|1080i|720p|576p|540p|480p|360p|2160p|1440p|50fps|60fps|25fps|fhd|uhd|qhd|quadhd|fullhd|hd|sd|hevc|h264)\\b"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s.trim('-', '.', '•', '|', '_').ifEmpty { raw.trim() }
    }

    fun normalizeName(raw: String): String {
        val clean = cleanChannelDisplayName(raw).lowercase()
            .replace("ё", "е")
            .replace("setanta", "сетанта").replace("sport", "спорт").replace("match", "матч")
            .replace("futbol", "футбол").replace("football", "футбол").replace("kino", "кино")
            .replace("discovery", "дискавери").replace("national geographic", "нэшнлгеографик")
            .replace("nat geo", "натгео").replace("eurosport", "евроспорт").replace("viasat", "виасат")
            .replace("history", "хистори").replace("nickelodeon", "никелодеон").replace("cartoon", "картун")
            .replace("tv1000", "тв1000").replace("tv 1000", "тв1000").replace("action", "экшн")
            .replace(Regex("\\b(телеканал|канал|тв|tv|ru|rus|original|international|int)\\b"), "")
        val sb = StringBuilder()
        for (c in clean) if (c in 'a'..'z' || c in 'а'..'я' || c in '0'..'9') sb.append(c)
        return sb.toString()
    }

    fun isArabicOrIndianChannel(rawName: String, rawGroup: String, url: String): Boolean {
        if (rawName.any { it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F || it.code in 0x0900..0x097F }) return true
        val lower = "$rawName $rawGroup $url".lowercase()
        val blockedKeywords = listOf(
            "arab", "al jazeera", "aljazeera", "rotana", "mbc", "osn", "shahid", "alarabiya", "al arabiya",
            "beinsport", "bein ", "alkass", "al kass", "dubai", "abu dhabi", "sharjah", "kuwait", "qatar",
            "saudi", "egypt", "nilesat", "badr", "koora", "quran", "sunnah", "iqraa", "majd",
            "india", "hindi", "bollywood", "punjabi", "tamil", "telugu", "malayalam", "kannada", "marathi",
            "bangla", "bengali", "urdu", "b4u", "zing", "zee ", "star bharat", "star plus", "sony sab",
            "colors", "aaj tak", "ndtv", "zoom tv", "9xm", "mastiii", "sun tv", "raj tv", "surya"
        )
        return blockedKeywords.any { lower.contains(it) }
    }

    fun isRadioStream(rawName: String, rawGroup: String, url: String): Boolean {
        val l = "$rawName $rawGroup $url".lowercase()
        if (url.endsWith(".mp3", true) || url.endsWith(".aac", true) || url.endsWith(".ogg", true) || url.endsWith(".flac", true)) return true
        if (l.contains("радио") || l.contains("radio") || l.contains("fm ") || l.contains(" fm") || l.contains("audio only")) {
            if (!l.contains("tv") && !l.contains("тв") && !l.contains("видео")) return true
        }
        return false
    }

    fun isStrictlyRussianChannel(name: String, url: String, group: String = ""): Boolean {
        if (name.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }) return true
        val l = "$name $group $url".lowercase()
        val foreignBlock = listOf("english", "espanol", "spanish", "french", "deutsch", "german", "italiano", "polski", "turk", "latino", "brasil", "portugues", "arabic", "farsi", "kurd", "uk ", "usa ", "us ")
        if (foreignBlock.any { l.contains(it) }) return false

        val ruMarkers = listOf(
            ".ru/", ".su/", ".kz/", ".by/", "vgtrk", "1tv", "ntv", "tnt", "sts", "muz-tv", "ru.tv", "bridge",
            "tv1000", "tv 1000", "viju", "vip ", "viasat", "amedia", "kinoprem", "kinohit", "kinomiks", "dom kino",
            "kinozal", "cineman", "bcu", "kion", "okko", "premier", "wink", "smotrim", "match", "khl", "karusel", "mult",
            "hollywood", "red ", "sci-fi", "scifi", "sony", "fox", "paramount", "comedy", "trash", "fan ", "eurokino",
            "illuzion", "feniks", "cinema", "film", "nickelodeon", "nick jr", "nickjr", "cartoon", "boomerang",
            "gulli", "tiji", "jimjam", "jim jam", "da vinci", "davinci", "disney", "ani ", "tlum", "2x2"
        )
        return ruMarkers.any { l.contains(it) }
    }

    fun isEuropeanOrRussianMusic(name: String, url: String): Boolean {
        if (isStrictlyRussianChannel(name, url)) return true
        val l = "$name $url".lowercase()
        val euMusic = listOf("mtv", "vh1", "mezzo", "stingray", "bridge", "mcm", "deluxe music", "trace", "europa", "clubbing", "4fun", "eska", "kiss tv", "kerrang", "magic tv", "box hits", "now 70s", "now 80s", "now 90s", "retro music", "ochevidnoe")
        return euMusic.any { l.contains(it) }
    }

    fun mapToTenCategories(rawGroup: String, name: String): String {
        val text = (rawGroup + " " + name).lowercase()
        return when {
            text.contains("2k") || text.contains("qhd") || text.contains("quadhd") || text.contains("1440p") || text.contains("2160p") || text.contains("uhd") || text.contains("4k") -> "2K"
            text.contains("взросл") || text.contains("18+") || text.contains("adult") || text.contains("xxx") -> "Прочее"
            text.contains("спорт") || text.contains("футбол") || text.contains("матч") || text.contains("сетанта") || text.contains("setanta") || text.contains("sport") || text.contains("khl") || text.contains("кхл") || text.contains("ufc") || text.contains("бокс") -> "Спорт"
            text.contains("детск") || text.contains("мульт") || text.contains("kids") || text.contains("карусель") || text.contains("nickelodeon") || text.contains("nick jr") || text.contains("cartoon") || text.contains("boomerang") || text.contains("gulli") || text.contains("tiji") || text.contains("jimjam") || text.contains("disney") || text.contains("тлум") || text.contains("смайлик") || text.contains("рыжий") || text.contains("мульти") || text.contains("сказк") -> "Детские"
            text.contains("кино") || text.contains("фильм") || text.contains("сериал") || text.contains("кинозал") || text.contains("cineman") || text.contains("bcu") || text.contains("viju") || text.contains("vip ") || text.contains("amedia") || text.contains("movie") || text.contains("cinema") || text.contains("film") || text.contains("tv1000") || text.contains("tv 1000") || text.contains("иллюзион") || text.contains("комедия") || text.contains("детектив") || text.contains("бестселлер") || text.contains("роман") || text.contains("нст") || text.contains("ужас") || text.contains("триллер") || text.contains("боевик") || text.contains("фантаст") || text.contains("hollywood") || text.contains("мосфильм") -> "Кино и сериалы"
            text.contains("наук") || text.contains("док") || text.contains("познав") || text.contains("science") || text.contains("discovery") || text.contains("nat geo") || text.contains("animal") || text.contains("планет") || text.contains("viasat") || text.contains("история") || text.contains("техно") || text.contains("оружие") || text.contains("охота") -> "Познавательные"
            text.contains("муз") || text.contains("music") || text.contains("mtv") || text.contains("bridge") || text.contains("stingray") || text.contains("муз-тв") || text.contains("ru.tv") || text.contains("шансон") || text.contains("жара") || text.contains("mezzo") -> "Музыка"
            text.contains("развлеч") || text.contains("шоу") || text.contains("тнт") || text.contains("стс") || text.contains("пятница") || text.contains("суббота") || text.contains("ю тв") || text.contains("че!") || text.contains("2x2") || text.contains("2х2") || text.contains("квн") || text.contains("сарафан") || text.contains("юмор") || text.contains("entertainment") || text.contains("comedy") || text.contains("fashion") || text.contains("lifestyle") || text.contains("еда") || text.contains("кухня") -> "Развлечения"
            text.contains("новост") || text.contains("общ") || text.contains("news") || text.contains("россия") || text.contains("первый") || text.contains("нтв") || text.contains("рбк") || text.contains("рен") || text.contains("твц") || text.contains("пятый") || text.contains("домашний") || text.contains("звезда") || text.contains("мир") -> "Новости и общие"
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
        var currentRawName = "Канал"; var currentGroup = ""; var currentTvgId = ""
        val quote = 34.toChar().toString()
        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:")) {
                    currentRawName = trimmed.substringAfterLast(",").trim()
                    currentGroup = if (trimmed.contains("group-title=$quote")) trimmed.substringAfter("group-title=$quote").substringBefore(quote).trim() else ""
                    currentTvgId = if (trimmed.contains("tvg-id=$quote")) trimmed.substringAfter("tvg-id=$quote").substringBefore(quote).trim() else ""
                } else if (trimmed.startsWith("#EXTGRP:")) {
                    currentGroup = trimmed.substringAfter("#EXTGRP:").trim()
                } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    if (isArabicOrIndianChannel(currentRawName, currentGroup, trimmed)) continue
                    if (isRadioStream(currentRawName, currentGroup, trimmed)) continue
                    if (onlyRussianAllCategories && !isStrictlyRussianChannel(currentRawName, trimmed, currentGroup)) continue

                    val cat = customCategoryMap[trimmed] ?: if (keepOriginalGroups && currentGroup.isNotEmpty()) {
                        currentGroup
                    } else {
                        mapToTenCategories(currentGroup, currentRawName)
                    }

                    if (filterBuiltinRules) {
                        if ((cat == "Кино и сериалы" || cat == "Детские") && !isStrictlyRussianChannel(currentRawName, trimmed, currentGroup)) continue
                        if (cat == "Музыка" && !isEuropeanOrRussianMusic(currentRawName, trimmed)) continue
                    }

                    val cleanTitle = cleanChannelDisplayName(currentRawName)
                    val ch = Channel(cleanTitle, trimmed, cat, currentTvgId)
                    val key = (normalizeName(cleanTitle) + "_" + cat).ifEmpty { trimmed }
                    val existing = targetMap[key]
                    if (existing == null || currentRawName.contains("50fps", true) || currentRawName.contains("1080", true)) {
                        targetMap[key] = ch
                    }
                }
            }
        }
    }

    fun saveEpgToStreamCache(file: File, byName: Map<String, List<EpgProgram>>) {
        try {
            BufferedWriter(FileWriter(file)).use { writer ->
                for ((k, list) in byName) {
                    val arr = JSONArray()
                    for (p in list) {
                        arr.put(JSONObject().apply {
                            put("t", p.title); put("d", p.desc); put("s", p.startMs); put("e", p.stopMs)
                        })
                    }
                    val lineObj = JSONObject().apply { put("k", k); put("p", arr) }
                    writer.write(lineObj.toString())
                    writer.newLine()
                }
            }
        } catch (_: Exception) {}
    }

    fun restoreEpgFromStreamCache(file: File, outMap: ConcurrentHashMap<String, MutableList<EpgProgram>>): Int {
        return try {
            val now = System.currentTimeMillis()
            var total = 0
            val temp = HashMap<String, MutableList<EpgProgram>>()
            BufferedReader(FileReader(file)).useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    val lineObj = JSONObject(line)
                    val k = lineObj.optString("k")
                    val arr = lineObj.optJSONArray("p") ?: continue
                    val list = mutableListOf<EpgProgram>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val stop = o.getLong("e")
                        if (stop >= now - 2 * 3600_000L) {
                            list.add(EpgProgram(o.getString("t"), o.optString("d", ""), o.getLong("s"), stop))
                            total++
                        }
                    }
                    if (k.isNotEmpty() && list.isNotEmpty()) temp[k] = list
                }
            }
            if (total > 0) {
                outMap.clear()
                outMap.putAll(temp)
            }
            total
        } catch (_: Exception) { 0 }
    }

    fun parseXmltvDate(raw: String): Long {
        if (raw.length < 14) return 0L
        return try {
            val clean = raw.trim()
            val digits = clean.take(14)
            val tzPart = clean.drop(14).trim()
            val sdf = if (tzPart.startsWith("+") || tzPart.startsWith("-")) {
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            } else {
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT+3") }
            }
            val parseTarget = if (tzPart.startsWith("+") || tzPart.startsWith("-")) "$digits $tzPart" else digits
            sdf.parse(parseTarget)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }
}
