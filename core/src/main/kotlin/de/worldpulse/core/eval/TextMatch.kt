package de.worldpulse.core.eval

/**
 * Sprachunabhängige, deterministische Text-Normalisierung für Abgleiche
 * (Claim-Matching, Belegspannen, Zahlen-/Namens-Treffer). Kein ML, damit Ergebnisse reproduzierbar sind.
 */
object TextMatch {
    private val tokenRe = Regex("[\\p{L}\\p{N}]+(?:[.,][\\p{N}]+)*")
    private val numberRe = Regex("(?<![\\p{L}\\p{N}])\\d{1,3}(?:[ . ']\\d{3})+(?:,\\d+)?|\\d+(?:[.,]\\d+)?")

    /** Kleine Stoppwortliste DE/EN/RU – nur damit Füllwörter das Matching nicht dominieren. */
    private val stop = setOf(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "und", "oder", "in", "im",
        "am", "an", "auf", "zu", "zum", "zur", "von", "vom", "mit", "für", "bei", "nach", "aus", "ist", "sind", "wurde",
        "wurden", "hat", "haben", "sei", "seien", "es", "sie", "er", "nicht", "auch", "dass", "als", "wie", "über",
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "at", "for", "with", "by", "from", "is", "are", "was",
        "were", "has", "have", "had", "be", "been", "it", "its", "that", "this", "as", "said", "says",
        "и", "в", "во", "на", "с", "по", "к", "о", "что", "не", "это", "как", "из", "за", "от", "для",
    )

    fun normalize(s: String): String =
        s.lowercase()
            .replace(' ', ' ')
            .replace(Regex("[„“”«»\"'‚‘’`]"), "")
            .replace(Regex("[–—−]"), "-")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun tokens(s: String): List<String> =
        tokenRe.findAll(normalize(s)).map { normNumber(it.value) }.filter { it.length >= 2 || it[0].isDigit() }
            .filter { it !in stop }.toList()

    /** Zahlen kanonisieren: "1.200" / "1 200" -> "1200", "7,2" -> "7.2". */
    fun numbers(s: String): Set<String> =
        numberRe.findAll(s).map { normNumber(it.value) }.toSet()

    private fun normNumber(v: String): String {
        if (!v[0].isDigit()) return v
        val t = v.replace(" ", "").replace(" ", "").replace("'", "")
        // Tausenderpunkte (1.200.000) vs. Dezimalpunkt (7.2)
        val thousandsDot = Regex("^\\d{1,3}(\\.\\d{3})+(,\\d+)?$")
        val thousandsComma = Regex("^\\d{1,3}(,\\d{3})+(\\.\\d+)?$")
        return when {
            thousandsDot.matches(t) -> t.replace(".", "").replace(',', '.')
            thousandsComma.matches(t) -> t.replace(",", "")
            else -> t.replace(',', '.')
        }.trimEnd('.')
    }

    /** Token-F1 zwischen zwei Texten (0..1). */
    fun tokenF1(a: String, b: String): Double {
        val ta = tokens(a); val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val cb = tb.groupingBy { it }.eachCount().toMutableMap()
        var common = 0
        for (t in ta) { val c = cb[t] ?: 0; if (c > 0) { common++; cb[t] = c - 1 } }
        if (common == 0) return 0.0
        val p = common.toDouble() / ta.size; val r = common.toDouble() / tb.size
        return 2 * p * r / (p + r)
    }

    /** Anteil der Token von [part], die in [whole] vorkommen (0..1). */
    fun containment(part: String, whole: String): Double {
        val tp = tokens(part); if (tp.isEmpty()) return 0.0
        val tw = tokens(whole).toSet()
        return tp.count { it in tw }.toDouble() / tp.size
    }

    /**
     * Belegspanne gültig? Wörtlich (normalisiert) im Quelltext, oder ≥ 90 % Token-Abdeckung
     * (kleine Abweichungen wie Anführungszeichen/Trennstriche). Leere Spanne = ungültig.
     */
    fun spanSupported(span: String, source: String, minContainment: Double = 0.9): Boolean {
        if (span.isBlank()) return false
        val ns = normalize(span).trim('.', ' ')
        if (ns.isNotEmpty() && normalize(source).contains(ns)) return true
        return tokens(span).size >= 3 && containment(span, source) >= minContainment
    }
}
