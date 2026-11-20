package de.worldpulse.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.worldpulse.app.map.SymbolIcons
import de.worldpulse.data.db.StatusRing
import de.worldpulse.data.db.Symbol
import de.worldpulse.data.db.UiEventEntity
import de.worldpulse.data.pins.PinAging
import de.worldpulse.data.pins.SymbolMapper

object WpColors {
    val bg = Color(0xFF0B1016)
    val panel = Color(0xFF121A22)
    val text = Color(0xFFD5DEE7)
    val dim = Color(0xFF7D8B99)
    val accent = Color(0xFFFFB300)
    val warn = Color(0xFFE5534B)
}

/** Symbol als Compose-Bild (gleiche Grafik wie auf der Karte). */
@Composable
fun SymbolImage(s: Symbol, r: StatusRing, sizeDp: Int = 30) {
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
    val bmp = remember(s, r, px) { SymbolIcons.draw(s, r, px).asImageBitmap() }
    Image(bmp, contentDescription = "${s.name} ${r.name}", modifier = Modifier.size(sizeDp.dp))
}

/**
 * Kurzkarte (SPEC 3.2): Ort · Ereignis · "vermutlich vor ca. X h (Annahme)" · Quellenstufe · Vertrauen.
 * Unbestätigte Konfliktsymbole immer mit sichtbarer Kennzeichnung.
 */
@Composable
fun EventCard(e: UiEventEntity, onClick: (() -> Unit)?) {
    val now = System.currentTimeMillis()
    Row(
        Modifier.fillMaxWidth().background(WpColors.panel, RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolImage(e.symbol, e.statusRing, 36)
        Column(Modifier.weight(1f)) {
            Text(e.text, color = WpColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(PinAging.agoText(now - e.time), color = WpColors.dim, fontSize = 12.sp)
            val conf = e.confidence?.let { " · Vertrauen ${(it * 100).toInt()} %" } ?: ""
            val ringCol = if (e.statusRing != StatusRing.BESTAETIGT && SymbolMapper.isConflict(e.symbol)) WpColors.warn else WpColors.dim
            Text("${PinAging.ringText(e.statusRing)} · Quellenstufe ${e.sourceTier}$conf", color = ringCol, fontSize = 12.sp)
        }
    }
}
