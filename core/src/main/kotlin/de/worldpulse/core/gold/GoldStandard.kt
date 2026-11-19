package de.worldpulse.core.gold

import de.worldpulse.core.claims.ClaimType
import de.worldpulse.core.claims.ExtractedClaim
import de.worldpulse.core.eval.ClaimEvaluator
import de.worldpulse.core.eval.ClaimScore
import de.worldpulse.core.eval.ExpectedClaim
import de.worldpulse.core.eval.TextMatch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Goldstandard-Format (SPEC 5 "Evaluation"). JSON-Schema: resources/gold/goldstandard.schema.json.
 * Ein Ereignis = Quellen + handgeprüfte Soll-Claims + Entitäten + optional Referenz-Dossier.
 */
@Serializable
data class GoldEvent(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val id: String,
    val title: String,
    val category: String,
    @SerialName("event_time") val eventTime: String? = null,
    val location: GoldLocation? = null,
    val sources: List<GoldSource>,
    val claims: List<GoldClaim>,
    val entities: List<GoldEntity> = emptyList(),
    /** Optional: Musterlösung; Sätze verweisen auf Claim-IDs. */
    @SerialName("reference_dossier") val referenceDossier: List<GoldSentence> = emptyList(),
    val notes: String? = null,
    @SerialName("checked_by") val checkedBy: String? = null,
    @SerialName("checked_at") val checkedAt: String? = null,
)

@Serializable
enum class GeoPrecision { LAND, REGION, STADT, STRASSE, PUNKT }

@Serializable
data class GoldLocation(val name: String, val lat: Double, val lon: Double, val precision: GeoPrecision)

@Serializable
enum class SourceTier { A, B, C }

@Serializable
data class GoldSource(
    val id: String,
    val publisher: String,
    val tier: SourceTier,
    val url: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val language: String,
    @SerialName("translated_from") val translatedFrom: String? = null,
    /** Echo-Gruppe: Agenturkopien teilen sich eine Gruppe und zählen als EINE Quelle. */
    @SerialName("echo_group") val echoGroup: String? = null,
    val text: String,
)

@Serializable
data class GoldClaim(
    val id: String,
    @SerialName("source_id") val sourceId: String,
    val text: String,
    val speaker: String,
    val type: ClaimType,
    val span: String,
    val entities: List<String> = emptyList(),
    @SerialName("event_time") val eventTime: String? = null,
)

@Serializable
data class GoldEntity(
    val id: String,
    val name: String,
    val type: String,
    val aliases: List<String> = emptyList(),
    @SerialName("wikidata_id") val wikidataId: String? = null,
)

@Serializable
data class GoldSentence(val text: String, @SerialName("claim_ids") val claimIds: List<String>)

object GoldLoader {
    val json = Json { ignoreUnknownKeys = false; prettyPrint = true; encodeDefaults = true }

    fun parse(text: String): GoldEvent = json.decodeFromString(GoldEvent.serializer(), text)

    /** Lädt alle mitgelieferten Beispiel-Ereignisse aus den Ressourcen (Index-Datei listet die Dateien). */
    fun loadBundled(): List<GoldEvent> {
        val idx = resource("gold/events/index.txt")
        return idx.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { parse(resource("gold/events/$it")) }
    }

    /** Struktur-Prüfung, die das JSON-Schema nicht ausdrücken kann (Referenzen, Spannen). */
    fun validate(e: GoldEvent): List<String> {
        val errs = mutableListOf<String>()
        val srcIds = e.sources.map { it.id }.toSet()
        val claimIds = e.claims.map { it.id }.toSet()
        val entIds = e.entities.map { it.id }.toSet()
        if (claimIds.size != e.claims.size) errs += "doppelte Claim-IDs"
        e.claims.forEach { c ->
            val src = e.sources.firstOrNull { it.id == c.sourceId }
            if (src == null) errs += "${c.id}: unbekannte Quelle ${c.sourceId}"
            else if (!TextMatch.spanSupported(c.span, src.text)) errs += "${c.id}: Belegspanne nicht im Quelltext"
            c.entities.filter { it !in entIds }.forEach { errs += "${c.id}: unbekannte Entität $it" }
        }
        e.referenceDossier.forEachIndexed { i, s ->
            if (s.claimIds.isEmpty()) errs += "Dossier-Satz $i ohne Claim-ID"
            s.claimIds.filter { it !in claimIds }.forEach { errs += "Dossier-Satz $i: unbekannte Claim-ID $it" }
        }
        if (srcIds.size != e.sources.size) errs += "doppelte Quellen-IDs"
        return errs
    }

    internal fun resource(path: String): String =
        (GoldLoader::class.java.classLoader.getResourceAsStream(path)
            ?: error("Ressource fehlt: $path")).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

/** Ergebnis "Zahlen-/Namens-Treffer" eines Dossier-Texts gegen ein Gold-Ereignis. */
data class FactHitScore(
    val numbersInText: Set<String>,
    val numbersSupported: Set<String>,
    val numbersUnsupported: Set<String>,
    val goldNumbers: Set<String>,
    val entitiesMentioned: Set<String>,
    val goldEntities: Set<String>,
) {
    /** Anteil der Zahlen im Text, die durch Gold-Claims gedeckt sind (1 - Halluzinationsrate für Zahlen). */
    val numberPrecision: Double get() = if (numbersInText.isEmpty()) 1.0 else numbersSupported.size.toDouble() / numbersInText.size
    val numberRecall: Double get() = if (goldNumbers.isEmpty()) 1.0 else numbersSupported.count { it in goldNumbers }.toDouble() / goldNumbers.size
    val entityRecall: Double get() = if (goldEntities.isEmpty()) 1.0 else entitiesMentioned.size.toDouble() / goldEntities.size
}

object GoldEvaluator {
    /** Claim-Recall/Präzision je Quelle, aufsummiert über das Ereignis. */
    fun scoreExtraction(
        event: GoldEvent,
        predictedBySource: Map<String, List<ExtractedClaim>>,
        threshold: Double = ClaimEvaluator.DEFAULT_THRESHOLD,
    ): ClaimScore = event.sources.fold(ClaimScore.EMPTY) { acc, src ->
        val exp = event.claims.filter { it.sourceId == src.id }.map { ExpectedClaim(it.id, it.text, it.speaker, it.span) }
        acc + ClaimEvaluator.score(predictedBySource[src.id].orEmpty(), exp, src.text, threshold)
    }

    /** Zahlen- und Namens-Treffer eines (generierten) Dossier-Texts. */
    fun scoreFacts(event: GoldEvent, dossierText: String): FactHitScore {
        val goldNums = event.claims.flatMap { TextMatch.numbers(it.text) }.toSet()
        // Jahreszahlen/Daten aus Ereigniszeit zählen als gedeckt.
        val allowed = goldNums + (event.eventTime?.let { TextMatch.numbers(it) } ?: emptySet())
        val textNums = TextMatch.numbers(dossierText)
        val norm = TextMatch.normalize(dossierText)
        val mentioned = event.entities.filter { ent ->
            (listOf(ent.name) + ent.aliases).any { a -> norm.contains(TextMatch.normalize(a)) }
        }.map { it.id }.toSet()
        return FactHitScore(
            numbersInText = textNums,
            numbersSupported = textNums.intersect(allowed),
            numbersUnsupported = textNums - allowed,
            goldNumbers = goldNums,
            entitiesMentioned = mentioned,
            goldEntities = event.entities.map { it.id }.toSet(),
        )
    }
}
