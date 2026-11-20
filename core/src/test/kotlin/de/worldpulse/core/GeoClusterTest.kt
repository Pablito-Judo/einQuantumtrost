package de.worldpulse.core

import de.worldpulse.core.cluster.Categorizer
import de.worldpulse.core.cluster.DocFeature
import de.worldpulse.core.cluster.EventClusterer
import de.worldpulse.core.geo.Gazetteer
import de.worldpulse.core.gold.GeoPrecision
import de.worldpulse.core.scout.FeedParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoClusterTest {
    private val gaz = Gazetteer.bundled()

    @Test fun gazetteer_multilingual_and_region() {
        assertEquals("UA", gaz.best("Russia launches mass missile attack on Kyiv")!!.place.iso2)
        assertEquals("UA", gaz.best("Massiver Raketenangriff auf Kiew")!!.place.iso2)
        assertEquals("RU", gaz.best("В Москве задержаны сотрудники")!!.place.iso2)
        val hn = gaz.best("Großbrand in Heilbronn: zwei Verletzte")!!
        assertEquals(GeoPrecision.STADT, hn.place.precision); assertEquals(49.142, hn.place.lat, 0.01)
        assertEquals("Bad Wimpfen", gaz.best("Neckar-Pegel in Bad Wimpfen steigt")!!.matched)
    }

    @Test fun gazetteer_avoids_common_words_and_prefers_title_city() {
        assertNull(gaz.best("Essen wird teurer", "Die Preise für Essen steigen."))
        val h = gaz.best("Explosion in Odesa", "Ukraine says Russia attacked the port.")!!
        assertEquals("Odesa", h.matched.trimEnd()); assertTrue(!h.place.isCountry)
        assertEquals("DE", gaz.best("Deutschlands Wirtschaft schrumpft")?.place?.iso2)
    }

    @Test fun categorizer() {
        assertEquals("erdbeben", Categorizer.categorize("M 5.6 - southeast of the Loyalty Islands"))
        assertEquals("explosion", Categorizer.categorize("Russia launches mass missile attack against Ukraine"))
        assertEquals("protest", Categorizer.categorize("IG Metall startet 24-Stunden-Warnstreiks"))
        assertEquals("unwetter", Categorizer.categorize("Hochwasser: Neckar-Pegel steigt auf 5,9 Meter"))
        assertEquals("brand", Categorizer.categorize("Großbrand in Lagerhalle"))
        assertEquals("sonstiges", Categorizer.categorize("Anthropic startet mit Kursplus an der Börse"))
    }

    @Test fun clusterer_groups_same_story_and_separates_others() {
        val c = EventClusterer()
        val t = 1_000_000L
        val a = c.assign(DocFeature(1, "Russia launches mass missile attack against Ukraine", t, "UA", 0))
        val b = c.assign(DocFeature(2, "Ukraine: Russia launches massive missile and drone attack overnight", t + 3_600_000, "UA", 0))
        val x = c.assign(DocFeature(3, "IG Metall startet Warnstreiks", t, "DE", 0))
        val y = c.assign(DocFeature(4, "Russia launches mass missile attack against Ukraine", t + 100 * 3_600_000L, "UA", 0)) // außerhalb 72 h
        assertEquals(a, b); assertNotEquals(a, x); assertNotEquals(a, y)
    }

    @Test fun clusterer_uses_embeddings_when_available() {
        val c = EventClusterer()
        val e1 = floatArrayOf(1f, 0f, 0f); val e2 = floatArrayOf(0.97f, 0.24f, 0f); val e3 = floatArrayOf(0f, 1f, 0f)
        val a = c.assign(DocFeature(1, "Titel eins", 0, null, 0, e1))
        assertEquals(a, c.assign(DocFeature(2, "ganz anderer Wortlaut", 0, null, 0, e2)))
        assertNotEquals(a, c.assign(DocFeature(3, "Titel eins", 0, null, 0, e3)))
    }

    @Test fun feed_coordinates_georss_and_geo() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom" xmlns:georss="http://www.georss.org/georss"><entry><id>1</id>
            <title>M 5.6 - Loyalty Islands</title><link href="https://e.org/q"/><georss:point>-21.9 169.4</georss:point></entry></feed>"""
        val i = FeedParser.parse("usgs", atom.toByteArray()).single()
        assertEquals(-21.9, i.lat!!, 1e-9); assertEquals(169.4, i.lon!!, 1e-9)
        val rss = """<rss xmlns:geo="http://www.w3.org/2003/01/geo/wgs84_pos#"><channel><item><title>Green alert</title><link>https://e.org/g</link>
            <geo:Point><geo:lat>15.5</geo:lat><geo:long>120.1</geo:long></geo:Point></item></channel></rss>"""
        val g = FeedParser.parse("gdacs", rss.toByteArray()).single()
        assertEquals(15.5, g.lat!!, 1e-9); assertEquals(120.1, g.lon!!, 1e-9)
    }
}
