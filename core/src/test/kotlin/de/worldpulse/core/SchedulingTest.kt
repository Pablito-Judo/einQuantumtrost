package de.worldpulse.core

import de.worldpulse.core.bench.Thermal
import de.worldpulse.core.run.CostModel
import de.worldpulse.core.run.DeviceState
import de.worldpulse.core.run.Dispatcher
import de.worldpulse.core.run.Job
import de.worldpulse.core.run.Level
import de.worldpulse.core.run.Phase
import de.worldpulse.core.run.Preflight
import de.worldpulse.core.run.Resubmit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchedulingTest {
    private val ok = DeviceState(charging = true, batteryPct = 80, batteryTempC = 31.0, thermal = Thermal.NONE, wifi = true, freeStorageMb = 40_000, nowMs = 0)

    @Test fun preflight_blocks_and_explains() {
        assertTrue(Preflight.check(ok, deadlineMs = 8 * 3600_000).canStart)
        val r = Preflight.check(ok.copy(charging = false, thermal = Thermal.MODERATE), 8 * 3600_000)
        assertFalse(r.canStart); assertTrue(r.reasons.any { "Ladekabel" in it }); assertTrue(r.reasons.any { "Thermal" in it })
        assertTrue(Preflight.check(ok.copy(wifi = false), 8 * 3600_000).canStart) // ohne WLAN: nur Warnung
        assertFalse(Preflight.check(ok, deadlineMs = 10 * 60_000).canStart)
    }

    @Test fun degradation_levels() {
        assertEquals(Level.NORMAL, Preflight.level(ok))
        assertEquals(Level.HEISS, Preflight.level(ok.copy(thermal = Thermal.MODERATE)))
        assertEquals(Level.HEISS, Preflight.level(ok.copy(batteryTempC = 42.0)))
        assertEquals(Level.AKKU_NIEDRIG, Preflight.level(ok.copy(charging = false, batteryPct = 20)))
        assertEquals(Level.SPEICHER_KNAPP, Preflight.level(ok.copy(freeStorageMb = 1_000)))
        assertFalse(Phase.EXTRAKTION in Level.HEISS.allowed); assertTrue(Phase.EMBEDDING in Level.HEISS.allowed)
    }

    @Test fun dispatcher_orders_phases_respects_budget_and_level() {
        val q = listOf(
            Job("w1", Phase.SCHREIBEN, "write_section", 5, 10.0, 0.9),
            Job("e1", Phase.EXTRAKTION, "extract", 2, 1.0, 0.2),
            Job("e2", Phase.EXTRAKTION, "extract", 5, 5.0, 0.9, focus = true),
            Job("e2", Phase.EXTRAKTION, "extract", 5, 5.0, 0.9, focus = true), // Duplikat (idempotent)
            Job("m1", Phase.EMBEDDING, "embed", 3, 1.0, 0.5),
        )
        val d = Dispatcher()
        val plan = d.plan(q, Level.NORMAL, budgetSec = 10_000.0)
        assertEquals(listOf("e2", "e1", "m1", "w1"), plan.map { it.key })   // Phase vor Priorität, Fokus zuerst
        assertEquals(listOf("m1"), d.plan(q, Level.HEISS, 10_000.0).map { it.key })
        assertEquals(listOf("e2", "m1"), d.plan(q, Level.NORMAL, budgetSec = 40.0).map { it.key }) // Zeitbudget: billiges Embedding passt noch
    }

    @Test fun cost_model_learns() {
        val c = CostModel()
        assertEquals(35.0, c.estimate("extract"))
        c.observe("extract", 20.0); assertEquals(20.0, c.estimate("extract"))
        c.observe("extract", 30.0); assertEquals(22.0, c.estimate("extract"), 1e-9)
    }

    @Test fun resubmission_schedule() {
        assertEquals(3600_000L, Resubmit.nextDue(0, 0, 0.0))
        assertEquals(1800_000L, Resubmit.nextDue(0, 0, 1.0))
        assertEquals(0, Resubmit.nextStep(3, changedClaims = 2))
        assertEquals(4, Resubmit.nextStep(3, changedClaims = 0))
        assertTrue(Resubmit.frozen(Resubmit.nextStep(5, 0)))
    }
}
