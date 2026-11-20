package de.worldpulse.core.scout

import com.sun.net.httpserver.HttpServer
import de.worldpulse.core.gold.SourceTier
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedParserTest {
    private val rss = """<?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><title>T</title>
        <item><title>Großbrand in &lt;b&gt;Heilbronn&lt;/b&gt;</title><link>https://example.org/a</link>
          <description><![CDATA[<p>Zwei Verletzte.</p>]]></description><pubDate>Tue, 18 Nov 2026 20:15:00 +0100</pubDate><guid>a1</guid></item>
        <item><title>Ohne Datum</title><link>https://example.org/b</link><dc:date>2026-11-18T10:00:00Z</dc:date></item>
        <item><title>Ohne Link</title></item>
        </channel></rss>""".trimIndent()

    private val atom = """<?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom"><title>USGS</title>
          <entry><id>x1</id><title>M 6.2 - 48 km W of Antofagasta, Chile</title>
            <link rel="alternate" type="text/html" href="https://example.org/q1"/><updated>2026-11-15T14:40:00.000Z</updated>
            <summary type="html">&lt;dl&gt;&lt;dt&gt;Depth&lt;/dt&gt;&lt;dd&gt;35 km&lt;/dd&gt;&lt;/dl&gt;</summary></entry>
        </feed>""".trimIndent()

    @Test fun rss_items_html_stripped_dates_parsed() {
        val items = FeedParser.parse("s", rss.toByteArray())
        assertEquals(2, items.size)
        assertEquals("Großbrand in Heilbronn", items[0].title)
        assertEquals("Zwei Verletzte.", items[0].summary)
        assertNotNull(items[0].publishedAt); assertNotNull(items[1].publishedAt)
    }

    @Test fun atom_entries() {
        val items = FeedParser.parse("usgs", atom.toByteArray())
        assertEquals(1, items.size)
        assertEquals("https://example.org/q1", items[0].url)
        assertTrue(items[0].summary!!.contains("35 km"))
    }

    @Test fun xxe_is_not_expanded() {
        val evil = """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <rss><channel><item><title>&x;</title><link>https://e.org/1</link></item></channel></rss>"""
        val r = runCatching { FeedParser.parse("evil", evil.toByteArray()) }
        // Entweder Parser verweigert DOCTYPE (bevorzugt) oder die Entität bleibt leer – nie Dateiinhalt.
        r.getOrNull()?.forEach { assertFalse(it.title.contains("root:")) }
        assertTrue(r.isFailure || r.getOrNull()!!.none { it.title.contains("root") })
    }

    @Test fun rfc822_variants() {
        assertNotNull(FeedParser.parseDate("Wed, 19 Nov 2026 07:00:00 GMT"))
        assertNotNull(FeedParser.parseDate("2026-11-19T07:00:00+01:00"))
        assertNull(FeedParser.parseDate("gestern"))
    }
}

class CleanerTest {
    private val html = """<html lang="de"><head><title>Seite</title><meta property="og:title" content="Neckar steigt"></head><body>
        <nav>Startseite Politik Sport Wetter Anmelden und so weiter lange Navigation</nav>
        <article><h1>Neckar steigt</h1>
          <p>Nach tagelangem Regen hat der Neckar in Bad Wimpfen einen Pegelstand von 5,9 Metern erreicht, teilte die Stadt mit.</p>
          <p>Die Hochwasservorhersagezentrale rechnet bis Sonntag mit einem weiteren Anstieg auf bis zu 6,3 Meter.</p>
          <div class="newsletter"><p>Abonnieren Sie unseren Newsletter für tägliche Nachrichten aus der Region!</p></div>
        </article><footer><p>Impressum Datenschutz Kontakt und weitere sehr lange Fußzeile</p></footer><script>alert(1)</script></body></html>"""

    @Test fun main_text_without_boilerplate() {
        val ex = Cleaner.fromHtml(html)
        assertEquals("Neckar steigt", ex.title)
        assertEquals("de", ex.language)
        assertTrue(ex.text.contains("5,9 Metern")); assertTrue(ex.text.contains("6,3 Meter"))
        assertFalse(ex.text.contains("Newsletter")); assertFalse(ex.text.contains("Impressum")); assertFalse(ex.text.contains("alert"))
        assertFalse(ex.paywallStub)
    }

    @Test fun paywall_stub_detected() {
        val pw = "<html><body><article><p>Der Gemeinderat hat heute entschieden, dass ...</p><div class=\"paywall\">Jetzt abonnieren und weiterlesen mit Plus</div></article></body></html>"
        assertTrue(Cleaner.fromHtml(pw).paywallStub)
    }

    @Test fun language_detection() {
        assertEquals("en", Language.detect("The central bank raised its key interest rate and said that inflation was still too high for the economy."))
        assertEquals("ru", Language.detect("Министерство обороны заявило, что в ночь на среду ПВО сбили беспилотники над областью, это подтвердил губернатор."))
        assertEquals("uk", Language.detect("Міністерство оборони України заявило, що протягом ночі сили ППО збили дрони над областю."))
        assertNull(Language.detect("ok"))
    }
}

class DedupeTest {
    private val a = "Nach tagelangem Regen hat der Neckar in Bad Wimpfen einen Pegelstand von 5,9 Metern erreicht. Die Stadt hat die Uferstraße gesperrt und Sandsäcke ausgegeben. Bürgermeister Weber sagte, man sei gut vorbereitet."
    private val aCopy = "HEILBRONN (dpa) – Nach tagelangem Regen hat der Neckar in Bad Wimpfen einen Pegelstand von 5,9 Metern erreicht. Die Stadt hat die Uferstraße gesperrt und Sandsäcke ausgegeben. Bürgermeister Weber sagte, man sei gut vorbereitet."
    private val other = "Die Gewerkschaft Verdi hat für Montag zu einem ganztägigen Warnstreik im Nahverkehr in Stuttgart aufgerufen. Betroffen sind rund 2.300 Beschäftigte."

    @Test fun simhash_near_duplicates() {
        val d1 = SimHash.distance(SimHash.of(a), SimHash.of(aCopy))
        val d2 = SimHash.distance(SimHash.of(a), SimHash.of(other))
        assertTrue(d1 <= 10, "Kopie zu weit entfernt: $d1"); assertTrue(d2 > 16, "fremder Text zu nah: $d2")
    }

    @Test fun echo_groups_agency_copies() {
        val g = EchoGrouper(maxDistance = 10)
        val g1 = g.assign(SimHash.of(a), Cleaner.sha256(a))
        val g2 = g.assign(SimHash.of(aCopy), Cleaner.sha256(aCopy))
        val g3 = g.assign(SimHash.of(other), Cleaner.sha256(other))
        assertEquals(g1, g2); assertTrue(g1 != g3)
        assertEquals("dpa", Agency.detect(aCopy))
        assertNull(Agency.detect(a))
    }

    @Test fun hash_ignores_whitespace_and_case() {
        assertEquals(Cleaner.sha256("Ein  Text\nhier"), Cleaner.sha256("ein text hier"))
    }
}

class SafeFetcherTest {
    private val publicDns: (String) -> List<InetAddress> = { listOf(InetAddress.getByName("93.184.215.14")) }

    @Test fun blocks_non_https_private_and_credentials() {
        val f = SafeFetcher(resolve = publicDns)
        assertFailsWith<SafeFetcher.Blocked> { f.check("http://example.org/") }
        assertFailsWith<SafeFetcher.Blocked> { f.check("https://user:pw@example.org/") }
        assertFailsWith<SafeFetcher.Blocked> { f.check("https://example.org:8443/") }
        f.check("https://example.org/feed.xml")
        for (ip in listOf("127.0.0.1", "10.1.2.3", "192.168.0.10", "172.16.5.5", "169.254.169.254", "100.64.1.1", "0.0.0.0", "::1", "fd00::1", "::ffff:10.0.0.1")) {
            val p = SafeFetcher(resolve = { listOf(InetAddress.getByName(ip)) })
            assertFailsWith<SafeFetcher.Blocked>("$ip müsste blockiert sein") { p.check("https://evil.example/") }
        }
    }

    private lateinit var server: HttpServer
    private var port = 0

    @BeforeTest fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/feed") { ex ->
            if (ex.requestHeaders.getFirst("If-None-Match") == "\"v1\"") { ex.sendResponseHeaders(304, -1); ex.close(); return@createContext }
            val b = "<rss><channel><item><title>x</title><link>https://e.org/1</link></item></channel></rss>".toByteArray()
            ex.responseHeaders.add("ETag", "\"v1\""); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) }
        }
        server.createContext("/loop") { ex -> ex.responseHeaders.add("Location", "/loop"); ex.sendResponseHeaders(302, -1); ex.close() }
        server.createContext("/big") { ex ->
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.use { o -> repeat(200) { o.write(ByteArray(16 * 1024)) } }
        }
        server.createContext("/to-private") { ex -> ex.responseHeaders.add("Location", "https://10.0.0.5/x"); ex.sendResponseHeaders(302, -1); ex.close() }
        server.start(); port = server.address.port
    }

    @AfterTest fun stop() = server.stop(0)

    @Test fun etag_conditional_get() {
        val f = SafeFetcher(allowLocalForTests = true)
        val r1 = f.get("http://127.0.0.1:$port/feed")
        assertEquals(200, r1.status); assertEquals("\"v1\"", r1.etag)
        assertTrue(f.get("http://127.0.0.1:$port/feed", etag = r1.etag).notModified)
    }

    @Test fun redirect_limit_size_limit_and_redirect_target_check() {
        val f = SafeFetcher(allowLocalForTests = true, maxBytes = 1_000_000)
        assertFailsWith<SafeFetcher.Blocked> { f.get("http://127.0.0.1:$port/loop") }
        assertFailsWith<SafeFetcher.Blocked> { f.get("http://127.0.0.1:$port/big") }
        assertFailsWith<SafeFetcher.Blocked> { f.get("http://127.0.0.1:$port/to-private") }
    }

    @Test fun backoff_doubles_and_pauses() {
        val b = Backoff(baseMs = 1000)
        assertEquals(1000, b.failure("s", 0)); assertEquals(2000, b.failure("s", 0))
        assertFalse(b.allowed("s", 1500)); assertTrue(b.allowed("s", 2001))
        repeat(3) { b.failure("s", 0) }; assertTrue(b.shouldPause("s"))
        b.success("s"); assertTrue(b.allowed("s", 0)); assertFalse(b.shouldPause("s"))
    }
}

class GdeltAndRegistryTest {
    @Test fun gdelt_url_and_parse() {
        val u = Gdelt.artListUrl("Heilbronn Brand", "24h", 50)
        assertTrue(u.startsWith("https://api.gdeltproject.org/api/v2/doc/doc?query=Heilbronn+Brand") || u.contains("query=Heilbronn%20Brand"))
        val body = """{"articles":[{"url":"https://e.org/1","url_mobile":"","title":"Fire in Heilbronn","seendate":"20261119T120000Z",
            "socialimage":"","domain":"e.org","language":"English","sourcecountry":"Germany"}]}"""
        val a = Gdelt.parseArtList(body).single()
        assertEquals("Fire in Heilbronn", a.title); assertNotNull(a.seenAt); assertEquals("Germany", a.country)
        assertTrue(Gdelt.parseArtList("").isEmpty()); assertTrue(Gdelt.parseArtList("kein json").isEmpty())
    }

    @Test fun bundled_registry_is_valid_and_all_candidates() {
        val list = SourceRegistry.bundled()
        assertTrue(list.size >= 10)
        assertEquals(emptyList(), SourceRegistry.validate(list))
        assertTrue(list.none { it.approved }, "Neue Quellen müssen Kandidaten sein (SPEC 8)")
        assertTrue(list.filter { it.stateMedia }.all { it.tier != SourceTier.A })
    }

    @Test fun scout_pipeline_with_local_feed_items() {
        val scout = Scout(SafeFetcher(resolve = { listOf(InetAddress.getByName("93.184.215.14")) }))
        val items = listOf(
            FeedItem("s", "https://e.org/1", "Neckar steigt", "Pegel 5,9 m in Bad Wimpfen, Stadt sperrt Uferstraße.", 0),
            FeedItem("s", "https://e.org/2", "Neckar steigt", "Pegel 5,9 m in Bad Wimpfen, Stadt sperrt Uferstraße.", 0),
            FeedItem("s", "https://e.org/old", "Alt", "schon bekannt", 0),
        )
        val stats = Scout.Stats()
        val docs = scout.toDocuments(items, seenUrls = setOf("https://e.org/old"), seenHashes = emptySet(), fetchFullText = false, stats = stats)
        assertEquals(1, docs.size); assertEquals(1, stats.duplicates)
        assertNotNull(docs[0].echoGroup)
    }
}
