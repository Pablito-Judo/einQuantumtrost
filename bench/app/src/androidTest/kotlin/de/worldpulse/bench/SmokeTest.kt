package de.worldpulse.bench

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.ResponseFormat
import de.worldpulse.core.claims.ClaimParsers
import de.worldpulse.core.claims.ExtractionPrompts
import de.worldpulse.core.claims.OutputFormat
import de.worldpulse.core.eval.ClaimEvaluator
import de.worldpulse.core.testset.TestSet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-End-Smoke-Test (CI-Emulator, CPU): HF-Suche -> Download -> LiteRT-LM laden -> Generieren ->
 * Extraktion (Zeilen + erzwungenes JSON) -> Embedding. Ausgaben unter Log-Tag SMOKE.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(m: String) { Log.i("SMOKE", m); println("SMOKE $m") }

    @Test fun endToEnd() = runBlocking {
        ResultLog.init(ctx)
        val dir = File(ctx.getExternalFilesDir(null), "models").apply { mkdirs() }
        val all = ModelStore.discover()
        all.forEach { log("found ${it.role} ${it.repo}/${it.file} ${it.sizeBytes / 1_048_576} MB generic=${ModelStore.isGeneric(it.file)}") }
        val defaults = ModelStore.defaults(all)
        defaults.forEach { log("default ${it.role} ${it.repo}/${it.file}") }
        val e2b = defaults.first { it.role == ModelStore.Role.E2B }
        val f = ModelStore.download(e2b, dir) { d, t -> if (d == t) log("downloaded $d/$t") }
        log("model ${f.name} ${f.length()} bytes; info=${Llm.describe(f)}")

        Llm.load(ctx, f, BackendChoice.CPU, maxNumTokens = 2048).use { llm ->
            log("load_ms=${llm.loadMs}")
            val g = llm.generate("Say OK.", maxOutputTokens = 16)
            log("gen='${g.text}' wall=${g.wallMs} bench=${g.bench}")
            assertTrue(g.text.isNotBlank())
            val art = TestSet.articles().first { it.id == "de01" }
            for (fmt in listOf(OutputFormat.LINES, OutputFormat.JSON_SCHEMA)) {
                val rf = if (fmt == OutputFormat.JSON_SCHEMA) ResponseFormat.json(ExtractionPrompts.jsonSchema) else null
                val r = runCatching {
                    llm.generate(ExtractionPrompts.user(fmt, art.text), ExtractionPrompts.system, 300, rf)
                }
                r.exceptionOrNull()?.let { log("extract ${fmt.id} ERROR ${it.javaClass.simpleName}: ${it.message}") }
                r.getOrNull()?.let { gr ->
                    val p = ClaimParsers.parse(fmt, gr.text)
                    val s = ClaimEvaluator.score(p.claims, art.expectedClaims(), art.text)
                    log("extract ${fmt.id} ok=${p.ok} n=${p.claims.size} R=${s.recall} P=${s.precision} span=${s.spanRate} ms=${gr.wallMs}\n${gr.text}")
                }
            }
        }

        defaults.firstOrNull { it.role == ModelStore.Role.EMBEDDING }?.let { ef ->
            runCatching {
                val m = ModelStore.download(ef, dir) { _, _ -> }
                EmbeddingEngine(EmbeddingEngineConfig(modelPath = m.absolutePath, cacheDir = ctx.cacheDir.path)).use { e ->
                    e.initialize()
                    val v = e.computeEmbedding(listOf(InputData.Text("Der Neckar steigt."))).embedding
                    log("embedding ${m.name} dim=${v.size}")
                }
            }.onFailure { log("embedding ERROR ${it.javaClass.simpleName}: ${it.message}") }
        } ?: log("embedding: kein Modell gefunden")
    }
}
