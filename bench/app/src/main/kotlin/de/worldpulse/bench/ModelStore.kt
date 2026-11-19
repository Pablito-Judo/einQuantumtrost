package de.worldpulse.bench

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

/**
 * Lädt .litertlm-Modelle direkt von Hugging Face (litert-community) – kein PC/adb nötig.
 * Dateinamen werden über die HF-API ermittelt (nicht geraten). Fortsetzbar (.part + Range).
 * Sicherheit (SPEC 9): nur https, Start nur auf huggingface.co.
 */
object ModelStore {
    data class RemoteFile(val repo: String, val file: String, val sizeBytes: Long) {
        val url get() = "https://huggingface.co/$repo/resolve/main/${file.split('/').joinToString("/") { enc(it) }}"
        val localName get() = file.substringAfterLast('/')
        val role: Role get() = roleOf(repo + "/" + file)
    }

    enum class Role { E2B, E4B, QAT, EMBEDDING, OTHER }

    private const val API = "https://huggingface.co/api"
    private val json = Json { ignoreUnknownKeys = true }

    fun roleOf(s: String): Role {
        val l = s.lowercase()
        return when {
            "embed" in l -> Role.EMBEDDING
            "qat" in l -> Role.QAT
            "e2b" in l -> Role.E2B
            "e4b" in l -> Role.E4B
            else -> Role.OTHER
        }
    }

    /** Generische Datei (nicht für eine bestimmte NPU/SoC gebaut)? */
    fun isGeneric(name: String): Boolean {
        val l = name.lowercase()
        return listOf("sm8", "sm7", "mt6", "tensor", "qualcomm", "mediatek", "_npu", "-npu", "exynos").none { it in l }
    }

    /** Sucht passende Repos bei litert-community und listet deren .litertlm-Dateien mit Größe. */
    suspend fun discover(): List<RemoteFile> = withContext(Dispatchers.IO) {
        val repos = linkedSetOf<String>()
        for (q in listOf("gemma-4-E2B", "gemma-4-E4B", "embeddinggemma", "gemma-4 qat")) {
            val arr = getJson("$API/models?author=litert-community&search=${enc(q)}&limit=30") as? JsonArray ?: continue
            arr.forEach { el -> el.jsonObject["id"]?.jsonPrimitive?.content?.let { repos += it } }
        }
        repos.flatMap { repo ->
            val o = runCatching { getJson("$API/models/$repo?blobs=true") as? JsonObject }.getOrNull() ?: return@flatMap emptyList()
            o["siblings"]?.jsonArray.orEmpty().mapNotNull { s ->
                val f = s.jsonObject["rfilename"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (!f.endsWith(".litertlm")) return@mapNotNull null
                RemoteFile(repo, f, s.jsonObject["size"]?.jsonPrimitive?.longOrNull ?: -1)
            }
        }.filter { it.role != Role.OTHER }
    }

    /** Standardauswahl für den Auto-Modus: je Rolle die kleinste generische Datei. */
    fun defaults(all: List<RemoteFile>): List<RemoteFile> =
        all.filter { isGeneric(it.file) }.groupBy { it.role }.mapNotNull { (_, fs) ->
            fs.minByOrNull { if (it.sizeBytes > 0) it.sizeBytes else Long.MAX_VALUE }
        }

    /** Lädt [rf] nach [dir]; überspringt, wenn schon vollständig vorhanden. */
    suspend fun download(rf: RemoteFile, dir: File, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val dest = File(dir, rf.localName)
        if (dest.exists() && (rf.sizeBytes <= 0 || dest.length() == rf.sizeBytes)) return@withContext dest
        val part = File(dir, rf.localName + ".part")
        var attempt = 0
        while (true) {
            try {
                fetch(rf.url, part, rf.sizeBytes, onProgress)
                break
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                if (++attempt >= 5) throw e
                ResultLog.info("Download unterbrochen (${e.message}), neuer Versuch $attempt/5")
                kotlinx.coroutines.delay(5_000L * attempt)
            }
        }
        if (rf.sizeBytes > 0 && part.length() != rf.sizeBytes) error("Größe stimmt nicht: ${part.length()} != ${rf.sizeBytes}")
        check(part.renameTo(dest)) { "Umbenennen fehlgeschlagen" }
        dest
    }

    private suspend fun fetch(url: String, part: File, total: Long, onProgress: (Long, Long) -> Unit) {
        require(url.startsWith("https://huggingface.co/"))
        val have = if (part.exists()) part.length() else 0L
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000; readTimeout = 60_000
            setRequestProperty("User-Agent", "wp-bench/0.1")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        val code = c.responseCode
        if (code == 401 || code == 403) error("HTTP $code – Modell verlangt Login/Lizenz auf Hugging Face")
        if (code !in 200..299) error("HTTP $code")
        val append = code == 206
        RandomAccessFile(part, "rw").use { out ->
            if (append) out.seek(have) else out.setLength(0)
            var done = if (append) have else 0L
            val buf = ByteArray(1 shl 16)
            var lastReport = 0L
            c.inputStream.use { inp ->
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (done - lastReport > 8 shl 20) {
                        coroutineContext.ensureActive()
                        onProgress(done, total); lastReport = done
                    }
                }
            }
            onProgress(done, total)
        }
    }

    private fun getJson(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 20_000; readTimeout = 30_000
        setRequestProperty("User-Agent", "wp-bench/0.1")
        if (responseCode !in 200..299) error("HTTP $responseCode bei $url")
        json.parseToJsonElement(inputStream.bufferedReader().use { it.readText() })
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
