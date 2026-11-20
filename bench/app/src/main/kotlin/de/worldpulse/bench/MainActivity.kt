package de.worldpulse.bench

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.worldpulse.bench.ui.MapCanvas
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val modelDir = File(getExternalFilesDir(null), "models").apply { mkdirs() }
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { BenchScreen(modelDir) } }
    }

    /** Bildschirm an halten; im Auto-Modus mit minimaler Helligkeit (SPEC 4). */
    fun keepScreenOn(on: Boolean, dim: Boolean = false) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = if (on && dim) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
}

@Composable
private fun BenchScreen(modelDir: File) {
    val act = androidx.compose.ui.platform.LocalContext.current as MainActivity
    val running by BenchState.running.collectAsStateWithLifecycle()
    val progress by BenchState.progress.collectAsStateWithLifecycle()
    val ticker by BenchState.ticker.collectAsStateWithLifecycle()
    val log by ResultLog.lines.collectAsStateWithLifecycle()
    val subTest by BenchState.subTest.collectAsStateWithLifecycle()
    val autoKeep by BenchState.keepScreen.collectAsStateWithLifecycle()

    var files by remember { mutableStateOf(scan(modelDir)) }
    val selLlm = remember { mutableStateListOf<File>() }
    val selEmb = remember { mutableStateListOf<File>() }
    var backend by remember { mutableStateOf(BackendChoice.GPU) }
    var mtp by remember { mutableStateOf(MtpChoice.DEFAULT) }
    var fgsWithLlm by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }

    val mapPhase = running == TestId.SCREEN_MAP || subTest == TestId.SCREEN_MAP
    LaunchedEffect(running, subTest, autoKeep, showMap) {
        act.keepScreenOn(mapPhase || showMap || autoKeep, dim = autoKeep && !mapPhase)
    }
    LaunchedEffect(mapPhase) { showMap = mapPhase }
    LaunchedEffect(running) { files = scan(modelDir) }

    if (showMap) {
        BackHandler { showMap = false }
        MapCanvas(ticker, "${running?.label ?: "–"} · $progress")
        return
    }

    fun go(id: TestId) {
        val spec = TestSpec(id, selLlm.toList(), selEmb.toList(), backend, mtp, fgsWithLlm = fgsWithLlm)
        BenchService.start(act, spec)
    }

    Column(Modifier.fillMaxSize().background(Color.Black).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("WP Bench – Nachtest: Ladekabel anstecken, oben „NACHTEST“ drücken, Handy ca. 1,5 h liegen lassen. " +
            "Am Ende „CSV teilen“ und die Datei an Claude schicken. (Modelle sind schon geladen.)", fontSize = 13.sp, color = Color(0xFFFFE082))
        Text("Modelle in: ${modelDir.absolutePath}", fontSize = 11.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { files = scan(modelDir) }) { Text("Neu einlesen") }
            OutlinedButton(onClick = { act.startActivity(ResultLog.shareIntent(act)) }) { Text("CSV teilen") }
            if (running != null) Button(onClick = { BenchService.stop(act) }) { Text("Stopp") }
        }
        Text("LLM (☐) / Embedding (◇) wählen:", fontSize = 12.sp)
        files.forEach { f ->
            Row(Modifier.fillMaxWidth().clickable {
                val isEmb = f.name.contains("embed", true)
                val l = if (isEmb) selEmb else selLlm
                if (f in l) l.remove(f) else l.add(f)
            }) {
                Checkbox(checked = f in selLlm || f in selEmb, onCheckedChange = null)
                Text("${if (f.name.contains("embed", true)) "◇" else "☐"} ${f.name} (${f.length() / 1_048_576} MB)", fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (files.isEmpty()) Text("Keine .litertlm/.tflite-Dateien. Per adb push in den Ordner oben kopieren.", color = Color(0xFFFFB300), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BackendChoice.entries.forEach { b -> FilterChip(selected = backend == b, onClick = { backend = b }, label = { Text(b.name) }) }
            MtpChoice.entries.forEach { m -> FilterChip(selected = mtp == m, onClick = { mtp = m }, label = { Text("MTP ${m.name}") }) }
        }
        Row { Checkbox(checked = fgsWithLlm, onCheckedChange = { fgsWithLlm = it }); Text("Test 9 mit LLM-Last (alle 5 min)", fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
        Text(if (running != null) "▶ ${running!!.label}\n$progress" else "bereit", color = Color(0xFF80CBC4), fontSize = 12.sp)

        val state = rememberLazyListState()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state) {
            items(TestId.entries) { id ->
                Button(onClick = { go(id) }, enabled = running == null, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(id.label, fontSize = 12.sp)
                }
            }
            if (running == TestId.SCREEN_MAP) item { OutlinedButton(onClick = { showMap = true }) { Text("Karte zeigen") } }
            items(log.reversed()) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFB0BEC5)) }
        }
    }
}

private fun scan(dir: File): List<File> =
    dir.listFiles { f -> f.isFile && (f.name.endsWith(".litertlm") || f.name.endsWith(".tflite")) }.orEmpty().sortedBy { it.name }
