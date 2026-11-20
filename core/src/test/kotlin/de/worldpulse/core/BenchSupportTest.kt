package de.worldpulse.core

import de.worldpulse.core.bench.Csv
import de.worldpulse.core.bench.DutyAction
import de.worldpulse.core.bench.DutyCycler
import de.worldpulse.core.bench.DutyPolicy
import de.worldpulse.core.bench.MinuteAggregator
import de.worldpulse.core.bench.PromptFiller
import de.worldpulse.core.bench.Thermal
import de.worldpulse.core.embed.PairQuality
import de.worldpulse.core.embed.Vectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchSupportTest {
    @Test fun duty_cycle_schedule() {
        val d = DutyCycler(DutyPolicy(workMs = 60_000, pauseMs = 30_000), 0)
        assertEquals(DutyAction.WORK, d.decide(10_000, Thermal.NONE))
        assertEquals(DutyAction.PAUSE_SCHEDULED, d.decide(60_000, Thermal.NONE))
        assertEquals(DutyAction.PAUSE_SCHEDULED, d.decide(80_000, Thermal.LIGHT))
        assertEquals(DutyAction.WORK, d.decide(90_000, Thermal.LIGHT))
    }

    @Test fun duty_cycle_thermal_hold_until_below_moderate() {
        val d = DutyCycler(DutyPolicy(), 0)
        assertEquals(DutyAction.PAUSE_THERMAL, d.decide(1_000, Thermal.MODERATE))
        assertEquals(DutyAction.PAUSE_THERMAL, d.decide(200_000, Thermal.MODERATE))
        assertEquals(DutyAction.WORK, d.decide(210_000, Thermal.LIGHT))
    }

    @Test fun measured_policy_works_through_moderate_pauses_at_severe() {
        val d = DutyCycler(DutyPolicy.MEASURED, 0)
        assertEquals(DutyAction.WORK, d.decide(3_600_000, Thermal.MODERATE))
        assertEquals(DutyAction.PAUSE_THERMAL, d.decide(3_700_000, Thermal.SEVERE))
        assertEquals(DutyAction.PAUSE_THERMAL, d.decide(3_800_000, Thermal.MODERATE))
        assertEquals(DutyAction.WORK, d.decide(3_900_000, Thermal.LIGHT))
    }

    @Test fun disabled_policy_always_works() {
        val d = DutyCycler(DutyPolicy(enabled = false), 0)
        assertEquals(DutyAction.WORK, d.decide(10_000_000, Thermal.SEVERE))
    }

    @Test fun minute_aggregation() {
        val m = MinuteAggregator(0)
        m.add(30_000, 100, 10_000); m.add(59_000, 50, 5_000); m.add(61_000, 20, 2_000)
        val s = m.summary()
        assertEquals(2, s.size); assertEquals(150.0, s[0].second); assertEquals(10.0, s[0].third)
        assertEquals(170, m.totalTokens())
    }

    @Test fun csv_escaping() {
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("plain", Csv.escape("plain"))
    }

    @Test fun prompt_filler_length() {
        val t = PromptFiller.build(listOf("abc def"), 500)
        assertEquals(2000, t.length)
    }

    @Test fun vectors_cosine_truncate_blob() {
        val a = floatArrayOf(1f, 0f, 1f, 0f); val b = floatArrayOf(1f, 0f, 0f, 1f)
        assertEquals(0.5, Vectors.cosine(a, b), 1e-6)
        val t = Vectors.truncate(a, 2); assertEquals(1.0, Vectors.cosine(t, floatArrayOf(1f, 0f)), 1e-6)
        assertTrue(Vectors.fromBlob(Vectors.toBlob(a)).contentEquals(a))
    }

    @Test fun pair_quality() {
        val q = PairQuality.of(listOf(0.9, 0.8, 0.85), listOf(0.3, 0.5, 0.82))
        assertTrue(q.margin < 0); assertEquals(5.0 / 6, q.bestThresholdAccuracy, 1e-9)
    }
}
