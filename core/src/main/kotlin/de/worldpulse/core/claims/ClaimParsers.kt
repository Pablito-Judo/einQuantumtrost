package de.worldpulse.core.claims

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deterministische Parser für LLM-Ausgaben (SPEC 9.3: feste Formate, validieren, sonst verwerfen).
 * Kein Parser wirft – Fehler landen in [ParseResult.errors].
 */
object ClaimParsers {

    fun parse(format: OutputFormat, raw: String): ParseResult = when (format) {
        OutputFormat.JSON_SCHEMA -> parseJson(raw)
        OutputFormat.LINES -> parseLines(raw)
        OutputFormat.THINK_THEN_LINES -> parseThinkThenLines(raw)
    }

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parseJson(raw: String): ParseResult {
        val body = stripCodeFence(raw).let { s ->
            val start = s.indexOfFirst { it == '{' || it == '[' }
            val end = s.lastIndexOfAny(charArrayOf('}', ']'))
            if (start < 0 || end <= start) return ParseResult(emptyList(), false, listOf("kein JSON gefunden"))
            s.substring(start, end + 1)
        }
        val root = try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            return ParseResult(emptyList(), false, listOf("JSON ungültig: ${e.message?.take(120)}"))
        }
        val arr: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["claims"] as? JsonArray)
                ?: return ParseResult(emptyList(), false, listOf("Feld 'claims' fehlt"))
            else -> return ParseResult(emptyList(), false, listOf("unerwarteter JSON-Typ"))
        }
        val errors = mutableListOf<String>()
        val claims = arr.mapIndexedNotNull { i, el ->
            val o = el as? JsonObject ?: run { errors += "Element $i kein Objekt"; return@mapIndexedNotNull null }
            val text = o.str("claim") ?: o.str("text") ?: o.str("aussage")
            if (text.isNullOrBlank()) { errors += "Element $i ohne claim"; return@mapIndexedNotNull null }
            ExtractedClaim(
                text = text.trim(),
                speaker = (o.str("speaker") ?: o.str("wer") ?: "").trim(),
                type = ClaimType.parse(o.str("type") ?: o.str("typ")),
                span = (o.str("span") ?: o.str("beleg") ?: "").trim(),
            )
        }
        return ParseResult(claims, ok = true, errors = errors)
    }

    /**
     * Zeilenformat: "Aussage | Wer | Typ | Beleg". 3 Felder werden akzeptiert (Beleg leer).
     * Ok = mindestens eine gültige Zeile ODER Ausgabe leer/nur "keine". Zeilen ohne '|' werden ignoriert
     * (Vorrede), zählen aber als Fehler, wenn gar keine gültige Zeile existiert.
     */
    fun parseLines(raw: String): ParseResult {
        val errors = mutableListOf<String>()
        val claims = mutableListOf<ExtractedClaim>()
        var junk = 0
        stripCodeFence(raw).lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line0 ->
            val line = line0.removePrefix("-").removePrefix("*").trim()
                .replace(Regex("^\\d+[.)]\\s*"), "")
            if (!line.contains('|')) { junk++; return@forEach }
            val f = line.split('|').map { it.trim() }
            if (f.size < 3) { errors += "zu wenige Felder: ${line.take(60)}"; return@forEach }
            if (f[0].equals("claim", true) || f[0].equals("aussage", true)) return@forEach // Kopfzeile
            if (f[0].isBlank()) { errors += "leere Aussage"; return@forEach }
            claims += ExtractedClaim(
                text = f[0],
                speaker = f[1],
                type = ClaimType.parse(f[2]),
                span = if (f.size >= 4) f.subList(3, f.size).joinToString(" | ").trim().trim('"', '„', '“') else "",
            )
        }
        val emptyOk = claims.isEmpty() && junk == 0 && errors.isEmpty()
        val ok = claims.isNotEmpty() || emptyOk
        if (!ok && junk > 0) errors += "$junk Zeilen ohne Trennzeichen"
        return ParseResult(claims, ok, errors)
    }

    fun parseThinkThenLines(raw: String): ParseResult {
        val idx = raw.indexOf(ExtractionPrompts.CLAIMS_MARKER)
        if (idx < 0) {
            // Fallback: Marker fehlt -> trotzdem Zeilen versuchen, aber als Formatfehler werten.
            val r = parseLines(raw)
            return r.copy(ok = false, errors = r.errors + "Marker ${ExtractionPrompts.CLAIMS_MARKER} fehlt")
        }
        return parseLines(raw.substring(idx + ExtractionPrompts.CLAIMS_MARKER.length))
    }

    private fun stripCodeFence(s: String): String =
        s.replace(Regex("```[a-zA-Z]*"), "").trim()

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.let { if (it.isString) it.content else it.toString() }
}

/** Hilfsfunktion für Tests/Debug: Claims zurück in JSON. */
fun List<ExtractedClaim>.toJsonString(): String =
    Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ExtractedClaim.serializer()), this)
