package de.worldpulse.core.scout

import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * RSS 2.0 / RSS 1.0 (RDF) / Atom-Parser auf DOM-Basis (läuft auf JVM und Android).
 * Sicherheit (SPEC 9): keine DTDs, keine externen Entitäten (XXE), Inhalt nur als Daten behandelt.
 */
object FeedParser {
    fun parse(sourceId: String, xml: ByteArray): List<FeedItem> {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Android-Parser kennt nicht alle Features -> einzeln, Fehler ignorieren
            listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            ).forEach { (k, v) -> runCatching { setFeature(k, v) } }
            runCatching { isXIncludeAware = false }
        }
        val doc = f.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val root = doc.documentElement
        return when (root.localName ?: root.tagName) {
            "feed" -> atom(sourceId, root)
            else -> rss(sourceId, root) // rss / RDF
        }
    }

    private fun rss(sourceId: String, root: Element): List<FeedItem> =
        root.descendants("item").mapNotNull { it ->
            val link = it.child("link")?.text() ?: it.child("guid")?.text()?.takeIf { g -> g.startsWith("http") } ?: return@mapNotNull null
            FeedItem(
                sourceId = sourceId,
                url = link.trim(),
                title = clean(it.child("title")?.text()),
                summary = it.child("description")?.text()?.let(::clean)?.takeIf { s -> s.isNotBlank() },
                publishedAt = (it.child("pubDate") ?: it.child("date"))?.text()?.let(::parseDate),
                guid = it.child("guid")?.text(),
            ).withCoords(it)
        }

    private fun atom(sourceId: String, root: Element): List<FeedItem> =
        root.descendants("entry").mapNotNull { e ->
            val links = e.children("link")
            val href = (links.firstOrNull { it.getAttribute("rel").let { r -> r.isEmpty() || r == "alternate" } } ?: links.firstOrNull())
                ?.getAttribute("href")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FeedItem(
                sourceId = sourceId,
                url = href.trim(),
                title = clean(e.child("title")?.text()),
                summary = (e.child("summary") ?: e.child("content"))?.text()?.let(::clean)?.takeIf { it.isNotBlank() },
                publishedAt = (e.child("published") ?: e.child("updated"))?.text()?.let(::parseDate),
                language = e.getAttribute("xml:lang").takeIf { it.isNotBlank() },
                guid = e.child("id")?.text(),
            ).withCoords(e)
        }

    /** georss:point "lat lon" oder geo:lat/geo:long (auch verschachtelt, z. B. GDACS geo:Point). */
    private fun FeedItem.withCoords(el: Element): FeedItem {
        fun firstText(name: String) = el.descendants(name).firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val point = firstText("point")?.split(Regex("[\\s,]+"))?.mapNotNull { it.toDoubleOrNull() }
        val (la, lo) = if (point != null && point.size >= 2) point[0] to point[1]
        else (firstText("lat")?.toDoubleOrNull() to (firstText("long") ?: firstText("lon"))?.toDoubleOrNull())
        return if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) copy(lat = la, lon = lo) else this
    }

    /** HTML in Titeln/Beschreibungen entfernen (nur Text behalten). */
    fun clean(s: String?): String = s?.let { Jsoup.parse(it).text().trim() } ?: ""

    /** RFC 822 (RSS), ISO 8601 (Atom/DC) -> epoch ms; unbekannt -> null. */
    fun parseDate(s: String): Long? {
        val t = s.trim()
        return runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                // RFC 822 mit Zonen-Kürzel ("GMT", "CET") oder ohne Wochentag
                ZonedDateTime.parse(t.replace(Regex("^[A-Za-z]{3},\\s*"), ""), DateTimeFormatter.ofPattern("d MMM yyyy HH:mm[:ss] [zzz][Z]", Locale.ENGLISH))
                    .toInstant().toEpochMilli()
            }.getOrNull()
    }

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { (it.localName ?: it.tagName) == name }

    private fun Element.child(name: String): Element? = children(name).firstOrNull()

    private fun Element.descendants(name: String): List<Element> {
        val nl = getElementsByTagNameNS("*", name).takeIf { it.length > 0 } ?: getElementsByTagName(name)
        return (0 until nl.length).map { nl.item(it) }.filterIsInstance<Element>()
    }

    private fun Node.text(): String = textContent ?: ""
}
