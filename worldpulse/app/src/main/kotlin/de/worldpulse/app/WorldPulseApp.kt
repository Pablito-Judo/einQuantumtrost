package de.worldpulse.app

import android.app.Application
import de.worldpulse.app.scout.ScoutRepository
import de.worldpulse.app.scout.ScoutWorker
import de.worldpulse.app.tiles.TileStore
import de.worldpulse.data.db.WorldPulseDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class WorldPulseApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var db: WorldPulseDb private set
    lateinit var tiles: TileStore private set
    lateinit var scout: ScoutRepository private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        MapLibre.getInstance(this)
        db = WorldPulseDb.open(this)
        tiles = TileStore(this)
        scout = ScoutRepository(this, db)
        // S1: Demo-Ereignisse, bis die echte Pipeline (S2–S4) UI-Events schreibt.
        appScope.launch { DemoData.seedIfEmpty(db) }
        appScope.launch { tiles.ensureDownloaded() }
        appScope.launch { scout.syncRegistry() }
        ScoutWorker.schedule(this)
    }

    companion object {
        lateinit var instance: WorldPulseApp private set
    }
}
