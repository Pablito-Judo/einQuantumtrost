package de.worldpulse.bench.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Test 8: einfache dunkle Canvas-Karte mit pulsierenden Punkten, ~12 fps (SPEC 4: 10–15 fps).
 * Bewusst ohne Kartenkacheln: grobes Gradnetz + "Landmassen"-Ellipsen als Last-Ersatz für den Natural-Earth-Umriss.
 */
@Composable
fun MapCanvas(ticker: String, status: String, modifier: Modifier = Modifier) {
    val pins = remember {
        val r = Random(7)
        List(40) { Triple(r.nextFloat(), 0.15f + r.nextFloat() * 0.7f, r.nextFloat() * 2 * PI.toFloat()) }
    }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(83); frame++ } } // ≈ 12 fps

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = frame / 12f
            drawRect(Color(0xFF05080D))
            val grid = Color(0xFF13202C)
            for (i in 0..12) drawLine(grid, androidx.compose.ui.geometry.Offset(size.width * i / 12, 0f), androidx.compose.ui.geometry.Offset(size.width * i / 12, size.height))
            for (i in 0..6) drawLine(grid, androidx.compose.ui.geometry.Offset(0f, size.height * i / 6), androidx.compose.ui.geometry.Offset(size.width, size.height * i / 6))
            val land = Color(0xFF0E1A24)
            listOf(0.22f to 0.35f, 0.5f to 0.3f, 0.7f to 0.4f, 0.28f to 0.7f, 0.55f to 0.65f, 0.85f to 0.75f).forEach { (x, y) ->
                drawOval(land, topLeft = androidx.compose.ui.geometry.Offset(size.width * (x - 0.12f), size.height * (y - 0.1f)),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.24f, size.height * 0.2f))
            }
            pins.forEach { (x, y, ph) ->
                val pulse = (sin(t * 2f + ph) + 1f) / 2f
                val c = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)
                drawCircle(Color(0xFFFFB300).copy(alpha = 0.25f * (1 - pulse)), radius = 6.dp.toPx() + pulse * 18.dp.toPx(), center = c)
                drawCircle(Color(0xFFFFB300).copy(alpha = 0.9f), radius = 3.dp.toPx(), center = c)
                drawCircle(Color(0xFFFFE082), radius = 6.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
            }
        }
        Text(status, color = Color(0xFF9FB3C8), fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
        Text(ticker.takeLast(280), color = Color(0xFFCFD8DC), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}
