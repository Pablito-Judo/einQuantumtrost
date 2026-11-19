package de.worldpulse.bench

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import de.worldpulse.core.bench.DeviceSnapshot
import de.worldpulse.core.bench.Thermal
import java.io.File

/** Liest Akku-Temperatur, Thermal-Status, RAM. Alles ohne Zusatzrechte. */
class Device(private val ctx: Context) {
    private val pm = ctx.getSystemService(PowerManager::class.java)
    private val am = ctx.getSystemService(ActivityManager::class.java)

    @Volatile var lastThermal: Thermal = Thermal.of(pm.currentThermalStatus)
        private set

    /** Thermal-Listener (SPEC: PowerManager.getCurrentThermalStatus + Listener). */
    private val listener = PowerManager.OnThermalStatusChangedListener { status ->
        val t = Thermal.of(status)
        lastThermal = t
        onThermalChange?.invoke(t)
    }
    var onThermalChange: ((Thermal) -> Unit)? = null

    fun start() = pm.addThermalStatusListener(ctx.mainExecutor, listener)
    fun stop() = pm.removeThermalStatusListener(listener)

    fun snapshot(withPss: Boolean = true): DeviceSnapshot {
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempC = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE)
            .let { if (it == Int.MIN_VALUE) Double.NaN else it / 10.0 }
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val pss = if (withPss) Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024 else -1
        val headroom = try { pm.getThermalHeadroom(0) } catch (_: Throwable) { Float.NaN }
        lastThermal = Thermal.of(pm.currentThermalStatus)
        return DeviceSnapshot(
            epochMs = System.currentTimeMillis(),
            batteryTempC = tempC,
            batteryPct = if (level >= 0) level * 100 / scale else -1,
            charging = plugged,
            thermal = lastThermal,
            thermalHeadroom = headroom,
            pssMb = pss,
            availMemMb = (mi.availMem / (1024 * 1024)).toInt(),
            lowMemory = mi.lowMemory,
        )
    }

    /** VmRSS/VmHWM aus /proc/self/status in MB (inkl. gemappter Modellseiten). */
    fun rssMb(): Pair<Int, Int> {
        var rss = -1; var hwm = -1
        try {
            File("/proc/self/status").forEachLine { l ->
                if (l.startsWith("VmRSS:")) rss = l.filter { it.isDigit() }.toInt() / 1024
                if (l.startsWith("VmHWM:")) hwm = l.filter { it.isDigit() }.toInt() / 1024
            }
        } catch (_: Throwable) { }
        return rss to hwm
    }

    fun totalRamMb(): Int = (ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem / (1024 * 1024)).toInt()
}
