package de.worldpulse.core.scout

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.security.MessageDigest

/**
 * Bereiniger (Agent 3): Textextraktion aus HTML ohne JavaScript, Spracherkennung (Code-Heuristik als
 * Fallback zu ML Kit), Paywall-Stummel verwerfen. Das LLM sieht später nur diesen bereinigten Text (SPEC 9.2).
 */
object Cleaner {
    private const val MIN_PARAGRAPH = 40

    data class Extract(val title: String, val text: String, val language: String?, val paywallStub: Boolean)

    fun fromHtml(html: String, baseUrl: String? = null): Extract {
        val doc = if (baseUrl != null) Jsoup.parse(html, baseUrl) else Jsoup.parse(html)
        val title = (doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() } ?: doc.title()).trim()
        val text = mainText(doc)
        val lang = doc.selectFirst("html")?.attr("lang")?.take(2)?.lowercase()?.takeIf { it.length == 2 } ?: Language.detect(text)
        return Extract(title, text, lang, Paywall.isStub(html, text))
    }

    /** Hauptinhalt: <article> bzw. Element mit den meisten Absatz-Zeichen; Navigation/Werbung raus. */
    fun mainText(doc: Document): String {
        doc.select("script, style, noscript, nav, header, footer, aside, form, iframe, svg, figure figcaption, .ad, .advert, .newsletter, .related, [aria-hidden=true]").remove()
        val candidates = doc.select("article, main, [itemprop=articleBody], .article-body, .story-body, #content").ifEmpty { listOf(doc.body()) }
        val best = candidates.filterNotNull().maxByOrNull { el -> el.select("p").sumOf { it.text().length } } ?: return ""
        val paras = best.select("p, h2, h3, li").map { it.text().trim() }.filter { it.length >= MIN_PARAGRAPH || it.endsWith(":") }
        return paras.distinct().joinToString("\n\n").ifBlank { best.text() }.take(60_000)
    }

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(normalizeForHash(s).toByteArray()).joinToString("") { "%02x".format(it) }

    private fun normalizeForHash(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()
}

/** Paywall-/Login-Stummel erkennen -> verwerfen statt umgehen (SPEC 2/8). */
object Paywall {
    private val markers = listOf(
        "jetzt abonnieren", "abo abschließen", "weiterlesen mit", "plus-artikel", "z+ ", "f+ ", "nur für abonnenten",
        "subscribe to continue", "subscribe to read", "already a subscriber", "this content is for subscribers",
        "paywall", "piano-offer", "class=\"paid", "регистрац", "оформите подписку",
    )

    fun isStub(html: String, text: String): Boolean {
        val h = html.lowercase()
        val hits = markers.count { it in h }
        return (text.length < 600 && hits >= 1) || hits >= 3
    }
}

/** Sehr einfache Spracherkennung über Stoppwörter/Schrift (DE/EN/RU/UK/FR/ES). ML Kit ersetzt das auf dem Gerät. */
object Language {
    private val stop = mapOf(
        "de" to setOf("der", "die", "und", "das", "nicht", "mit", "ist", "für", "auf", "dem", "ein", "eine", "wurde"),
        "en" to setOf("the", "and", "of", "to", "in", "is", "that", "for", "with", "was", "on", "said", "are"),
        "fr" to setOf("le", "la", "les", "et", "des", "est", "une", "pour", "dans", "que", "qui", "sur"),
        "es" to setOf("el", "la", "los", "y", "de", "que", "en", "una", "por", "con", "para", "las"),
        "ru" to setOf("и", "в", "не", "на", "что", "с", "по", "это", "как", "из", "заявил", "года"),
        "uk" to setOf("і", "та", "не", "на", "що", "з", "по", "це", "як", "із", "року", "україни"),
    )

    fun detect(text: String): String? {
        val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }.take(400)
        if (words.size < 5) return null
        val cyr = words.count { w -> w.any { it in 'Ѐ'..'ӿ' } }
        val pool = if (cyr > words.size / 3) listOf("ru", "uk") else listOf("de", "en", "fr", "es")
        val scores = pool.associateWith { l -> words.count { it in stop.getValue(l) } }
        // Ukrainisch-spezifische Buchstaben
        if (cyr > words.size / 3 && text.any { it in "іїєґІЇЄҐ" }) return "uk"
        return scores.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    }
}
