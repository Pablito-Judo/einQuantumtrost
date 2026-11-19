package de.worldpulse.core

import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import de.worldpulse.core.claims.ExtractedClaim
import de.worldpulse.core.gold.GoldEvaluator
import de.worldpulse.core.gold.GoldEvent
import de.worldpulse.core.gold.GoldLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoldStandardTest {
    private fun res(p: String) = javaClass.classLoader.getResourceAsStream(p)!!.bufferedReader().readText()

    @Test fun bundled_events_load_and_validate() {
        val events = GoldLoader.loadBundled()
        assertEquals(3, events.size)
        events.forEach { assertEquals(emptyList(), GoldLoader.validate(it), it.id) }
    }

    @Test fun bundled_events_match_json_schema() {
        val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(res("gold/goldstandard.schema.json"))
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        res("gold/events/index.txt").lines().filter { it.endsWith(".json") }.forEach { f ->
            val errs = schema.validate(mapper.readTree(res("gold/events/$f")))
            assertTrue(errs.isEmpty(), "$f: $errs")
        }
    }

    @Test fun roundtrip_is_lossless() {
        val e = GoldLoader.loadBundled().first()
        val back = GoldLoader.parse(GoldLoader.json.encodeToString(GoldEvent.serializer(), e))
        assertEquals(e, back)
    }

    @Test fun validate_detects_broken_references() {
        val e = GoldLoader.loadBundled().first()
        val broken = e.copy(claims = e.claims.map { if (it.id == "g1-c1") it.copy(span = "erfundener Text der nicht vorkommt", sourceId = "s1") else it } +
            e.claims.first().copy(id = "g1-c99", sourceId = "s9"))
        val errs = GoldLoader.validate(broken)
        assertTrue(errs.any { "Belegspanne" in it }); assertTrue(errs.any { "unbekannte Quelle" in it })
    }

    @Test fun extraction_scoring_per_event() {
        val e = GoldLoader.loadBundled().first { it.id == "gold-0001" }
        val perfect = e.claims.groupBy { it.sourceId }.mapValues { (_, cs) -> cs.map { ExtractedClaim(it.text, it.speaker, it.type, it.span) } }
        val s = GoldEvaluator.scoreExtraction(e, perfect)
        assertEquals(1.0, s.recall); assertEquals(1.0, s.precision)
        val half = mapOf("s1" to perfect.getValue("s1"))
        assertTrue(GoldEvaluator.scoreExtraction(e, half).recall < 0.5)
    }

    @Test fun reference_dossier_has_no_unsupported_numbers() {
        GoldLoader.loadBundled().forEach { e ->
            val text = e.referenceDossier.joinToString(" ") { it.text }
            val f = GoldEvaluator.scoreFacts(e, text)
            assertEquals(emptySet(), f.numbersUnsupported, e.id)
            assertTrue(f.entityRecall >= 0.6, "${e.id}: ${f.entitiesMentioned}")
        }
    }

    @Test fun hallucinated_number_is_flagged() {
        val e = GoldLoader.loadBundled().first { it.id == "gold-0002" }
        val f = GoldEvaluator.scoreFacts(e, "Russland meldet 57 abgeschossene Drohnen über Belgorod.")
        assertEquals(setOf("57"), f.numbersUnsupported)
        assertEquals(0.0, f.numberPrecision)
    }
}
