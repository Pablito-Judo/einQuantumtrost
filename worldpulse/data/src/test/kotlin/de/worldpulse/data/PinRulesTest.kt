package de.worldpulse.data

import de.worldpulse.core.gold.SourceTier
import de.worldpulse.data.db.StatusRing
import de.worldpulse.data.db.Symbol
import de.worldpulse.data.pins.EvidenceSummary
import de.worldpulse.data.pins.PinAging
import de.worldpulse.data.pins.PinStage
import de.worldpulse.data.pins.StatusRule
import de.worldpulse.data.pins.SymbolMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRulesTest {
    @Test fun cameo_mapping() {
        assertEquals(Symbol.PROTEST, SymbolMapper.fromCameo("141"))
        assertEquals(Symbol.PROTEST, SymbolMapper.fromCameo("14"))
        assertEquals(Symbol.KAMPFHANDLUNG, SymbolMapper.fromCameo("193"))
        assertEquals(Symbol.EXPLOSION_BESCHUSS, SymbolMapper.fromCameo("195"))
        assertEquals(Symbol.EXPLOSION_BESCHUSS, SymbolMapper.fromCameo("1831"))
        assertEquals(Symbol.GEWALT_ZIVILISTEN, SymbolMapper.fromCameo("182"))
        assertEquals(Symbol.GEWALT_ZIVILISTEN, SymbolMapper.fromCameo("202"))
        assertNull(SymbolMapper.fromCameo("042"))
        assertNull(SymbolMapper.fromCameo("x9"))
    }

    @Test fun acled_mapping() {
        assertEquals(Symbol.KAMPFHANDLUNG, SymbolMapper.fromAcled("Battles"))
        assertEquals(Symbol.EXPLOSION_BESCHUSS, SymbolMapper.fromAcled("Explosions/Remote violence"))
        assertEquals(Symbol.PROTEST, SymbolMapper.fromAcled("Riots"))
        assertNull(SymbolMapper.fromAcled("unknown"))
    }

    @Test fun status_ring_rule() {
        fun e(a: Int = 0, b: Int = 0, c: Int = 0, p: Boolean = false, x: Boolean = false) = EvidenceSummary(a, b, c, p, x)
        assertEquals(StatusRing.GEMELDET, StatusRule.ring(e(a = 1)))
        assertEquals(StatusRing.GEMELDET, StatusRule.ring(e(c = 5)))           // nur Social/Video reicht nie
        assertEquals(StatusRing.BESTAETIGT, StatusRule.ring(e(a = 1, b = 1)))
        assertEquals(StatusRing.BESTAETIGT, StatusRule.ring(e(p = true)))
        assertEquals(StatusRing.UMSTRITTEN, StatusRule.ring(e(a = 3, x = true))) // Widerspruch schlägt Bestätigung
        assertEquals(SourceTier.B, StatusRule.bestTier(e(b = 1, c = 2)))
    }

    @Test fun aging() {
        assertEquals(PinStage.NEU, PinAging.look(60_000).stage)
        assertTrue(PinAging.look(60_000).pulse)
        val mid = PinAging.look(12 * 3600_000L)
        assertEquals(PinStage.AKTIV, mid.stage); assertTrue(mid.alpha in 0.4f..1f)
        assertEquals(PinStage.BLASS, PinAging.look(30 * 3600_000L).stage)
        assertEquals(PinStage.PUNKT, PinAging.look(100 * 3600_000L).stage)
    }

    @Test fun ago_text() {
        assertEquals("vermutlich vor ca. 3 h (Annahme)", PinAging.agoText(3 * 3600_000L))
        assertEquals("vor ca. 20 min", PinAging.agoText(20 * 60_000L, estimated = false))
        assertEquals("vermutlich vor ca. 3 Tagen (Annahme)", PinAging.agoText(72 * 3600_000L))
    }
}
