package de.worldpulse.bench.tests

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import de.worldpulse.bench.Device
import de.worldpulse.bench.ResultLog
import de.worldpulse.bench.RunCtx
import java.io.File

/**
 * Erkennt, ob Android die App während eines Langlaufs beendet hat (RAM-Test E4B, Test 9):
 * Marker-Datei vor dem Lauf anlegen, danach löschen; beim nächsten App-Start auswerten
 * + ApplicationExitInfo (Grund, PSS/RSS beim Tod) protokollieren.
 */
object RunMarker {
    private fun dir(ctx: Context) = File(ctx.filesDir, "markers").apply { mkdirs() }

    fun begin(ctx: Context, label: String): File =
        File(dir(ctx), "run_${System.currentTimeMillis()}.marker").apply { writeText(label) }

    fun end(f: File) { f.delete() }

    fun checkOnStart(ctx: Context, dev: Device) {
        val run = "startup_${System.currentTimeMillis()}"
        val orphans = dir(ctx).listFiles().orEmpty()
        val prefs = ctx.getSharedPreferences("bench", Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong("last_exit_ts", 0L)
        val am = ctx.getSystemService(ActivityManager::class.java)
        val exits = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 10).filter { it.timestamp > lastSeen }
        orphans.forEach { f ->
            val c = RunCtx(run, "killed_run", variant = f.readText())
            val started = f.name.removePrefix("run_").removeSuffix(".marker").toLongOrNull() ?: 0
            val exit = exits.firstOrNull { it.timestamp >= started }
            ResultLog.rec(c, "killed", true, "", dev.snapshot(false),
                exit?.let { "reason=${reasonName(it.reason)} desc=${it.description} pss_kb=${it.pss} rss_kb=${it.rss}" } ?: "kein ExitInfo")
            f.delete()
        }
        exits.forEach { e ->
            ResultLog.rec(RunCtx(run, "exit_info"), "exit_reason", reasonName(e.reason), "", dev.snapshot(false),
                "ts=${e.timestamp} importance=${e.importance} pss_kb=${e.pss} rss_kb=${e.rss} desc=${e.description}")
        }
        exits.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong("last_exit_ts", it).apply() }
    }

    private fun reasonName(r: Int) = when (r) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "CODE_$r"
    }
}
