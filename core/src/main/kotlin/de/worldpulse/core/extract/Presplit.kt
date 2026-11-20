package de.worldpulse.core.extract

/**
 * Aufgabenleiter Schritt 1 (SPEC 5): Code schneidet Sätze/Zahlen/Zitate vor, bevor das LLM klassifiziert/verdichtet.
 * Sprachen DE/EN/RU (übersetzte Texte). Offsets beziehen sich auf den Originaltext (für Belegspannen).
 */
data class Segment(
    val text: String,
    val start: Int,
    val end: Int,
    val numbers: List<String>,
    val quotes: List<String>,
    /** Erkannte Zuschreibung ("Polizei", "the ministry", …) – nur Hinweis fürs LLM, keine Wahrheit. */
    val attribution: String?,
) {
    val claimLikely get() = numbers.isNotEmpty() || quotes.isNotEmpty() || attribution != null || text.length > 60
}

object Presplit {
    private val abbreviations = setOf(
        "z.b", "z. b", "u.a", "u. a", "d.h", "d. h", "bzw", "ca", "dr", "prof", "nr", "st", "str", "abs", "vgl", "etc", "inkl", "evtl",
        "mio", "mrd", "jan", "feb", "mär", "apr", "jun", "jul", "aug", "sep", "sept", "okt", "nov", "dez", "mr", "mrs", "ms", "no", "vs",
        "gen", "lt", "col", "sgt", "u.s", "u.k", "e.g", "i.e", "jr", "sr", "inc", "corp", "st", "ул", "г", "т.е", "т.д", "им",
    )

    private val numberRe = Regex("(?<![\\p{L}])\\d+(?:[.,]\\d+)*(?:\\s?(?:%|Prozent|percent|Millionen|Milliarden|million|billion|km|Meter|meters|Grad|°C|Euro|€|Dollar|\\$|Rubel|rubles|ha|Tote|dead|Verletzte|injured))?")
    private val quoteRe = Regex("[„\"«‚‹“]([^„\"«»“”‚‘‹›]{3,300})[“\"»‘›”]")
    private val attributionRe = listOf(
        Regex("(?:teilte|teilten|sagte|sagten|erklärte|erklärten|berichtete|berichteten|meldete|meldeten|betonte|warnte|kündigte)\\s+(?:am\\s+\\w+\\s+)?(?:der|die|das|ein|eine)?\\s*([A-ZÄÖÜ][\\p{L}-]+(?:\\s+[A-ZÄÖÜ][\\p{L}-]+){0,3})"),
        Regex("(?:[Nn]ach (?:eigenen )?Angaben|[Ll]aut|zufolge)\\s+(?:der|des|dem|die)?\\s*([A-ZÄÖÜ][\\p{L}-]+(?:\\s+[A-ZÄÖÜ][\\p{L}-]+){0,3})"),
        Regex("(?:according to|said|told|reported|announced|claimed)\\s+(?:the\\s+)?([A-Z][\\p{L}.-]+(?:\\s+[A-Z][\\p{L}.-]+){0,3})"),
        Regex("([A-Z][\\p{L}.-]+(?:\\s+[A-Z][\\p{L}.-]+){0,3})\\s+(?:said|says|told|reported|announced|claimed)"),
        Regex("(?:заявил|заявила|сообщил|сообщила|сообщили|по данным)\\s+([А-ЯЁ][\\p{L}-]+(?:\\s+[А-ЯЁ][\\p{L}-]+){0,3})"),
    )

    fun sentences(text: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var start = 0
        var i = 0
        var inQuote = false
        while (i < text.length) {
            val c = text[i]
            if (!inQuote && (c == '„' || c == '«' || c == '“')) { inQuote = true; i++; continue }
            if (inQuote && (c == '“' || c == '»' || c == '"' || c == '”')) {
                inQuote = false
                // Satzende innerhalb des Zitats: „… vorbereitet.“ Die …
                if (i > 0 && text[i - 1] in ".!?" && boundary(text, i)) {
                    add(out, text, start, i + 1); start = i + 1; i++; continue
                }
                i++; continue
            }
            val isEnd = (c == '.' || c == '!' || c == '?' || c == '\n') && !inQuote
            if (isEnd && boundary(text, i)) {
                var j = i + 1
                while (j < text.length && (text[j] == '"' || text[j] == '“' || text[j] == '»' || text[j] == ')')) j++
                add(out, text, start, j)
                start = j; i = j; continue
            }
            i++
        }
        add(out, text, start, text.length)
        return out
    }

    private fun boundary(t: String, i: Int): Boolean {
        val c = t[i]
        if (c == '\n') return true
        if (c == '.') {
            // Dezimal/Datum: 5.9 / 19.11.2026
            if (i > 0 && i + 1 < t.length && t[i - 1].isDigit() && t[i + 1].isDigit()) return false
            // Ordinalzahl/Datum "19. November", "am 3. Tag"
            if (i > 0 && t[i - 1].isDigit() && i + 2 < t.length && t[i + 1] == ' ' && t[i + 2].isLetter() && t[i + 2].isLowerCase().not() &&
                Regex("^\\s(Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)").containsMatchIn(t.substring(i + 1, minOf(t.length, i + 14)))) return false
            // Abkürzung
            val wordStart = t.lastIndexOfAny(charArrayOf(' ', '\n', '(', '„', '"'), i - 1) + 1
            val word = t.substring(wordStart, i).lowercase().trimEnd('.')
            if (word in abbreviations || (word.length == 1 && word[0].isLetter())) return false
        }
        // Satzende nur, wenn danach Leerraum + Großbuchstabe/Ziffer/Anführungszeichen oder Textende kommt
        var j = i + 1
        while (j < t.length && (t[j] == '"' || t[j] == '“' || t[j] == '»' || t[j] == ')')) j++
        if (j >= t.length) return true
        if (!t[j].isWhitespace()) return false
        while (j < t.length && t[j].isWhitespace()) j++
        return j >= t.length || t[j].isUpperCase() || t[j].isDigit() || t[j] in "„\"«‚"
    }

    private fun add(out: MutableList<Segment>, text: String, s: Int, e: Int) {
        var a = s; var b = e
        while (a < b && text[a].isWhitespace()) a++
        while (b > a && text[b - 1].isWhitespace()) b--
        if (b - a < 2) return
        val t = text.substring(a, b)
        out += Segment(
            text = t, start = a, end = b,
            numbers = numberRe.findAll(t).map { it.value.trim() }.filter { it.any(Char::isDigit) }.toList(),
            quotes = quoteRe.findAll(t).map { it.groupValues[1].trim() }.toList(),
            attribution = attributionRe.firstNotNullOfOrNull { r -> r.find(t)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',') },
        )
    }
}
