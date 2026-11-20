package de.worldpulse.app.tiles

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline-Kartendaten (PMTiles) – vom GitHub-Workflow "tiles" erzeugt und im Release "tiles-latest" abgelegt.
 * Die App lädt sie einmalig (fortsetzbar) in den App-Ordner; MapLibre liest sie danach per pmtiles://file://.
 */
class TileStore(private val ctx: Context) {
    data class State(val world: Boolean, val region: Boolean, val progress: String = "", val error: String? = null) {
        val any get() = world || region
    }

    val dir: File = File(ctx.getExternalFilesDir(null), "tiles").apply { mkdirs() }
    val worldFile get() = File(dir, WORLD)
    val regionFile get() = File(dir, REGION)

    private val _state = MutableStateFlow(State(worldFile.exists(), regionFile.exists()))
    val state: StateFlow<State> = _state
    private val mutex = Mutex()

    suspend fun ensureDownloaded() = mutex.withLock {
        withContext(Dispatchers.IO) {
            for (name in listOf(WORLD, REGION)) {
                val dest = File(dir, name)
                if (dest.exists()) continue
                var attempt = 0
                while (true) {
                    try {
                        fetch("$BASE/$name", File(dir, "$name.part")) { done, total ->
                            _state.value = _state.value.copy(progress = "Kartendaten $name: ${done / 1_048_576} / ${if (total > 0) total / 1_048_576 else "?"} MB", error = null)
                        }
                        File(dir, "$name.part").renameTo(dest)
                        break
                    } catch (e: Exception) {
                        if (++attempt >= 5) { _state.value = _state.value.copy(error = "Download $name: ${e.message}"); break }
                        delay(5_000L * attempt)
                    }
                }
                _state.value = State(worldFile.exists(), regionFile.exists(), progress = "", error = _state.value.error)
            }
        }
    }

    private fun fetch(url: String, part: File, onProgress: (Long, Long) -> Unit) {
        require(url.startsWith("https://github.com/"))
        val have = if (part.exists()) part.length() else 0L
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000; readTimeout = 60_000
            setRequestProperty("User-Agent", "worldpulse/0.1")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        val code = c.responseCode
        if (code !in 200..299) error("HTTP $code")
        val append = code == 206
        val total = (if (append) have else 0L) + c.contentLengthLong.coerceAtLeast(0)
        RandomAccessFile(part, "rw").use { out ->
            if (append) out.seek(have) else out.setLength(0)
            var done = if (append) have else 0L
            val buf = ByteArray(1 shl 16)
            var last = 0L
            c.inputStream.use { inp ->
                while (true) {
                    val n = inp.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (done - last > 4 shl 20) { onProgress(done, total); last = done }
                }
            }
            onProgress(done, total)
        }
    }

    companion object {
        const val WORLD = "world.pmtiles"
        const val REGION = "heilbronn.pmtiles"
        const val BASE = "https://github.com/Pablito-Judo/einQuantumtrost/releases/download/tiles-latest"
    }
}
