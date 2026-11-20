package de.worldpulse.bench

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import de.worldpulse.bench.tests.LlmTests
import de.worldpulse.bench.tests.QualityTests
import de.worldpulse.bench.tests.RunMarker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Vordergrunddienst (Typ dataSync) für alle Tests – läuft auch bei ausgeschaltetem Bildschirm
 * (Teil-Wakelock). Android 15: onTimeout(startId, fgsType) nach 6 h/24 h im Hintergrund -> sauber stoppen.
 * Test: adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS de.worldpulse.bench
 */
class BenchService : LifecycleService() {
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var dev: Device

    override fun onCreate() {
        super.onCreate()
        dev = Device(this).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) { stopAll("Nutzer"); return START_NOT_STICKY }
        val spec = BenchState.pending ?: run { stopSelf(); return START_NOT_STICKY }
        BenchState.pending = null
        startForeground(NOTIF_ID, notification("Läuft: ${spec.id.label}"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wpbench:run").apply { acquire(14 * 60 * 60 * 1000L) }
        job?.cancel()
        job = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            BenchState.running.value = spec.id
            val run = spec.id.name.lowercase() + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmmss"))
            ResultLog.info("START ${spec.id.label} run=$run backend=${spec.backend} mtp=${spec.mtp}")
            try {
                runSpec(spec, run)
                ResultLog.info("FERTIG ${spec.id.label}")
            } catch (e: CancellationException) {
                ResultLog.info("ABGEBROCHEN ${spec.id.label}")
            } catch (t: Throwable) {
                ResultLog.info("FEHLER ${spec.id.label}: ${t.javaClass.simpleName}: ${t.message}")
                ResultLog.rec(RunCtx(run, spec.id.name.lowercase()), "error", "${t.javaClass.simpleName}: ${t.message}".take(300), "", dev.snapshot(false))
            } finally {
                BenchState.running.value = null
                BenchState.keepScreen.value = false
                BenchState.progress.value = ""
                stopAll(null)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runSpec(s: TestSpec, run: String) {
        val llm = LlmTests(this, dev, run)
        val q = QualityTests(this, dev, run)
        val first = s.llmModels.firstOrNull()
        fun needModel() = first ?: error("Kein LLM-Modell ausgewählt")
        when (s.id) {
            TestId.DOWNLOAD -> downloadModels(run)
            TestId.AUTO_ALL -> autoAll(s, run, llm, q)
            TestId.NACHTEST -> nachtest(run, llm, q)
            TestId.MODEL_INFO -> llm.modelInfo(s.llmModels + s.embedModels)
            TestId.LOAD_PREFILL_DECODE -> llm.loadPrefillDecode(needModel(), s.backend, s.mtp)
            TestId.BACKEND_MATRIX -> llm.backendMatrix(needModel(), s.mtp)
            TestId.MODEL_MATRIX -> llm.modelMatrix(s.llmModels, s.mtp)
            TestId.RAM_SUSTAIN -> llm.ramSustain(needModel(), s.backend, s.minutes)
            TestId.MTP_COMPARE -> llm.mtpCompare(needModel(), s.backend)
            TestId.SUSTAINED_DUTY -> llm.sustainedThenDuty(needModel(), s.backend, s.minutes)
            TestId.EXTRACTION -> s.llmModels.forEach { q.extraction(it, s.backend) }
            TestId.EMBEDDING -> q.embedding(s.embedModels, listOf(BackendChoice.CPU, BackendChoice.GPU))
            TestId.SCREEN_MAP -> llm.screenRun(needModel(), s.backend, s.minutes, "map")
            TestId.SCREEN_OFF -> llm.screenRun(needModel(), s.backend, s.minutes, "screen_off")
            TestId.FGS_LONG -> fgsLong(s, run, llm)
        }
    }

    private val modelDir get() = File(getExternalFilesDir(null), "models").apply { mkdirs() }

    /** Lädt die Standard-Modelle (E2B, E4B, QAT falls vorhanden, EmbeddingGemma) von Hugging Face. */
    private suspend fun downloadModels(run: String) {
        BenchState.progress.value = "Suche Modelle auf Hugging Face …"
        val all = ModelStore.discover()
        ResultLog.info("Gefunden: " + all.joinToString { "${it.repo}/${it.file} (${it.sizeBytes / 1_048_576} MB)" })
        val pick = ModelStore.defaults(all)
        if (pick.isEmpty()) error("Keine passenden .litertlm-Dateien gefunden")
        for (rf in pick) {
            val c = RunCtx(run, "download", rf.localName, variant = rf.repo)
            val t0 = SystemClock.elapsedRealtime()
            try {
                ModelStore.download(rf, modelDir) { done, total ->
                    BenchState.progress.value = "Download ${rf.localName}: ${done / 1_048_576} / ${if (total > 0) total / 1_048_576 else "?"} MB"
                }
                ResultLog.rec(c, "download_s", (SystemClock.elapsedRealtime() - t0) / 1000, "s", dev.snapshot(false), "role=${rf.role}")
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                ResultLog.rec(c, "download_error", (t.message ?: t.javaClass.simpleName).take(200), "", dev.snapshot(false))
            }
        }
    }

    private fun localModels(): List<File> =
        modelDir.listFiles { f -> f.isFile && f.name.endsWith(".litertlm") }.orEmpty().sortedBy { it.length() }

    /**
     * Auto-Modus: alles nacheinander, ein Fehler stoppt die Kette nicht.
     * Reihenfolge: Download → Info → 1 → 2 → 3 → 4 → 6 → 7 → 5 → 8a (Karte) → 8b (Bildschirm aus) → 3b (E4B, kann abstürzen) → 9.
     */
    private suspend fun autoAll(s: TestSpec, run: String, t: LlmTests, q: QualityTests) {
        suspend fun step(id: TestId, block: suspend () -> Unit) {
            BenchState.subTest.value = id
            ResultLog.info("── Auto: ${id.label}")
            try { block() } catch (e: Throwable) {
                if (e is CancellationException) throw e
                ResultLog.rec(RunCtx(run, "auto", variant = id.name), "step_error", "${e.javaClass.simpleName}: ${e.message}".take(300), "", dev.snapshot(false))
            } finally { BenchState.subTest.value = null }
        }
        BenchState.keepScreen.value = true
        step(TestId.DOWNLOAD) { downloadModels(run) }
        val files = localModels()
        val llms = files.filter { ModelStore.roleOf(it.name) in setOf(ModelStore.Role.E2B, ModelStore.Role.E4B, ModelStore.Role.QAT) }
        val embs = files.filter { ModelStore.roleOf(it.name) == ModelStore.Role.EMBEDDING }
        val e2b = ModelStore.mainFile(files, ModelStore.Role.E2B) ?: llms.firstOrNull() ?: error("Kein LLM-Modell vorhanden")
        val e4b = ModelStore.mainFile(files, ModelStore.Role.E4B)
        ResultLog.info("Auto: SoC=${ModelStore.deviceSoc()} E2B=${e2b.name} E4B=${e4b?.name} LLMs=${llms.map { it.name }} Embedding=${embs.map { it.name }}")
        val gpu = BackendChoice.GPU
        step(TestId.MODEL_INFO) { t.modelInfo(files) }
        step(TestId.LOAD_PREFILL_DECODE) { t.loadPrefillDecode(e2b, gpu, MtpChoice.DEFAULT) }
        step(TestId.BACKEND_MATRIX) { t.backendMatrix(e2b, MtpChoice.DEFAULT) }
        step(TestId.MODEL_MATRIX) { t.modelMatrix(llms.filter { it != e2b }, MtpChoice.DEFAULT) }
        step(TestId.MTP_COMPARE) { (listOfNotNull(e2b, e4b)).forEach { t.mtpCompare(it, gpu) } }
        step(TestId.EXTRACTION) { listOfNotNull(e2b, e4b).forEach { q.extraction(it, gpu) } }
        step(TestId.EMBEDDING) { q.embedding(embs, listOf(BackendChoice.CPU, BackendChoice.GPU)) }
        step(TestId.SUSTAINED_DUTY) { t.sustainedThenDuty(e2b, gpu, s.minutes) }
        step(TestId.SCREEN_MAP) { t.cooldown("screen"); t.screenRun(e2b, gpu, s.minutes, "map") }
        BenchState.keepScreen.value = false // ab hier geht der Bildschirm per System-Timeout aus
        step(TestId.SCREEN_OFF) { t.cooldown("screen"); t.screenRun(e2b, gpu, s.minutes, "screen_off") }
        if (e4b != null) step(TestId.RAM_SUSTAIN) { t.ramSustain(e4b, gpu, s.minutes) }
        step(TestId.FGS_LONG) { fgsLong(s.copy(fgsHours = 6.5), run, t) }
        ResultLog.info("AUTO FERTIG – bitte „CSV teilen“ drücken und an Claude schicken.")
    }

    /**
     * Nachtest nach Auswertung 20.11.: (1) Embedding mit getrenntem Cache je Modell (erster Lauf lieferte identische
     * Vektoren für alle Modelle), (2) Extraktion mit der schnelleren GPU-Datei, (3) 60 min Dauerlast mit vorsichtiger
     * Hitze-Regel (Pause ab 40 °C/MODERATE, weiter unter 38 °C), Bildschirm aus, RAM jede Minute.
     */
    private suspend fun nachtest(run: String, t: LlmTests, q: QualityTests) {
        suspend fun step(name: String, block: suspend () -> Unit) {
            ResultLog.info("── Nachtest: $name")
            try { block() } catch (e: Throwable) {
                if (e is CancellationException) throw e
                ResultLog.rec(RunCtx(run, "nachtest", variant = name), "step_error", "${e.javaClass.simpleName}: ${e.message}".take(300), "", dev.snapshot(false))
            }
        }
        val files = localModels()
        val embs = files.filter { ModelStore.roleOf(it.name) == ModelStore.Role.EMBEDDING }
        val e2bGpu = files.firstOrNull { ModelStore.roleOf(it.name) == ModelStore.Role.E2B && "-gpu" in it.name.lowercase() }
            ?: ModelStore.mainFile(files, ModelStore.Role.E2B) ?: error("Kein E2B-Modell vorhanden")
        ResultLog.info("Nachtest: E2B=${e2bGpu.name}, Embedding=${embs.map { it.name }}")
        BenchState.keepScreen.value = false
        step("embedding") { q.embedding(embs, listOf(BackendChoice.CPU, BackendChoice.GPU)) }
        step("extraktion_gpu_datei") { q.extraction(e2bGpu, BackendChoice.GPU) }
        step("dauerlast_60min") {
            t.cooldown("sustained_60")
            Llm.load(this, e2bGpu, BackendChoice.GPU).use { llm ->
                t.llmLoop(llm, RunCtx(run, "sustained_60", e2bGpu.name, "GPU", "cautious_40_38"), 60,
                    de.worldpulse.core.bench.DutyPolicy.CAUTIOUS, ramEveryMs = 60_000)
            }
        }
        ResultLog.info("NACHTEST FERTIG – bitte „CSV teilen“ und an Claude schicken.")
    }

    /** Test 9: Herzschlag je Minute über viele Stunden; optional leichte LLM-Last alle 5 min. */
    private suspend fun fgsLong(s: TestSpec, run: String, llmTests: LlmTests) {
        val c = RunCtx(run, "fgs_long", s.llmModels.firstOrNull()?.name ?: "", s.backend.name, if (s.fgsWithLlm) "with_llm" else "heartbeat")
        val marker = RunMarker.begin(this, "fgs_long ${c.variant}")
        val pm = getSystemService(PowerManager::class.java)
        val t0 = SystemClock.elapsedRealtime()
        val end = t0 + (s.fgsHours * 3600_000).toLong()
        var min = 0
        val model = if (s.fgsWithLlm) s.llmModels.firstOrNull() else null
        val llm = model?.let { Llm.load(this, it, s.backend) }
        try {
            while (SystemClock.elapsedRealtime() < end) {
                ResultLog.rec(c, "heartbeat_min", min, "min", dev.snapshot(false), "interactive=${pm.isInteractive}")
                if (llm != null && min % 5 == 0) {
                    val g = llm.generate("Summarize in one sentence: the river level rose to 5.9 meters.", maxOutputTokens = 48)
                    llmTests.recordGen(c, g)
                }
                BenchState.progress.value = "Dienst-Langlauf: $min min"
                delay(60_000); min++
            }
            ResultLog.rec(c, "completed_hours", s.fgsHours, "h", dev.snapshot(false))
        } finally {
            llm?.close()
            RunMarker.end(marker)
        }
    }

    /** Android 15+: Zeitlimit für dataSync erreicht. Muss stopSelf() zeitnah aufrufen, sonst Absturz. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ResultLog.rec(RunCtx("timeout", "fgs_long"), "fgs_on_timeout", "startId=$startId type=$fgsType", "", dev.snapshot(false))
        ResultLog.info("onTimeout(fgsType=$fgsType) – stoppe Dienst")
        stopAll("onTimeout")
    }

    private fun stopAll(reason: String?) {
        reason?.let { ResultLog.info("Stop: $it") }
        job?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        dev.stop()
        super.onDestroy()
    }

    private fun notification(text: String): Notification =
        Notification.Builder(this, CHANNEL).setContentTitle("WP Bench").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage).setOngoing(true).build()

    companion object {
        const val CHANNEL = "bench"
        const val NOTIF_ID = 42
        const val ACTION_STOP = "de.worldpulse.bench.STOP"

        fun createChannel(ctx: Context) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Benchmark", NotificationManager.IMPORTANCE_LOW),
            )
        }

        fun start(ctx: Context, spec: TestSpec) {
            BenchState.pending = spec
            ctx.startForegroundService(Intent(ctx, BenchService::class.java))
        }

        fun stop(ctx: Context) = ctx.startService(Intent(ctx, BenchService::class.java).setAction(ACTION_STOP))
    }
}
