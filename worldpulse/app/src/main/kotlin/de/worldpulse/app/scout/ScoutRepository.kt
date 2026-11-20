package de.worldpulse.app.scout

import android.content.Context
import de.worldpulse.core.scout.Backoff
import de.worldpulse.core.scout.EchoGrouper
import de.worldpulse.core.scout.SafeFetcher
import de.worldpulse.core.scout.Scout
import de.worldpulse.core.scout.SourceDef
import de.worldpulse.core.scout.SourceRegistry
import de.worldpulse.core.scout.SourceType
import de.worldpulse.core.claims.ClaimType
import de.worldpulse.core.gold.GeoPrecision
import de.worldpulse.core.gold.SourceTier
import de.worldpulse.data.db.ClaimEntity
import de.worldpulse.data.db.DocumentEntity
import de.worldpulse.data.db.DocumentStatus
import de.worldpulse.data.db.OwnerType
import de.worldpulse.data.db.Phase
import de.worldpulse.data.db.RunLogEntity
import de.worldpulse.data.db.SourceEntity
import de.worldpulse.data.db.SourceKind
import de.worldpulse.data.db.WorldPulseDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * S2 in der App: Quellenregister (sources.yaml) ↔ Tabelle source, Abruf freigegebener Quellen, Speichern
 * bereinigter Dokumente (Status BEREINIGT) inkl. Hash/SimHash/Echo-Gruppe, Lauf-Protokoll je Quelle.
 * Regeln: nur freigegebene Quellen; sanktionierte Medien ohne Volltext; Abrufintervall je Quelle.
 */
class ScoutRepository(private val ctx: Context, private val db: WorldPulseDb) {
    private val prefs = ctx.getSharedPreferences("scout", Context.MODE_PRIVATE)
    private val registry: Map<String, SourceDef> by lazy { SourceRegistry.bundled().associateBy { it.id } }

    /** Register in die DB übernehmen; Freigaben des Nutzers bleiben erhalten. */
    suspend fun syncRegistry() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        registry.values.forEach { d ->
            val old = db.sources().byId(d.id)
            db.sources().upsert(
                SourceEntity(
                    id = d.id, name = d.name, url = d.url, kind = kindOf(d.type), tier = d.tier, countryCode = d.country,
                    stateMedia = d.stateMedia, sanctioned = d.sanctioned, profileJson = old?.profileJson,
                    approved = old?.approved ?: d.approved, active = true, fetchIntervalMin = d.intervalMin,
                    etag = old?.etag, lastModified = old?.lastModified, createdAt = old?.createdAt ?: now,
                ),
            )
        }
    }

    data class RunResult(val sources: Int, val newDocs: Int, val errors: List<String>)

    /** Ein Abruf-Durchgang über alle fälligen, freigegebenen Quellen. [force] ignoriert Intervalle. */
    suspend fun runOnce(force: Boolean = false): RunResult = withContext(Dispatchers.IO) {
        syncRegistry()
        val now = System.currentTimeMillis()
        val since = now - 7 * 24 * 3600_000L
        val scout = Scout(SafeFetcher(), Backoff(), EchoGrouper().also { g -> db.documents().echoSince(now - 72 * 3600_000L).forEach { g.preload(it.grp, it.simhash) } })
        val seenUrls = db.documents().urlsSince(since).toHashSet()
        val seenHashes = db.documents().hashesSince(since).toHashSet()
        var count = 0; var newDocs = 0
        val errors = mutableListOf<String>()
        for (src in db.sources().activeApproved()) {
            val def = registry[src.id] ?: continue
            val last = prefs.getLong("last_${src.id}", 0L)
            if (!force && now - last < src.fetchIntervalMin * 60_000L) continue
            count++
            val t0 = System.currentTimeMillis()
            val res = scout.poll(def, Scout.SourceState(src.etag, src.lastModified))
            prefs.edit().putLong("last_${src.id}", now).apply()
            if (res.error != null) {
                errors += "${src.name}: ${res.error}"
                log(src.id, t0, ok = false, error = res.error)
                continue
            }
            db.sources().updateFetchState(src.id, res.state.etag, res.state.lastModified)
            val stats = Scout.Stats()
            val fullText = def.fulltext && !def.sanctioned
            val docs = scout.toDocuments(res.items.take(40), seenUrls, seenHashes, fetchFullText = fullText, stats = stats)
            docs.forEach { d ->
                val id = db.documents().insert(
                    DocumentEntity(
                        sourceId = d.sourceId, url = d.url, contentHash = d.contentHash, simhash = d.simhash, fetchedAt = d.fetchedAt,
                        publishedAt = d.publishedAt, language = d.language, echoGroup = d.echoGroup, title = d.title,
                        // Sanktionierte Medien: keine wörtlichen Kopien speichern (SPEC 8)
                        text = if (def.sanctioned) null else d.text, status = DocumentStatus.BEREINIGT,
                    ),
                )
                if (id > 0) {
                    newDocs++; seenUrls += d.url; seenHashes += d.contentHash
                    // Vorläufiger Schlagzeilen-Claim (bis LLM-Extraktion, S4): "Quelle X meldet: <Titel>".
                    // Messnetze der Stufe A mit eigenen Koordinaten (USGS, GDACS) gelten als Fakt, alles andere als Behauptung.
                    val measured = src.tier == SourceTier.A && d.lat != null
                    db.claims().insert(
                        ClaimEntity(
                            documentId = id, text = d.title, speaker = src.name,
                            type = if (measured) ClaimType.FAKT else ClaimType.BEHAUPTUNG,
                            reportedAt = d.publishedAt ?: d.fetchedAt, lat = d.lat, lon = d.lon,
                            geoPrecision = if (d.lat != null) GeoPrecision.PUNKT else null,
                            span = d.title, spanStart = null, spanEnd = null,
                            model = "headline", promptVersion = "headline-v1", createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            log(src.id, t0, ok = true, error = "neu=${docs.size} $stats")
        }
        // Ereignisse bilden/aktualisieren (ohne LLM) -> echte Pins
        val ev = de.worldpulse.app.pipeline.EventBuilder(db).run()
        if (ev.claims > 0) log("eventbuilder", System.currentTimeMillis(), ok = true, error = "claims=${ev.claims} neu=${ev.newEvents} aktualisiert=${ev.updatedEvents}")
        RunResult(count, newDocs, errors)
    }

    private suspend fun log(sourceId: String, t0: Long, ok: Boolean, error: String?) {
        db.runLog().insert(
            RunLogEntity(
                startedAt = t0, endedAt = System.currentTimeMillis(), phase = Phase.SAMMELN, task = "scout:$sourceId",
                model = null, backend = null, promptVersion = null, batteryTempC = null, thermalStatus = null, batteryPct = null,
                ok = ok, error = error, targetType = OwnerType.DOKUMENT,
            ),
        )
    }

    private fun kindOf(t: SourceType) = when (t) {
        SourceType.RSS -> SourceKind.RSS; SourceType.ATOM -> SourceKind.ATOM; SourceType.ICAL -> SourceKind.ICAL
        SourceType.JSONLD -> SourceKind.JSONLD; SourceType.HTML -> SourceKind.HTML; SourceType.GDELT -> SourceKind.GDELT
        SourceType.API -> SourceKind.API
    }

    fun note(id: String): String? = registry[id]?.note
}
