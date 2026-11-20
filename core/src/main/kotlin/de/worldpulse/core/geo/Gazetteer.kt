package de.worldpulse.core.geo

import de.worldpulse.core.gold.GeoPrecision

/**
 * Offline-Ortsverzeichnis (Geo-Agent, Code-Teil, SPEC 5 Agent 9): Natural-Earth-Länder + Städte ≥ 50.000 Einw.
 * + Hauptstädte + Heimatregion. Namen DE/EN/RU/UK/FR. Der LLM-Teil (Ort eindeutig formulieren) kommt in S4.
 * Treffer bleiben Schätzungen: Genauigkeit wird immer mitgeliefert (Pflichtfeld).
 */
data class Place(val names: List<String>, val lat: Double, val lon: Double, val precision: GeoPrecision, val iso2: String, val population: Long, val isCountry: Boolean)

data class GeoHit(val place: Place, val matched: String, val inTitle: Boolean)

class Gazetteer(places: List<Place>) {
    /** Ortsnamen, die auch gewöhnliche Wörter sind -> nur mit Länderkontext oder gar nicht verwenden. */
    private val ambiguous = setOf(
        "essen", "hof", "halle", "most", "nice", "mobile", "reading", "split", "bath", "male", "bar", "marl", "weil", "lage",
        "gera", "soest", "wels", "steyr", "mons", "sale", "york", "victoria", "georgia", "jordan", "chad", "niger", "guinea",
        "lima", "santiago", "córdoba", "cordoba", "valencia", "florence", "alexandria", "mitte", "neustadt", "nord", "süd",
        "ost", "west", "union", "liberty", "independence", "concord", "hope", "eagle", "temple", "cork", "derby",
    )

    private val byName = HashMap<String, MutableList<Place>>()
    /** Kyrillische Stämme (ohne Endung) für Fallformen: Москва → Москв (Москве, Москвой, Москвы). */
    private val cyrStem = HashMap<String, MutableList<Place>>()
    private val maxWords: Int

    init {
        var mw = 1
        places.forEach { p -> p.names.forEach { n ->
            val k = norm(n); if (k.length < 3) return@forEach
            byName.getOrPut(k) { mutableListOf() } += p
            if (' ' !in k && k.length >= 5 && k.any { it in '\u0400'..'\u04FF' }) {
                val stem = if (k.last() in "аяьйоеиы") k.dropLast(1) else k
                cyrStem.getOrPut(stem) { mutableListOf() } += p
            }
            mw = maxOf(mw, k.split(' ').size)
        } }
        byName.values.forEach { it.sortByDescending { p -> p.population } }
        cyrStem.values.forEach { it.sortByDescending { p -> p.population } }
        maxWords = minOf(mw, 4)
    }

    /**
     * Alle Ortsnennungen in Titel und Text. Großgeschriebene Wörter, längste Übereinstimmung zuerst.
     * Mehrdeutige Namen werden über im Text genannte Länder aufgelöst, sonst größte Stadt.
     */
    fun find(title: String, text: String = ""): List<GeoHit> {
        val countriesMentioned = HashSet<String>()
        val raw = mutableListOf<Triple<String, List<Place>, Boolean>>()
        for ((src, inTitle) in listOf(title to true, text.take(4000) to false)) {
            val words = Regex("[\\p{L}][\\p{L}'’.-]*").findAll(src).toList()
            var i = 0
            while (i < words.size) {
                var hit: Pair<Int, List<Place>>? = null
                for (n in minOf(maxWords, words.size - i) downTo 1) {
                    val first = words[i].value
                    if (!first[0].isUpperCase()) break
                    val phrase = words.subList(i, i + n).joinToString(" ") { it.value }.trimEnd('.', '’', '\'')
                    val k = norm(phrase)
                    val c = byName[k] ?: byName[k.removeSuffix("s")]?.takeIf { k.length > 5 } // Genitiv: Deutschlands
                        ?: if (n == 1 && k.length >= 5 && k.any { it in '\u0400'..'\u04FF' }) (cyrStem[k.dropLast(1)] ?: cyrStem[k.dropLast(2)]) else null
                    if (c != null && (n > 1 || k !in ambiguous)) { hit = n to c; break }
                }
                if (hit != null) {
                    val phrase = words.subList(i, i + hit.first).joinToString(" ") { it.value }
                    raw += Triple(phrase, hit.second, inTitle)
                    hit.second.filter { it.isCountry }.forEach { countriesMentioned += it.iso2 }
                    i += hit.first
                } else i++
            }
        }
        return raw.map { (phrase, cands, inTitle) ->
            val p = cands.firstOrNull { !it.isCountry && it.iso2 in countriesMentioned } ?: cands.first()
            GeoHit(p, phrase, inTitle)
        }
    }

    /** Bester Ort: Titel vor Text, Stadt vor Land, häufigste Nennung, dann größte Einwohnerzahl. */
    fun best(title: String, text: String = ""): GeoHit? {
        val hits = find(title, text)
        if (hits.isEmpty()) return null
        val counts = hits.groupingBy { it.place }.eachCount()
        return hits.maxWithOrNull(compareBy<GeoHit>({ it.inTitle }, { !it.place.isCountry }, { counts[it.place] ?: 0 }, { it.place.population }))
    }

    companion object {
        fun norm(s: String) = s.lowercase().replace('’', '\'').replace(Regex("\\s+"), " ").trim()

        fun bundled(): Gazetteer {
            val txt = (Gazetteer::class.java.classLoader.getResourceAsStream("geo/gazetteer.tsv") ?: error("gazetteer.tsv fehlt"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            return Gazetteer(parse(txt))
        }

        fun parse(tsv: String): List<Place> = tsv.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
            val f = l.split('\t'); if (f.size < 7) return@mapNotNull null
            Place(f[6].split('|'), f[2].toDouble(), f[3].toDouble(), GeoPrecision.valueOf(f[1]), f[4], f[5].toLongOrNull() ?: 0, f[0] == "C")
        }.toList()
    }
}
