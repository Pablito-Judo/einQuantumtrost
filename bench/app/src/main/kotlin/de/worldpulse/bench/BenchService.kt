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
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wpbench:run").apply { acquire(10 * 60 * 60 * 1000L) }
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
