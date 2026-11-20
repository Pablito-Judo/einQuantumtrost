package de.worldpulse.core.run

import de.worldpulse.core.bench.Thermal
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/*
 * Analyse-Modus-Rahmen (SPEC 4/5, Roadmap S3) – reine Logik ohne Android:
 * Vorab-Check, Degradationsstufen, Dispatcher (Priorität, Phasen-Batching, Zeitbudget, lernende Kosten).
 * Grenzwerte sind Startwerte (Schätzung) und werden nach dem S23-Benchmark angepasst.
 */

data class DeviceState(
    val charging: Boolean,
    val batteryPct: Int,
    val batteryTempC: Double,
    val thermal: Thermal,
    val wifi: Boolean,
    val freeStorageMb: Long,
    val nowMs: Long,
)

data class Limits(
    val minBatteryPct: Int = 30,
    /** Nutzervorgabe 20.11.: vorsichtig – Start nur unter 38 °C. */
    val maxStartTempC: Double = 38.0,
    val minFreeStorageMb: Long = 2_000,
    val lowStorageMb: Long = 5_000,
    /** Nutzervorgabe 20.11.: ab 40 °C nur noch Arbeit ohne LLM (Messung: 42–43 °C wären technisch möglich). */
    val hotTempC: Double = 40.0,
)

/** Degradationsstufen (SPEC 4): normal → heiß (nur Sammeln+Embedding) → Akku niedrig (nur Sammeln) → Speicher knapp (Haushalt zuerst). */
enum class Level(val allowed: Set<Phase>) {
    NORMAL(Phase.entries.toSet()),
    HEISS(setOf(Phase.SAMMELN, Phase.BEREINIGEN, Phase.DEDUPE, Phase.EMBEDDING, Phase.HAUSHALT)),
    AKKU_NIEDRIG(setOf(Phase.SAMMELN, Phase.BEREINIGEN, Phase.DEDUPE, Phase.HAUSHALT)),
    SPEICHER_KNAPP(setOf(Phase.HAUSHALT, Phase.SAMMELN)),
}

/** Phasen in Batching-Reihenfolge (SPEC 5): erst alle Extraktionen, dann Embedding/Clustering, dann Schreiben/Prüfen, dann Wiedervorlage. */
enum class Phase(val llm: Boolean) {
    HAUSHALT(false), SAMMELN(false), BEREINIGEN(false), DEDUPE(false),
    EXTRAKTION(true), EMBEDDING(false), CLUSTERING(false), SCHREIBEN(true), PRUEFEN(true), WIEDERVORLAGE(false),
}

object Preflight {
    data class Result(val canStart: Boolean, val level: Level, val reasons: List<String>)

    fun check(d: DeviceState, deadlineMs: Long, limits: Limits = Limits()): Result {
        val reasons = mutableListOf<String>()
        if (!d.charging) reasons += "Ladekabel fehlt"
        if (d.batteryPct < limits.minBatteryPct) reasons += "Akku unter ${limits.minBatteryPct} %"
        if (d.batteryTempC > limits.maxStartTempC) reasons += "Akku zu warm (${d.batteryTempC} °C)"
        if (d.thermal.code >= Thermal.MODERATE.code) reasons += "Thermal-Status ${d.thermal}"
        if (!d.wifi) reasons += "kein WLAN (nur Analyse vorhandener Daten)"
        if (d.freeStorageMb < limits.minFreeStorageMb) reasons += "Speicher unter ${limits.minFreeStorageMb} MB"
        if (deadlineMs - d.nowMs < 15 * 60_000) reasons += "Zeitbudget unter 15 min"
        val blocking = reasons.filterNot { it.startsWith("kein WLAN") }
        return Result(blocking.isEmpty(), level(d, limits), reasons)
    }

    /** Laufende Einstufung (bei jeder Aufgabe neu). */
    fun level(d: DeviceState, limits: Limits = Limits()): Level = when {
        d.freeStorageMb < limits.lowStorageMb -> Level.SPEICHER_KNAPP
        !d.charging && d.batteryPct < limits.minBatteryPct -> Level.AKKU_NIEDRIG
        d.thermal.code >= Thermal.MODERATE.code || d.batteryTempC >= limits.hotTempC -> Level.HEISS
        else -> Level.NORMAL
    }
}

/** Eine Aufgabe in der Prioritäts-Warteschlange (idempotent über [key]). */
data class Job(
    val key: String,
    val phase: Phase,
    val kind: String,
    /** 1–5 (Aufgabenleiter Schritt 5 / Fokus). */
    val importance: Int,
    /** Stunden seit letzter Aktualisierung des Ziels. */
    val stalenessH: Double,
    /** 0–1 (Eilmeldung hoch, langsames Thema niedrig). */
    val volatility: Double,
    val focus: Boolean = false,
)

/** Lernt mittlere Kosten (Sekunden) je Aufgabentyp aus dem Lauf-Protokoll (gleitender Mittelwert). */
class CostModel(private val defaults: Map<String, Double> = DEFAULTS, private val alpha: Double = 0.2) {
    private val est = HashMap<String, Double>()
    fun estimate(kind: String): Double = est[kind] ?: defaults[kind] ?: 30.0
    fun observe(kind: String, seconds: Double) { est[kind] = est[kind]?.let { it + alpha * (seconds - it) } ?: seconds }

    companion object {
        /** Schätzwerte bis zum S23-Benchmark (SPEC 6): Extraktion ~30–40 s, Abschnitt ~60–120 s. */
        val DEFAULTS = mapOf(
            "extract" to 35.0, "embed" to 0.5, "cluster" to 2.0, "write_section" to 90.0, "verify_sentence" to 8.0,
            "nli_pair" to 6.0, "fetch" to 2.0, "clean" to 0.2, "dedupe" to 0.05, "housekeeping" to 5.0,
        )
    }
}

/**
 * Dispatcher (Agent 1): Priorität = Wichtigkeit × Veraltung × Volatilität × Fokus − Kosten.
 * Plant einen Batch: nur erlaubte Phasen (Degradation), Phasen in fester Reihenfolge (Ladezeiten sparen),
 * innerhalb der Phase nach Priorität, bis das Zeitbudget verbraucht ist.
 */
class Dispatcher(private val costs: CostModel = CostModel(), private val costWeight: Double = 0.02) {

    fun priority(j: Job): Double {
        val imp = j.importance.coerceIn(1, 5).toDouble()
        val stale = 1.0 + ln(1.0 + max(0.0, j.stalenessH))          // wächst langsam mit dem Alter
        val vol = 0.5 + j.volatility.coerceIn(0.0, 1.0)              // 0.5 … 1.5
        val focus = if (j.focus) 2.0 else 1.0
        return imp * stale * vol * focus - costWeight * costs.estimate(j.kind)
    }

    fun plan(queue: List<Job>, level: Level, budgetSec: Double): List<Job> {
        val seen = HashSet<String>()
        val allowed = queue.filter { it.phase in level.allowed && seen.add(it.key) }
        val out = mutableListOf<Job>()
        var used = 0.0
        for (phase in Phase.entries) {
            for (j in allowed.filter { it.phase == phase }.sortedByDescending(::priority)) {
                val c = costs.estimate(j.kind)
                if (used + c > budgetSec) continue
                out += j; used += c
            }
        }
        return out
    }

    fun record(kind: String, seconds: Double) = costs.observe(kind, seconds)
}

/** Wiedervorlage (Agent 14, S7): Abstände 1 h, 6 h, 1 d, 3 d, 7 d, 30 d, kürzer bei Volatilität. */
object Resubmit {
    val STEPS_H = doubleArrayOf(1.0, 6.0, 24.0, 72.0, 168.0, 720.0)

    /** Nächste Fälligkeit nach Schritt [step]; Volatilität 1.0 halbiert den Abstand. */
    fun nextDue(nowMs: Long, step: Int, volatility: Double): Long {
        val h = STEPS_H[min(step, STEPS_H.size - 1)] * (1.0 - 0.5 * volatility.coerceIn(0.0, 1.0))
        return nowMs + (h * 3600_000).toLong()
    }

    /** Nach einer Aktualisierung: bei geänderten Claims zurück auf Schritt 0, sonst eine Stufe weiter (ruhend → einfrieren nach letzter Stufe). */
    fun nextStep(step: Int, changedClaims: Int): Int = if (changedClaims > 0) 0 else min(step + 1, STEPS_H.size)

    fun frozen(step: Int) = step >= STEPS_H.size

    /** Dringlichkeit -> Update-Takt (SPEC 3.4): Eilmeldung stündlich, normal täglich, langsam wöchentlich. */
    fun cadenceH(urgency: String): Double = when (urgency) { "EILMELDUNG" -> 1.0; "LANGSAM" -> 168.0; else -> 24.0 }

}
