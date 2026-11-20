package de.worldpulse.core.scout

import de.worldpulse.core.gold.SourceTier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Eintrag im Quellenregister sources.yaml (SPEC 8 "Lokale Quellen"). */
@Serializable
data class SourceDef(
    val id: String,
    val name: String,
    val url: String,
    val type: SourceType,
    val tier: SourceTier,
    val lang: String? = null,
    val country: String? = null,
    /** Hierarchie Gemeinde → Landkreis → Region → Land → Welt. */
    val scope: Scope = Scope.WELT,
    @SerialName("interval_min") val intervalMin: Int = 60,
    /** Kandidat (false) → erst nach manueller Freigabe abrufen. */
    val approved: Boolean = false,
    @SerialName("state_media") val stateMedia: Boolean = false,
    val sanctioned: Boolean = false,
    /** Fallback-Adapter für HTML: CSS-Selektor für Artikel-Links. */
    val selector: String? = null,
    val note: String? = null,
)

@Serializable
enum class SourceType { RSS, ATOM, ICAL, JSONLD, HTML, GDELT, API }

@Serializable
enum class Scope { GEMEINDE, LANDKREIS, REGION, LAND, WELT }

/** Ein Eintrag aus einem Feed/einer Liste – noch ohne Volltext. */
data class FeedItem(
    val sourceId: String,
    val url: String,
    val title: String,
    val summary: String?,
    val publishedAt: Long?,
    val language: String? = null,
    val guid: String? = null,
)

/** Bereinigtes Dokument, bereit für die Dokument-Tabelle (Room: DocumentEntity). */
data class CleanDocument(
    val sourceId: String,
    val url: String,
    val title: String,
    val text: String,
    val language: String?,
    val publishedAt: Long?,
    val fetchedAt: Long,
    val contentHash: String,
    val simhash: Long,
    val echoGroup: String? = null,
    val paywallStub: Boolean = false,
)
