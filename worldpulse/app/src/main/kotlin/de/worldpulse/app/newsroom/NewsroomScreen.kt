package de.worldpulse.app.newsroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.worldpulse.app.ui.EventCard
import de.worldpulse.app.ui.WpColors
import de.worldpulse.data.db.UiEventEntity
import de.worldpulse.data.pins.SymbolMapper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Newsroom (SPEC 3.3): Liste über der UI-Events-Tabelle. Live während der Analyse, sonst Rückblick.
 * Tipp -> Karte fliegt hin.
 */
@Composable
fun NewsroomScreen(events: List<UiEventEntity>, onOpen: (UiEventEntity) -> Unit) {
    var filter by remember { mutableStateOf("alle") }
    val shown = when (filter) {
        "konflikt" -> events.filter { SymbolMapper.isConflict(it.symbol) }
        "lokal" -> events.filter { it.lat != null && it.lon != null && near(it.lat!!, it.lon!!) }
        else -> events
    }
    val since = events.minOfOrNull { it.time }?.let {
        DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
    }
    LazyColumn(
        Modifier.fillMaxSize().background(WpColors.bg),
        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Newsroom" + (since?.let { " · seit $it" } ?: ""), color = WpColors.text, fontSize = 20.sp)
            Text("${shown.size} Ereignisse · Demo-Daten bis die Pipeline läuft", color = WpColors.dim, fontSize = 12.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("alle", "lokal", "konflikt").forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
                }
            }
        }
        items(shown, key = { it.id }) { e -> EventCard(e) { onOpen(e) } }
    }
}

/** Heimatradius (Prototyp: 60 km um Heilbronn; später GPS, Standort bleibt lokal). */
private fun near(lat: Double, lon: Double): Boolean {
    val dLat = (lat - 49.142) * 111.0
    val dLon = (lon - 9.219) * 111.0 * Math.cos(Math.toRadians(49.142))
    return dLat * dLat + dLon * dLon < 60.0 * 60.0
}
