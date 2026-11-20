package de.worldpulse.core.extract

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Zeit-Agent, Code-Teil (SPEC 5 Agent 7): relative Zeitangaben → absolutes Datum, bezogen auf die Meldezeit.
 * Ergebnis immer mit Genauigkeit und Kennzeichen "abgeleitet" (Annahme), das LLM übernimmt nur Unklares.
 */
enum class PartOfDay { NACHT, MORGEN, VORMITTAG, MITTAG, NACHMITTAG, ABEND }

data class TimeMention(
    val span: String,
    val start: Int,
    val end: Int,
    val date: LocalDate,
    val partOfDay: PartOfDay? = null,
    /** true = aus relativer Angabe abgeleitet (Anzeige "vermutlich … (Annahme)"). */
    val derived: Boolean = true,
)

object TimeNormalizer {
    private val weekdaysDe = mapOf("montag" to DayOfWeek.MONDAY, "dienstag" to DayOfWeek.TUESDAY, "mittwoch" to DayOfWeek.WEDNESDAY,
        "donnerstag" to DayOfWeek.THURSDAY, "freitag" to DayOfWeek.FRIDAY, "samstag" to DayOfWeek.SATURDAY, "sonnabend" to DayOfWeek.SATURDAY,
        "sonntag" to DayOfWeek.SUNDAY)
    private val weekdaysEn = DayOfWeek.entries.associateBy { it.name.lowercase() }
    private val weekdaysRu = mapOf("понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY, "сред" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY, "пятниц" to DayOfWeek.FRIDAY, "суббот" to DayOfWeek.SATURDAY, "воскресень" to DayOfWeek.SUNDAY)
    private val monthsDe = listOf("januar", "februar", "märz", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "dezember")
    private val monthsEn = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private val numWordsDe = mapOf("einem" to 1, "einer" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5, "sechs" to 6, "sieben" to 7)
    private val numWordsEn = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7)

    fun find(text: String, reference: LocalDate): List<TimeMention> {
        val out = mutableListOf<TimeMention>()
        fun add(m: MatchResult, d: LocalDate, p: PartOfDay? = null, derived: Boolean = true) {
            if (out.none { it.start < m.range.last + 1 && m.range.first < it.end }) out += TimeMention(m.value, m.range.first, m.range.last + 1, d, p, derived)
        }
        val lower = text.lowercase()

        // "in der Nacht zu Donnerstag" = Nacht vor Donnerstag -> Datum Donnerstag (früh)
        Regex("in der nacht (?:zu|zum) (\\p{L}+)").findAll(lower).forEach { m ->
            weekdaysDe[m.groupValues[1]]?.let { add(m, lastOrSame(reference, it), PartOfDay.NACHT) }
        }
        // "am Dienstagabend", "Dienstag", "am Dienstag"
        Regex("\\b(?:am |seit |vom )?(montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonnabend|sonntag)(morgen|vormittag|mittag|nachmittag|abend|nacht)?\\b").findAll(lower).forEach { m ->
            add(m, lastOrSame(reference, weekdaysDe.getValue(m.groupValues[1])), part(m.groupValues[2]))
        }
        Regex("\\b(?:on |last )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)( morning| afternoon| evening| night)?\\b").findAll(lower).forEach { m ->
            add(m, lastOrSame(reference, weekdaysEn.getValue(m.groupValues[1])), part(m.groupValues[2].trim()))
        }
        Regex("(?:в |во )?(понедельник|вторник|сред\\p{L}*|четверг|пятниц\\p{L}*|суббот\\p{L}*|воскресень\\p{L}*)").findAll(lower).forEach { m ->
            weekdaysRu.entries.firstOrNull { m.groupValues[1].startsWith(it.key) }?.let { add(m, lastOrSame(reference, it.value)) }
        }
        // heute/gestern/vorgestern + Tageszeit
        Regex("(?<!\\p{L})(heute|gestern|vorgestern|today|yesterday|сегодня|вчера|позавчера)( ?(?:morgen|früh|vormittag|mittag|nachmittag|abend|nacht|morning|afternoon|evening|night))?").findAll(lower).forEach { m ->
            val d = when (m.groupValues[1]) { "heute", "today", "сегодня" -> 0L; "gestern", "yesterday", "вчера" -> 1L; else -> 2L }
            add(m, reference.minusDays(d), part(m.groupValues[2].trim()))
        }
        Regex("(?<!\\p{L})(?:last night|letzte nacht|in der nacht(?! zu)|прошлой ночью|ночью)(?!\\p{L})").findAll(lower).forEach { m -> add(m, reference, PartOfDay.NACHT) }
        // "vor zwei Tagen", "two days ago"
        Regex("\\bvor (\\d+|einem|zwei|drei|vier|fünf|sechs|sieben) tag(?:en)?\\b").findAll(lower).forEach { m ->
            (m.groupValues[1].toIntOrNull() ?: numWordsDe[m.groupValues[1]])?.let { add(m, reference.minusDays(it.toLong())) }
        }
        Regex("\\b(\\d+|one|two|three|four|five|six|seven) days? ago\\b").findAll(lower).forEach { m ->
            (m.groupValues[1].toIntOrNull() ?: numWordsEn[m.groupValues[1]])?.let { add(m, reference.minusDays(it.toLong())) }
        }
        // Absolute Daten: "19. November (2026)", "19.11.(2026)", "November 19(, 2026)"
        Regex("\\b(\\d{1,2})\\. (januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember)(?: (\\d{4}))?").findAll(lower).forEach { m ->
            date(m.groupValues[3], monthsDe.indexOf(m.groupValues[2]) + 1, m.groupValues[1].toInt(), reference)?.let { add(m, it, derived = m.groupValues[3].isEmpty()) }
        }
        Regex("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}|\\d{2})?(?!\\d)").findAll(lower).forEach { m ->
            val y = m.groupValues[3].let { if (it.length == 2) "20$it" else it }
            date(y, m.groupValues[2].toInt(), m.groupValues[1].toInt(), reference)?.let { add(m, it, derived = y.isEmpty()) }
        }
        Regex("\\b(january|february|march|april|may|june|july|august|september|october|november|december) (\\d{1,2})(?:, (\\d{4}))?").findAll(lower).forEach { m ->
            date(m.groupValues[3], monthsEn.indexOf(m.groupValues[1]) + 1, m.groupValues[2].toInt(), reference)?.let { add(m, it, derived = m.groupValues[3].isEmpty()) }
        }
        return out.sortedBy { it.start }
    }

    /** Wochentag ohne Datum = der letzte solche Tag bis einschließlich Meldetag (Nachrichten berichten Vergangenes). */
    private fun lastOrSame(ref: LocalDate, d: DayOfWeek): LocalDate = ref.with(TemporalAdjusters.previousOrSame(d))

    /** Ohne Jahr: Jahr der Meldung; liegt das Datum dann > 2 Monate in der Zukunft, Vorjahr. */
    private fun date(year: String, month: Int, day: Int, ref: LocalDate): LocalDate? = runCatching {
        if (year.isNotEmpty()) LocalDate.of(year.toInt(), month, day)
        else LocalDate.of(ref.year, month, day).let { if (it.isAfter(ref.plusMonths(2))) it.minusYears(1) else it }
    }.getOrNull()

    private fun part(s: String): PartOfDay? = when (s) {
        "nacht", "night" -> PartOfDay.NACHT
        "morgen", "früh", "morning" -> PartOfDay.MORGEN
        "vormittag" -> PartOfDay.VORMITTAG
        "mittag" -> PartOfDay.MITTAG
        "nachmittag", "afternoon" -> PartOfDay.NACHMITTAG
        "abend", "evening" -> PartOfDay.ABEND
        else -> null
    }
}
