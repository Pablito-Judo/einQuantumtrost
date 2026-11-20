package de.worldpulse.core.scout

/**
 * Live-Probe (nur CI, braucht Internet): ruft jede Kandidatenquelle einmal ab und druckt Ergebnis je Quelle.
 * Aufruf: ./gradlew liveProbe
 */
fun main() {
    val scout = Scout(SafeFetcher())
    val list = SourceRegistry.bundled()
    var ok = 0
    for (s in list) {
        val r = scout.poll(s)
        val sample = r.items.firstOrNull()
        if (r.error == null && r.items.isNotEmpty()) ok++
        println("PROBE ${s.id.padEnd(18)} ${if (r.error == null) "OK " else "ERR"} items=${r.items.size} " +
            (r.error ?: "etag=${r.state.etag != null} neuestes=${r.items.mapNotNull { it.publishedAt }.maxOrNull()?.let { java.time.Instant.ofEpochMilli(it) }} " +
                "titel=\"${sample?.title?.take(70)}\""))
        // Volltext-Stichprobe: erster Artikel bereinigen (Paywall/Sprache/Länge prüfen)
        if (sample != null && s.type != SourceType.GDELT && !s.sanctioned && s.fulltext) {
            val st = Scout.Stats()
            val d = scout.toDocuments(listOf(sample), emptySet(), emptySet(), fetchFullText = true, stats = st).firstOrNull()
            println("PROBE   volltext: ${d?.let { "sprache=${it.language} zeichen=${it.text.length} echo=${it.echoGroup}" } ?: "verworfen $st"}")
        }
    }
    println("PROBE ERGEBNIS: $ok/${list.size} Quellen liefern Einträge")
}
