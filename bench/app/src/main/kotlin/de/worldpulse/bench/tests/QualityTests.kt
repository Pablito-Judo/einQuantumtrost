package de.worldpulse.bench.tests

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.ResponseFormat
import de.worldpulse.bench.BackendChoice
import de.worldpulse.bench.BenchState
import de.worldpulse.bench.Device
import de.worldpulse.bench.Llm
import de.worldpulse.bench.ResultLog
import de.worldpulse.bench.RunCtx
import de.worldpulse.core.claims.ClaimParsers
import de.worldpulse.core.claims.ExtractionPrompts
import de.worldpulse.core.claims.OutputFormat
import de.worldpulse.core.claims.toJsonString
import de.worldpulse.core.embed.PairQuality
import de.worldpulse.core.embed.Vectors
import de.worldpulse.core.eval.ClaimEvaluator
import de.worldpulse.core.eval.ClaimScore
import de.worldpulse.core.testset.TestSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** Test 6 (Extraktion, 3 Ausgabeformate) und Test 7 (Embedding). */
class QualityTests(private val ctx: Context, private val dev: Device, private val run: String) {

    private val rawDir = File(ctx.getExternalFilesDir(null), "results").apply { mkdirs() }

    /**
     * 20 Artikel × 3 Formate, Temperatur 0, Denkmodus aus.
     * Rohausgaben landen zusätzlich als JSONL (results/extraction_<run>.jsonl) zur Handprüfung.
     */
    suspend fun extraction(model: File, backend: BackendChoice) {
        val articles = TestSet.articles()
        val raw = File(rawDir, "extraction_$run.jsonl")
        Llm.load(ctx, model, backend).use { llm ->
            ResultLog.rec(RunCtx(run, "extraction", model.name, backend.name), "load_ms", llm.loadMs, "ms", dev.snapshot())
            for (format in OutputFormat.entries) {
                var constrained = format == OutputFormat.JSON_SCHEMA
                val c = RunCtx(run, "extraction", model.name, backend.name, "${format.id};${ExtractionPrompts.VERSION}")
                var total = ClaimScore.EMPTY
                var parsedOk = 0; var timeMs = 0L; var errors = 0
                articles.forEachIndexed { i, a ->
                    BenchState.progress.value = "Extraktion ${format.id}: ${i + 1}/${articles.size}"
                    val rf = if (constrained) ResponseFormat.json(ExtractionPrompts.jsonSchema) else null
                    val g = try {
                        llm.generate(
                            prompt = ExtractionPrompts.user(format, a.text),
                            system = ExtractionPrompts.system,
                            maxOutputTokens = ExtractionPrompts.maxOutputTokens(format),
                            responseFormat = rf,
                            greedy = true,
                        )
                    } catch (t: Throwable) {
                        if (constrained) {
                            // Eingeschränkte Ausgabe nicht verfügbar -> protokollieren, ohne Zwang weiter.
                            ResultLog.rec(c, "constrained_error", (t.message ?: t.javaClass.simpleName).take(200), "", dev.snapshot(false))
                            constrained = false
                        }
                        errors++
                        return@forEachIndexed
                    }
                    val p = ClaimParsers.parse(format, g.text)
                    val s = ClaimEvaluator.score(p.claims, a.expectedClaims(), a.text)
                    total += s; timeMs += g.wallMs; if (p.ok) parsedOk++
                    val ac = c.copy(variant = c.variant + ";${a.id}" + if (format == OutputFormat.JSON_SCHEMA && !constrained) ";unconstrained" else "")
                    val d = dev.snapshot(withPss = false)
                    ResultLog.rec(ac, "parse_ok", p.ok, "", d, p.errors.joinToString("; ").take(150))
                    ResultLog.rec(ac, "recall", s.recall, "", d)
                    ResultLog.rec(ac, "precision", s.precision, "", d)
                    ResultLog.rec(ac, "span_rate", s.spanRate, "", d)
                    ResultLog.rec(ac, "time_s", g.wallMs / 1000.0, "s", d)
                    g.bench?.let {
                        ResultLog.rec(ac, "prefill_tokens", it.lastPrefillTokenCount, "tok", d)
                        ResultLog.rec(ac, "decode_tokens", it.lastDecodeTokenCount, "tok", d)
                    }
                    raw.appendText(Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), buildJsonObject {
                        put("run", run); put("model", model.name); put("backend", backend.name)
                        put("format", format.id); put("constrained", constrained); put("prompt_version", ExtractionPrompts.VERSION)
                        put("article", a.id); put("raw", g.text); put("parsed", p.claims.toJsonString())
                        put("recall", s.recall); put("precision", s.precision); put("ms", g.wallMs)
                    }) + "\n")
                }
                val n = articles.size
                val d = dev.snapshot()
                val sc = c.copy(variant = c.variant + ";SUMMARY")
                ResultLog.rec(sc, "parse_rate", parsedOk.toDouble() / n, "", d)
                ResultLog.rec(sc, "micro_recall", total.recall, "", d)
                ResultLog.rec(sc, "micro_precision", total.precision, "", d)
                ResultLog.rec(sc, "micro_f1", total.f1, "", d)
                ResultLog.rec(sc, "span_rate", total.spanRate, "", d)
                ResultLog.rec(sc, "mean_time_s", timeMs / 1000.0 / (n - errors).coerceAtLeast(1), "s", d)
                ResultLog.rec(sc, "errors", errors, "", d)
                ResultLog.info("== ${format.id}: Parse ${parsedOk}/$n, R=%.2f P=%.2f F1=%.2f, %.1fs/Artikel".format(
                    total.recall, total.precision, total.f1, timeMs / 1000.0 / (n - errors).coerceAtLeast(1)))
            }
        }
    }

    /**
     * EmbeddingGemma 1 vs. 2: Ladezeit, RAM, Zeit je Satz, Satzpaar-Qualität bei 768/256/128 Dimensionen.
     * Präfix-Varianten: ohne / mit Aufgaben-Präfix (Format laut EmbeddingGemma-Modellkarte – auf dem
     * Gerät gegenprüfen, ob die .litertlm-Datei den Präfix bereits selbst einfügt).
     */
    fun embedding(models: List<File>, backends: List<BackendChoice>) {
        val pairs = TestSet.embeddingPairs()
        val prefixes = listOf("none" to "", "task_sim" to "task: sentence similarity | query: ")
        for (m in models) for (b in backends) {
            val base = RunCtx(run, "embedding", m.name, b.name)
            val rssBefore = dev.rssMb().first
            val engine = EmbeddingEngine(EmbeddingEngineConfig(modelPath = m.absolutePath, backend = b.toBackend(ctx), cacheDir = ctx.cacheDir.path))
            val t0 = SystemClock.elapsedRealtime()
            try { engine.initialize() } catch (t: Throwable) {
                ResultLog.rec(base, "load_error", (t.message ?: t.javaClass.simpleName).take(200), "", dev.snapshot())
                runCatching { engine.close() }; continue
            }
            engine.use {
                val d0 = dev.snapshot()
                ResultLog.rec(base, "load_ms", SystemClock.elapsedRealtime() - t0, "ms", d0)
                ResultLog.rec(base, "rss_delta_mb", dev.rssMb().first - rssBefore, "MB", d0)
                for ((pname, prefix) in prefixes) {
                    val c = base.copy(variant = "prefix=$pname")
                    val sentences = pairs.flatMap { listOf(it.a, it.b) }.distinct()
                    val vecs = HashMap<String, FloatArray>()
                    val times = ArrayList<Long>()
                    sentences.forEach { s ->
                        val t = SystemClock.elapsedRealtime()
                        val r = engine.computeEmbedding(listOf(InputData.Text(prefix + s)), EmbeddingOptions(normalize = true))
                        times += SystemClock.elapsedRealtime() - t
                        vecs[s] = r.embedding
                    }
                    val d = dev.snapshot()
                    val dim = vecs.values.first().size
                    ResultLog.rec(c, "dim", dim, "", d)
                    ResultLog.rec(c, "ms_per_sentence_mean", times.drop(1).average(), "ms", d, "erster Satz (Warmup) ${times.first()} ms")
                    ResultLog.rec(c, "rss_mb", dev.rssMb().first, "MB", d)
                    for (k in listOf(dim, 256, 128).filter { it <= dim }.distinct()) {
                        val cos = pairs.map { p ->
                            p to Vectors.cosine(Vectors.truncate(vecs[p.a]!!, k), Vectors.truncate(vecs[p.b]!!, k))
                        }
                        cos.forEach { (p, v) -> ResultLog.rec(c.copy(variant = c.variant + ";dim=$k;${p.id}"), "cosine", v, "", d, "same=${p.same}") }
                        val q = PairQuality.of(cos.filter { it.first.same }.map { it.second }, cos.filterNot { it.first.same }.map { it.second })
                        val qc = c.copy(variant = c.variant + ";dim=$k;SUMMARY")
                        ResultLog.rec(qc, "mean_same", q.meanSame, "", d)
                        ResultLog.rec(qc, "mean_different", q.meanDifferent, "", d)
                        ResultLog.rec(qc, "margin_min_same_minus_max_diff", q.margin, "", d)
                        ResultLog.rec(qc, "best_threshold_accuracy", q.bestThresholdAccuracy, "", d, "schwelle=%.3f".format(q.bestThreshold))
                    }
                }
            }
        }
    }
}
