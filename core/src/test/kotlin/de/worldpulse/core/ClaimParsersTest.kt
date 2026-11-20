package de.worldpulse.core

import de.worldpulse.core.claims.ClaimParsers
import de.worldpulse.core.claims.ClaimType
import de.worldpulse.core.claims.ExtractionPrompts
import de.worldpulse.core.claims.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaimParsersTest {
    @Test fun json_plain_and_fenced() {
        val raw = """```json
            {"claims":[{"claim":"Der Pegel stieg auf 5,9 Meter","speaker":"HVZ","type":"Fakt","span":"5,9 Metern"}]}
            ```"""
        val r = ClaimParsers.parse(OutputFormat.JSON_SCHEMA, raw)
        assertTrue(r.ok); assertEquals(1, r.claims.size)
        assertEquals(ClaimType.FAKT, r.claims[0].type)
        assertEquals("5,9 Metern", r.claims[0].span)
    }

    @Test fun json_broken_is_not_ok() {
        val r = ClaimParsers.parseJson("""{"claims":[{"claim":"x", """)
        assertFalse(r.ok)
    }

    @Test fun json_top_level_array_and_missing_text() {
        val r = ClaimParsers.parseJson("""[{"claim":"A","type":"Meinung"},{"speaker":"B"}]""")
        assertTrue(r.ok); assertEquals(1, r.claims.size); assertEquals(1, r.errors.size)
        assertEquals(ClaimType.MEINUNG, r.claims[0].type)
    }

    @Test fun lines_with_preamble_numbering_and_header() {
        val raw = """
            Here are the claims:
            claim | speaker | type | span
            1. Zwei Feuerwehrleute wurden verletzt | Polizei | Fakt | zwei Feuerwehrleute leicht verletzt worden
            - Schaden 1,5 Mio. Euro | Polizei | fact | "Schaden von rund 1,5 Millionen Euro"
            Ursache unklar | Polizei | Fakt
        """.trimIndent()
        val r = ClaimParsers.parseLines(raw)
        assertTrue(r.ok); assertEquals(3, r.claims.size)
        assertEquals("Schaden von rund 1,5 Millionen Euro", r.claims[1].span)
        assertEquals("", r.claims[2].span)
    }

    @Test fun lines_empty_output_is_ok_but_prose_is_not() {
        assertTrue(ClaimParsers.parseLines("").ok)
        assertFalse(ClaimParsers.parseLines("Ich kann keine Claims finden.").ok)
    }

    @Test fun think_then_lines_requires_marker() {
        val good = "Notizen: Brand, 2 Verletzte.\n${ExtractionPrompts.CLAIMS_MARKER}\nBrand in Halle | Polizei | Fakt | Großbrand"
        val r = ClaimParsers.parseThinkThenLines(good)
        assertTrue(r.ok); assertEquals(1, r.claims.size)
        val bad = ClaimParsers.parseThinkThenLines("Brand in Halle | Polizei | Fakt | Großbrand")
        assertFalse(bad.ok); assertEquals(1, bad.claims.size)
    }

    @Test fun claim_type_synonyms() {
        assertEquals(ClaimType.BEHAUPTUNG, ClaimType.parse("Claim"))
        assertEquals(ClaimType.ZITAT, ClaimType.parse(" quote."))
        assertEquals(ClaimType.UNKLAR, ClaimType.parse("??"))
    }

    @Test fun json_schema_is_valid_json() {
        kotlinx.serialization.json.Json.parseToJsonElement(ExtractionPrompts.jsonSchema)
        OutputFormat.entries.forEach { assertTrue(ExtractionPrompts.user(it, "X").contains("X")) }
    }
}
