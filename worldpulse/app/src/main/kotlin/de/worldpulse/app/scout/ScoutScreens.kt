package de.worldpulse.app.scout

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import de.worldpulse.app.WorldPulseApp
import de.worldpulse.app.ui.WpColors
import de.worldpulse.data.db.DocumentEntity
import de.worldpulse.data.db.SourceEntity
import de.worldpulse.data.pins.PinAging
import kotlinx.coroutines.launch

/** Quellenregister (SPEC 8): Kandidaten sichtbar, Freigabe per Schalter durch den Nutzer. */
@Composable
fun SourcesScreen(app: WorldPulseApp) {
    val sources by app.db.sources().observeAll().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    LazyColumn(
        Modifier.fillMaxSize().background(WpColors.bg), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Quellen", color = WpColors.text, fontSize = 20.sp)
            Text("${sources.count { it.approved }} von ${sources.size} freigegeben · Stufe A Behörde/Agentur · B geprüfte Medien · C Social/Partei",
                color = WpColors.dim, fontSize = 12.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch { sources.filter { app.scout.note(it.id) == null && !it.sanctioned }.forEach { app.db.sources().setApproved(it.id, true) } }
                }) { Text("Geprüfte freigeben") }
                OutlinedButton(onClick = { scope.launch { sources.forEach { app.db.sources().setApproved(it.id, false) } } }) { Text("Alle aus") }
            }
        }
        items(sources, key = { it.id }) { s -> SourceRow(s, app.scout.note(s.id)) { on -> scope.launch { app.db.sources().setApproved(s.id, on) } } }
    }
}

@Composable
private fun SourceRow(s: SourceEntity, note: String?, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(WpColors.panel, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(s.tier.name, color = when (s.tier.name) { "A" -> Color(0xFF3FB950); "B" -> Color(0xFF4FA3E0); else -> Color(0xFFF0883E) },
            fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Column(Modifier.weight(1f)) {
            Text(s.name, color = WpColors.text, fontSize = 14.sp)
            val flags = listOfNotNull(s.kind.name, s.countryCode, if (s.stateMedia) "Staatsmedium" else null, if (s.sanctioned) "sanktioniert" else null,
                "alle ${s.fetchIntervalMin} min").joinToString(" · ")
            Text(flags, color = WpColors.dim, fontSize = 11.sp)
            note?.let { Text(it, color = WpColors.accent, fontSize = 11.sp) }
        }
        Switch(checked = s.approved, onCheckedChange = onToggle)
    }
}

/** Eingang: gesammelte, bereinigte Dokumente (vor Extraktion/Clustering). Tipp öffnet die Originalseite. */
@Composable
fun InboxScreen(app: WorldPulseApp) {
    val ctx = LocalContext.current
    val docs by app.db.documents().observeRecent(300).collectAsStateWithLifecycle(emptyList())
    val total by app.db.documents().observeCount().collectAsStateWithLifecycle(0)
    val work by ScoutWorker.observeNow(ctx).collectAsStateWithLifecycle(emptyList())
    val running = work.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    val names = app.db.sources().observeAll().collectAsStateWithLifecycle(emptyList()).value.associate { it.id to it.name }
    LazyColumn(
        Modifier.fillMaxSize().background(WpColors.bg), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text("Eingang", color = WpColors.text, fontSize = 20.sp)
            Text("$total Dokumente · Abruf alle 30 min im WLAN · nur freigegebene Quellen", color = WpColors.dim, fontSize = 12.sp)
            Button(onClick = { ScoutWorker.runNow(ctx) }, enabled = !running, modifier = Modifier.padding(top = 6.dp)) {
                Text(if (running) "Abruf läuft …" else "Jetzt abrufen")
            }
            if (names.isNotEmpty() && docs.isEmpty()) Text("Noch leer: unter „Quellen“ freigeben, dann „Jetzt abrufen“.", color = WpColors.accent, fontSize = 12.sp)
        }
        items(docs, key = { it.id }) { d -> DocRow(d, names[d.sourceId] ?: d.sourceId) {
            if (d.url.startsWith("https://")) ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url)))
        } }
    }
}

@Composable
private fun DocRow(d: DocumentEntity, source: String, onClick: () -> Unit) {
    val age = System.currentTimeMillis() - (d.publishedAt ?: d.fetchedAt)
    Column(Modifier.fillMaxWidth().background(WpColors.panel, RoundedCornerShape(10.dp)).clickable { onClick() }.padding(10.dp)) {
        Text(d.title ?: d.url, color = WpColors.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("$source · ${PinAging.agoText(age, estimated = false)} · ${d.language ?: "?"} · ${d.text?.length ?: 0} Zeichen", color = WpColors.dim, fontSize = 11.sp)
    }
}
