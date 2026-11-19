package de.worldpulse.bench

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import de.worldpulse.core.bench.Csv
import de.worldpulse.core.bench.DeviceSnapshot
import de.worldpulse.core.bench.Measurement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Ergebnisse als CSV in den App-Ordner (Android/data/de.worldpulse.bench/files/results/) + Teilen-Menü.
 * Eine Datei pro Tag; jede Zeile = eine Messgröße mit Gerätezustand.
 */
object ResultLog {
    private const val TAG = "WPBench"
    private val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
    private val day = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault())

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    private lateinit var dir: File

    fun init(ctx: Context) {
        dir = File(ctx.getExternalFilesDir(null), "results").apply { mkdirs() }
    }

    fun csvFile(): File = File(dir, "bench_${day.format(Instant.now())}.csv").also {
        if (!it.exists()) it.writeText(Csv.HEADER + "\n")
    }

    @Synchronized
    fun record(m: Measurement) {
        csvFile().appendText(Csv.row(m, iso.format(Instant.ofEpochMilli(m.device.epochMs))) + "\n")
        info("${m.test}/${m.variant} ${m.metric}=${m.value}${m.unit} (${"%.1f".format(m.device.batteryTempC)}°C, ${m.device.thermal})")
    }

    /** Kurzform für Tests. */
    fun rec(
        ctx: RunCtx, metric: String, value: Any?, unit: String = "", device: DeviceSnapshot, note: String = "",
    ) = record(
        Measurement(ctx.run, ctx.test, ctx.model, ctx.backend, ctx.variant, metric, fmt(value), unit, device, note),
    )

    fun info(msg: String) {
        Log.i(TAG, msg)
        val t = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now())
        _lines.value = (_lines.value + "$t $msg").takeLast(400)
    }

    fun shareIntent(ctx: Context): Intent {
        val files = dir.listFiles { f -> f.name.endsWith(".csv") }?.sortedBy { it.name }.orEmpty()
        val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", it) })
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Benchmark-CSV teilen") }
    }

    private fun fmt(v: Any?): String = when (v) {
        null -> ""
        is Double -> if (v.isNaN()) "" else "%.3f".format(java.util.Locale.ROOT, v)
        is Float -> if (v.isNaN()) "" else "%.3f".format(java.util.Locale.ROOT, v)
        else -> v.toString()
    }
}

/** Kontext einer Messreihe (wird in jede CSV-Zeile geschrieben). */
data class RunCtx(
    val run: String,
    val test: String,
    val model: String = "",
    val backend: String = "",
    val variant: String = "",
)
