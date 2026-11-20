package de.worldpulse.core.eval

import de.worldpulse.core.claims.ExtractedClaim

/** Soll-Claim (aus Test-Set oder Goldstandard) in minimaler Form. */
data class ExpectedClaim(val id: String, val text: String, val speaker: String = "", val span: String = "")

data class ClaimMatch(val predicted: ExtractedClaim, val expected: ExpectedClaim, val score: Double)

data class ClaimScore(
    val predicted: Int,
    val expected: Int,
    val matched: Int,
    /** Claims mit wörtlich im Text gefundener Belegspanne (SPEC Aufgabenleiter Schritt 3). */
    val spanSupported: Int,
    val matches: List<ClaimMatch>,
) {
    val recall: Double get() = if (expected == 0) 1.0 else matched.toDouble() / expected
    val precision: Double get() = if (predicted == 0) (if (expected == 0) 1.0 else 0.0) else matched.toDouble() / predicted
    val f1: Double get() = if (recall + precision == 0.0) 0.0 else 2 * recall * precision / (recall + precision)
    val spanRate: Double get() = if (predicted == 0) 0.0 else spanSupported.toDouble() / predicted

    operator fun plus(o: ClaimScore) = ClaimScore(
        predicted + o.predicted, expected + o.expected, matched + o.matched,
        spanSupported + o.spanSupported, matches + o.matches,
    )

    companion object { val EMPTY = ClaimScore(0, 0, 0, 0, emptyList()) }
}

/**
 * Claim-Abgleich: gieriges 1:1-Matching nach Token-F1.
 * Regel: Kommt im Soll-Claim eine Zahl vor, muss der vorhergesagte Claim diese Zahl enthalten
 * (Zahlen sind die häufigste Halluzinationsquelle). Schwelle 0.5 = Startwert, am Goldstandard einstellen.
 */
object ClaimEvaluator {
    const val DEFAULT_THRESHOLD = 0.5

    fun score(
        predicted: List<ExtractedClaim>,
        expected: List<ExpectedClaim>,
        sourceText: String,
        threshold: Double = DEFAULT_THRESHOLD,
    ): ClaimScore {
        val candidates = mutableListOf<Triple<Int, Int, Double>>()
        predicted.forEachIndexed { i, p ->
            val pNums = TextMatch.numbers(p.text)
            expected.forEachIndexed { j, e ->
                val eNums = TextMatch.numbers(e.text)
                if (!pNums.containsAll(eNums)) return@forEachIndexed
                val s = TextMatch.tokenF1(p.text, e.text)
                if (s >= threshold) candidates += Triple(i, j, s)
            }
        }
        val usedP = HashSet<Int>(); val usedE = HashSet<Int>()
        val matches = mutableListOf<ClaimMatch>()
        for ((i, j, s) in candidates.sortedByDescending { it.third }) {
            if (i in usedP || j in usedE) continue
            usedP += i; usedE += j
            matches += ClaimMatch(predicted[i], expected[j], s)
        }
        val spans = predicted.count { TextMatch.spanSupported(it.span, sourceText) }
        return ClaimScore(predicted.size, expected.size, matches.size, spans, matches)
    }
}
