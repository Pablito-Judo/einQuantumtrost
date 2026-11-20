package de.worldpulse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Upsert suspend fun upsert(s: SourceEntity)
    @Query("SELECT * FROM source WHERE active = 1 AND approved = 1") suspend fun activeApproved(): List<SourceEntity>
    @Query("SELECT * FROM source WHERE id = :id") suspend fun byId(id: String): SourceEntity?
    @Query("SELECT * FROM source ORDER BY approved DESC, tier, name") fun observeAll(): Flow<List<SourceEntity>>
    @Query("UPDATE source SET approved = :approved WHERE id = :id") suspend fun setApproved(id: String, approved: Boolean)
    @Query("UPDATE source SET etag = :etag, last_modified = :lastModified WHERE id = :id")
    suspend fun updateFetchState(id: String, etag: String?, lastModified: String?)
}

data class EchoRow(val grp: String, val simhash: Long)

@Dao
interface DocumentDao {
    /** Exakte Duplikate (gleicher content_hash) werden ignoriert -> Rückgabe -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(d: DocumentEntity): Long
    @Query("SELECT * FROM document WHERE id = :id") suspend fun byId(id: Long): DocumentEntity?
    @Query("SELECT * FROM document WHERE status = :status ORDER BY published_at LIMIT :limit")
    suspend fun byStatus(status: DocumentStatus, limit: Int): List<DocumentEntity>
    @Query("UPDATE document SET status = :status WHERE id = :id") suspend fun setStatus(id: Long, status: DocumentStatus)
    /** Haushalt: Rohtext nach Frist entfernen (Claims + Hash + Link bleiben). */
    @Query("UPDATE document SET text = NULL WHERE fetched_at < :before AND text IS NOT NULL")
    suspend fun dropTextsBefore(before: Long): Int
    @Query("SELECT * FROM document ORDER BY COALESCE(published_at, fetched_at) DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DocumentEntity>>
    @Query("SELECT url FROM document WHERE fetched_at >= :since") suspend fun urlsSince(since: Long): List<String>
    @Query("SELECT content_hash FROM document WHERE fetched_at >= :since") suspend fun hashesSince(since: Long): List<String>
    @Query("SELECT echo_group AS grp, simhash FROM document WHERE fetched_at >= :since AND simhash IS NOT NULL AND echo_group IS NOT NULL")
    suspend fun echoSince(since: Long): List<EchoRow>
    @Query("SELECT COUNT(*) FROM document") fun observeCount(): Flow<Int>
    /** Unabhängige Quellen = verschiedene Echo-Gruppen (ohne Gruppe zählt jedes Dokument einzeln). */
    @Query("""SELECT COUNT(DISTINCT COALESCE(d.echo_group, 'doc:' || d.id)) FROM claim c
              JOIN document d ON d.id = c.document_id WHERE c.event_id = :eventId AND c.retracted = 0""")
    suspend fun independentSourceCount(eventId: Long): Int
}

@Dao
interface EntityDao {
    @Insert suspend fun insert(e: EntityEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAlias(a: EntityAliasEntity)
    @Query("SELECT e.* FROM entity e JOIN entity_alias a ON a.entity_id = e.id WHERE a.alias = :normalizedAlias")
    suspend fun byAlias(normalizedAlias: String): List<EntityEntity>
}

@Dao
interface ClaimDao {
    @Insert suspend fun insert(c: ClaimEntity): Long
    @Insert suspend fun insertAll(c: List<ClaimEntity>): List<Long>
    @Query("SELECT * FROM claim WHERE document_id = :docId") suspend fun forDocument(docId: Long): List<ClaimEntity>
    @Query("SELECT * FROM claim WHERE event_id = :eventId AND retracted = 0 ORDER BY event_time")
    suspend fun forEvent(eventId: Long): List<ClaimEntity>
    @Query("SELECT * FROM claim WHERE event_id IS NULL ORDER BY created_at LIMIT :limit")
    suspend fun unclustered(limit: Int): List<ClaimEntity>
    @Query("UPDATE claim SET event_id = :eventId WHERE id IN (:ids)") suspend fun assignEvent(ids: List<Long>, eventId: Long)
    @Query("SELECT event_id FROM claim WHERE document_id = :docId AND event_id IS NOT NULL LIMIT 1")
    suspend fun eventOfDocument(docId: Long): Long?
    @Query("SELECT COUNT(*) FROM claim WHERE model != 'headline'") fun observeLlmClaimCount(): Flow<Int>
    /** Belege eines Ereignisses für die Status-Ring-Regel (Echo-Gruppe zählt einfach). */
    @Query("""SELECT COALESCE(d.echo_group, 'doc:' || d.id) AS grp, s.tier AS tier, (c.lat IS NOT NULL) AS hasGeo
              FROM claim c JOIN document d ON d.id = c.document_id JOIN source s ON s.id = d.source_id
              WHERE c.event_id = :eventId AND c.retracted = 0""")
    suspend fun evidence(eventId: Long): List<EvidenceRow>
}

data class EvidenceRow(val grp: String, val tier: String, val hasGeo: Boolean)

@Dao
interface EventDao {
    @Insert suspend fun insert(e: EventEntity): Long
    @Query("SELECT * FROM event WHERE id = :id") suspend fun byId(id: Long): EventEntity?
    @Query("SELECT * FROM event WHERE merged_into_id IS NULL AND last_update >= :since ORDER BY last_update DESC")
    suspend fun activeSince(since: Long): List<EventEntity>
    @androidx.room.Update suspend fun update(e: EventEntity)
    @Query("UPDATE event SET status = :status, last_update = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: EventStatus, now: Long)
}

/** Satz + zugehörige Claim-IDs für das atomare Speichern einer Dossier-Version. */
data class NewSentence(val sentence: DossierSentenceEntity, val claimIds: List<Long>)

@Dao
abstract class DossierDao {
    @Insert abstract suspend fun insertDossier(d: DossierEntity): Long
    @Insert abstract suspend fun insertVersion(v: DossierVersionEntity): Long
    @Insert abstract suspend fun insertSentence(s: DossierSentenceEntity): Long
    @Insert abstract suspend fun insertSentenceClaims(l: List<SentenceClaimEntity>)
    @Query("UPDATE dossier SET current_version = :version, updated_at = :now WHERE id = :id")
    abstract suspend fun setCurrentVersion(id: Long, version: Int, now: Long)
    @Query("SELECT * FROM dossier WHERE id = :id") abstract suspend fun byId(id: Long): DossierEntity?
    @Query("SELECT * FROM dossier_version WHERE dossier_id = :dossierId ORDER BY version DESC LIMIT 1")
    abstract suspend fun latestVersion(dossierId: Long): DossierVersionEntity?
    @Query("SELECT * FROM dossier_sentence WHERE version_id = :versionId ORDER BY section, position")
    abstract suspend fun sentences(versionId: Long): List<DossierSentenceEntity>
    @Query("SELECT claim_id FROM sentence_claim WHERE sentence_id = :sentenceId") abstract suspend fun claimIds(sentenceId: Long): List<Long>
    @Query("SELECT * FROM dossier WHERE state = 'AKTIV' ORDER BY updated_at DESC") abstract fun observeActive(): Flow<List<DossierEntity>>

    /**
     * Neue Version atomar: Version + Sätze + Satz→Claim-Zuordnung + Zeiger.
     * Regel (SPEC 5): jeder Satz braucht ≥ 1 Claim-ID, sonst wird nichts gespeichert.
     */
    @Transaction
    open suspend fun addVersion(dossierId: Long, version: DossierVersionEntity, sentences: List<NewSentence>, now: Long): Long {
        require(sentences.all { it.claimIds.isNotEmpty() }) { "Satz ohne Claim-ID" }
        val vId = insertVersion(version.copy(dossierId = dossierId))
        sentences.forEach { ns ->
            val sId = insertSentence(ns.sentence.copy(versionId = vId))
            insertSentenceClaims(ns.claimIds.distinct().map { SentenceClaimEntity(sId, it) })
        }
        setCurrentVersion(dossierId, version.version, now)
        return vId
    }
}

@Dao
interface ClaimLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(l: ClaimLinkEntity): Long
    @Query("SELECT * FROM claim_link WHERE relation = 'WIDERSPRICHT' AND (from_claim_id IN (:ids) OR to_claim_id IN (:ids))")
    suspend fun contradictionsFor(ids: List<Long>): List<ClaimLinkEntity>
}

@Dao
interface ResubmissionDao {
    @Insert suspend fun insert(r: ResubmissionEntity): Long
    @Query("SELECT * FROM resubmission WHERE done_at IS NULL AND due_at <= :now ORDER BY due_at")
    suspend fun due(now: Long): List<ResubmissionEntity>
    @Query("UPDATE resubmission SET done_at = :now WHERE id = :id") suspend fun markDone(id: Long, now: Long)
}

@Dao
interface FeedbackDao {
    @Insert suspend fun insert(f: FeedbackEntity): Long
    @Query("SELECT * FROM feedback WHERE target = :target AND target_id = :id") suspend fun forTarget(target: FeedbackTarget, id: Long): List<FeedbackEntity>
}

@Dao
interface RunLogDao {
    @Insert suspend fun insert(r: RunLogEntity): Long
    @Query("UPDATE run_log SET ended_at = :endedAt, ok = :ok, error = :error, output_tokens = :outputTokens WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long, ok: Boolean, error: String?, outputTokens: Int?)
    @Query("SELECT * FROM run_log WHERE started_at >= :since ORDER BY started_at") suspend fun since(since: Long): List<RunLogEntity>
    /** Dispatcher lernt Kosten je Aufgabentyp (SPEC 5 Agent 1): mittlere Dauer der letzten erfolgreichen Läufe. */
    @Query("""SELECT AVG(ended_at - started_at) FROM (SELECT * FROM run_log WHERE task = :task AND ok = 1
              AND ended_at IS NOT NULL ORDER BY started_at DESC LIMIT :window)""")
    suspend fun meanDurationMs(task: String, window: Int = 50): Double?
}

@Dao
interface UiEventDao {
    @Insert suspend fun insert(e: UiEventEntity): Long
    /** Newsroom / Canvas-Karte / MapLibre: ein Datenstrom (SPEC 7). */
    @Query("SELECT * FROM ui_event WHERE time >= :since ORDER BY time DESC") fun observeSince(since: Long): Flow<List<UiEventEntity>>
    @Query("DELETE FROM ui_event WHERE time < :before") suspend fun deleteBefore(before: Long): Int
    @Query("SELECT COUNT(*) FROM ui_event") suspend fun count(): Int
    @Query("SELECT * FROM ui_event WHERE event_id = :eventId LIMIT 1") suspend fun byEvent(eventId: Long): UiEventEntity?
    @androidx.room.Update suspend fun update(e: UiEventEntity)
    /** Demo-Pins entfernen, sobald echte Ereignisse existieren. */
    @Query("DELETE FROM ui_event WHERE event_id IS NULL AND text LIKE '%(DEMO%'") suspend fun deleteDemo(): Int
    @Insert suspend fun insertAll(e: List<UiEventEntity>)
}

@Dao
interface EmbeddingDao {
    @Upsert suspend fun upsert(e: EmbeddingEntity)
    @Query("SELECT * FROM embedding WHERE owner_type = :type AND model = :model AND dim = :dim")
    suspend fun all(type: OwnerType, model: String, dim: Int): List<EmbeddingEntity>
}
