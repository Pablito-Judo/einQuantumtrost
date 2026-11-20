package de.worldpulse.core

import de.worldpulse.core.extract.PartOfDay
import de.worldpulse.core.extract.Presplit
import de.worldpulse.core.extract.TimeNormalizer
import de.worldpulse.core.testset.TestSet
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExtractPrepTest {
    @Test fun sentences_respect_abbreviations_decimals_dates() {
        val t = "Der Neckar erreichte am 19. November 5,9 m, d.h. mehr als z. B. 2024. Dr. Weber sagte: „Wir sind gut vorbereitet.“ Die Stadt sperrte die Uferstraße."
        val s = Presplit.sentences(t)
        assertEquals(3, s.size, s.joinToString(" | ") { it.text })
        assertTrue(s[1].quotes.single().startsWith("Wir sind gut vorbereitet"))
        s.forEach { assertEquals(it.text, t.substring(it.start, it.end)) } // Offsets stimmen
    }

    @Test fun numbers_and_attribution() {
        val s = Presplit.sentences("Nach Angaben der Polizei entstand ein Schaden von rund 1,5 Millionen Euro. Police said about 8,000 people attended.")
        assertTrue(s[0].numbers.any { it.startsWith("1,5") }); assertEquals("Polizei", s[0].attribution)
        assertEquals("Police", s[1].attribution); assertTrue(s[1].numbers.any { it.startsWith("8,000") })
    }

    @Test fun all_test_articles_split_and_cover_expected_spans() {
        // Jede Soll-Belegspanne muss vollständig in genau einem oder über benachbarte Segmente liegen (keine Zerstückelung mitten im Wort).
        TestSet.articles().forEach { a ->
            val segs = Presplit.sentences(a.text)
            assertTrue(segs.isNotEmpty(), a.id)
            a.expected.forEach { c ->
                val i = a.text.indexOf(c.span)
                assertTrue(segs.any { it.start <= i && i < it.end }, "${a.id}/${c.id}: Spanne nicht in einem Segment")
            }
        }
    }

    private val ref = LocalDate.of(2026, 11, 20) // Freitag

    @Test fun relative_dates_de() {
        val m = TimeNormalizer.find("Am Dienstagabend brannte es. In der Nacht zu Donnerstag bebte die Erde. Gestern Abend gab es Streik.", ref)
        assertEquals(LocalDate.of(2026, 11, 17), m[0].date); assertEquals(PartOfDay.ABEND, m[0].partOfDay)
        val night = m.first { it.span.startsWith("in der nacht") }
        assertEquals(LocalDate.of(2026, 11, 19), night.date); assertEquals(PartOfDay.NACHT, night.partOfDay)
        assertEquals(LocalDate.of(2026, 11, 19), m.last().date)
    }

    @Test fun relative_dates_en_ru_and_ago() {
        assertEquals(LocalDate.of(2026, 11, 15), TimeNormalizer.find("an earthquake struck on Sunday", ref).single().date)
        assertEquals(LocalDate.of(2026, 11, 18), TimeNormalizer.find("the strike happened two days ago", ref).single().date)
        assertEquals(LocalDate.of(2026, 11, 19), TimeNormalizer.find("вчера губернатор сообщил", ref).single().date)
        assertEquals(LocalDate.of(2026, 11, 17), TimeNormalizer.find("vor 3 Tagen", ref).single().date)
    }

    @Test fun absolute_dates_and_year_rollover() {
        val m = TimeNormalizer.find("Am 19. November 2026 und am 3.12. sowie December 28", LocalDate.of(2027, 1, 5))
        assertEquals(LocalDate.of(2026, 11, 19), m[0].date); assertEquals(false, m[0].derived)
        assertEquals(LocalDate.of(2026, 12, 3), m[1].date)   // ohne Jahr, im Januar -> Vorjahr
        assertEquals(LocalDate.of(2026, 12, 28), m[2].date)
        assertNotNull(TimeNormalizer.find("heute", ref).single())
    }
}
