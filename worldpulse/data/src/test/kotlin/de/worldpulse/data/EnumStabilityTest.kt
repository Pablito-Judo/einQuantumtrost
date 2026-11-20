package de.worldpulse.data

import de.worldpulse.core.claims.ClaimType
import de.worldpulse.core.gold.GeoPrecision
import de.worldpulse.core.gold.SourceTier
import de.worldpulse.data.db.Phase
import de.worldpulse.data.db.StatusRing
import de.worldpulse.data.db.Symbol
import de.worldpulse.data.db.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Room speichert Enums als Namen. Diese Liste friert die gespeicherten Werte ein:
 * Umbenennen/Löschen bricht bestehende Datenbanken -> nur anhängen, sonst Migration schreiben.
 */
class EnumStabilityTest {
    private fun names(e: Array<out Enum<*>>) = e.map { it.name }

    @Test fun persisted_enum_names_are_stable() {
        assertEquals(listOf("FAKT", "BEHAUPTUNG", "ZITAT", "MEINUNG", "UNKLAR"), names(ClaimType.entries.toTypedArray()))
        assertEquals(listOf("A", "B", "C"), names(SourceTier.entries.toTypedArray()))
        assertEquals(listOf("LAND", "REGION", "STADT", "STRASSE", "PUNKT"), names(GeoPrecision.entries.toTypedArray()))
        assertEquals(listOf("BESTAETIGT", "GEMELDET", "UMSTRITTEN"), names(StatusRing.entries.toTypedArray()))
        assertEquals(listOf("BELEGT", "TEILWEISE", "UNBELEGT", "UNGEPRUEFT"), names(Verdict.entries.toTypedArray()))
        assertEquals(
            listOf("KAMPFHANDLUNG", "EXPLOSION_BESCHUSS", "GEWALT_ZIVILISTEN", "PROTEST", "ERDBEBEN", "BRAND",
                "UNWETTER_KATASTROPHE", "POLITIK_WAHL", "EVENT", "SONSTIGES"),
            names(Symbol.entries.toTypedArray()),
        )
        assertEquals(12, Phase.entries.size)
    }
}
