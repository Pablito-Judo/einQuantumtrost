package de.worldpulse.core.scout

/**
 * Scout-Ablauf (Agent 2+3+4) ohne Android: Quelle abrufen (bedingt) → Einträge → neue URLs → Artikel holen →
 * bereinigen → Paywall-Stummel verwerfen → Hash/SimHash/Echo-Gruppe → CleanDocument.
 * Speicher (Room), WorkManager und Netzbedingungen (WLAN, Laden) kommen in der App-Schicht dazu.
 */
class Scout(
    private val fetcher: SafeFetcher,
    private val backoff: Backoff = Backoff(),
    private val echo: EchoGrouper = EchoGrouper(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class SourceState(val etag: String? = null, val lastModified: String? = null)

    data class SourceResult(
        val source: SourceDef,
        val items: List<FeedItem>,
        val state: SourceState,
        val notModified: Boolean = false,
        val error: String? = null,
    )

    data class Stats(var fetched: Int = 0, var kept: Int = 0, var duplicates: Int = 0, var paywall: Int = 0, var tooShort: Int = 0, var errors: Int = 0)

    /** Schritt 1: Quelle abrufen und Einträge parsen. */
    fun poll(src: SourceDef, state: SourceState = SourceState()): SourceResult {
        val now = clock()
        if (!backoff.allowed(src.id, now)) return SourceResult(src, emptyList(), state, error = "Backoff aktiv")
        return try {
            val r = fetcher.get(src.url, state.etag, state.lastModified)
            if (r.notModified) { backoff.success(src.id); return SourceResult(src, emptyList(), state, notModified = true) }
            if (r.status !in 200..299 || r.body == null) error("HTTP ${r.status}")
            val items = when (src.type) {
                SourceType.RSS, SourceType.ATOM -> FeedParser.parse(src.id, r.body)
                SourceType.GDELT -> Gdelt.toFeedItems(src.id, Gdelt.parseArtList(String(r.body)))
                else -> error("Adapter ${src.type} noch nicht implementiert")
            }
            backoff.success(src.id)
            SourceResult(src, items, SourceState(r.etag, r.lastModified))
        } catch (e: Exception) {
            backoff.failure(src.id, now)
            SourceResult(src, emptyList(), state, error = "${e.javaClass.simpleName}: ${e.message}".take(200))
        }
    }

    /**
     * Schritt 2: neue Einträge zu Dokumenten machen. [seen] = bereits gespeicherte URLs/Hashes (aus der DB).
     * [fetchFullText] = false -> nur Titel+Zusammenfassung (für Quellen ohne Volltext-Erlaubnis, z. B. sanktionierte Medien).
     */
    fun toDocuments(items: List<FeedItem>, seenUrls: Set<String>, seenHashes: Set<String>, fetchFullText: Boolean, stats: Stats = Stats()): List<CleanDocument> {
        val out = mutableListOf<CleanDocument>()
        val hashes = seenHashes.toMutableSet()
        for (it in items) {
            if (it.url in seenUrls) continue
            val (title, text, lang, paywall) = if (fetchFullText) {
                try {
                    val r = fetcher.get(it.url)
                    stats.fetched++
                    if (r.status !in 200..299 || r.body == null) { stats.errors++; continue }
                    val ex = Cleaner.fromHtml(String(r.body), r.finalUrl)
                    Quad(ex.title.ifBlank { it.title }, ex.text, ex.language, ex.paywallStub)
                } catch (e: Exception) { stats.errors++; continue }
            } else Quad(it.title, listOfNotNull(it.title, it.summary).joinToString("\n\n"), it.language ?: Language.detect(it.title + " " + (it.summary ?: "")), false)

            if (paywall) { stats.paywall++; continue }
            if (text.length < 200 && fetchFullText) { stats.tooShort++; continue }
            val hash = Cleaner.sha256(text)
            if (hash in hashes) { stats.duplicates++; continue }
            hashes += hash
            val sim = SimHash.of(text)
            out += CleanDocument(
                sourceId = it.sourceId, url = it.url, title = title, text = text, language = lang ?: it.language,
                publishedAt = it.publishedAt, fetchedAt = clock(), contentHash = hash, simhash = sim,
                echoGroup = echo.assign(sim, hash),
            )
            stats.kept++
        }
        return out
    }

    private data class Quad(val a: String, val b: String, val c: String?, val d: Boolean)
}
