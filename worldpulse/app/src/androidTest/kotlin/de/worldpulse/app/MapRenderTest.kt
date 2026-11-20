package de.worldpulse.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * S1-Abnahme im Emulator: echte Kartendaten aus dem Release laden, prüfen ob MapLibre lokale PMTiles per
 * pmtiles://file:// rendert (gezählte Features je Ebene), Screenshots aller Ansichten speichern (Log-Tag S1).
 */
@RunWith(AndroidJUnit4::class)
class MapRenderTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val shots = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
    private fun log(m: String) { Log.i("S1", m); println("S1 $m") }

    @Test fun renderAndScreenshots(): Unit = runBlocking {
        val app = ctx.applicationContext as WorldPulseApp
        app.tiles.ensureDownloaded()
        val st = app.tiles.state.value
        log("tiles world=${st.world} (${app.tiles.worldFile.length() / 1_048_576} MB) region=${st.region} (${app.tiles.regionFile.length() / 1_048_576} MB) err=${st.error}")
        assertTrue("Kartendaten fehlen: ${st.error}", st.world)

        shoot("1_welt", "KARTE", "30,15,1.6", listOf("world-earth", "world-water", "world-country", "ev-icon", "ev-cluster"))
        shoot("2_europa", "KARTE", "49.5,12,4.2", listOf("world-earth", "world-boundaries", "world-locality", "ev-icon"))
        shoot("3_heilbronn", "KARTE", "49.15,9.21,11.5", listOf("region-earth", "region-roads-major", "region-roads-highway", "region-locality", "ev-icon"))
        shoot("4_newsroom", "NEWSROOM", null, emptyList())
        shoot("5_analyse", "ANALYSE", null, emptyList())
    }

    private fun shoot(name: String, tab: String, cam: String?, layers: List<String>) {
        val intent = Intent(ctx, MainActivity::class.java).putExtra("tab", tab).apply { cam?.let { putExtra("cam", it) } }
        ActivityScenario.launch<MainActivity>(intent).use { sc ->
            Thread.sleep(12_000) // Stil, Kacheln, Schriften laden (Emulator-GPU ist langsam)
            if (layers.isNotEmpty()) sc.onActivity { act ->
                val mv = findMapView(act.window.decorView) ?: run { log("$name: keine MapView"); return@onActivity }
                val latch = CountDownLatch(1)
                mv.getMapAsync { m ->
                    val rect = RectF(0f, 0f, mv.width.toFloat(), mv.height.toFloat())
                    layers.forEach { l -> log("$name layer=$l features=${runCatching { m.queryRenderedFeatures(rect, l).size }.getOrElse { -1 }}") }
                    latch.countDown()
                }
                latch.await(5, TimeUnit.SECONDS)
            }
            val bmp: Bitmap? = inst.uiAutomation.takeScreenshot()
            if (bmp != null) {
                File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                log("$name screenshot ${bmp.width}x${bmp.height}")
            } else log("$name: kein Screenshot")
        }
    }

    private fun findMapView(v: View): MapView? {
        if (v is MapView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findMapView(v.getChildAt(i))?.let { return it }
        return null
    }
}
