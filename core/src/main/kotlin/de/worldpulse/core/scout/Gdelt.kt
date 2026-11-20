package de.worldpulse.core.scout

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * GDELT DOC 2.0 API (kostenlos, ohne Schlüssel): Artikel-Liste zu einer Abfrage.
 * Format der Antwort (ArtList/JSON): {"articles":[{url,title,seendate,domain,language,sourcecountry,…}]}
 * – Feldnamen per CI-Live-Abruf gegengeprüft (Workflow-Schritt "Referenzdaten").
 */
object Gdelt {
    const val BASE = "https://api.gdeltproject.org/api/v2/doc/doc"

    data class Article(val url: String, val title: String, val seenAt: Long?, val domain: String?, val language: String?, val country: String?)

    /** @param timespan z. B. "1h", "24h", "3d" ; maxRecords ≤ 250. */
    fun artListUrl(query: String, timespan: String = "1h", maxRecords: Int = 100): String {
        require(maxRecords in 1..250)
        val q = URLEncoder.encode(query, "UTF-8")
        return "$BASE?query=$q&mode=ArtList&format=json&maxrecords=$maxRecords&timespan=$timespan&sort=DateDesc"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val seenFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun parseArtList(body: String): List<Article> {
        if (body.isBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        return root["articles"]?.jsonArray?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val url = o.s("url") ?: return@mapNotNull null
            Article(
                url = url,
                title = o.s("title") ?: "",
                seenAt = o.s("seendate")?.let { runCatching { LocalDateTime.parse(it, seenFmt).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() },
                domain = o.s("domain"),
                language = o.s("language"),
                country = o.s("sourcecountry"),
            )
        }.orEmpty()
    }

    fun toFeedItems(sourceId: String, arts: List<Article>) = arts.map {
        FeedItem(sourceId, it.url, it.title, null, it.seenAt, language = it.language)
    }

    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
}
