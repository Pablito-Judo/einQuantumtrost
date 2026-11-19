package de.worldpulse.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.worldpulse.data.db.Migrations
import de.worldpulse.data.db.WorldPulseDb
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrationsgerüst: prüft, dass das exportierte Schema (schemas/…/1.json) erzeugt und geöffnet werden kann
 * und dass alle Migrationen bis zur aktuellen Version durchlaufen. Neue Version -> hier einen Fall ergänzen.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WorldPulseDb::class.java)

    @Test fun create_v1_and_open_with_all_migrations() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO source (id, name, url, kind, tier, state_media, sanctioned, approved, active, fetch_interval_min, created_at) " +
                "VALUES ('dpa', 'dpa', NULL, 'RSS', 'A', 0, 0, 1, 1, 60, 0)")
        }
        // Validiert gegen das aktuelle Schema; schlägt fehl, wenn eine Migration fehlt.
        helper.runMigrationsAndValidate(dbName, WorldPulseDb.VERSION, true, *Migrations.ALL)
        Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, WorldPulseDb::class.java, dbName)
            .addMigrations(*Migrations.ALL).build().apply { openHelper.writableDatabase; close() }
    }
}
