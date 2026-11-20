package de.worldpulse.bench.tests

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import de.worldpulse.bench.BackendChoice
import de.worldpulse.bench.BenchState
import de.worldpulse.bench.Device
import de.worldpulse.bench.GenResult
import de.worldpulse.bench.Llm
import de.worldpulse.bench.MtpChoice
import de.worldpulse.bench.ResultLog
import de.worldpulse.bench.RunCtx
import de.worldpulse.core.bench.DutyAction
import de.worldpulse.core.bench.DutyCycler
import de.worldpulse.core.bench.DutyPolicy
import de.worldpulse.core.bench.MinuteAggregator
import de.worldpulse.core.bench.PromptFiller
import de.worldpulse.core.bench.Thermal
import de.worldpulse.core.testset.TestSet
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Tests 1–5, 8: Laden/Prefill/Decode, Backends, Modelle, RAM, MTP, Dauerlauf, Bildschirm. */
class LlmTests(private val ctx: Context, private val dev: Device, private val run: String) {

    private val pool: List<String> by lazy { TestSet.articles().map { it.text } }
    /** Zeichen je Token; wird nach dem ersten echten Prefill kalibriert. */
    private var charsPerToken = 4.0

    private fun rc(test: String, model: File, backend: BackendChoice, variant: String = "") =
        RunCtx(run, test, model.name, backend.name, variant)

    fun recordGen(c: RunCtx, g: GenResult, extraNote: String = "") {
        val d = dev.snapshot(withPss = false)
        ResultLog.rec(c, "wall_ms", g.wallMs, "ms", d, extraNote)
        ResultLog.rec(c, "first_chunk_ms", g.firstChunkMs, "ms", d)
        g.bench?.let { b ->
            ResultLog.rec(c, "ttft_s", b.timeToFirstTokenInSecond, "s", d)
            ResultLog.rec(c, "prefill_tokens", b.lastPrefillTokenCount, "tok", d)
            ResultLog.rec(c, "prefill_tps", b.lastPrefillTokensPerSecond, "tok/s", d)
            ResultLog.rec(c, "decode_tokens", b.lastDecodeTokenCount, "tok", d)
            ResultLog.rec(c, "decode_tps", b.lastDecodeTokensPerSecond, "tok/s", d)
        } ?: ResultLog.rec(c, "benchmark_info", "unavailable", "", d)
    }

    /** Test 0: Metadaten je Datei (MTP-Fähigkeit, Backends, NPU-Marke/SoC). */
    fun modelInfo(files: List<File>) {
        files.forEach { f ->
            val c = RunCtx(run, "model_info", f.name)
            val d = dev.snapshot(withPss = false)
            ResultLog.rec(c, "file_mb", f.length() / (1024 * 1024), "MB", d)
            Llm.describe(f).forEach { (k, v) -> ResultLog.rec(c, k, v, "", d) }
        }
    }

    /** Test 1: Laden getrennt von Prefill und Decode; Prefill bei 500/1000/2000 Eingabe-Token. */
    suspend fun loadPrefillDecode(model: File, backend: BackendChoice, mtp: MtpChoice, test: String = "load_prefill_decode") {
        val c0 = rc(test, model, backend, "mtp=${mtp.name}")
        val before = dev.rssMb().first
        val llm = try {
            Llm.load(ctx, model, backend, mtp)
        } catch (t: Throwable) {
            ResultLog.rec(c0, "load_error", (t.message ?: t.javaClass.simpleName).take(200), "", dev.snapshot())
            return
        }
        llm.use {
            val d = dev.snapshot()
            ResultLog.rec(c0, "load_ms", llm.loadMs, "ms", d)
            ResultLog.rec(c0, "rss_after_load_mb", dev.rssMb().first, "MB", d, "vorher $before MB")
            // Kaltstart-Aufruf (erste Ausführung, ggf. Kernel-Kompilierung GPU)
            val cold = llm.generate("Say OK.", maxOutputTokens = 8)
            recordGen(c0.copy(variant = c0.variant + ";cold_short"), cold)
            for (n in listOf(500, 1000, 2000)) {
                BenchState.progress.value = "${model.name} ${backend.name}: Prefill $n"
                val text = PromptFiller.build(pool, n, charsPerToken)
                val g = llm.generate("$text\n\nSummarize the text above in three sentences.", maxOutputTokens = 128)
                g.bench?.lastPrefillTokenCount?.takeIf { it > 0 }?.let { real ->
                    if (n == 500) charsPerToken = text.length.toDouble() / real
                }
                recordGen(c0.copy(variant = c0.variant + ";in=$n"), g, "chars_per_token=%.2f".format(charsPerToken))
            }
            // Reiner Decode-Test: kurzer Prompt, 256 Ausgabe-Token
            val dec = llm.generate("Write a detailed 300-word news-style report about a river flood in a small town.", maxOutputTokens = 256)
            recordGen(c0.copy(variant = c0.variant + ";decode256"), dec)
            ResultLog.rec(c0, "rss_peak_mb", dev.rssMb().second, "MB", dev.snapshot())
        }
    }

    /** SoC-spezifische Dateien (z. B. _Qualcomm_SM8550) nur auf NPU, sonst GPU + CPU. */
    fun backendsFor(f: File): List<BackendChoice> =
        if (de.worldpulse.bench.ModelStore.isNpuBuild(f.name)) listOf(BackendChoice.NPU) else listOf(BackendChoice.GPU, BackendChoice.CPU)

    /** Test 2: CPU / GPU / NPU nacheinander (NPU: Fehler wird protokolliert, falls nicht unterstützt). */
    suspend fun backendMatrix(model: File, mtp: MtpChoice) {
        for (b in BackendChoice.entries) {
            loadPrefillDecode(model, b, mtp, test = "backend_matrix")
            cooldown("backend_matrix")
        }
    }

    /** Test 3: alle LLM-Dateien (E2B, E4B, QAT …) mit GPU und CPU. */
    suspend fun modelMatrix(models: List<File>, mtp: MtpChoice) {
        for (m in models) for (b in backendsFor(m)) {
            loadPrefillDecode(m, b, mtp, test = "model_matrix")
            cooldown("model_matrix")
        }
    }

    /** Test 4: MTP aus vs. an, je 3 Decode-Läufe. */
    suspend fun mtpCompare(model: File, backend: BackendChoice) {
        for (m in listOf(MtpChoice.OFF, MtpChoice.ON)) {
            val c = rc("mtp_compare", model, backend, "mtp=${m.name}")
            val llm = try { Llm.load(ctx, model, backend, m) } catch (t: Throwable) {
                ResultLog.rec(c, "load_error", (t.message ?: t.javaClass.simpleName).take(200), "", dev.snapshot()); continue
            }
            llm.use {
                ResultLog.rec(c, "load_ms", llm.loadMs, "ms", dev.snapshot())
                repeat(3) { i ->
                    BenchState.progress.value = "MTP ${m.name} Lauf ${i + 1}/3"
                    val g = llm.generate(pool[i] + "\n\nRewrite this as a longer background article (about 250 words).", maxOutputTokens = 256)
                    recordGen(c.copy(variant = c.variant + ";rep=$i"), g)
                }
            }
            cooldown("mtp_compare")
        }
    }

    /**
     * Dauerlast-Schleife (Tests 3b, 5, 8): wiederholte Generierung, Durchsatz je Minute,
     * Temperatur/Thermal je Minute, optional RAM alle 5 s und Duty-Cycling.
     */
    suspend fun llmLoop(
        llm: Llm, c: RunCtx, minutes: Int, duty: DutyPolicy?, ramEveryMs: Long? = null,
    ) = coroutineScope {
        val start = SystemClock.elapsedRealtime()
        val end = start + minutes * 60_000L
        val agg = MinuteAggregator(start)
        val cycler = duty?.let { DutyCycler(it, start) }
        val pm = ctx.getSystemService(PowerManager::class.java)
        val ram = ramEveryMs?.let {
            launch {
                while (isActive) {
                    val d = dev.snapshot()
                    val (rss, hwm) = dev.rssMb()
                    ResultLog.rec(c, "rss_mb", rss, "MB", d, "hwm=$hwm")
                    delay(it)
                }
            }
        }
        val minuteLogger = launch {
            var m = 0
            while (isActive) {
                delay(60_000); m++
                val d = dev.snapshot(withPss = false)
                ResultLog.rec(c, "minute_mark", m, "min", d, "interactive=${pm.isInteractive}")
            }
        }
        var i = 0; var pausedMs = 0L
        while (SystemClock.elapsedRealtime() < end) {
            val now = SystemClock.elapsedRealtime()
            val tempC = if (cycler != null) dev.snapshot(withPss = false).batteryTempC else Double.NaN
            when (cycler?.decide(now, dev.lastThermal, tempC) ?: DutyAction.WORK) {
                DutyAction.WORK -> {
                    val prompt = pool[i % pool.size] + "\n\nWrite a neutral background paragraph (about 150 words) on this news."
                    BenchState.progress.value = "${c.test}: Aufruf ${i + 1}, ${(end - now) / 1000}s übrig"
                    val g = llm.generate(prompt, maxOutputTokens = 200, onChunk = { BenchState.ticker.value = (BenchState.ticker.value + it).takeLast(600) })
                    val tok = g.bench?.lastDecodeTokenCount ?: g.chunks
                    agg.add(SystemClock.elapsedRealtime(), tok, g.wallMs)
                    recordGen(c.copy(variant = c.variant + ";call=$i"), g)
                    i++
                }
                DutyAction.PAUSE_SCHEDULED, DutyAction.PAUSE_THERMAL -> { delay(1_000); pausedMs += 1_000 }
            }
        }
        ram?.cancel(); minuteLogger.cancel()
        val d = dev.snapshot()
        agg.summary().forEach { (m, tokPerMin, tps) ->
            ResultLog.rec(c, "tokens_per_minute", tokPerMin, "tok/min", d, "minute=$m")
            ResultLog.rec(c, "busy_tps_minute", tps, "tok/s", d, "minute=$m")
        }
        ResultLog.rec(c, "total_tokens", agg.totalTokens(), "tok", d)
        ResultLog.rec(c, "paused_s", pausedMs / 1000, "s", d)
        ResultLog.rec(c, "calls", i, "", d)
    }

    /** Test 3b: E4B 10 min, RAM-Verlauf; Marker-Datei erkennt einen Kill durch Android beim nächsten Start. */
    suspend fun ramSustain(model: File, backend: BackendChoice, minutes: Int) {
        val c = rc("ram_sustain", model, backend)
        val marker = RunMarker.begin(ctx, "ram_sustain ${model.name} ${backend.name}")
        try {
            val llm = Llm.load(ctx, model, backend)
            llm.use {
                ResultLog.rec(c, "load_ms", llm.loadMs, "ms", dev.snapshot())
                ResultLog.rec(c, "total_ram_mb", dev.totalRamMb(), "MB", dev.snapshot(false))
                llmLoop(llm, c, minutes, duty = null, ramEveryMs = 5_000)
            }
            ResultLog.rec(c, "killed", false, "", dev.snapshot())
        } catch (t: Throwable) {
            ResultLog.rec(c, "error", (t.message ?: t.javaClass.simpleName).take(200), "", dev.snapshot())
        } finally {
            RunMarker.end(marker)
        }
    }

    /** Test 5: 10 min durchgehend, Abkühlen, dann 10 min Duty-Cycling (60 s / 30 s, Pause ab MODERATE). */
    suspend fun sustainedThenDuty(model: File, backend: BackendChoice, minutes: Int) {
        Llm.load(ctx, model, backend).use { llm ->
            val c = rc("sustained_duty", model, backend)
            ResultLog.rec(c, "load_ms", llm.loadMs, "ms", dev.snapshot())
            llmLoop(llm, c.copy(variant = "continuous"), minutes, duty = null)
            cooldown("sustained_duty")
            llmLoop(llm, c.copy(variant = "duty_60_30_pause_moderate"), minutes, duty = DutyPolicy())
        }
    }

    /** Test 8: 10-min-Lauf; Variante "map" (Karte sichtbar) oder "screen_off" (Nutzer schaltet Bildschirm aus). */
    suspend fun screenRun(model: File, backend: BackendChoice, minutes: Int, variant: String) {
        if (variant == "screen_off") {
            for (s in 20 downTo 1) { BenchState.progress.value = "Bildschirm jetzt AUSSCHALTEN – Start in $s s"; delay(1_000) }
        }
        Llm.load(ctx, model, backend).use { llm ->
            llmLoop(llm, rc("screen", model, backend, variant), minutes, duty = null)
        }
    }

    /** Abkühlen bis Thermal NONE (max. 10 min), damit Vergleiche fair starten. */
    suspend fun cooldown(test: String, maxMs: Long = 10 * 60_000L) {
        val t0 = SystemClock.elapsedRealtime()
        while (dev.lastThermal.code > Thermal.NONE.code && SystemClock.elapsedRealtime() - t0 < maxMs) {
            BenchState.progress.value = "Abkühlen … (${dev.lastThermal})"
            delay(5_000)
        }
        ResultLog.rec(RunCtx(run, test, variant = "cooldown"), "cooldown_s", (SystemClock.elapsedRealtime() - t0) / 1000, "s", dev.snapshot(false))
    }
}
