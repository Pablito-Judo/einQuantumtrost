package de.worldpulse.app.map

import de.worldpulse.data.db.UiEventEntity
import de.worldpulse.data.pins.PinAging
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** UI-Events -> GeoJSON-FeatureCollection für die MapLibre-Pin-Ebenen (Alterung bereits eingerechnet). */
object EventGeoJson {
    fun build(events: List<UiEventEntity>, now: Long): String = buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            events.filter { it.lat != null && it.lon != null }.forEach { e ->
                val look = PinAging.look(now - e.time)
                add(buildJsonObject {
                    put("type", "Feature")
                    putJsonObject("geometry") {
                        put("type", "Point")
                        putJsonArray("coordinates") { add(e.lon!!); add(e.lat!!) }
                    }
                    putJsonObject("properties") {
                        put("id", e.id)
                        put("icon", SymbolIcons.name(e.symbol, e.statusRing))
                        put("color", "#%06X".format(SymbolIcons.color(e.symbol) and 0xFFFFFF))
                        put("alpha", look.alpha)
                        put("stage", look.stage.name)
                        put("pulse", look.pulse)
                    }
                })
            }
        }
    }.toString()
}
