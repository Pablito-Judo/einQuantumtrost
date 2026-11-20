package de.worldpulse.app.map

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.worldpulse.app.ui.EventCard
import de.worldpulse.data.db.UiEventEntity
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import kotlin.math.sin

const val SRC_EVENTS = "events"
const val LAYER_ICON = "ev-icon"
const val LAYER_DOT = "ev-dot"
const val LAYER_PULSE = "ev-pulse"
const val LAYER_CLUSTER = "ev-cluster"

/**
 * Zoombare flache Karte (SPEC 3.1/7 Stufe 1): MapLibre, lokale PMTiles, Pins als GeoJSON-Ebene mit Clustering bei Zoom-out,
 * Puls für neue Ereignisse (nur solange es welche gibt, ~12 fps), Antippen -> Kurzkarte.
 */
@Composable
fun MapScreen(
    events: List<UiEventEntity>,
    worldFile: File?,
    regionFile: File?,
    focus: UiEventEntity?,
    onFocusConsumed: () -> Unit,
    banner: String?,
    initialCam: List<Double>? = null,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val iconPx = with(LocalDensity.current) { 34.dp.roundToPx() }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var selected by remember { mutableStateOf<UiEventEntity?>(null) }
    var clickAdded by remember { mutableStateOf(false) }
    val mapView = remember { MapView(ctx).apply { onCreate(Bundle()) } }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); mapView.onStop(); mapView.onDestroy() }
    }

    // Stil (neu) setzen, sobald Kartendaten da sind
    LaunchedEffect(worldFile?.path, regionFile?.path) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            if (m.cameraPosition.zoom < 0.5) {
                val c = initialCam ?: listOf(49.14, 9.22, 3.0)
                m.cameraPosition = CameraPosition.Builder().target(LatLng(c[0], c[1])).zoom(c[2]).build()
            }
            m.setStyle(Style.Builder().fromJson(MapStyle.json(worldFile, regionFile))) { s ->
                SymbolIcons.all(iconPx).forEach { (n, b) -> s.addImage(n, b) }
                addEventLayers(s)
                style = s
            }
            if (!clickAdded) m.addOnMapClickListener { ll ->
                val pt = m.projection.toScreenLocation(ll)
                val f = m.queryRenderedFeatures(pt, LAYER_ICON, LAYER_DOT, LAYER_CLUSTER).firstOrNull()
                    ?: return@addOnMapClickListener false.also { selected = null }
                if (f.hasProperty("point_count")) {
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, m.cameraPosition.zoom + 2), 600)
                } else {
                    val id = f.getNumberProperty("id")?.toLong()
                    selected = eventsRef.firstOrNull { it.id == id }
                }
                true
            }
            clickAdded = true
        }
    }

    eventsRef = events
    // Pins aktualisieren (bei neuen Events und minütlich wegen Alterung)
    LaunchedEffect(style, events) {
        val s = style ?: return@LaunchedEffect
        while (true) {
            (s.getSource(SRC_EVENTS) as? GeoJsonSource)?.setGeoJson(EventGeoJson.build(events, System.currentTimeMillis()))
            delay(60_000)
        }
    }
    // Puls-Animation nur, wenn es neue Ereignisse gibt (spart GPU/Akku)
    LaunchedEffect(style, events) {
        val s = style ?: return@LaunchedEffect
        val layer = s.getLayer(LAYER_PULSE) as? CircleLayer ?: return@LaunchedEffect
        var t = 0f
        while (events.any { System.currentTimeMillis() - it.time < de.worldpulse.data.pins.PinAging.NEW_MS }) {
            val ph = (sin(t) + 1f) / 2f
            layer.setProperties(PropertyFactory.circleRadius(10f + 18f * ph), PropertyFactory.circleOpacity(0.45f * (1f - ph)))
            t += 0.35f
            delay(83)
        }
        layer.setProperties(PropertyFactory.circleOpacity(0f))
    }
    // Fokus aus dem Newsroom: hinfliegen + Karte öffnen
    LaunchedEffect(focus, map) {
        val m = map ?: return@LaunchedEffect
        val e = focus ?: return@LaunchedEffect
        if (e.lat != null && e.lon != null) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(e.lat!!, e.lon!!), zoomFor(e)), 1200)
        }
        selected = e
        onFocusConsumed()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        banner?.let {
            Text(it, color = Color(0xFFFFE082), fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                .background(Color(0xCC0B1016), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        selected?.let { e ->
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) { EventCard(e, onClick = null) }
        }
    }
}

private var eventsRef: List<UiEventEntity> = emptyList()

private fun zoomFor(e: UiEventEntity) = when (e.geoPrecision) {
    de.worldpulse.core.gold.GeoPrecision.LAND -> 4.5
    de.worldpulse.core.gold.GeoPrecision.REGION -> 7.0
    de.worldpulse.core.gold.GeoPrecision.STADT -> 11.0
    else -> 13.5
}

private fun addEventLayers(s: Style) {
    s.addSource(GeoJsonSource(SRC_EVENTS, "{\"type\":\"FeatureCollection\",\"features\":[]}",
        GeoJsonOptions().withCluster(true).withClusterMaxZoom(4).withClusterRadius(36)))
    val notCluster = Expression.not(Expression.has("point_count"))
    s.addLayer(CircleLayer(LAYER_CLUSTER, SRC_EVENTS).withFilter(Expression.has("point_count")).withProperties(
        PropertyFactory.circleColor("#FFB300"),
        PropertyFactory.circleOpacity(0.55f),
        PropertyFactory.circleStrokeColor("#FFE082"),
        PropertyFactory.circleStrokeWidth(1.2f),
        PropertyFactory.circleRadius(Expression.step(Expression.get("point_count"), Expression.literal(12), Expression.stop(5, 16), Expression.stop(15, 22))),
    ))
    s.addLayer(SymbolLayer("ev-cluster-count", SRC_EVENTS).withFilter(Expression.has("point_count")).withProperties(
        PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
        PropertyFactory.textFont(arrayOf("Noto Sans Medium")),
        PropertyFactory.textSize(12f),
        PropertyFactory.textColor("#0B1016"),
        PropertyFactory.textAllowOverlap(true),
    ))
    s.addLayer(CircleLayer(LAYER_PULSE, SRC_EVENTS).withFilter(Expression.all(notCluster, Expression.eq(Expression.get("pulse"), true))).withProperties(
        PropertyFactory.circleColor(Expression.get("color")),
        PropertyFactory.circleRadius(14f),
        PropertyFactory.circleOpacity(0f),
    ))
    s.addLayer(CircleLayer(LAYER_DOT, SRC_EVENTS).withFilter(Expression.all(notCluster, Expression.eq(Expression.get("stage"), "PUNKT"))).withProperties(
        PropertyFactory.circleColor(Expression.get("color")),
        PropertyFactory.circleRadius(3.5f),
        PropertyFactory.circleOpacity(0.55f),
    ))
    s.addLayer(SymbolLayer(LAYER_ICON, SRC_EVENTS).withFilter(Expression.all(notCluster, Expression.neq(Expression.get("stage"), "PUNKT"))).withProperties(
        PropertyFactory.iconImage(Expression.get("icon")),
        PropertyFactory.iconOpacity(Expression.get("alpha")),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.iconSize(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(2, 0.7f), Expression.stop(10, 1.0f))),
    ))
}
