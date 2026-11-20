package de.worldpulse.app.pipeline

import de.worldpulse.core.cluster.Categorizer
import de.worldpulse.core.cluster.DocFeature
import de.worldpulse.core.cluster.EventClusterer
import de.worldpulse.core.geo.Gazetteer
import de.worldpulse.core.gold.GeoPrecision
import de.worldpulse.data.db.ClaimEntity
import de.worldpulse.data.db.EventEntity
import de.worldpulse.data.db.EventStatus
import de.worldpulse.data.db.Phase
import de.worldpulse.data.db.StatusRing
import de.worldpulse.data.db.UiEventEntity
import de.worldpulse.data.db.WorldPulseDb
import de.worldpulse.data.pins.EvidenceSummary
import de.worldpulse.data.pins.StatusRule
import de.worldpulse.data.pins.SymbolMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ereignis-Bildung ohne LLM (Stufe A von S4/S5): Schlagzeilen-Claims → Strom-Clustering (Titel + Ort, 72 h) →
 * Ereignis mit Kategorie/Ort → Status-Ring aus unabhängigen Quellen → UI-Event (ein Datenstrom für Newsroom/Karte/Canvas).
 * Wenn LLM-Claims (S4) und Embeddings (S5) dazukommen, laufen sie durch denselben Weg.
 */
class EventBuilder(private val db: WorldPulseDb) {
    private val gazetteer by lazy { Gazetteer.bundled() }

    data class Result(val claims: Int, val newEvents: Int, val updatedEvents: Int)

    suspend fun run(now: Long = System.currentTimeMillis()): Result = withContext(Dispatchers.IO) {
        val pending = db.claims().unclustered(1000)
        if (pending.isEmpty()) return@withContext Result(0, 0, 0)

        val clusterer = EventClusterer()
        val recent = db.events().activeSince(now - 72 * 3600_000L)
        recent.forEach { e ->
            clusterer.preload(EventClusterer.Cluster(e.id, e.lastUpdate, EventClusterer.tokens(e.title).toMutableSet(), null, 1, mutableSetOf()))
        }
        val tempToEvent = HashMap<Long, Long>()
        val touched = HashSet<Long>()
        var created = 0
        for (c in pending) {
            // LLM-Claims erben das Ereignis ihres Artikels (über den Schlagzeilen-Claim)
            db.claims().eventOfDocument(c.documentId)?.let { ev ->
                db.claims().assignEvent(listOf(c.id), ev); touched += ev; continue
            }
            val doc = db.documents().byId(c.documentId) ?: continue
            val title = doc.title ?: c.text
            val geo = placeFor(c, title, doc.text.orEmpty())
            val time = doc.publishedAt ?: doc.fetchedAt
            val cid = clusterer.assign(DocFeature(c.id, title, time, geo?.key, doc.simhash ?: 0L))
            val eventId = recent.firstOrNull { it.id == cid }?.id ?: tempToEvent[cid] ?: run {
                created++
                db.events().insert(
                    EventEntity(
                        title = title.take(200), category = Categorizer.categorize(title, doc.text.orEmpty()),
                        firstSeen = time, lastUpdate = time, lat = geo?.lat, lon = geo?.lon, geoPrecision = geo?.precision,
                    ),
                ).also { tempToEvent[cid] = it }
            }
            db.claims().assignEvent(listOf(c.id), eventId)
            val e = db.events().byId(eventId)!!
            db.events().update(e.copy(
                lastUpdate = maxOf(e.lastUpdate, time),
                // genauerer Ort gewinnt (Punkt > Stadt > Region > Land)
                lat = if (geo != null && better(geo.precision, e.geoPrecision)) geo.lat else e.lat,
                lon = if (geo != null && better(geo.precision, e.geoPrecision)) geo.lon else e.lon,
                geoPrecision = if (geo != null && better(geo.precision, e.geoPrecision)) geo.precision else e.geoPrecision,
            ))
            touched += eventId
        }
        touched.forEach { publish(it, now) }
        if (touched.isNotEmpty()) db.uiEvents().deleteDemo()
        Result(pending.size, created, touched.size - created)
    }

    private data class Geo(val key: String, val name: String, val lat: Double, val lon: Double, val precision: GeoPrecision)

    private fun placeFor(c: ClaimEntity, title: String, text: String): Geo? {
        if (c.lat != null && c.lon != null) {
            val near = gazetteer.best(title, text)
            return Geo("pt:%.1f,%.1f".format(java.util.Locale.ROOT, c.lat, c.lon), near?.matched ?: "Ort aus Messnetz", c.lat!!, c.lon!!, c.geoPrecision ?: GeoPrecision.PUNKT)
        }
        val h = gazetteer.best(title, text) ?: return null
        return Geo(if (h.place.isCountry) h.place.iso2 else h.place.names.first(), h.matched, h.place.lat, h.place.lon, h.place.precision)
    }

    private fun better(a: GeoPrecision, b: GeoPrecision?): Boolean = b == null || a.ordinal > b.ordinal

    /** Status-Ring, Symbol und Kurztext neu berechnen und das UI-Event schreiben/aktualisieren. */
    private suspend fun publish(eventId: Long, now: Long) {
        val e = db.events().byId(eventId) ?: return
        val ev = db.claims().evidence(eventId)
        val groupsByTier = ev.groupBy { it.tier }.mapValues { (_, rows) -> rows.map { it.grp }.toSet().size }
        val summary = EvidenceSummary(
            independentA = groupsByTier["A"] ?: 0, independentB = groupsByTier["B"] ?: 0, independentC = groupsByTier["C"] ?: 0,
            // Primärquelle: Messnetz/Behörde der Stufe A mit eigenem Ort (USGS, GDACS)
            hasPrimarySource = ev.any { it.tier == "A" && it.hasGeo },
            hasContradiction = false, // NLI/Widerspruch kommt in S4/S6
        )
        val ring = StatusRule.ring(summary)
        val sources = ev.map { it.grp }.toSet().size
        val conf = (0.35 + 0.15 * sources + if (summary.hasPrimarySource) 0.3 else 0.0).coerceAtMost(0.95)
        db.events().setStatus(eventId, when (ring) { StatusRing.BESTAETIGT -> EventStatus.BESTAETIGT; StatusRing.UMSTRITTEN -> EventStatus.UMSTRITTEN; else -> EventStatus.GEMELDET }, e.lastUpdate)
        val place = e.lat?.let { gazetteer.best(e.title)?.matched }
        val text = (if (place != null && !e.title.contains(place)) "$place · " else "") + e.title + if (sources > 1) " ($sources Quellen)" else ""
        val ui = UiEventEntity(
            time = e.firstSeen, lat = e.lat, lon = e.lon, geoPrecision = e.geoPrecision, text = text.take(160),
            symbol = SymbolMapper.fromCategory(e.category), statusRing = ring, sourceTier = StatusRule.bestTier(summary),
            confidence = conf, eventId = eventId, phase = Phase.CLUSTERING,
        )
        val old = db.uiEvents().byEvent(eventId)
        if (old == null) db.uiEvents().insert(ui) else db.uiEvents().update(ui.copy(id = old.id))
    }
}
