package de.worldpulse.core

import de.worldpulse.core.claims.ExtractionPrompts
import de.worldpulse.core.extract.ClaimExtractor
import de.worldpulse.core.testset.TestSet
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaimExtractorTest {
    private val article = TestSet.articles().first { it.id == "de01" }

    @Test fun keeps_supported_spans_rejects_invented_and_dedupes() = runBlocking {
        val fake: suspend (String, String, Int) -> String = { _, user, _ ->
            assertTrue(user.contains("Lagerhalle"))
            """Notizen: Brand, Verletzte, Schaden.
${ExtractionPrompts.CLAIMS_MARKER}
Zwei Feuerwehrleute wurden leicht verletzt | Polizei | Fakt | zwei Feuerwehrleute leicht verletzt worden
Zwei Feuerwehrleute wurden leicht verletzt | Polizei | Fakt | zwei Feuerwehrleute leicht verletzt worden
Der Schaden beträgt 3 Millionen Euro | Polizei | Fakt | ein Schaden von rund 3 Millionen Euro
Brand am Dienstagabend | Polizei | Fakt | am Dienstagabend zwei Feuerwehrleute"""
        }
        // Meldezeit Mittwoch 18.11.2026 -> "Dienstagabend" = 17.11.
        val published = LocalDate.of(2026, 11, 18).atTime(9, 0).atZone(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
        val r = ClaimExtractor(fake).extract(article.text, published)
        assertEquals(2, r.claims.size, r.claims.toString())   // Duplikat entfernt
        assertEquals(1, r.rejected)                              // erfundene Zahl (3 Mio) ohne Beleg verworfen
        val first = r.claims[0]
        assertEquals(article.text.substring(first.spanStart!!, first.spanEnd!!), first.claim.span)
        assertEquals(LocalDate.of(2026, 11, 17), r.claims[1].eventDate)
    }

    @Test fun chunking_respects_limits() {
        val long = (1..200).joinToString(" ") { "Satz Nummer $it beschreibt ein Ereignis in Heilbronn." }
        val ch = ClaimExtractor({ _, _, _ -> "" }, maxChunkChars = 1000, maxChunks = 2).chunk(long)
        assertEquals(2, ch.size); assertTrue(ch.all { it.length <= 1000 })
    }

    @Test fun parse_error_counted_not_thrown() = runBlocking {
        val r = ClaimExtractor({ _, _, _ -> "Ich kann dazu nichts sagen." }).extract(article.text, null)
        assertEquals(0, r.claims.size); assertEquals(1, r.parseErrors)
    }
}
