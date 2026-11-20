package de.worldpulse.app.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Modelldateien der App (Dateinamen per S0 auf Hugging Face bestätigt). Download fortsetzbar, nur https/huggingface.co.
 * Extraktion: Standard-E2B (gemessen F1 0,90). Die schnellere -gpu-Datei wird erst nach dem Nachtest übernommen.
 */
object ModelFiles {
    /** [approxBytes] nur für die Anzeige (Stand 20.11.2026); maßgeblich ist die Content-Length des Servers. */
    data class Spec(val repo: String, val file: String, val approxBytes: Long)

    val E2B = Spec("litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm", 2_588_147_712L)

    fun dir(ctx: Context) = File(ctx.getExternalFilesDir(null), "models").apply { mkdirs() }
    fun file(ctx: Context, s: Spec) = File(dir(ctx), s.file)
    /** Fertig = umbenannt (erst nach vollständigem Download). */
    fun ready(ctx: Context, s: Spec) = file(ctx, s).exists()

    suspend fun download(ctx: Context, s: Spec, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val dest = file(ctx, s)
        if (ready(ctx, s)) return@withContext dest
        val part = File(dir(ctx), s.file + ".part")
        var attempt = 0
        while (true) {
            try { fetch("https://huggingface.co/${s.repo}/resolve/main/${s.file}", part, onProgress); break } catch (e: Exception) {
                if (++attempt >= 5) throw e
                delay(5_000L * attempt)
            }
        }
        check(part.renameTo(dest))
        dest
    }

    private fun fetch(url: String, part: File, onProgress: (Long, Long) -> Unit) {
        val have = if (part.exists()) part.length() else 0L
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 30_000; readTimeout = 60_000
            setRequestProperty("User-Agent", "worldpulse/0.1")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        val code = c.responseCode
        if (code !in 200..299) error("HTTP $code")
        val total = (if (code == 206) have else 0L) + c.contentLengthLong
        RandomAccessFile(part, "rw").use { out ->
            if (code == 206) out.seek(have) else out.setLength(0)
            var done = if (code == 206) have else 0L
            val buf = ByteArray(1 shl 16); var last = 0L
            c.inputStream.use { inp ->
                while (true) {
                    val n = inp.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (done - last > 16 shl 20) { onProgress(done, total); last = done }
                }
            }
            onProgress(done, total)
            if (c.contentLengthLong > 0 && done != total) error("unvollständig: $done / $total")
        }
    }
}
