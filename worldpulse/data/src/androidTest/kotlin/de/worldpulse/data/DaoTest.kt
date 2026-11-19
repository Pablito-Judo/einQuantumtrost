package de.worldpulse.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.worldpulse.core.claims.ClaimType
import de.worldpulse.core.embed.Vectors
import de.worldpulse.core.gold.GeoPrecision
import de.worldpulse.core.gold.SourceTier
import de.worldpulse.data.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Läuft auf dem S23: ./gradlew :data:connectedAndroidTest */
@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: WorldPulseDb
    private val now = 1_763_500_000_000L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorldPulseDb::class.java).build()
    }

    @After fun close() = db.close()

    private suspend fun seed(): Triple<Long, Long, Long> {
        db.sources().upsert(SourceEntity("dpa", "dpa", "https://example.org/rss", SourceKind.RSS, SourceTier.A, approved = true, createdAt = now))
        val d1 = db.documents().insert(DocumentEntity(sourceId = "dpa", url = "https://example.org/1", contentHash = "h1", fetchedAt = now,
            publishedAt = now, language = "de", echoGroup = "g1", title = "T", text = "Der Neckar erreichte 5,9 Meter."))
        val d2 = db.documents().insert(DocumentEntity(sourceId = "dpa", url = "https://example.org/2", contentHash = "h2", fetchedAt = now,
            publishedAt = now, language = "de", echoGroup = "g1", title = "T2", text = "Kopie: Der Neckar erreichte 5,9 Meter."))
        val ev = db.events().insert(EventEntity(title = "Hochwasser", category = "unwetter", firstSeen = now, lastUpdate = now,
            lat = 49.23, lon = 9.16, geoPrecision = GeoPrecision.STADT))
        return Triple(d1, d2, ev)
    }

    private fun claim(doc: Long, ev: Long?, text: String = "Der Neckar erreichte 5,9 Meter") = ClaimEntity(
        documentId = doc, text = text, speaker = "HVZ", type = ClaimType.FAKT, reportedAt = now, span = text,
        eventId = ev, model = "gemma-4-E2B", promptVersion = "extract-v1", createdAt = now)

    @Test fun duplicate_hash_is_ignored() = runTest {
        seed()
        val dup = db.documents().insert(DocumentEntity(sourceId = "dpa", url = "https://example.org/3", contentHash = "h1",
            fetchedAt = now, publishedAt = null, language = "de", title = null, text = "x"))
        assertEquals(-1L, dup)
    }

    @Test fun echo_group_counts_as_one_source() = runTest {
        val (d1, d2, ev) = seed()
        db.claims().insertAll(listOf(claim(d1, ev), claim(d2, ev)))
        assertEquals(1, db.documents().independentSourceCount(ev))
    }

    @Test fun dossier_version_with_claim_ids_and_cascade() = runTest {
        val (d1, _, ev) = seed()
        val c1 = db.claims().insert(claim(d1, ev))
        val dossierId = db.dossiers().insertDossier(DossierEntity(eventId = ev, title = "Neckar-Hochwasser", createdAt = now, updatedAt = now))
        val vId = db.dossiers().addVersion(
            dossierId,
            DossierVersionEntity(dossierId = 0, version = 1, createdAt = now, model = "gemma-4-E4B", promptVersion = "write-v1"),
            listOf(NewSentence(DossierSentenceEntity(versionId = 0, section = DossierSection.KURZFASSUNG, position = 0,
                text = "Der Neckar hat 5,9 Meter erreicht.", verdict = Verdict.BELEGT), listOf(c1, c1))),
            now,
        )
        assertEquals(1, db.dossiers().byId(dossierId)!!.currentVersion)
        val s = db.dossiers().sentences(vId).single()
        assertEquals(listOf(c1), db.dossiers().claimIds(s.id))
        // Dokument löschen -> Claim weg -> Satz-Zuordnung weg (CASCADE)
        db.openHelper.writableDatabase.execSQL("DELETE FROM document WHERE id = $d1")
        assertTrue(db.dossiers().claimIds(s.id).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun sentence_without_claim_is_rejected() = runTest {
        val (_, _, ev) = seed()
        val dossierId = db.dossiers().insertDossier(DossierEntity(eventId = ev, title = "x", createdAt = now, updatedAt = now))
        db.dossiers().addVersion(dossierId, DossierVersionEntity(dossierId = 0, version = 1, createdAt = now, model = "m", promptVersion = "p"),
            listOf(NewSentence(DossierSentenceEntity(versionId = 0, section = DossierSection.WAS_PASSIERT, position = 0, text = "unbelegt"), emptyList())), now)
    }

    @Test fun ui_events_flow_and_retention() = runTest {
        db.uiEvents().insert(UiEventEntity(time = now, lat = 49.14, lon = 9.22, geoPrecision = GeoPrecision.STADT, text = "Heilbronn · Brand",
            symbol = Symbol.BRAND, statusRing = StatusRing.GEMELDET, sourceTier = SourceTier.B, confidence = 0.6, phase = Phase.EXTRAKTION))
        db.uiEvents().insert(UiEventEntity(time = now - 86_400_000, lat = null, lon = null, geoPrecision = null, text = "alt",
            symbol = Symbol.EVENT, statusRing = StatusRing.BESTAETIGT, sourceTier = SourceTier.A, confidence = null, phase = Phase.SAMMELN))
        assertEquals(listOf("Heilbronn · Brand"), db.uiEvents().observeSince(now - 1000).first().map { it.text })
        assertEquals(1, db.uiEvents().deleteBefore(now - 1000))
    }

    @Test fun run_log_cost_learning() = runTest {
        listOf(30_000L, 40_000L).forEach { dur ->
            val id = db.runLog().insert(RunLogEntity(startedAt = now, endedAt = null, phase = Phase.EXTRAKTION, task = "claim_extractor",
                model = "gemma-4-E2B", backend = "GPU", promptVersion = "extract-v1", batteryTempC = 33.5, thermalStatus = 0, batteryPct = 80, ok = false))
            db.runLog().finish(id, now + dur, ok = true, error = null, outputTokens = 200)
        }
        assertEquals(35_000.0, db.runLog().meanDurationMs("claim_extractor")!!, 0.1)
        assertNull(db.runLog().meanDurationMs("unbekannt"))
    }

    @Test fun embedding_blob_roundtrip() = runTest {
        val v = floatArrayOf(0.1f, -0.2f, 0.3f)
        db.embeddings().upsert(EmbeddingEntity(OwnerType.CLAIM, 1, "embeddinggemma", 3, Vectors.toBlob(v), now))
        val back = Vectors.fromBlob(db.embeddings().all(OwnerType.CLAIM, "embeddinggemma", 3).single().vector)
        assertTrue(back.contentEquals(v))
    }

    @Test fun alias_lookup() = runTest {
        val id = db.entities().insert(EntityEntity(wikidataId = "Q1899", name = "Kyjiw", type = EntityType.ORT))
        listOf("kyiv", "kiew", "киев").forEach { db.entities().insertAlias(EntityAliasEntity(it, id)) }
        assertEquals("Kyjiw", db.entities().byAlias("киев").single().name)
    }
}
