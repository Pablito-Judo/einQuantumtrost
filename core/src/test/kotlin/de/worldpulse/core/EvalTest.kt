package de.worldpulse.core

import de.worldpulse.core.claims.ClaimType
import de.worldpulse.core.claims.ExtractedClaim
import de.worldpulse.core.eval.ClaimEvaluator
import de.worldpulse.core.eval.TextMatch
import de.worldpulse.core.testset.TestSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvalTest {
    @Test fun numbers_are_canonical() {
        assertEquals(setOf("1200", "7.2", "2026"), TextMatch.numbers("1.200 Menschen, Pegel 7,2 m, Jahr 2026"))
        assertEquals(setOf("20000", "4.5"), TextMatch.numbers("more than 20,000 people, rate 4.5 percent"))
        assertEquals(setOf("2300"), TextMatch.numbers("rund 2.300 Beschäftigte"))
    }

    @Test fun span_support() {
        val src = "Bürgermeister Klaus Brenner nannte die Entscheidung \"überfällig\"."
        assertTrue(TextMatch.spanSupported("nannte die Entscheidung „überfällig“", src))
        assertFalse(TextMatch.spanSupported("nannte die Entscheidung falsch", src))
        assertFalse(TextMatch.spanSupported("", src))
    }

    @Test fun perfect_prediction_scores_one() {
        TestSet.articles().forEach { a ->
            val pred = a.expected.map { ExtractedClaim(it.text, it.speaker, it.type, it.span) }
            val s = ClaimEvaluator.score(pred, a.expectedClaims(), a.text)
            assertEquals(1.0, s.recall, a.id); assertEquals(1.0, s.precision, a.id)
            assertEquals(1.0, s.spanRate, "${a.id}: Soll-Spannen müssen wörtlich im Text stehen")
        }
    }

    @Test fun wrong_number_does_not_match() {
        val a = TestSet.articles().first { it.id == "de03" }
        val pred = listOf(ExtractedClaim("Die Inflationsrate in Baden-Württemberg lag im Oktober bei 2,5 Prozent", "", ClaimType.FAKT, ""))
        val s = ClaimEvaluator.score(pred, a.expectedClaims(), a.text)
        assertEquals(0, s.matched)
    }

    @Test fun paraphrase_matches_and_extra_claim_lowers_precision() {
        val a = TestSet.articles().first { it.id == "de01" }
        val pred = listOf(
            ExtractedClaim("Zwei Feuerwehrleute wurden bei dem Brand leicht verletzt", "Polizei", ClaimType.FAKT, "zwei Feuerwehrleute leicht verletzt worden"),
            ExtractedClaim("Der Bürgermeister besuchte die Brandstelle", "Artikel", ClaimType.FAKT, "Bürgermeister besuchte"),
        )
        val s = ClaimEvaluator.score(pred, a.expectedClaims(), a.text)
        assertEquals(1, s.matched); assertEquals(0.5, s.precision); assertEquals(0.25, s.recall)
        assertEquals(0.5, s.spanRate)
    }

    @Test fun testset_shape() {
        val arts = TestSet.articles()
        assertEquals(20, arts.size)
        assertEquals(arts.size, arts.map { it.id }.toSet().size)
        assertTrue(arts.count { it.translatedFrom == "ru" } >= 5)
        val pairs = TestSet.embeddingPairs()
        assertEquals(10, pairs.size); assertEquals(5, pairs.count { it.same })
    }
}
