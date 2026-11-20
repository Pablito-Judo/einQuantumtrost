package de.worldpulse.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.worldpulse.core.claims.ClaimType
import de.worldpulse.core.gold.GeoPrecision
import de.worldpulse.core.gold.SourceTier

/*
 * Room-Schema laut SPEC 5 "Datenmodell". Zeiten = epoch millis (UTC). Koordinaten WGS84.
 * Ort immer mit Genauigkeit (Pflichtfeld, SPEC 5 Geo-Agent), sobald lat/lon gesetzt sind.
 */

/** Quelle(ID, Stufe A/B/C, Profil). */
@Entity(tableName = "source")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String?,
    val kind: SourceKind,
    val tier: SourceTier,
    @ColumnInfo(name = "country_code") val countryCode: String? = null,
    @ColumnInfo(name = "state_media") val stateMedia: Boolean = false,
    /** Sanktionierte Medien (SPEC 8): nur Claims + eigene Zusammenfassung + Link, kein Volltext. */
    val sanctioned: Boolean = false,
    /** Quellen-Profiler (Agent 18): bestätigt/widerlegt/korrigiert, Verspätung, Echo-Anteil – JSON. */
    @ColumnInfo(name = "profile_json") val profileJson: String? = null,
    /** Kandidat -> manuell freigegeben (SPEC 8 Quellenregister). */
    val approved: Boolean = false,
    val active: Boolean = true,
    @ColumnInfo(name = "fetch_interval_min") val fetchIntervalMin: Int = 60,
    val etag: String? = null,
    @ColumnInfo(name = "last_modified") val lastModified: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Dokument(Hash, URL, Zeit, Sprache, Echo-Gruppe). */
@Entity(
    tableName = "document",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["source_id"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["content_hash"], unique = true),
        Index("source_id"), Index("echo_group"), Index("published_at"), Index("status"),
    ],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val url: String,
    /** SHA-256 des bereinigten Texts (Dedupe exakt). */
    @ColumnInfo(name = "content_hash") val contentHash: String,
    /** SimHash/MinHash für Fast-Duplikate (Agent 4). */
    val simhash: Long? = null,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "published_at") val publishedAt: Long?,
    val language: String?,
    @ColumnInfo(name = "translated_from") val translatedFrom: String? = null,
    /** Agenturkopien teilen eine Gruppe und zählen als EINE Quelle. */
    @ColumnInfo(name = "echo_group") val echoGroup: String? = null,
    val title: String?,
    /** Bereinigter Text; nach 30 Tagen null (Haushalt: Claims + Hash + Link bleiben). */
    val text: String?,
    val status: DocumentStatus = DocumentStatus.NEU,
)

/** Entität mit Wikidata-Verknüpfung (Agent 8) + Alias-Tabelle (Kyiv/Kiew/Киев). */
@Entity(tableName = "entity", indices = [Index(value = ["wikidata_id"], unique = true)])
data class EntityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "wikidata_id") val wikidataId: String?,
    val name: String,
    val type: EntityType,
)

@Entity(
    tableName = "entity_alias",
    primaryKeys = ["alias", "entity_id"],
    foreignKeys = [ForeignKey(entity = EntityEntity::class, parentColumns = ["id"], childColumns = ["entity_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entity_id")],
)
data class EntityAliasEntity(
    /** Normalisiert (klein, ohne Anführungszeichen) – siehe core TextMatch.normalize. */
    val alias: String,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    val lang: String? = null,
)

/** Cluster/Ereignis (Agent 10). */
@Entity(
    tableName = "event",
    foreignKeys = [ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["merged_into_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("last_update"), Index("merged_into_id")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    @ColumnInfo(name = "first_seen") val firstSeen: Long,
    @ColumnInfo(name = "last_update") val lastUpdate: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    @ColumnInfo(name = "geo_precision") val geoPrecision: GeoPrecision? = null,
    val status: EventStatus = EventStatus.GEMELDET,
    /** 0..1, steuert Wiedervorlage-Abstände. */
    val volatility: Double = 0.5,
    @ColumnInfo(name = "merged_into_id") val mergedIntoId: Long? = null,
)

/** Claim(Text, Subjekt-Entität, Ereigniszeit, Ort+Genauigkeit, Quelle, Belegspanne, Typ). */
@Entity(
    tableName = "claim",
    foreignKeys = [
        ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["document_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EntityEntity::class, parentColumns = ["id"], childColumns = ["subject_entity_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("document_id"), Index("subject_entity_id"), Index("event_id"), Index("event_time")],
)
data class ClaimEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    val text: String,
    val speaker: String,
    val type: ClaimType,
    @ColumnInfo(name = "subject_entity_id") val subjectEntityId: Long? = null,
    /** Ereigniszeit UND Meldezeit getrennt (Agent 7). */
    @ColumnInfo(name = "event_time") val eventTime: Long? = null,
    @ColumnInfo(name = "reported_at") val reportedAt: Long,
    @ColumnInfo(name = "place_name") val placeName: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @ColumnInfo(name = "geo_precision") val geoPrecision: GeoPrecision? = null,
    /** Wörtliche Belegspanne + Offsets im Dokumenttext (Aufgabenleiter Schritt 3). */
    val span: String,
    @ColumnInfo(name = "span_start") val spanStart: Int? = null,
    @ColumnInfo(name = "span_end") val spanEnd: Int? = null,
    /** Übersetzt (ML Kit) -> Original-Span mitführen (Agent 5). */
    val translated: Boolean = false,
    @ColumnInfo(name = "original_span") val originalSpan: String? = null,
    @ColumnInfo(name = "event_id") val eventId: Long? = null,
    /** Wichtigkeit 1–5 (Aufgabenleiter Schritt 5). */
    val importance: Int? = null,
    val confidence: Double? = null,
    val model: String,
    @ColumnInfo(name = "prompt_version") val promptVersion: String,
    val retracted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Dossier(Version, Satz→Claim-IDs, Prüf-Ergebnis, Vertrauen). */
@Entity(
    tableName = "dossier",
    foreignKeys = [ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("event_id"), Index("updated_at")],
)
data class DossierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_id") val eventId: Long?,
    val title: String,
    val urgency: Urgency = Urgency.NORMAL,
    val state: DossierState = DossierState.AKTIV,
    @ColumnInfo(name = "current_version") val currentVersion: Int = 0,
    val confidence: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "dossier_version",
    foreignKeys = [ForeignKey(entity = DossierEntity::class, parentColumns = ["id"], childColumns = ["dossier_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["dossier_id", "version"], unique = true)],
)
data class DossierVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dossier_id") val dossierId: Long,
    val version: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val model: String,
    @ColumnInfo(name = "prompt_version") val promptVersion: String,
    /** Anteil belegter Sätze (Metrik "Ausbeute"). */
    @ColumnInfo(name = "yield_rate") val yieldRate: Double? = null,
)

@Entity(
    tableName = "dossier_sentence",
    foreignKeys = [ForeignKey(entity = DossierVersionEntity::class, parentColumns = ["id"], childColumns = ["version_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["version_id", "section", "position"], unique = true)],
)
data class DossierSentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "version_id") val versionId: Long,
    val section: DossierSection,
    val position: Int,
    val text: String,
    val verdict: Verdict = Verdict.UNGEPRUEFT,
    val confidence: Double? = null,
    val change: SentenceChange = SentenceChange.NEU,
)

/** Satz -> Claim-IDs (jeder Satz endet mit [Claim-ID]). */
@Entity(
    tableName = "sentence_claim",
    primaryKeys = ["sentence_id", "claim_id"],
    foreignKeys = [
        ForeignKey(entity = DossierSentenceEntity::class, parentColumns = ["id"], childColumns = ["sentence_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ClaimEntity::class, parentColumns = ["id"], childColumns = ["claim_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("claim_id")],
)
data class SentenceClaimEntity(
    @ColumnInfo(name = "sentence_id") val sentenceId: Long,
    @ColumnInfo(name = "claim_id") val claimId: Long,
)

/** Verknüpfung(stützt/widerspricht/gleiche Entität) zwischen Claims. */
@Entity(
    tableName = "claim_link",
    foreignKeys = [
        ForeignKey(entity = ClaimEntity::class, parentColumns = ["id"], childColumns = ["from_claim_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ClaimEntity::class, parentColumns = ["id"], childColumns = ["to_claim_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["from_claim_id", "to_claim_id", "relation"], unique = true), Index("to_claim_id")],
)
data class ClaimLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "from_claim_id") val fromClaimId: Long,
    @ColumnInfo(name = "to_claim_id") val toClaimId: Long,
    val relation: LinkRelation,
    val score: Double?,
    val method: LinkMethod,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Wiedervorlage(Fälligkeit, Grund). */
@Entity(
    tableName = "resubmission",
    foreignKeys = [ForeignKey(entity = DossierEntity::class, parentColumns = ["id"], childColumns = ["dossier_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dossier_id"), Index("due_at")],
)
data class ResubmissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dossier_id") val dossierId: Long,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    val reason: ResubmitReason,
    /** Index in der Abstandsfolge 1 h, 6 h, 1 d, 3 d, 7 d, 30 d. */
    val step: Int = 0,
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
)

@Entity(tableName = "feedback", indices = [Index(value = ["target", "target_id"])])
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val target: FeedbackTarget,
    @ColumnInfo(name = "target_id") val targetId: Long,
    val kind: FeedbackKind,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Lauf-Protokoll (Modell, Prompt-Version, Zeit, Temperatur) – SPEC 4 Robustheit, Auftrag 2. */
@Entity(tableName = "run_log", indices = [Index("started_at"), Index("phase")])
data class RunLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    val phase: Phase,
    /** Agent/Aufgabe, z. B. "claim_extractor". */
    val task: String,
    val model: String?,
    val backend: String?,
    @ColumnInfo(name = "prompt_version") val promptVersion: String?,
    @ColumnInfo(name = "input_tokens") val inputTokens: Int? = null,
    @ColumnInfo(name = "output_tokens") val outputTokens: Int? = null,
    @ColumnInfo(name = "battery_temp_c") val batteryTempC: Double?,
    /** PowerManager.THERMAL_STATUS_* */
    @ColumnInfo(name = "thermal_status") val thermalStatus: Int?,
    @ColumnInfo(name = "battery_pct") val batteryPct: Int?,
    val ok: Boolean,
    val error: String? = null,
    @ColumnInfo(name = "target_type") val targetType: OwnerType? = null,
    @ColumnInfo(name = "target_id") val targetId: Long? = null,
)

/** UI-Events(Zeit, Ort+Genauigkeit, 1-Zeilen-Text, Symboltyp, Statusring, Quellenstufe, Vertrauen, Dossier-ID, Phase). */
@Entity(
    tableName = "ui_event",
    foreignKeys = [
        ForeignKey(entity = DossierEntity::class, parentColumns = ["id"], childColumns = ["dossier_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("time"), Index("dossier_id"), Index("event_id")],
)
data class UiEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val lat: Double?,
    val lon: Double?,
    @ColumnInfo(name = "geo_precision") val geoPrecision: GeoPrecision?,
    /** Eine Zeile, z. B. "Heilbronn · Großbrand Lagerhalle". */
    val text: String,
    val symbol: Symbol,
    @ColumnInfo(name = "status_ring") val statusRing: StatusRing,
    @ColumnInfo(name = "source_tier") val sourceTier: SourceTier,
    val confidence: Double?,
    @ColumnInfo(name = "dossier_id") val dossierId: Long? = null,
    @ColumnInfo(name = "event_id") val eventId: Long? = null,
    val phase: Phase,
)

/** Embeddings als Float32-BLOB (core Vectors.toBlob), Kosinus in Kotlin/SQLite. */
@Entity(tableName = "embedding", primaryKeys = ["owner_type", "owner_id", "model", "dim"])
data class EmbeddingEntity(
    @ColumnInfo(name = "owner_type") val ownerType: OwnerType,
    @ColumnInfo(name = "owner_id") val ownerId: Long,
    val model: String,
    /** Matryoshka: 768 fein / 128 grob (SPEC 5 Agent 10). */
    val dim: Int,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val vector: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    override fun equals(other: Any?) = other is EmbeddingEntity && ownerType == other.ownerType &&
        ownerId == other.ownerId && model == other.model && dim == other.dim && vector.contentEquals(other.vector)
    override fun hashCode() = ((ownerType.hashCode() * 31 + ownerId.hashCode()) * 31 + model.hashCode()) * 31 + dim
}
