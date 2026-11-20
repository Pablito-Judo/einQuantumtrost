package de.worldpulse.app.analysis

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.worldpulse.app.DemoData
import de.worldpulse.app.WorldPulseApp
import de.worldpulse.app.map.SymbolIcons
import de.worldpulse.app.ui.WpColors
import de.worldpulse.data.db.UiEventEntity
import de.worldpulse.data.pins.PinAging
import de.worldpulse.data.pins.PinStage
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlin.math.sin

/**
 * Analyse-Modus-Ansicht (SPEC 4): leichte dunkle Canvas-Weltkarte statt MapLibre (spart GPU fürs LLM).
 * Natural-Earth-Umriss einmal in ein Bitmap gerendert (Cache), Animation nur solange neue Pins pulsieren (~12 fps),
 * Zoom ×1–×4, Ticker mit Phase/Zähler/Temperatur/Akku. "Demo-Lauf" simuliert die Pipeline (S3 ersetzt das).
 */
@Composable
fun AnalysisScreen(events: List<UiEventEntity>) {
    val ctx = LocalContext.current
    val land = remember { loadLand(ctx) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val base: ImageBitmap? = remember(size) { if (size.width > 0) renderLand(land, size.width, size.height).asImageBitmap() else null }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableLongStateOf(0L) }
    var demo by remember { mutableStateOf(false) }
    var counter by remember { mutableIntStateOf(0) }
    var device by remember { mutableStateOf(DeviceLine(0.0, 0, "–")) }

    val now = System.currentTimeMillis()
    val pulsing = events.any { now - it.time < PinAging.NEW_MS }
    LaunchedEffect(pulsing) { while (pulsing) { frame++; delay(83) } }
    LaunchedEffect(Unit) { while (true) { device = readDevice(ctx); delay(10_000) } }
    LaunchedEffect(demo) {
        val db = WorldPulseApp.instance.db
        while (demo) { db.uiEvents().insert(DemoData.randomLive()); counter++; delay(4_000) }
    }

    Column(Modifier.fillMaxSize().background(WpColors.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier.fillMaxSize().onSizeChanged { size = it }.pointerInput(Unit) {
                    detectTransformGestures { _, p, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        pan = if (scale == 1f) Offset.Zero else pan + p
                    }
                },
            ) {
                @Suppress("UNUSED_VARIABLE") val f = frame // Neuzeichnen bei Animation
                withTransform({ translate(pan.x, pan.y); scale(scale, scale, center) }) {
                    base?.let { drawImage(it) }
                    val t = System.currentTimeMillis()
                    events.forEach { e ->
                        val lat = e.lat ?: return@forEach; val lon = e.lon ?: return@forEach
                        val x = ((lon + 180.0) / 360.0 * this.size.width).toFloat()
                        val y = ((90.0 - lat) / 180.0 * this.size.height).toFloat()
                        val look = PinAging.look(t - e.time)
                        val col = Color(SymbolIcons.color(e.symbol))
                        val r = (if (look.stage == PinStage.PUNKT) 2.5f else 4f) / scale
                        if (look.pulse) {
                            val ph = ((sin(frame / 3.0) + 1) / 2).toFloat()
                            drawCircle(col.copy(alpha = 0.4f * (1 - ph)), radius = (6f + 16f * ph) / scale, center = Offset(x, y))
                        }
                        drawCircle(col.copy(alpha = look.alpha), radius = r, center = Offset(x, y))
                        if (look.stage == PinStage.NEU || look.stage == PinStage.AKTIV) {
                            drawCircle(col.copy(alpha = look.alpha * 0.8f), radius = r + 2.5f / scale, center = Offset(x, y), style = Stroke(1f / scale))
                        }
                    }
                }
            }
            Text("Analyse-Modus · Prototyp", color = WpColors.dim, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
        }
        // Ticker (SPEC 4): Phase, Artikelzähler, Restzeit, Temperatur, Akku, Abkühlpause
        val latest = events.maxByOrNull { it.time }
        Column(Modifier.fillMaxWidth().background(WpColors.panel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Phase: ${if (demo) "Extraktion (Demo)" else "bereit"} · Ereignisse: ${events.size} · neu: $counter · " +
                    "Akku ${device.batteryPct} % · ${"%.1f".format(device.tempC)} °C · Thermal ${device.thermal}",
                color = WpColors.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            )
            latest?.let { Text("▸ ${it.text}", color = WpColors.accent, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { demo = !demo }) { Text(if (demo) "Demo-Lauf stoppen" else "Demo-Lauf starten") }
            }
        }
    }
}

private data class DeviceLine(val tempC: Double, val batteryPct: Int, val thermal: String)

private fun readDevice(ctx: Context): DeviceLine {
    val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val t = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
    val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val sc = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val th = when (ctx.getSystemService(PowerManager::class.java).currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> "ok"; PowerManager.THERMAL_STATUS_LIGHT -> "leicht"
        PowerManager.THERMAL_STATUS_MODERATE -> "mittel"; else -> "hoch"
    }
    return DeviceLine(t, if (lvl >= 0) lvl * 100 / sc else -1, th)
}

/** Natural Earth 110m Land (Public Domain) -> Liste von Ringen (lon/lat). */
private fun loadLand(ctx: Context): List<List<Pair<Double, Double>>> {
    val txt = ctx.assets.open("ne_110m_land.geojson").bufferedReader().use { it.readText() }
    val root = Json.parseToJsonElement(txt).jsonObject
    val rings = mutableListOf<List<Pair<Double, Double>>>()
    fun ring(a: JsonArray) = a.map { p -> p.jsonArray[0].jsonPrimitive.double to p.jsonArray[1].jsonPrimitive.double }
    root["features"]!!.jsonArray.forEach { f ->
        val g = f.jsonObject["geometry"]!!.jsonObject
        val c = g["coordinates"]!!.jsonArray
        when (g["type"]!!.jsonPrimitive.content) {
            "Polygon" -> c.forEach { rings += ring(it.jsonArray) }
            "MultiPolygon" -> c.forEach { poly -> poly.jsonArray.forEach { rings += ring(it.jsonArray) } }
        }
    }
    return rings
}

/** Plattkarte (equirektangulär) einmal pro Größe rendern – Bitmap-Cache statt Neuzeichnen je Frame. */
private fun renderLand(rings: List<List<Pair<Double, Double>>>, w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(0xFF0B1016.toInt())
    val grid = Paint().apply { color = 0xFF121C26.toInt(); strokeWidth = 1f }
    for (lon in -180..180 step 30) { val x = ((lon + 180) / 360f) * w; c.drawLine(x, 0f, x, h.toFloat(), grid) }
    for (lat in -90..90 step 30) { val y = ((90 - lat) / 180f) * h; c.drawLine(0f, y, w.toFloat(), y, grid) }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16212C.toInt() }
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3A4A.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
    rings.forEach { r ->
        val p = Path()
        r.forEachIndexed { i, (lon, lat) ->
            val x = ((lon + 180.0) / 360.0 * w).toFloat(); val y = ((90.0 - lat) / 180.0 * h).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close(); c.drawPath(p, fill); c.drawPath(p, edge)
    }
    return bmp
}
