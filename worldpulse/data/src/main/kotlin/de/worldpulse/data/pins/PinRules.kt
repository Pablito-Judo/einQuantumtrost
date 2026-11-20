package de.worldpulse.data.pins

import de.worldpulse.core.gold.SourceTier
import de.worldpulse.data.db.StatusRing
import de.worldpulse.data.db.Symbol
import kotlin.math.roundToInt

/*
 * Reine Regeln für Pins/Symbole (SPEC 3.2) – Android-frei, per JVM-Test geprüft.
 * Werden von Pipeline (UI-Events schreiben) und allen drei Darstellungen (Newsroom, Canvas, MapLibre) genutzt.
 */

/** Zuordnung Ereignistyp -> neutrales Symbol. Quellen: GDELT-CAMEO-Codes, ACLED-Ereignistypen, eigene Kategorien. */
object SymbolMapper {
    /**
     * CAMEO-Ereigniscode (z. B. "193", "1823", "14") -> Symbol.
     * 14x Protest, 18x Gewalt/Angriff, 19x Kampf, 20x Massengewalt. Feinere Unterscheidung über Untercodes:
     * Bombenanschlag (183x), Artillerie/Panzer (194), Luftangriff (195) -> Explosion/Beschuss.
     * UNGEPRÜFT gegen die offizielle Liste (gdeltproject.org hier nicht erreichbar) – CI-Schritt
     * "Referenzdaten" druckt CAMEO 14/18/19/20 ins Log; danach abgleichen.
     */
    fun fromCameo(code: String?): Symbol? {
        val c = code?.trim()?.takeIf { it.length >= 2 && it.all(Char::isDigit) } ?: return null
        return when {
            c.startsWith("183") -> Symbol.EXPLOSION_BESCHUSS
            c.startsWith("194") || c.startsWith("195") -> Symbol.EXPLOSION_BESCHUSS
            c.startsWith("14") -> Symbol.PROTEST
            c.startsWith("18") -> Symbol.GEWALT_ZIVILISTEN
            c.startsWith("19") -> Symbol.KAMPFHANDLUNG
            c.startsWith("20") -> Symbol.GEWALT_ZIVILISTEN
            else -> null
        }
    }

    /** ACLED event_type -> Symbol (6 Typen laut ACLED-Codebook, UNGEPRÜFT – acleddata.com hier nicht erreichbar). */
    fun fromAcled(eventType: String?): Symbol? = when (eventType?.trim()?.lowercase()) {
        "battles" -> Symbol.KAMPFHANDLUNG
        "explosions/remote violence" -> Symbol.EXPLOSION_BESCHUSS
        "violence against civilians" -> Symbol.GEWALT_ZIVILISTEN
        "protests", "riots" -> Symbol.PROTEST
        "strategic developments" -> Symbol.SONSTIGES
        else -> null
    }

    /** Eigene Kategorien (Goldstandard/Cluster) + Naturgefahren-Ebenen (USGS, FIRMS, GDACS, DWD). */
    fun fromCategory(category: String?): Symbol = when (category?.trim()?.lowercase()) {
        "konflikt", "kampf" -> Symbol.KAMPFHANDLUNG
        "explosion", "beschuss" -> Symbol.EXPLOSION_BESCHUSS
        "gewalt" -> Symbol.GEWALT_ZIVILISTEN
        "protest", "unruhen" -> Symbol.PROTEST
        "erdbeben", "earthquake" -> Symbol.ERDBEBEN
        "brand", "feuer", "wildfire", "fire" -> Symbol.BRAND
        "unwetter", "katastrophe", "flood", "storm", "hochwasser" -> Symbol.UNWETTER_KATASTROPHE
        "politik", "wahl" -> Symbol.POLITIK_WAHL
        "event", "lokal", "fest" -> Symbol.EVENT
        else -> Symbol.SONSTIGES
    }

    /** Konfliktsymbole: nie ohne Kennzeichnung "gemeldet/unbestätigt" anzeigen, solange nicht bestätigt. */
    fun isConflict(s: Symbol) = s == Symbol.KAMPFHANDLUNG || s == Symbol.EXPLOSION_BESCHUSS || s == Symbol.GEWALT_ZIVILISTEN
}

/** Eingang für die Status-Ring-Regel (aus Claims/Quellen/Verknüpfungen eines Ereignisses berechnet). */
data class EvidenceSummary(
    /** Unabhängige Quellen (Echo-Gruppen einfach gezählt) je Stufe. */
    val independentA: Int,
    val independentB: Int,
    val independentC: Int,
    /** Mindestens eine Primärquelle (Behörde/Messnetz selbst, z. B. USGS zum Erdbeben). */
    val hasPrimarySource: Boolean,
    /** Mindestens eine Widerspruchs-Verknüpfung zwischen Claims verschiedener Parteien. */
    val hasContradiction: Boolean,
)

object StatusRule {
    /**
     * SPEC 3.2/10: durchgezogen = bestätigt (≥ 2 unabhängige A/B-Quellen ODER Primärquelle);
     * halb = umstritten (Widerspruch); sonst gestrichelt = gemeldet. Widerspruch schlägt Bestätigung.
     */
    fun ring(e: EvidenceSummary): StatusRing = when {
        e.hasContradiction -> StatusRing.UMSTRITTEN
        e.hasPrimarySource || e.independentA + e.independentB >= 2 -> StatusRing.BESTAETIGT
        else -> StatusRing.GEMELDET
    }

    /** Beste Quellenstufe für die Anzeige. */
    fun bestTier(e: EvidenceSummary): SourceTier = when {
        e.independentA > 0 -> SourceTier.A
        e.independentB > 0 -> SourceTier.B
        else -> SourceTier.C
    }
}

/** Darstellungsstufe eines Pins nach Alter (SPEC 3.2: hell aufleuchten → Symbol → nach ~24 h blass → Punkt). */
enum class PinStage { NEU, AKTIV, BLASS, PUNKT }

data class PinLook(val stage: PinStage, val alpha: Float, val pulse: Boolean)

object PinAging {
    const val NEW_MS = 15 * 60_000L
    const val FADE_MS = 24 * 3600_000L
    const val DOT_MS = 72 * 3600_000L

    fun look(ageMs: Long): PinLook {
        val a = ageMs.coerceAtLeast(0)
        return when {
            a < NEW_MS -> PinLook(PinStage.NEU, 1f, pulse = true)
            a < FADE_MS -> PinLook(PinStage.AKTIV, 1f - 0.6f * (a - NEW_MS).toFloat() / (FADE_MS - NEW_MS), pulse = false)
            a < DOT_MS -> PinLook(PinStage.BLASS, 0.35f, pulse = false)
            else -> PinLook(PinStage.PUNKT, 0.25f, pulse = false)
        }
    }

    /** Kurzkarte: "vermutlich vor ca. X h (Annahme)" – bewusst vage, weil Ereigniszeit oft geschätzt ist. */
    fun agoText(ageMs: Long, estimated: Boolean = true): String {
        val min = (ageMs / 60_000.0).roundToInt().coerceAtLeast(0)
        val core = when {
            min < 2 -> "gerade eben"
            min < 60 -> "vor ca. $min min"
            min < 48 * 60 -> "vor ca. ${(min / 60.0).roundToInt()} h"
            else -> "vor ca. ${(min / 1440.0).roundToInt()} Tagen"
        }
        return if (estimated) "vermutlich $core (Annahme)" else core
    }

    fun ringText(r: StatusRing) = when (r) {
        StatusRing.BESTAETIGT -> "bestätigt"
        StatusRing.GEMELDET -> "gemeldet · unbestätigt"
        StatusRing.UMSTRITTEN -> "umstritten"
    }
}
