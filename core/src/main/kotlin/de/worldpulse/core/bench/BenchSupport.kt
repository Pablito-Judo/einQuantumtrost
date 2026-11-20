package de.worldpulse.core.bench

/**
 * Android-freie Benchmark-Hilfen: CSV-Zeilen, Duty-Cycling-Regel, Minuten-Aggregation, Prompt-Füller.
 * Die Android-App liefert nur Messwerte (Temperatur, Thermal-Status, RAM) hinein.
 */

/** Spiegel von android.os.PowerManager.THERMAL_STATUS_* (Werte identisch). */
enum class Thermal(val code: Int) {
    NONE(0), LIGHT(1), MODERATE(2), SEVERE(3), CRITICAL(4), EMERGENCY(5), SHUTDOWN(6), UNKNOWN(-1);
    companion object { fun of(code: Int) = entries.firstOrNull { it.code == code } ?: UNKNOWN }
}

/** Gerätezustand zum Zeitpunkt einer Messung. */
data class DeviceSnapshot(
    val epochMs: Long,
    val batteryTempC: Double,
    val batteryPct: Int,
    val charging: Boolean,
    val thermal: Thermal,
    /** PowerManager.getThermalHeadroom(0); NaN wenn nicht verfügbar. */
    val thermalHeadroom: Float,
    val pssMb: Int,
    val availMemMb: Int,
    val lowMemory: Boolean,
)

/** Eine CSV-Zeile = eine Messgröße. Langes Format: einfach zu filtern/pivotieren. */
data class Measurement(
    val run: String,
    val test: String,
    val model: String,
    val backend: String,
    val variant: String,
    val metric: String,
    val value: String,
    val unit: String,
    val device: DeviceSnapshot,
    val note: String = "",
)

object Csv {
    val HEADER = listOf(
        "iso_time", "epoch_ms", "run", "test", "model", "backend", "variant", "metric", "value", "unit",
        "battery_temp_c", "battery_pct", "charging", "thermal", "thermal_headroom", "pss_mb", "avail_mem_mb",
        "low_memory", "note",
    ).joinToString(",")

    fun row(m: Measurement, isoTime: String): String = listOf(
        isoTime, m.device.epochMs.toString(), m.run, m.test, m.model, m.backend, m.variant, m.metric, m.value,
        m.unit, "%.1f".format(java.util.Locale.ROOT, m.device.batteryTempC), m.device.batteryPct.toString(),
        m.device.charging.toString(), m.device.thermal.name,
        if (m.device.thermalHeadroom.isNaN()) "" else "%.3f".format(java.util.Locale.ROOT, m.device.thermalHeadroom),
        m.device.pssMb.toString(), m.device.availMemMb.toString(), m.device.lowMemory.toString(), m.note,
    ).joinToString(",") { escape(it) }

    fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

/**
 * Duty-Cycling (SPEC 4 "Hitze/Akku"): feste Arbeits-/Pausenfenster + thermischer Notstopp.
 * Ab [pauseAt] wird pausiert, bis der Status wieder unter [resumeBelow] liegt.
 */
data class DutyPolicy(
    val workMs: Long = 60_000,
    val pauseMs: Long = 30_000,
    val pauseAt: Thermal = Thermal.MODERATE,
    val resumeBelow: Thermal = Thermal.MODERATE,
    val enabled: Boolean = true,
) {
    companion object {
        /**
         * Nach S23-Messung 20.11.2026: Bei Thermal MODERATE (Akku ~42,5 °C) blieb der GPU-Durchsatz über 10 min
         * konstant (25–26 tok/s); feste 60/30-Pausen kosteten 45 % Durchsatz ohne Nutzen. Daher: keine festen Pausen,
         * Pause erst ab SEVERE, weiter unter MODERATE. Langzeitverhalten (> 10 min) wird im Nachtest gemessen.
         */
        val MEASURED = DutyPolicy(workMs = Long.MAX_VALUE / 4, pauseMs = 0, pauseAt = Thermal.SEVERE, resumeBelow = Thermal.MODERATE)
    }
}

enum class DutyAction { WORK, PAUSE_SCHEDULED, PAUSE_THERMAL }

class DutyCycler(private val policy: DutyPolicy, startMs: Long) {
    private var phaseStart = startMs
    private var inPause = false
    private var thermalHold = false

    fun decide(nowMs: Long, thermal: Thermal): DutyAction {
        if (!policy.enabled) return DutyAction.WORK
        if (thermal != Thermal.UNKNOWN && thermal.code >= policy.pauseAt.code) thermalHold = true
        if (thermalHold) {
            if (thermal == Thermal.UNKNOWN || thermal.code >= policy.resumeBelow.code) return DutyAction.PAUSE_THERMAL
            thermalHold = false
            phaseStart = nowMs; inPause = false
        }
        val elapsed = nowMs - phaseStart
        if (!inPause && elapsed >= policy.workMs) { inPause = true; phaseStart = nowMs }
        else if (inPause && elapsed >= policy.pauseMs) { inPause = false; phaseStart = nowMs }
        return if (inPause) DutyAction.PAUSE_SCHEDULED else DutyAction.WORK
    }
}

/** Durchsatz je Minute (Token aus Decode, Arbeitszeit vs. Wanduhr). */
class MinuteAggregator(private val startMs: Long) {
    data class Bucket(var tokens: Long = 0, var busyMs: Long = 0, var calls: Int = 0)
    private val buckets = sortedMapOf<Int, Bucket>()

    fun add(endMs: Long, tokens: Int, busyMs: Long) {
        val b = buckets.getOrPut(((endMs - startMs) / 60_000).toInt()) { Bucket() }
        b.tokens += tokens; b.busyMs += busyMs; b.calls++
    }

    /** Minute -> (Token/Minute Wanduhr, Token/s während Arbeit, Aufrufe). */
    fun summary(): List<Triple<Int, Double, Double>> = buckets.map { (m, b) ->
        Triple(m, b.tokens.toDouble(), if (b.busyMs == 0L) 0.0 else b.tokens * 1000.0 / b.busyMs)
    }

    fun totalTokens(): Long = buckets.values.sumOf { it.tokens }
}

/**
 * Erzeugt Eingabetext mit ungefähr [targetTokens] Token aus einem Textvorrat.
 * Schätzung 1 Token ≈ 4 Zeichen (Englisch) – die echte Zahl liefert LiteRT-LM (lastPrefillTokenCount)
 * und wird mitgeloggt; die App kalibriert [charsPerToken] nach dem ersten Lauf nach.
 */
object PromptFiller {
    fun build(pool: List<String>, targetTokens: Int, charsPerToken: Double = 4.0): String {
        require(pool.isNotEmpty())
        val targetChars = (targetTokens * charsPerToken).toInt()
        val sb = StringBuilder()
        var i = 0
        while (sb.length < targetChars) { sb.append(pool[i % pool.size]).append("\n\n"); i++ }
        return sb.substring(0, targetChars)
    }
}
