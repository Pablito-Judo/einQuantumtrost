package de.worldpulse.core.extract

import de.worldpulse.core.claims.ClaimParsers
import de.worldpulse.core.claims.ExtractedClaim
import de.worldpulse.core.claims.ExtractionPrompts
import de.worldpulse.core.claims.OutputFormat
import de.worldpulse.core.eval.TextMatch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Claim-Extraktor (Agent 6) nach S0-Messung: Format "kurz nachdenken → Zeilen" (E2B GPU: F1 0,90, ~10 s/Artikel).
 * Ablauf (Aufgabenleiter 1–4): Text kürzen/zerlegen (Code) → LLM → Parser → Belegspanne im Text prüfen, sonst
 * verwerfen (Code) → Offsets, Ereigniszeit (Code). Das LLM ist als Funktion übergeben → ohne Gerät testbar.
 */
class ClaimExtractor(
    /** (system, user, maxTokens) -> Rohtext. */
    private val llm: suspend (String, String, Int) -> String,
    private val format: OutputFormat = OutputFormat.THINK_THEN_LINES,
    private val maxChunkChars: Int = 2_400,
    private val maxChunks: Int = 2,
) {
    data class Claim(
        val claim: ExtractedClaim,
        val spanStart: Int?,
        val spanEnd: Int?,
        val eventDate: LocalDate?,
        val dateDerived: Boolean,
    )

    data class Result(val claims: List<Claim>, val rejected: Int, val parseErrors: Int, val chunks: Int)

    suspend fun extract(text: String, publishedAt: Long?, zone: ZoneId = ZoneId.of("Europe/Berlin")): Result {
        val chunks = chunk(text)
        val ref = Instant.ofEpochMilli(publishedAt ?: System.currentTimeMillis()).atZone(zone).toLocalDate()
        val out = mutableListOf<Claim>()
        var rejected = 0; var parseErrors = 0
        val seen = HashSet<String>()
        for (ch in chunks) {
            val raw = llm(ExtractionPrompts.system, ExtractionPrompts.user(format, ch), ExtractionPrompts.maxOutputTokens(format))
            val p = ClaimParsers.parse(format, raw)
            if (!p.ok) parseErrors++
            for (c in p.claims) {
                // Schritt 3: Belegspanne muss im Originaltext stehen – sonst verwerfen (keine erfundenen Belege)
                if (!TextMatch.spanSupported(c.span, ch)) { rejected++; continue }
                val key = TextMatch.normalize(c.text)
                if (!seen.add(key)) continue
                val start = text.indexOf(c.span).takeIf { it >= 0 }
                val time = TimeNormalizer.find(c.span + " " + c.text, ref).firstOrNull()
                out += Claim(c, start, start?.let { it + c.span.length }, time?.date, time?.derived ?: true)
            }
        }
        return Result(out, rejected, parseErrors, chunks.size)
    }

    /** Absatzweise bis [maxChunkChars] zusammenfassen; Satzgrenzen aus [Presplit] beibehalten. */
    fun chunk(text: String): List<String> {
        val sentences = Presplit.sentences(text.take(maxChunkChars * maxChunks + 500))
        val chunks = mutableListOf<String>()
        val sb = StringBuilder()
        for (s in sentences) {
            if (sb.length + s.text.length + 1 > maxChunkChars && sb.isNotEmpty()) {
                chunks += sb.toString(); sb.clear()
                if (chunks.size == maxChunks) break
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s.text)
        }
        if (sb.isNotEmpty() && chunks.size < maxChunks) chunks += sb.toString()
        return chunks.filter { it.length >= 40 }
    }
}
