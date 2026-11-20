package de.worldpulse.core.scout

import java.security.MessageDigest

/**
 * Dedupe/Echo (Agent 4): SimHash (64 Bit) über Wort-Shingles für Fast-Duplikate + exakter Hash.
 * Agenturkopien (gleiche dpa/Reuters-Meldung in vielen Medien) landen in EINER Echo-Gruppe und zählen als eine Quelle.
 * Embedding-basierte Ähnlichkeit kommt in S5 dazu; SimHash ist billig und läuft im Leicht-Hintergrund ohne LLM.
 */
object SimHash {
    private const val SHINGLE = 3

    fun of(text: String): Long {
        val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }
        if (words.isEmpty()) return 0L
        val v = IntArray(64)
        val shingles = if (words.size < SHINGLE) listOf(words.joinToString(" ")) else words.windowed(SHINGLE) { it.joinToString(" ") }
        shingles.forEach { sh ->
            val h = hash64(sh)
            for (i in 0 until 64) v[i] += if ((h ushr i) and 1L == 1L) 1 else -1
        }
        var out = 0L
        for (i in 0 until 64) if (v[i] > 0) out = out or (1L shl i)
        return out
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    private fun hash64(s: String): Long {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        var h = 0L
        for (i in 0 until 8) h = (h shl 8) or (d[i].toLong() and 0xFF)
        return h
    }
}

object Agency {
    /** Agentur-Kürzel im Text -> Hinweis auf Agenturkopie. */
    private val re = Regex("\\((dpa|afp|ap|reuters|kna|epd|dts|ots|tass|ria|ria nowosti|ukrinform|anadolu)(/[a-z]+)?\\)|^(dpa|afp|reuters)\\b|\\b(reuters|associated press)\\s*[-–—]", RegexOption.IGNORE_CASE)
    fun detect(text: String): String? = re.find(text.take(2000))?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.lowercase()?.removePrefix("/")
}

/**
 * Gruppiert Dokumente zu Echo-Gruppen: SimHash-Abstand ≤ [maxDistance] (Schätzwert, am Goldstandard einstellen).
 * Gruppen-ID = stabil aus dem ältesten Dokument abgeleitet.
 */
class EchoGrouper(private val maxDistance: Int = 6) {
    private data class Rep(val group: String, val simhash: Long)
    private val reps = mutableListOf<Rep>()

    /** Liefert die Echo-Gruppe für [doc] (neue Gruppe, falls keine passt). Vorhandene Gruppen bleiben stabil. */
    fun assign(simhash: Long, contentHash: String): String {
        reps.firstOrNull { SimHash.distance(it.simhash, simhash) <= maxDistance }?.let { return it.group }
        val g = "echo-" + contentHash.take(12)
        reps += Rep(g, simhash)
        return g
    }

    /** Bekannte Gruppen (z. B. aus der Datenbank der letzten 72 h) vorladen. */
    fun preload(group: String, simhash: Long) { reps += Rep(group, simhash) }
}
