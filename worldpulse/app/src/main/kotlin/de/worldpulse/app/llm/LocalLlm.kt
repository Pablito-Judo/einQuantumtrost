package de.worldpulse.app.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

/**
 * LiteRT-LM-Hülle für die App (gleiche API wie WP Bench, auf dem S23 gemessen).
 * Entscheidungen S0: GPU, MTP aus (kein Gewinn), Temperatur 0 für Extraktion, kein erzwungenes JSON.
 * Regel SPEC 4: nie zwei LLMs gleichzeitig – Aufrufer schließt per use{}.
 */
class LocalLlm private constructor(private val engine: Engine, val backendName: String, val modelName: String) : AutoCloseable {

    companion object {
        /** Emulator (CI): GPU-Pfad hängt dort (Software-GPU) – direkt CPU. Auf dem S23 GPU (gemessen). */
        val isEmulator: Boolean get() = android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.PRODUCT.contains("sdk_gphone")

        /** GPU zuerst; wenn das scheitert, CPU. */
        fun load(ctx: Context, model: File, preferGpu: Boolean = !isEmulator): LocalLlm {
            ExperimentalFlags.enableBenchmark = true
            val order = if (preferGpu) listOf("GPU", "CPU") else listOf("CPU")
            var last: Throwable? = null
            for (b in order) {
                val cache = File(ctx.cacheDir, "litertlm/${model.nameWithoutExtension}_$b").apply { mkdirs() }.path
                val engine = Engine(EngineConfig(modelPath = model.absolutePath, backend = if (b == "GPU") Backend.GPU() else Backend.CPU(),
                    maxNumTokens = 4096, cacheDir = cache))
                try { engine.initialize(); return LocalLlm(engine, b, model.name) } catch (t: Throwable) {
                    last = t; runCatching { engine.close() }
                }
            }
            throw IllegalStateException("Modell konnte nicht geladen werden: ${last?.message}", last)
        }
    }

    data class Out(val text: String, val prefillTokens: Int, val decodeTokens: Int)

    /** Ein Aufruf in frischer Conversation (kein KV-Übertrag zwischen Artikeln). */
    fun generate(system: String, user: String, maxTokens: Int): Out {
        val cc = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
            maxOutputToken = maxTokens,
        )
        engine.createConversation(cc).use { conv ->
            val msg = conv.sendMessage(user)
            val b = runCatching { conv.getBenchmarkInfo() }.getOrNull()
            return Out(msg.toString(), b?.lastPrefillTokenCount ?: -1, b?.lastDecodeTokenCount ?: -1)
        }
    }

    override fun close() = engine.close()
}
