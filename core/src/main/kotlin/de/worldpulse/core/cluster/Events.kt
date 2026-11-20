package de.worldpulse.core.cluster

import de.worldpulse.core.embed.Vectors
import de.worldpulse.core.eval.TextMatch

/**
 * Kategorie aus Schlagworten (DE/EN/RU) – Startwert bis Claim-Typ/LLM (S4) und CAMEO/ACLED (S8) übernehmen.
 * Ergebnis-Strings passen zu SymbolMapper.fromCategory (data-Modul).
 */
object Categorizer {
    private val rules: List<Pair<String, List<String>>> = listOf(
        "erdbeben" to listOf("erdbeben", "beben", "earthquake", "quake", "seismic", "землетрясен", "magnitude", " m 4.", " m 5.", " m 6.", " m 7.", "m 4.", "m 5.", "m 6.", "m 7."),
        "explosion" to listOf("airstrike", "air strike", "shelling", "missile", "drone attack", "drone strike", "drones", "bomb", "explosion",
            "rakete", "luftangriff", "beschuss", "drohnenangriff", "drohnen", "explosion", "ракет", "обстрел", "беспилотник", "дрон", "удар"),
        "kampf" to listOf("fighting", "clashes", "offensive", "troops advance", "frontline", "front line", "battle", "kämpfe", "gefechte", "front", "бои", "наступлен"),
        "gewalt" to listOf("massacre", "civilians killed", "killed civilians", "genocide", "execution", "massaker", "zivilisten getötet", "убит"),
        "protest" to listOf("protest", "demonstrat", "rally", "riot", "strike", "walkout", "warnstreik", "streik", "demo ", "kundgebung", "протест", "митинг", "забастов"),
        "brand" to listOf("wildfire", "fire", "blaze", "brand", "feuer", "flammen", "пожар"),
        "unwetter" to listOf("flood", "storm", "cyclone", "hurricane", "typhoon", "tornado", "tsunami", "landslide", "volcano", "drought",
            "hochwasser", "überschwemmung", "unwetter", "sturm", "orkan", "zyklon", "vulkan", "dürre", "pegel", "наводнен", "шторм", "ураган",
            "green alert", "orange alert", "red alert"),
        "politik" to listOf("election", "parliament", "president", "prime minister", "minister", "government", "summit", "sanction", "vote",
            "wahl", "bundestag", "landtag", "gemeinderat", "regierung", "kanzler", "minister", "gipfel", "sanktion", "выбор", "правительств", "парламент"),
        "event" to listOf("festival", "konzert", "concert", "weindorf", "markt", "fest ", "ausstellung", "exhibition"),
    )

    fun categorize(title: String, text: String = ""): String {
        val t = " " + (title + " " + title + " " + text.take(600)).lowercase() + " " // Titel doppelt gewichtet
        return rules.maxByOrNull { (_, kws) -> kws.sumOf { kw -> Regex(Regex.escape(kw)).findAll(t).count() } }
            ?.takeIf { (_, kws) -> kws.any { it in t } }?.first ?: "sonstiges"
    }
}

/** Eingang für das Clustering: ein Dokument (bzw. seine Schlagzeile). */
data class DocFeature(
    val id: Long,
    val title: String,
    val timeMs: Long,
    /** ISO-Länderkürzel oder Ortsname des besten Geo-Treffers (null = unbekannt). */
    val place: String?,
    val simhash: Long,
    val embedding: FloatArray? = null,
)

/**
 * Strom-Clustering (SPEC 5 Agent 10, Startversion): neues Dokument kommt in das ähnlichste offene Ereignis im
 * Zeitfenster, sonst neues Ereignis. Ähnlichkeit: Embedding-Kosinus (wenn vorhanden) ODER Titel-Token-Überlappung,
 * gleicher Ort erhöht die Ähnlichkeit, widersprüchlicher Ort senkt sie.
 * Schwellen: Kosinus 0,88 (S23-Messung 20.11.: gleich ~0,95, anders ~0,83 bei 768 Dim. mit Präfix), Titel-Jaccard 0,34 (Schätzung) –
 * am Goldstandard einstellen.
 */
class EventClusterer(
    private val windowMs: Long = 72 * 3600_000L,
    private val cosThreshold: Double = 0.88,
    private val jaccardThreshold: Double = 0.34,
) {
    class Cluster(val id: Long, var lastMs: Long, val tokens: MutableSet<String>, var centroid: FloatArray?, var n: Int, val places: MutableSet<String>)

    private val clusters = mutableListOf<Cluster>()
    private var nextId = 1L

    fun preload(c: Cluster) { clusters += c; nextId = maxOf(nextId, c.id + 1) }

    /** Ordnet [d] zu und liefert die Cluster-ID (neu oder bestehend). */
    fun assign(d: DocFeature, newId: (() -> Long)? = null): Long {
        val toks = tokens(d.title)
        var best: Cluster? = null; var bestScore = 0.0
        for (c in clusters) {
            if (d.timeMs - c.lastMs > windowMs) continue
            val placeBonus = when {
                d.place == null || c.places.isEmpty() -> 0.0
                d.place in c.places -> 0.12
                else -> -0.15
            }
            val score = if (d.embedding != null && c.centroid != null) {
                Vectors.cosine(d.embedding, c.centroid!!) + placeBonus / 2 - (cosThreshold - jaccardThreshold) // auf Jaccard-Skala verschoben
            } else jaccard(toks, c.tokens) + placeBonus
            if (score > bestScore) { bestScore = score; best = c }
        }
        if (best != null && bestScore >= jaccardThreshold) {
            best.tokens += toks; best.lastMs = maxOf(best.lastMs, d.timeMs); best.n++
            d.place?.let { best.places += it }
            if (d.embedding != null) best.centroid = best.centroid?.let { avg(it, d.embedding, best.n) } ?: d.embedding.copyOf()
            return best.id
        }
        val id = newId?.invoke() ?: nextId++
        clusters += Cluster(id, d.timeMs, toks.toMutableSet(), d.embedding?.copyOf(), 1, listOfNotNull(d.place).toMutableSet())
        return id
    }

    private fun avg(c: FloatArray, v: FloatArray, n: Int) = FloatArray(c.size) { c[it] + (v[it] - c[it]) / n }

    companion object {
        fun tokens(title: String): Set<String> = TextMatch.tokens(title).filter { it.length >= 3 || it.any(Char::isDigit) }.toSet()
        fun jaccard(a: Set<String>, b: Set<String>): Double = if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size
    }
}
