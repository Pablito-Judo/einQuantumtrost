package de.worldpulse.app.map

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Dunkler Offline-Kartenstil für die Protomaps-Basemap (Schema: earth, water, landuse, roads, boundaries,
 * buildings, places). Zwei PMTiles-Quellen: "world" (bis z8, überzoomt darunter) und "region" (Heimat bis z14).
 * Schriften als Asset (Noto Sans, OFL) -> Beschriftung ohne Netz. Pins kommen zur Laufzeit als GeoJSON dazu.
 */
object MapStyle {
    const val BG = "#0B1016"
    private const val EARTH = "#131B23"
    private const val WATER = "#08131F"
    private const val GREEN = "#14201A"
    private const val BOUNDARY = "#3B4B5C"
    private const val ROAD_MAJOR = "#2E3842"
    private const val ROAD_HWY = "#4A3B22"
    private const val LABEL = "#AFBCC9"
    private const val LABEL_DIM = "#6F7E8C"

    private const val REGION_MINZOOM = 8.5

    fun json(world: File?, region: File?): String = buildJsonObject {
        put("version", 8)
        put("name", "WorldPulse dunkel")
        put("glyphs", "asset://fonts/{fontstack}/{range}.pbf")
        putJsonObject("sources") {
            world?.let { put("world", vectorSource(it)) }
            region?.let { put("region", vectorSource(it)) }
        }
        putJsonArray("layers") {
            add(layer("background", "background", null) { put("paint", obj("background-color" to BG)) })
            world?.let { baseLayers("world", null, labels = true, maxLabelZoom = if (region != null) REGION_MINZOOM else 24.0).forEach(::add) }
            region?.let { baseLayers("region", REGION_MINZOOM, labels = true, maxLabelZoom = 24.0).forEach(::add) }
        }
    }.toString()

    private fun vectorSource(f: File) = buildJsonObject {
        put("type", "vector")
        // Lokale Datei: pmtiles://file://<absoluter Pfad>  (im Emulator-Test geprüft, siehe MapRenderTest)
        put("url", "pmtiles://file://${f.absolutePath}")
        put("attribution", "© OpenStreetMap-Mitwirkende, Protomaps")
    }

    private fun baseLayers(src: String, minzoom: Double?, labels: Boolean, maxLabelZoom: Double): List<JsonObject> {
        val L = mutableListOf<JsonObject>()
        fun id(s: String) = "$src-$s"
        L += layer(id("earth"), "fill", src, "earth", minzoom) { put("paint", obj("fill-color" to EARTH)) }
        L += layer(id("green"), "fill", src, "landuse", minzoom) {
            put("filter", inList("kind", "park", "forest", "wood", "nature_reserve", "grass", "meadow"))
            put("paint", obj("fill-color" to GREEN))
        }
        L += layer(id("water"), "fill", src, "water", minzoom) { put("paint", obj("fill-color" to WATER)) }
        L += layer(id("boundaries"), "line", src, "boundaries", minzoom) {
            put("filter", arr("<=", "kind_detail", 4))
            put("paint", buildJsonObject {
                put("line-color", BOUNDARY)
                put("line-width", interp(2 to 0.6, 8 to 1.2, 14 to 1.6))
                put("line-dasharray", buildJsonArray { add(JsonPrimitive(3)); add(JsonPrimitive(2)) })
            })
        }
        L += layer(id("roads-minor"), "line", src, "roads", maxOf(minzoom ?: 0.0, 11.0)) {
            put("filter", arr("==", "kind", "minor_road"))
            put("paint", buildJsonObject { put("line-color", "#222B33"); put("line-width", interp(11 to 0.5, 16 to 3.0)) })
        }
        L += layer(id("roads-major"), "line", src, "roads", maxOf(minzoom ?: 0.0, 7.0)) {
            put("filter", arr("==", "kind", "major_road"))
            put("paint", buildJsonObject { put("line-color", ROAD_MAJOR); put("line-width", interp(7 to 0.4, 16 to 5.0)) })
        }
        L += layer(id("roads-highway"), "line", src, "roads", maxOf(minzoom ?: 0.0, 5.0)) {
            put("filter", arr("==", "kind", "highway"))
            put("paint", buildJsonObject { put("line-color", ROAD_HWY); put("line-width", interp(5 to 0.5, 16 to 7.0)) })
        }
        L += layer(id("buildings"), "fill", src, "buildings", 14.0) {
            put("paint", buildJsonObject { put("fill-color", "#1A222B"); put("fill-opacity", 0.8) })
        }
        if (labels) {
            L += label(id("country"), src, "country", minzoom, maxLabelZoom, size = interp(2 to 10.0, 6 to 14.0), color = LABEL_DIM, bold = true)
            L += label(id("region"), src, "region", maxOf(minzoom ?: 0.0, 5.0), maxLabelZoom, size = interp(5 to 10.0, 9 to 12.0), color = LABEL_DIM, bold = false)
            L += label(id("locality"), src, "locality", minzoom, maxLabelZoom, size = interp(4 to 10.0, 14 to 15.0), color = LABEL, bold = false, byMinZoom = true)
        }
        return L
    }

    private fun label(
        id: String, src: String, kind: String, minzoom: Double?, maxzoom: Double, size: JsonElement, color: String, bold: Boolean,
        byMinZoom: Boolean = false,
    ) = layer(id, "symbol", src, "places", minzoom) {
        put("maxzoom", maxzoom)
        put("filter", if (byMinZoom) buildJsonArray {
            add(JsonPrimitive("all"))
            add(arr("==", "kind", kind))
            // Protomaps setzt min_zoom je Ort: nur zeigen, wenn die aktuelle Zoomstufe reicht
            add(buildJsonArray { add(JsonPrimitive("<=")); add(buildJsonArray { add(JsonPrimitive("get")); add(JsonPrimitive("min_zoom")) }); add(buildJsonArray { add(JsonPrimitive("zoom")) }) })
        } else arr("==", "kind", kind))
        put("layout", buildJsonObject {
            // Deutscher Name bevorzugt, sonst Originalname
            put("text-field", buildJsonArray {
                add(JsonPrimitive("coalesce"))
                add(buildJsonArray { add(JsonPrimitive("get")); add(JsonPrimitive("name:de")) })
                add(buildJsonArray { add(JsonPrimitive("get")); add(JsonPrimitive("name")) })
            })
            put("text-font", buildJsonArray { add(JsonPrimitive(if (bold) "Noto Sans Medium" else "Noto Sans Regular")) })
            put("text-size", size)
            put("text-max-width", 8)
        })
        put("paint", buildJsonObject {
            put("text-color", color)
            put("text-halo-color", BG)
            put("text-halo-width", 1.2)
        })
    }

    private fun layer(
        id: String, type: String, src: String?, sourceLayer: String? = null, minzoom: Double? = null,
        body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ) = buildJsonObject {
        put("id", id); put("type", type)
        src?.let { put("source", it) }
        sourceLayer?.let { put("source-layer", it) }
        minzoom?.let { put("minzoom", it) }
        body()
    }

    private fun obj(vararg kv: Pair<String, Any>) = buildJsonObject {
        kv.forEach { (k, v) -> when (v) { is String -> put(k, v); is Number -> put(k, v); else -> put(k, v.toString()) } }
    }

    /** ["op", ["get", key], v1, v2 …] */
    private fun arr(op: String, key: String, vararg values: Any): JsonArray = buildJsonArray {
        add(JsonPrimitive(op))
        add(buildJsonArray { add(JsonPrimitive("get")); add(JsonPrimitive(key)) })
        values.forEach { v -> add(if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString())) }
    }

    /** ["match", ["get", key], [v…], true, false] – Ausdrucks-Syntax für "ist einer von". */
    private fun inList(key: String, vararg values: String): JsonArray = buildJsonArray {
        add(JsonPrimitive("match"))
        add(buildJsonArray { add(JsonPrimitive("get")); add(JsonPrimitive(key)) })
        add(buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
        add(JsonPrimitive(true)); add(JsonPrimitive(false))
    }

    /** ["interpolate", ["linear"], ["zoom"], z1, v1, …] */
    private fun interp(vararg stops: Pair<Int, Double>): JsonArray = buildJsonArray {
        add(JsonPrimitive("interpolate"))
        add(buildJsonArray { add(JsonPrimitive("linear")) })
        add(buildJsonArray { add(JsonPrimitive("zoom")) })
        stops.forEach { (z, v) -> add(JsonPrimitive(z)); add(JsonPrimitive(v)) }
    }
}
