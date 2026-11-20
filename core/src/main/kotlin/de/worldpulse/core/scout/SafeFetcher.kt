package de.worldpulse.core.scout

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

/**
 * Abrufer für den Scout (SPEC 9): nur https, SSRF-Schutz (keine privaten/lokalen Adressen, auch nach DNS und
 * bei jeder Weiterleitung), max. Weiterleitungen, Größen- und Zeitlimit, bedingte Abrufe (ETag/Last-Modified),
 * Backoff je Quelle. Kein JavaScript, keine Cookies, keine Zugangsdaten.
 */
class SafeFetcher(
    private val maxBytes: Int = 3 * 1024 * 1024,
    private val maxRedirects: Int = 3,
    private val timeoutMs: Int = 20_000,
    private val userAgent: String = "WorldPulse/0.1 (privat; RSS-Leser)",
    /** DNS-Auflösung austauschbar (Tests). */
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    /** Nur für lokale Tests: http://localhost erlauben. */
    private val allowLocalForTests: Boolean = false,
) {
    data class Result(
        val status: Int,
        val body: ByteArray?,
        val finalUrl: String,
        val etag: String?,
        val lastModified: String?,
        val contentType: String?,
    ) {
        val notModified get() = status == 304
    }

    class Blocked(msg: String) : Exception(msg)

    fun get(url: String, etag: String? = null, lastModified: String? = null): Result {
        var current = url
        repeat(maxRedirects + 1) {
            check(current)
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false // selbst folgen, um jedes Ziel zu prüfen
                connectTimeout = timeoutMs; readTimeout = timeoutMs
                useCaches = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, application/json;q=0.9, */*;q=0.5")
                etag?.let { setRequestProperty("If-None-Match", it) }
                lastModified?.let { setRequestProperty("If-Modified-Since", it) }
            }
            try {
                val code = c.responseCode
                if (code in 300..399 && code != 304) {
                    val loc = c.getHeaderField("Location") ?: throw Blocked("Weiterleitung ohne Ziel")
                    current = URI(current).resolve(loc).toString()
                    return@repeat
                }
                if (code == 304) return Result(304, null, current, etag, lastModified, null)
                c.contentLengthLong.takeIf { it > maxBytes }?.let { throw Blocked("zu groß: $it Bytes") }
                val stream = if (code >= 400) c.errorStream else c.inputStream
                val body = stream?.use { readLimited(it) }
                return Result(code, body, current, c.getHeaderField("ETag"), c.getHeaderField("Last-Modified"), c.contentType)
            } finally {
                c.disconnect()
            }
        }
        throw Blocked("zu viele Weiterleitungen (> $maxRedirects)")
    }

    private fun readLimited(inp: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = inp.read(buf); if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > maxBytes) throw Blocked("zu groß (> $maxBytes Bytes)")
        }
        return out.toByteArray()
    }

    /** Wirft [Blocked], wenn die URL nicht erlaubt ist. */
    fun check(url: String) {
        val u = runCatching { URI(url) }.getOrNull() ?: throw Blocked("ungültige URL")
        val host = u.host ?: throw Blocked("kein Host")
        if (allowLocalForTests && (host == "localhost" || host == "127.0.0.1")) return
        if (u.scheme != "https") throw Blocked("nur https erlaubt: ${u.scheme}")
        if (u.userInfo != null) throw Blocked("Zugangsdaten in URL")
        if (u.port != -1 && u.port != 443) throw Blocked("Port ${u.port} nicht erlaubt")
        val addrs = runCatching { resolve(host) }.getOrElse { throw Blocked("DNS: ${it.message}") }
        if (addrs.isEmpty()) throw Blocked("DNS leer")
        addrs.forEach { if (isPrivate(it)) throw Blocked("private/lokale Adresse: ${it.hostAddress}") }
    }

    companion object {
        fun isPrivate(a: InetAddress): Boolean {
            if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return true
            val b = a.address
            if (b.size == 4) {
                val o0 = b[0].toInt() and 0xFF; val o1 = b[1].toInt() and 0xFF
                if (o0 == 100 && o1 in 64..127) return true // CGNAT 100.64/10
                if (o0 == 0 || o0 >= 224) return true
                if (o0 == 192 && o1 == 0 && (b[2].toInt() and 0xFF) == 0) return true
                if (o0 == 198 && (o1 == 18 || o1 == 19)) return true
            } else if (b.size == 16) {
                val f = b[0].toInt() and 0xFF
                if (f == 0xFC || f == 0xFD) return true // ULA fc00::/7
                // IPv4-mapped ::ffff:a.b.c.d
                if ((0 until 10).all { b[it].toInt() == 0 } && (b[10].toInt() and 0xFF) == 0xFF && (b[11].toInt() and 0xFF) == 0xFF) {
                    return isPrivate(InetAddress.getByAddress(b.copyOfRange(12, 16)))
                }
            }
            return false
        }
    }
}

/** Backoff je Quelle: Fehler verdoppeln die Wartezeit (max. 24 h), Erfolg setzt zurück. */
class Backoff(private val baseMs: Long = 15 * 60_000L, private val maxMs: Long = 24 * 3600_000L) {
    private val failures = HashMap<String, Int>()
    private val nextAllowed = HashMap<String, Long>()

    fun allowed(sourceId: String, now: Long) = now >= (nextAllowed[sourceId] ?: 0L)
    fun success(sourceId: String) { failures.remove(sourceId); nextAllowed.remove(sourceId) }
    fun failure(sourceId: String, now: Long): Long {
        val n = (failures[sourceId] ?: 0) + 1
        failures[sourceId] = n
        val wait = (baseMs shl (n - 1).coerceAtMost(16)).coerceAtMost(maxMs)
        nextAllowed[sourceId] = now + wait
        return wait
    }
    /** Nach 5 Fehlern in Folge Adapter pausieren (SPEC 8: kaputte Adapter pausieren). */
    fun shouldPause(sourceId: String) = (failures[sourceId] ?: 0) >= 5
}
