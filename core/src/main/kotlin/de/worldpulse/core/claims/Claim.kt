package de.worldpulse.core.claims

import kotlinx.serialization.Serializable

/** Claim-Typ laut SPEC 5 (Aufgabenleiter Schritt 2). */
@Serializable
enum class ClaimType {
    FAKT, BEHAUPTUNG, ZITAT, MEINUNG, UNKLAR;

    companion object {
        /** Tolerant: deutsch/englisch, Groß-/Kleinschreibung, Umlaute, Kurzformen. */
        fun parse(raw: String?): ClaimType {
            val s = raw?.trim()?.lowercase()?.trim('.', '"', '\'', '*', '[', ']') ?: return UNKLAR
            return when {
                s.startsWith("fakt") || s.startsWith("fact") -> FAKT
                s.startsWith("behaupt") || s.startsWith("claim") || s.startsWith("allegation") -> BEHAUPTUNG
                s.startsWith("zitat") || s.startsWith("quote") -> ZITAT
                s.startsWith("meinung") || s.startsWith("opinion") || s.startsWith("einschätzung") -> MEINUNG
                else -> UNKLAR
            }
        }
    }
}

/** Ein vom LLM extrahierter Claim (noch ungeprüft). */
@Serializable
data class ExtractedClaim(
    val text: String,
    val speaker: String = "",
    val type: ClaimType = ClaimType.UNKLAR,
    /** Wörtliche Belegspanne aus dem Artikel; leer = keine angegeben. */
    val span: String = "",
)

/** Ergebnis eines Parse-Versuchs: Parse-Erfolg ist separat von "0 Claims". */
data class ParseResult(
    val claims: List<ExtractedClaim>,
    val ok: Boolean,
    val errors: List<String> = emptyList(),
)
