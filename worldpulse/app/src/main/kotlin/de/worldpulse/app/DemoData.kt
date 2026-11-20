package de.worldpulse.app

import de.worldpulse.core.gold.GeoPrecision
import de.worldpulse.core.gold.SourceTier
import de.worldpulse.data.db.Phase
import de.worldpulse.data.db.StatusRing
import de.worldpulse.data.db.Symbol
import de.worldpulse.data.db.UiEventEntity
import de.worldpulse.data.db.WorldPulseDb
import kotlin.random.Random

/**
 * Dummy-UI-Events für S1 (Design/Interaktion prüfen). Alle Texte sind als DEMO gekennzeichnet und frei erfunden.
 * Zeiten über 0–80 h verteilt, damit alle Alterungsstufen (neu, aktiv, blass, Punkt) sichtbar sind.
 */
object DemoData {
    private data class D(val lat: Double, val lon: Double, val p: GeoPrecision, val text: String, val s: Symbol, val r: StatusRing, val t: SourceTier, val c: Double, val ageH: Double)

    private val items = listOf(
        // Heimatregion
        D(49.142, 9.219, GeoPrecision.STADT, "Heilbronn · Großbrand Lagerhalle (DEMO)", Symbol.BRAND, StatusRing.BESTAETIGT, SourceTier.A, 0.9, 0.1),
        D(49.191, 9.224, GeoPrecision.STADT, "Neckarsulm · Gemeinderat beschließt Grundschule (DEMO)", Symbol.POLITIK_WAHL, StatusRing.BESTAETIGT, SourceTier.B, 0.8, 5.0),
        D(49.230, 9.158, GeoPrecision.STADT, "Bad Wimpfen · Neckar-Pegel 5,9 m (DEMO)", Symbol.UNWETTER_KATASTROPHE, StatusRing.BESTAETIGT, SourceTier.A, 0.85, 2.0),
        D(49.205, 9.010, GeoPrecision.STRASSE, "A6 bei Sinsheim · Lkw-Unfall, Sperrung (DEMO)", Symbol.SONSTIGES, StatusRing.GEMELDET, SourceTier.B, 0.6, 9.0),
        D(49.140, 9.215, GeoPrecision.PUNKT, "Heilbronn · Weindorf am Marktplatz (DEMO)", Symbol.EVENT, StatusRing.BESTAETIGT, SourceTier.A, 0.9, 20.0),
        D(48.775, 9.182, GeoPrecision.STADT, "Stuttgart · Warnstreik im Nahverkehr (DEMO)", Symbol.PROTEST, StatusRing.BESTAETIGT, SourceTier.A, 0.85, 14.0),
        D(48.243, 9.026, GeoPrecision.REGION, "Schwäbische Alb · Erdbeben M3,1 (DEMO)", Symbol.ERDBEBEN, StatusRing.BESTAETIGT, SourceTier.A, 0.95, 30.0),
        // Welt
        D(50.60, 36.60, GeoPrecision.REGION, "Region Belgorod · Drohnenangriff, Angaben widersprüchlich (DEMO)", Symbol.EXPLOSION_BESCHUSS, StatusRing.UMSTRITTEN, SourceTier.C, 0.4, 0.2),
        D(48.02, 37.80, GeoPrecision.REGION, "Donezk · Kampfhandlungen gemeldet (DEMO)", Symbol.KAMPFHANDLUNG, StatusRing.GEMELDET, SourceTier.C, 0.35, 3.0),
        D(46.48, 30.72, GeoPrecision.STADT, "Odesa · Beschuss Hafen gemeldet (DEMO)", Symbol.EXPLOSION_BESCHUSS, StatusRing.GEMELDET, SourceTier.B, 0.5, 11.0),
        D(41.69, 44.80, GeoPrecision.STADT, "Tiflis · Proteste vor dem Parlament (DEMO)", Symbol.PROTEST, StatusRing.BESTAETIGT, SourceTier.A, 0.8, 7.0),
        D(-23.10, -70.90, GeoPrecision.REGION, "Nordchile · Erdbeben M6,2 (DEMO)", Symbol.ERDBEBEN, StatusRing.BESTAETIGT, SourceTier.A, 0.95, 26.0),
        D(34.10, -118.20, GeoPrecision.REGION, "Südkalifornien · Waldbrand 4.500 ha (DEMO)", Symbol.BRAND, StatusRing.BESTAETIGT, SourceTier.A, 0.9, 40.0),
        D(15.50, 32.55, GeoPrecision.STADT, "Khartum · Gewalt gegen Zivilisten gemeldet (DEMO)", Symbol.GEWALT_ZIVILISTEN, StatusRing.GEMELDET, SourceTier.B, 0.45, 18.0),
        D(50.85, 4.35, GeoPrecision.STADT, "Brüssel · EU-Kommission: Klimaziel 2040 (DEMO)", Symbol.POLITIK_WAHL, StatusRing.BESTAETIGT, SourceTier.A, 0.9, 50.0),
        D(23.70, 90.40, GeoPrecision.REGION, "Bangladesch · Zyklon-Warnung (DEMO)", Symbol.UNWETTER_KATASTROPHE, StatusRing.GEMELDET, SourceTier.A, 0.7, 1.0),
        D(-6.20, 106.85, GeoPrecision.STADT, "Jakarta · Überschwemmungen (DEMO)", Symbol.UNWETTER_KATASTROPHE, StatusRing.BESTAETIGT, SourceTier.B, 0.75, 75.0),
        D(55.75, 37.62, GeoPrecision.STADT, "Moskau · Haushalt mit höheren Verteidigungsausgaben (DEMO)", Symbol.POLITIK_WAHL, StatusRing.GEMELDET, SourceTier.C, 0.5, 33.0),
    )

    suspend fun seedIfEmpty(db: WorldPulseDb) {
        if (db.uiEvents().count() > 0) return
        val now = System.currentTimeMillis()
        db.uiEvents().insertAll(items.map { d ->
            UiEventEntity(
                time = now - (d.ageH * 3600_000).toLong(), lat = d.lat, lon = d.lon, geoPrecision = d.p, text = d.text,
                symbol = d.s, statusRing = d.r, sourceTier = d.t, confidence = d.c, phase = Phase.EXTRAKTION,
            )
        })
    }

    /** Simulierter Analyse-Lauf: erzeugt alle paar Sekunden ein neues Ereignis (Analyse-Ansicht "Demo-Lauf"). */
    fun randomLive(rnd: Random = Random.Default): UiEventEntity {
        val base = items[rnd.nextInt(items.size)]
        return UiEventEntity(
            time = System.currentTimeMillis(), lat = base.lat + rnd.nextDouble(-2.0, 2.0), lon = base.lon + rnd.nextDouble(-2.0, 2.0),
            geoPrecision = GeoPrecision.REGION, text = base.text.replace("(DEMO)", "(DEMO, live)"), symbol = base.s,
            statusRing = StatusRing.GEMELDET, sourceTier = base.t, confidence = 0.5, phase = Phase.EXTRAKTION,
        )
    }
}
