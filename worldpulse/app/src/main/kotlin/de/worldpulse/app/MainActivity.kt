package de.worldpulse.app

import android.os.Bundle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.worldpulse.app.analysis.AnalysisScreen
import de.worldpulse.app.map.MapScreen
import de.worldpulse.app.newsroom.NewsroomScreen
import de.worldpulse.app.ui.SymbolImage
import de.worldpulse.app.ui.WpColors
import de.worldpulse.data.db.StatusRing
import de.worldpulse.data.db.Symbol
import de.worldpulse.data.db.UiEventEntity

/** S1-Prototyp: Karte (MapLibre) · Newsroom · Analyse (Canvas). Ein Datenstrom (UI-Events), drei Darstellungen. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as WorldPulseApp
        // CI/Demo: geprüfte Quellen freigeben + sofort abrufen
        if (savedInstanceState == null && intent.getBooleanExtra("approveChecked", false)) app.appScope.launch {
            app.scout.syncRegistry()
            app.db.sources().observeAll().first().filter { app.scout.note(it.id) == null && !it.sanctioned }
                .forEach { app.db.sources().setApproved(it.id, true) }
            de.worldpulse.app.scout.ScoutWorker.runNow(app)
        }
        // CI/Test: Analyse direkt starten (kurzes Zeitbudget)
        if (savedInstanceState == null && intent.getBooleanExtra("startAnalysis", false)) {
            de.worldpulse.app.analysis.AnalysisService.start(this, intent.getLongExtra("budgetMs", 10 * 60_000L))
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = WpColors.accent, background = WpColors.bg, surface = WpColors.panel)) {
                // Test-/Deep-Link-Extras: tab=KARTE|NEWSROOM|ANALYSE, cam=lat,lon,zoom
                val tab = intent.getStringExtra("tab")?.let { n -> Tab.entries.firstOrNull { it.name == n } } ?: Tab.KARTE
                val cam = intent.getStringExtra("cam")?.split(",")?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 3 }
                Root(app, tab, cam)
            }
        }
    }
}

internal enum class Tab(val label: String, val sym: Symbol) {
    KARTE("Karte", Symbol.SONSTIGES), NEWSROOM("Newsroom", Symbol.EVENT), EINGANG("Eingang", Symbol.POLITIK_WAHL),
    QUELLEN("Quellen", Symbol.PROTEST), ANALYSE("Analyse", Symbol.ERDBEBEN)
}

@Composable
private fun Root(app: WorldPulseApp, initialTab: Tab, initialCam: List<Double>?) {
    val since = remember { System.currentTimeMillis() - 7 * 24 * 3600_000L }
    val events by app.db.uiEvents().observeSince(since).collectAsStateWithLifecycle(emptyList())
    val tiles by app.tiles.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(initialTab) }
    var focus by remember { mutableStateOf<UiEventEntity?>(null) }

    val banner = when {
        tiles.error != null -> "Kartendaten: ${tiles.error}"
        tiles.progress.isNotEmpty() -> tiles.progress
        !tiles.any -> "Kartendaten werden geladen …"
        else -> null
    }

    Column(Modifier.fillMaxSize().background(WpColors.bg).systemBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.KARTE -> MapScreen(
                    events = events,
                    worldFile = app.tiles.worldFile.takeIf { tiles.world },
                    regionFile = app.tiles.regionFile.takeIf { tiles.region },
                    focus = focus, onFocusConsumed = { focus = null }, banner = banner, initialCam = initialCam,
                )
                Tab.NEWSROOM -> NewsroomScreen(events) { e -> focus = e; tab = Tab.KARTE }
                Tab.EINGANG -> de.worldpulse.app.scout.InboxScreen(app)
                Tab.QUELLEN -> de.worldpulse.app.scout.SourcesScreen(app)
                Tab.ANALYSE -> AnalysisScreen(events)
            }
        }
        NavigationBar(containerColor = WpColors.panel) {
            Tab.entries.forEach { t ->
                NavigationBarItem(
                    selected = tab == t, onClick = { tab = t },
                    icon = { SymbolImage(t.sym, StatusRing.BESTAETIGT, 24) },
                    label = { Text(t.label, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0x33FFB300)),
                )
            }
        }
    }
}
