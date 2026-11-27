package com.example.miboxiptv

import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.channels.SocketChannel
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkHelper {

    data class NetworkConfig(
        val useDoh: Boolean = true,
        val useDpiBypass: Boolean = true,
        val proxyUrl: String = ""
    )

    @Volatile
    var activeConfig: NetworkConfig = NetworkConfig()

    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val trustAllSslFactory by lazy {
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        sc.socketFactory
    }

    private val dohHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .dns(Dns.SYSTEM)
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            applyTrustAllSsl(builder)
        }
        builder.build()
    }

    fun updateActiveConfig(useDoh: Boolean, useDpiBypass: Boolean, proxyUrl: String) {
        val cleanProxy = proxyUrl.trim()
        val next = NetworkConfig(useDoh = useDoh, useDpiBypass = useDpiBypass, proxyUrl = cleanProxy)
        if (next != activeConfig) {
            activeConfig = next
            clientCache.clear()
        }
    }

    fun applyTrustAllSsl(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        runCatching {
            builder.sslSocketFactory(trustAllSslFactory, trustAllManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder
    }

    fun applyToOkHttpBuilder(
        builder: OkHttpClient.Builder,
        cfg: NetworkConfig = activeConfig
    ): OkHttpClient.Builder {
        val parsedProxy = parseProxy(cfg.proxyUrl)
        if (parsedProxy != null) {
            builder.proxySelector(SmartProxySelector(parsedProxy))
        } else {
            if (cfg.useDoh) {
                builder.dns(DohDns)
            }
            if (cfg.useDpiBypass) {
                builder.socketFactory(DpiFragmentSocketFactory())
            }
        }
        return builder
    }

    fun getExternalClient(
        connectSec: Long = 8L,
        readSec: Long = 15L,
        trustAll: Boolean = true,
        cfg: NetworkConfig = activeConfig
    ): OkHttpClient {
        val key = "ext|${cfg.useDoh}|${cfg.useDpiBypass}|${cfg.proxyUrl}|$connectSec|$readSec|$trustAll"
        return clientCache.getOrPut(key) {
            val builder = OkHttpClient.Builder()
                .connectTimeout(connectSec, TimeUnit.SECONDS)
                .readTimeout(readSec, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
            applyToOkHttpBuilder(builder, cfg)
            if (trustAll) applyTrustAllSsl(builder)
            builder.build()
        }
    }

    fun openInputStream(
        urlStr: String,
        userAgent: String,
        headers: Map<String, String>,
        timeoutMs: Int = 8000,
        cfg: NetworkConfig = activeConfig
    ): InputStream {
        val cleanUrl = urlStr.substringBefore("#").trim()
        val sec = (timeoutMs / 1000L).coerceIn(4L, 45L)
        val client = getExternalClient(connectSec = sec, readSec = sec, trustAll = true, cfg = cfg)
        val reqBuilder = Request.Builder()
            .url(cleanUrl)
            .header("User-Agent", userAgent)
        for ((k, v) in headers) {
            if (k.isNotBlank() && v.isNotBlank()) {
                reqBuilder.header(k, v)
            }
        }
        val resp = client.newCall(reqBuilder.build()).execute()
        if (!resp.isSuccessful) {
            val code = resp.code
            resp.close()
            throw IOException("HTTP $code")
        }
        val body = resp.body ?: run {
            resp.close()
            throw IOException("Empty response body")
        }
        val rawStream = body.byteStream()
        return object : InputStream() {
            override fun read(): Int = rawStream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = rawStream.read(b, off, len)
            override fun available(): Int = rawStream.available()
            override fun close() {
                runCatching { rawStream.close() }
                runCatching { resp.close() }
            }
        }
    }

    fun isLocalOrPrivateHost(host: String?): Boolean {
        val h = host?.trim()?.lowercase(Locale.US)?.trim('[', ']') ?: return false
        if (h.isBlank()) return false
        if (h == "localhost" || h == "::1" || h.endsWith(".local")) return true
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")) return true
        if (h.startsWith("172.")) {
            val parts = h.split('.')
            val second = parts.getOrNull(1)?.toIntOrNull() ?: -1
            if (second in 16..31) return true
        }
        return false
    }

    private fun isIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { p ->
            val n = p.toIntOrNull()
            n != null && n in 0..255
        }
    }

    private fun isSinkholeOrPrivateIpv4(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return true
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        if (b0 == 0 || b0 == 10 || b0 == 127) return true
        if (b0 == 169 && b1 == 254) return true
        if (b0 == 172 && b1 in 16..31) return true
        if (b0 == 192 && b1 == 168) return true
        if (b0 >= 224) return true
        return false
    }

    private fun isSinkholeAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress) {
            return true
        }
        if (addr is Inet4Address) {
            return isSinkholeOrPrivateIpv4(addr.address)
        }
        return false
    }

    fun parseProxy(raw: String): Proxy? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return null

        val lower = trimmed.lowercase(Locale.US)
        val (type, hostPort) = when {
            lower.startsWith("socks5://") -> Proxy.Type.SOCKS to trimmed.substring(9)
            lower.startsWith("socks4://") -> Proxy.Type.SOCKS to trimmed.substring(9)
            lower.startsWith("socks://") -> Proxy.Type.SOCKS to trimmed.substring(8)
            lower.startsWith("http://") -> Proxy.Type.HTTP to trimmed.substring(7)
            lower.startsWith("https://") -> Proxy.Type.HTTP to trimmed.substring(8)
            else -> Proxy.Type.SOCKS to trimmed
        }
        val cleanHostPort = hostPort.substringAfter('@').substringBefore('/')
        val colonIdx = cleanHostPort.lastIndexOf(':')
        if (colonIdx <= 0 || colonIdx >= cleanHostPort.length - 1) return null

        val host = cleanHostPort.substring(0, colonIdx).trim().trim('[', ']')
        val port = cleanHostPort.substring(colonIdx + 1).trim().toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null

        return runCatching {
            Proxy(type, InetSocketAddress.createUnresolved(host, port))
        }.getOrNull()
    }

    fun formatNetworkSummary(cfg: NetworkConfig = activeConfig): String {
        val parts = mutableListOf<String>()
        if (cfg.proxyUrl.isNotBlank() && parseProxy(cfg.proxyUrl) != null) {
            parts.add("Прокси")
        } else {
            if (cfg.useDoh) parts.add("DoH")
            if (cfg.useDpiBypass) parts.add("DPI")
        }
        return if (parts.isEmpty()) "Прямое" else parts.joinToString("+")
    }

    private class SmartProxySelector(private val externalProxy: Proxy) : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val host = uri?.host
            return if (isLocalOrPrivateHost(host)) {
                listOf(Proxy.NO_PROXY)
            } else {
                listOf(externalProxy)
            }
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            CoreEngines.appendDiagLog("NET_PROXY", "Ошибка прокси для ${uri?.host}: ${ioe?.message}")
        }
    }

    private object DohDns : Dns {
        private data class CacheEntry(val addresses: List<InetAddress>, val expiresAtMs: Long)
        private data class ResolverTarget(val name: String, val kind: String, val endpoint: String)

        private val dnsMessageMediaType = "application/dns-message".toMediaType()
        private val txIdCounter = AtomicInteger(0x1234)
        private val cache = ConcurrentHashMap<String, CacheEntry>()
        private val loggedHosts = ConcurrentHashMap<String, Long>()

        @Volatile
        private var preferredTargetName: String? = null

        private val resolverTargets = listOf(
            ResolverTarget("DoH-AdGuard", "wire", "https://94.140.14.140/dns-query"),
            ResolverTarget("DoH-Alibaba", "wire", "https://223.5.5.5/dns-query"),
            ResolverTarget("DoH-Google", "wire", "https://8.8.8.8/dns-query"),
            ResolverTarget("DoH-Cloudflare", "wire", "https://1.1.1.1/dns-query"),
            ResolverTarget("DoH-Quad9", "wire", "https://9.9.9.9/dns-query"),
            ResolverTarget("DoH-Google-JSON", "json", "https://dns.google/resolve?name=%s&type=A"),
            ResolverTarget("DoH-Alibaba-JSON", "json", "https://223.5.5.5/resolve?name=%s&type=A"),
            ResolverTarget("UDP-AdGuard", "udp", "94.140.14.140"),
            ResolverTarget("UDP-Alibaba", "udp", "223.5.5.5"),
            ResolverTarget("UDP-Google", "udp", "8.8.8.8"),
            ResolverTarget("UDP-Cloudflare", "udp", "1.1.1.1")
        )

        override fun lookup(hostname: String): List<InetAddress> {
            val cleanHost = hostname.trim()
            if (cleanHost.isBlank() || isLocalOrPrivateHost(cleanHost) || isIpv4Literal(cleanHost) || cleanHost.contains(':')) {
                return Dns.SYSTEM.lookup(hostname)
            }

            val lowerHost = cleanHost.lowercase(Locale.US)
            val now = System.currentTimeMillis()
            val cached = cache[lowerHost]
            if (cached != null && cached.expiresAtMs > now && cached.addresses.isNotEmpty()) {
                return cached.addresses
            }

            val ordered = buildOrderedTargets()
            for (target in ordered) {
                val resolved = when (target.kind) {
                    "wire" -> resolveViaDohWire(cleanHost, target.endpoint)
                    "json" -> resolveViaDohJson(cleanHost, target.endpoint)
                    "udp" -> resolveViaUdp(cleanHost, target.endpoint)
                    else -> emptyList()
                }
                if (resolved.isNotEmpty()) {
                    preferredTargetName = target.name
                    cache[lowerHost] = CacheEntry(resolved, now + 10 * 60_000L)
                    logResolutionOnce(lowerHost, resolved.first().hostAddress.orEmpty(), target.name)
                    return resolved
                }
            }

            val sysAll = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
            val sysClean = sysAll.filter { !isSinkholeAddress(it) }
            if (sysClean.isNotEmpty()) {
                cache[lowerHost] = CacheEntry(sysClean, now + 3 * 60_000L)
                logResolutionOnce(lowerHost, sysClean.first().hostAddress.orEmpty(), "SystemDNS")
                return sysClean
            }
            if (sysAll.isNotEmpty()) {
                val blockedIp = sysAll.first().hostAddress.orEmpty()
                CoreEngines.appendDiagLog("DNS", "Системный DNS вернул заглушку ($cleanHost -> $blockedIp)")
                throw UnknownHostException("DNS подмена ($blockedIp) для $cleanHost")
            }
            throw UnknownHostException("Не удалось разрешить домен $cleanHost")
        }

        private fun buildOrderedTargets(): List<ResolverTarget> {
            val pref = preferredTargetName ?: return resolverTargets
            val idx = resolverTargets.indexOfFirst { it.name == pref }
            if (idx <= 0) return resolverTargets
            val out = ArrayList<ResolverTarget>(resolverTargets.size)
            out.add(resolverTargets[idx])
            for (i in resolverTargets.indices) {
                if (i != idx) out.add(resolverTargets[i])
            }
            return out
        }

        private fun logResolutionOnce(lowerHost: String, ip: String, via: String) {
            val now = System.currentTimeMillis()
            val prev = loggedHosts[lowerHost] ?: 0L
            if (now - prev > 10 * 60_000L) {
                loggedHosts[lowerHost] = now
                CoreEngines.appendDiagLog("DNS", "$lowerHost -> $ip ($via)")
            }
        }

        private fun resolveViaDohWire(hostname: String, endpointUrl: String): List<InetAddress> {
            val queryPacket = buildDnsQueryPacket(hostname) ?: return emptyList()
            return runCatching {
                val req = Request.Builder()
                    .url(endpointUrl)
                    .post(queryPacket.toRequestBody(dnsMessageMediaType))
                    .header("Accept", "application/dns-message")
                    .header("Content-Type", "application/dns-message")
                    .build()
                dohHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val bodyBytes = resp.body?.bytes() ?: return@use emptyList()
                    parseDnsResponsePacket(hostname, bodyBytes)
                }
            }.getOrDefault(emptyList())
        }

        private fun resolveViaDohJson(hostname: String, urlTemplate: String): List<InetAddress> {
            return runCatching {
                val encoded = URLEncoder.encode(hostname, "UTF-8")
                val url = String.format(Locale.US, urlTemplate, encoded)
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()
                dohHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use emptyList()
                    val root = JSONObject(body)
                    val answers = root.optJSONArray("Answer") ?: return@use emptyList()
                    val out = mutableListOf<InetAddress>()
                    for (i in 0 until answers.length()) {
                        val ans = answers.optJSONObject(i) ?: continue
                        if (ans.optInt("type", 0) != 1) continue
                        val ip = ans.optString("data").trim()
                        val bytes = parseIpv4Bytes(ip) ?: continue
                        if (isSinkholeOrPrivateIpv4(bytes)) continue
                        out.add(InetAddress.getByAddress(hostname, bytes))
                    }
                    out
                }
            }.getOrDefault(emptyList())
        }

        private fun resolveViaUdp(hostname: String, serverIp: String): List<InetAddress> {
            val queryPacket = buildDnsQueryPacket(hostname) ?: return emptyList()
            return runCatching {
                DatagramSocket().use { sock ->
                    sock.soTimeout = 1800
                    val targetAddr = InetAddress.getByName(serverIp)
                    val outPkt = DatagramPacket(queryPacket, queryPacket.size, targetAddr, 53)
                    sock.send(outPkt)
                    val buf = ByteArray(512)
                    val inPkt = DatagramPacket(buf, buf.size)
                    sock.receive(inPkt)
                    val respBytes = buf.copyOf(inPkt.length)
                    parseDnsResponsePacket(hostname, respBytes)
                }
            }.getOrDefault(emptyList())
        }

        private fun buildDnsQueryPacket(hostname: String): ByteArray? {
            val labels = hostname.trim('.').split('.').filter { it.isNotBlank() }
            if (labels.isEmpty()) return null
            val out = ByteArrayOutputStream(64)
            val txId = txIdCounter.incrementAndGet() and 0xFFFF
            out.write((txId ushr 8) and 0xFF)
            out.write(txId and 0xFF)
            out.write(0x01); out.write(0x00)
            out.write(0x00); out.write(0x01)
            out.write(0x00); out.write(0x00)
            out.write(0x00); out.write(0x00)
            out.write(0x00); out.write(0x00)
            for (label in labels) {
                val b = label.toByteArray(Charsets.US_ASCII)
                if (b.isEmpty() || b.size > 63) return null
                out.write(b.size)
                out.write(b)
            }
            out.write(0x00)
            out.write(0x00); out.write(0x01)
            out.write(0x00); out.write(0x01)
            return out.toByteArray()
        }

        private fun parseDnsResponsePacket(hostname: String, data: ByteArray): List<InetAddress> {
            if (data.size < 12) return emptyList()
            val rcode = data[3].toInt() and 0x0F
            if (rcode != 0) return emptyList()
            val qdCount = readU16(data, 4)
            val anCount = readU16(data, 6)
            if (anCount <= 0) return emptyList()

            var pos = 12
            repeat(qdCount) {
                pos = skipDnsName(data, pos)
                if (pos < 0 || pos + 4 > data.size) return emptyList()
                pos += 4
            }

            val out = mutableListOf<InetAddress>()
            repeat(anCount) {
                pos = skipDnsName(data, pos)
                if (pos < 0 || pos + 10 > data.size) return out
                val type = readU16(data, pos)
                val clazz = readU16(data, pos + 2)
                val rdLength = readU16(data, pos + 8)
                pos += 10
                if (pos + rdLength > data.size) return out
                if (type == 1 && clazz == 1 && rdLength == 4) {
                    val ipBytes = data.copyOfRange(pos, pos + 4)
                    if (!isSinkholeOrPrivateIpv4(ipBytes)) {
                        runCatching { out.add(InetAddress.getByAddress(hostname, ipBytes)) }
                    }
                }
                pos += rdLength
            }
            return out
        }

        private fun readU16(data: ByteArray, offset: Int): Int {
            return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        }

        private fun skipDnsName(data: ByteArray, start: Int): Int {
            var pos = start
            var guard = 0
            while (pos < data.size && guard < 128) {
                guard++
                val len = data[pos].toInt() and 0xFF
                if (len == 0) return pos + 1
                if ((len and 0xC0) == 0xC0) {
                    return if (pos + 2 <= data.size) pos + 2 else -1
                }
                pos += 1 + len
            }
            return -1
        }

        private fun parseIpv4Bytes(ip: String): ByteArray? {
            val parts = ip.split('.')
            if (parts.size != 4) return null
            val out = ByteArray(4)
            for (i in 0 until 4) {
                val n = parts[i].toIntOrNull() ?: return null
                if (n !in 0..255) return null
                out[i] = n.toByte()
            }
            return out
        }
    }

    /**
     * Fragments both HTTPS TLS ClientHello AND plain HTTP Host headers on external connections
     * to bypass passive ISP DPI throttling / resets without needing VPN or root.
     */
    private class DpiFragmentSocketFactory : SocketFactory() {
        private val defaultFactory = getDefault()

        override fun createSocket(): Socket = DelegatingFragmentSocket(defaultFactory.createSocket())

        override fun createSocket(host: String, port: Int): Socket {
            val sock = DelegatingFragmentSocket(defaultFactory.createSocket())
            sock.connect(InetSocketAddress(host, port))
            return sock
        }

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
            val sock = DelegatingFragmentSocket(defaultFactory.createSocket())
            sock.bind(InetSocketAddress(localHost, localPort))
            sock.connect(InetSocketAddress(host, port))
            return sock
        }

        override fun createSocket(host: InetAddress, port: Int): Socket {
            val sock = DelegatingFragmentSocket(defaultFactory.createSocket())
            sock.connect(InetSocketAddress(host, port))
            return sock
        }

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
            val sock = DelegatingFragmentSocket(defaultFactory.createSocket())
            sock.bind(InetSocketAddress(localAddress, localPort))
            sock.connect(InetSocketAddress(address, port))
            return sock
        }
    }

    private class DelegatingFragmentSocket(private val delegate: Socket) : Socket() {
        private var wrappedOut: OutputStream? = null
        private var allowFragmentation: Boolean = true

        override fun connect(endpoint: SocketAddress?) {
            updateLocalFlag(endpoint)
            delegate.connect(endpoint)
        }

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            updateLocalFlag(endpoint)
            delegate.connect(endpoint, timeout)
        }

        private fun updateLocalFlag(endpoint: SocketAddress?) {
            val inet = endpoint as? InetSocketAddress ?: return
            val host = inet.hostString ?: inet.address?.hostAddress
            if (isLocalOrPrivateHost(host)) {
                allowFragmentation = false
            }
        }

        override fun bind(bindpoint: SocketAddress?) = delegate.bind(bindpoint)
        override fun getInetAddress(): InetAddress? = delegate.inetAddress
        override fun getLocalAddress(): InetAddress = delegate.localAddress
        override fun getPort(): Int = delegate.port
        override fun getLocalPort(): Int = delegate.localPort
        override fun getRemoteSocketAddress(): SocketAddress? = delegate.remoteSocketAddress
        override fun getLocalSocketAddress(): SocketAddress? = delegate.localSocketAddress
        override fun getChannel(): SocketChannel? = null
        override fun getInputStream(): InputStream = delegate.getInputStream()

        override fun getOutputStream(): OutputStream {
            if (!allowFragmentation) return delegate.getOutputStream()
            val existing = wrappedOut
            if (existing != null) return existing
            val created = DpiFragmentingOutputStream(delegate, delegate.getOutputStream())
            wrappedOut = created
            return created
        }

        override fun setTcpNoDelay(on: Boolean) { delegate.tcpNoDelay = on }
        override fun getTcpNoDelay(): Boolean = delegate.tcpNoDelay
        override fun setSoLinger(on: Boolean, linger: Int) = delegate.setSoLinger(on, linger)
        override fun getSoLinger(): Int = delegate.soLinger
        override fun sendUrgentData(data: Int) = delegate.sendUrgentData(data)
        override fun setOOBInline(on: Boolean) { delegate.oobInline = on }
        override fun getOOBInline(): Boolean = delegate.oobInline
        override fun setSoTimeout(timeout: Int) { delegate.soTimeout = timeout }
        override fun getSoTimeout(): Int = delegate.soTimeout
        override fun setSendBufferSize(size: Int) { delegate.sendBufferSize = size }
        override fun getSendBufferSize(): Int = delegate.sendBufferSize
        override fun setReceiveBufferSize(size: Int) { delegate.receiveBufferSize = size }
        override fun getReceiveBufferSize(): Int = delegate.receiveBufferSize
        override fun setKeepAlive(on: Boolean) { delegate.keepAlive = on }
        override fun getKeepAlive(): Boolean = delegate.keepAlive
        override fun setTrafficClass(tc: Int) { delegate.trafficClass = tc }
        override fun getTrafficClass(): Int = delegate.trafficClass
        override fun setReuseAddress(on: Boolean) { delegate.reuseAddress = on }
        override fun getReuseAddress(): Boolean = delegate.reuseAddress
        override fun close() = delegate.close()
        override fun shutdownInput() = delegate.shutdownInput()
        override fun shutdownOutput() = delegate.shutdownOutput()
        override fun isConnected(): Boolean = delegate.isConnected
        override fun isBound(): Boolean = delegate.isBound
        override fun isClosed(): Boolean = delegate.isClosed
        override fun isInputShutdown(): Boolean = delegate.isInputShutdown
        override fun isOutputShutdown(): Boolean = delegate.isOutputShutdown
    }

    private class DpiFragmentingOutputStream(
        private val socket: Socket,
        out: OutputStream
    ) : FilterOutputStream(out) {

        private var fragmentedFirstWrite = false

        override fun write(b: Int) {
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (!fragmentedFirstWrite && len > 16) {
                val b0 = b[off].toInt() and 0xFF
                val b1 = b[off + 1].toInt() and 0xFF
                // Case 1: TLS ClientHello (HTTPS)
                if (b0 == 0x16 && b1 == 0x03) {
                    fragmentedFirstWrite = true
                    runCatching { socket.tcpNoDelay = true }
                    val split1 = 2
                    val split2 = 4.coerceAtMost(len - split1)
                    out.write(b, off, split1)
                    out.flush()
                    if (split2 > 0) {
                        out.write(b, off + split1, split2)
                        out.flush()
                    }
                    val rem = len - split1 - split2
                    if (rem > 0) {
                        out.write(b, off + split1 + split2, rem)
                        out.flush()
                    }
                    return
                }
                // Case 2: Plain HTTP request (GET / HEAD / POST) -> split inside the Host: header value
                if ((b0 == 'G'.code && b1 == 'E'.code) ||
                    (b0 == 'H'.code && b1 == 'E'.code) ||
                    (b0 == 'P'.code && b1 == 'O'.code)
                ) {
                    val splitPos = findHttpHostSplitOffset(b, off, len)
                    if (splitPos in 4 until (len - 4)) {
                        fragmentedFirstWrite = true
                        runCatching { socket.tcpNoDelay = true }
                        out.write(b, off, splitPos)
                        out.flush()
                        out.write(b, off + splitPos, len - splitPos)
                        out.flush()
                        return
                    }
                }
            }
            out.write(b, off, len)
        }

        private fun findHttpHostSplitOffset(b: ByteArray, off: Int, len: Int): Int {
            val end = off + len
            val limit = (end - 8).coerceAtMost(off + 1024)
            var i = off
            while (i < limit) {
                if (b[i] == '\n'.code.toByte()) {
                    val c1 = b[i + 1].toInt() and 0xDF
                    val c2 = b[i + 2].toInt() and 0xDF
                    val c3 = b[i + 3].toInt() and 0xDF
                    val c4 = b[i + 4].toInt() and 0xDF
                    if (c1 == 'H'.code && c2 == 'O'.code && c3 == 'S'.code && c4 == 'T'.code && b[i + 5] == ':'.code.toByte()) {
                        var valStart = i + 6
                        while (valStart < end && b[valStart] == ' '.code.toByte()) valStart++
                        return (valStart + 2) - off
                    }
                }
                i++
            }
            return -1
        }
    }
}
