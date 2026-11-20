package de.worldpulse.app.analysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.os.StatFs
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import de.worldpulse.app.WorldPulseApp
import de.worldpulse.app.llm.LocalLlm
import de.worldpulse.app.llm.ModelFiles
import de.worldpulse.app.pipeline.EventBuilder
import de.worldpulse.core.bench.DutyAction
import de.worldpulse.core.bench.DutyCycler
import de.worldpulse.core.bench.DutyPolicy
import de.worldpulse.core.bench.Thermal
import de.worldpulse.core.claims.ExtractionPrompts
import de.worldpulse.core.extract.ClaimExtractor
import de.worldpulse.core.geo.Gazetteer
import de.worldpulse.core.run.DeviceState
import de.worldpulse.core.run.Level
import de.worldpulse.core.run.Preflight
import de.worldpulse.data.db.ClaimEntity
import de.worldpulse.data.db.DocumentStatus
import de.worldpulse.data.db.OwnerType
import de.worldpulse.data.db.Phase
import de.worldpulse.data.db.RunLogEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Zustand für Ticker/Analyse-Ansicht (SPEC 4: Phase, Artikelzähler, Restzeit, Temperatur, Akku, Abkühlpause). */
data class AnalysisStatus(
    val running: Boolean = false,
    val phase: String = "bereit",
    val done: Int = 0,
    val total: Int = 0,
    val claims: Int = 0,
    val tempC: Double = Double.NaN,
    val batteryPct: Int = -1,
    val paused: String? = null,
    val lastClaim: String? = null,
    val message: String? = null,
)

/**
 * Analyse-Modus (SPEC 4, Roadmap S3+S4): Vordergrunddienst dataSync, Start per Knopf bei sichtbarer App.
 * Ablauf: Vorab-Check → (einmalig) Modell laden → Phase Extraktion (alle offenen Artikel am Stück, Phasen-Batching)
 * mit Hitze-Regel 40/38 °C → Ereignisse aktualisieren → automatisch beenden (Zeitbudget, nichts mehr zu tun, onTimeout).
 */
class AnalysisService : LifecycleService() {
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) { finish("vom Nutzer beendet"); return START_NOT_STICKY }
        if (job?.isActive == true) return START_NOT_STICKY
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CH, "Analyse", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, notif("Analyse läuft …"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "worldpulse:analyse").apply { acquire(6 * 3600_000L) }
        val budgetMs = intent?.getLongExtra(EXTRA_BUDGET_MS, 5 * 3600_000L) ?: 5 * 3600_000L
        job = lifecycleScope.launch(Dispatchers.Default) {
            try { runAnalysis(System.currentTimeMillis() + budgetMs) } catch (e: CancellationException) { throw e } catch (t: Throwable) {
                status.value = status.value.copy(message = "Fehler: ${t.javaClass.simpleName}: ${t.message}".take(200))
            } finally { finish(null) }
        }
        return START_NOT_STICKY
    }

    private suspend fun runAnalysis(deadline: Long) {
        val app = application as WorldPulseApp
        val db = app.db
        status.value = AnalysisStatus(running = true, phase = "Vorab-Check")
        val pre = Preflight.check(device(), deadline)
        if (!pre.canStart) { status.value = AnalysisStatus(message = "Start nicht möglich: " + pre.reasons.joinToString(", ")); return }

        // Modell (einmalig ~2,5 GB, nur WLAN empfohlen)
        if (!ModelFiles.ready(this, ModelFiles.E2B)) {
            status.value = status.value.copy(phase = "Modell laden (einmalig)")
            ModelFiles.download(this, ModelFiles.E2B) { d, t -> status.value = status.value.copy(phase = "Modell-Download ${d / 1_048_576} / ${t / 1_048_576} MB") }
        }
        val todo = db.documents().byStatus(DocumentStatus.BEREINIGT, 2000)
        status.value = status.value.copy(phase = "Extraktion", total = todo.size)
        if (todo.isEmpty()) { status.value = status.value.copy(message = "Nichts zu tun – erst Quellen freigeben/abrufen."); return }

        val gaz = Gazetteer.bundled()
        val duty = DutyCycler(DutyPolicy.CAUTIOUS, System.currentTimeMillis())
        status.value = status.value.copy(phase = "Modell starten (${if (LocalLlm.isEmulator) "CPU" else "GPU"})")
        LocalLlm.load(this, ModelFiles.file(this, ModelFiles.E2B)).use { llm ->
            val extractor = ClaimExtractor({ sys, user, max -> llm.generate(sys, user, max).text })
            var done = 0; var claimsTotal = 0
            for (doc in todo) {
                if (System.currentTimeMillis() > deadline) break
                // Hitze-Regel (Nutzervorgabe): Pause ab 40 °C / MODERATE, weiter unter 38 °C
                while (true) {
                    val d = device()
                    status.value = status.value.copy(tempC = d.batteryTempC, batteryPct = d.batteryPct)
                    if (Preflight.level(d) == Level.AKKU_NIEDRIG || Preflight.level(d) == Level.SPEICHER_KNAPP) {
                        status.value = status.value.copy(message = "Abbruch: ${Preflight.level(d)}"); return
                    }
                    if (duty.decide(System.currentTimeMillis(), d.thermal, d.batteryTempC) == DutyAction.WORK) { status.value = status.value.copy(paused = null); break }
                    status.value = status.value.copy(paused = "Abkühlpause (${"%.1f".format(d.batteryTempC)} °C)")
                    if (System.currentTimeMillis() > deadline) return
                    delay(15_000)
                }
                val text = doc.text
                if (text.isNullOrBlank()) { db.documents().setStatus(doc.id, DocumentStatus.VERWORFEN); continue }
                val t0 = System.currentTimeMillis(); val dev0 = device()
                val r = try { extractor.extract(text, doc.publishedAt, ZoneId.systemDefault()) } catch (t: Throwable) {
                    log(db, t0, dev0, ok = false, error = t.message, docId = doc.id); db.documents().setStatus(doc.id, DocumentStatus.FEHLER); continue
                }
                val now = System.currentTimeMillis()
                r.claims.forEach { c ->
                    val g = gaz.best(c.claim.text, c.claim.span)
                    db.claims().insert(ClaimEntity(
                        documentId = doc.id, text = c.claim.text, speaker = c.claim.speaker, type = c.claim.type,
                        eventTime = c.eventDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                        reportedAt = doc.publishedAt ?: doc.fetchedAt, placeName = g?.matched, lat = g?.place?.lat, lon = g?.place?.lon,
                        geoPrecision = g?.place?.precision, span = c.claim.span, spanStart = c.spanStart, spanEnd = c.spanEnd,
                        translated = false, model = llm.modelName + "@" + llm.backendName, promptVersion = ExtractionPrompts.VERSION, createdAt = now,
                    ))
                }
                db.documents().setStatus(doc.id, DocumentStatus.EXTRAHIERT)
                log(db, t0, dev0, ok = true, error = "claims=${r.claims.size} verworfen=${r.rejected} parsefehler=${r.parseErrors}", docId = doc.id)
                done++; claimsTotal += r.claims.size
                status.value = status.value.copy(done = done, claims = claimsTotal, lastClaim = r.claims.firstOrNull()?.claim?.text ?: status.value.lastClaim)
                if (done % 10 == 0) EventBuilder(db).run()
            }
        }
        status.value = status.value.copy(phase = "Ereignisse aktualisieren")
        EventBuilder(db).run()
        status.value = status.value.copy(phase = "fertig", message = "Fertig: ${status.value.done} Artikel, ${status.value.claims} Claims.")
    }

    private suspend fun log(db: de.worldpulse.data.db.WorldPulseDb, t0: Long, d: DeviceState, ok: Boolean, error: String?, docId: Long) {
        db.runLog().insert(RunLogEntity(
            startedAt = t0, endedAt = System.currentTimeMillis(), phase = Phase.EXTRAKTION, task = "claim_extractor",
            model = ModelFiles.E2B.file, backend = null, promptVersion = ExtractionPrompts.VERSION,
            batteryTempC = d.batteryTempC, thermalStatus = d.thermal.code, batteryPct = d.batteryPct, ok = ok, error = error,
            targetType = OwnerType.DOKUMENT, targetId = docId,
        ))
    }

    private fun device(): DeviceState = deviceState(this)

    override fun onTimeout(startId: Int, fgsType: Int) {
        status.value = status.value.copy(message = "Android-Zeitlimit für Hintergrunddienste erreicht – beendet.")
        finish(null)
    }

    private fun finish(msg: String?) {
        msg?.let { status.value = status.value.copy(message = it) }
        job?.cancel()
        wake?.let { if (it.isHeld) it.release() }; wake = null
        status.value = status.value.copy(running = false, paused = null)
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun notif(t: String): Notification = Notification.Builder(this, CH).setContentTitle("WorldPulse").setContentText(t)
        .setSmallIcon(android.R.drawable.ic_menu_compass).setOngoing(true).build()

    companion object {
        private const val CH = "analyse"
        private const val ACTION_STOP = "de.worldpulse.app.STOP_ANALYSIS"
        private const val EXTRA_BUDGET_MS = "budget_ms"
        val status = MutableStateFlow(AnalysisStatus())

        fun start(ctx: Context, budgetMs: Long = 5 * 3600_000L) =
            ctx.startForegroundService(Intent(ctx, AnalysisService::class.java).putExtra(EXTRA_BUDGET_MS, budgetMs))

        fun stop(ctx: Context) = ctx.startService(Intent(ctx, AnalysisService::class.java).setAction(ACTION_STOP))

        fun deviceState(ctx: Context): DeviceState {
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val sc = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val wifi = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val free = StatFs(ctx.filesDir.path).availableBytes / 1_048_576
            return DeviceState(plugged, if (lvl >= 0) lvl * 100 / sc else -1, temp,
                Thermal.of(ctx.getSystemService(PowerManager::class.java).currentThermalStatus), wifi, free, System.currentTimeMillis())
        }
    }
}
