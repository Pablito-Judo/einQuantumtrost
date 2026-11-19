package de.worldpulse.bench

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LlmCapability
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.Modality
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingMode
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File

/** Backend-Wahl der Benchmark-UI (LiteRT-LM 0.19.0: Backend.CPU / GPU / NPU). */
enum class BackendChoice { CPU, GPU, NPU;
    fun toBackend(ctx: Context): Backend = when (this) {
        CPU -> Backend.CPU()
        GPU -> Backend.GPU()
        NPU -> Backend.NPU(nativeLibraryDir = ctx.applicationInfo.nativeLibraryDir)
    }
}

/** MTP/spekulatives Dekodieren: null = Modell-Standard. */
enum class MtpChoice(val mode: SpeculativeDecodingMode?) { DEFAULT(null), ON(SpeculativeDecodingMode.ON), OFF(SpeculativeDecodingMode.OFF) }

data class GenResult(
    val text: String,
    val wallMs: Long,
    /** Wanduhr bis zum ersten gestreamten Stück (inkl. Prefill). */
    val firstChunkMs: Long,
    val chunks: Int,
    val bench: BenchmarkInfo?,
)

/**
 * Dünne Hülle um LiteRT-LM Engine/Conversation.
 * Regel (SPEC 4): nie zwei LLMs gleichzeitig im RAM -> Aufrufer schließen per use{}.
 */
class Llm private constructor(
    val engine: Engine,
    val loadMs: Long,
    val modelName: String,
    val backend: BackendChoice,
    /** Nur wenn das Modell Denkmodus kann, wird er explizit AUS gesetzt (sonst Modell-Standard). */
    private val supportsThinking: Boolean,
) : AutoCloseable {

    companion object {
        fun load(ctx: Context, modelFile: File, backend: BackendChoice, mtp: MtpChoice = MtpChoice.DEFAULT, maxNumTokens: Int = 4096): Llm {
            ExperimentalFlags.enableBenchmark = true // muss VOR Engine-Erzeugung gesetzt sein
            val cfg = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend.toBackend(ctx),
                maxNumTokens = maxNumTokens,
                cacheDir = ctx.cacheDir.path,
                speculativeDecodingConfig = mtp.mode?.let { SpeculativeDecodingConfig(mode = it) },
            )
            val engine = Engine(cfg)
            val t0 = SystemClock.elapsedRealtime()
            try {
                engine.initialize()
            } catch (t: Throwable) {
                runCatching { engine.close() }
                throw t
            }
            val loadMs = SystemClock.elapsedRealtime() - t0
            val thinking = describe(modelFile)["thinking_support"] == "true"
            return Llm(engine, loadMs, modelFile.name, backend, thinking)
        }

        /** Modell-Metadaten (Typ, MTP-Fähigkeit, Backends, NPU-Marke) ohne Engine. */
        fun describe(modelFile: File): Map<String, String> = try {
            ModelInfo.from(modelFile.absolutePath).use { mi ->
                buildMap {
                    put("model_type", mi.modelType.name)
                    put("max_context_tokens", mi.maxContextTokens().toString())
                    put("backends_text", mi.supportedBackends(Modality.TEXT).joinToString("/"))
                    put("npu_brand", mi.npuBrand(Modality.TEXT).name)
                    put("soc", mi.socName(Modality.TEXT) ?: "")
                    put("min_runtime", mi.minRuntimeVersion() ?: "")
                    (mi as? LlmCapability)?.let {
                        put("mtp_support", it.hasSpeculativeDecodingSupport().toString())
                        put("thinking_support", it.supportsThinking().toString())
                    }
                }
            }
        } catch (t: Throwable) {
            mapOf("error" to (t.message ?: t.javaClass.simpleName))
        }
    }

    /**
     * Ein Aufruf in frischer Conversation (kein KV-Übertrag zwischen Artikeln).
     * @param greedy Temperatur 0 (Extraktion, SPEC 5).
     */
    suspend fun generate(
        prompt: String,
        system: String? = null,
        maxOutputTokens: Int? = null,
        responseFormat: ResponseFormat? = null,
        greedy: Boolean = true,
        thinking: Boolean = false,
        onChunk: ((String) -> Unit)? = null,
    ): GenResult {
        val cc = ConversationConfig(
            systemInstruction = system?.let { Contents.of(it) },
            samplerConfig = if (greedy) SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0) else null,
            maxOutputToken = maxOutputTokens,
            thinkingConfig = if (supportsThinking || thinking) ThinkingConfig(enableThinking = thinking) else null,
            enableResponseFormat = responseFormat != null,
        )
        engine.createConversation(cc).use { conv ->
            val sb = StringBuilder()
            var first = -1L; var chunks = 0
            val t0 = SystemClock.elapsedRealtime()
            conv.sendMessageAsync(prompt, responseFormat = responseFormat).collect { msg ->
                if (first < 0) first = SystemClock.elapsedRealtime() - t0
                val s = msg.toString()
                sb.append(s); chunks++
                onChunk?.invoke(s)
            }
            val wall = SystemClock.elapsedRealtime() - t0
            val bi = runCatching { conv.getBenchmarkInfo() }.getOrNull()
            return GenResult(sb.toString(), wall, first, chunks, bi)
        }
    }

    override fun close() = engine.close()
}
