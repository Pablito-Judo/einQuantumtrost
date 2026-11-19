package de.worldpulse.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = WorldPulseDb.VERSION,
    exportSchema = true,
    entities = [
        SourceEntity::class, DocumentEntity::class, EntityEntity::class, EntityAliasEntity::class,
        EventEntity::class, ClaimEntity::class, DossierEntity::class, DossierVersionEntity::class,
        DossierSentenceEntity::class, SentenceClaimEntity::class, ClaimLinkEntity::class,
        ResubmissionEntity::class, FeedbackEntity::class, RunLogEntity::class, UiEventEntity::class,
        EmbeddingEntity::class,
    ],
    // Einfache Änderungen (neue Spalte/Tabelle): hier AutoMigration(from = 1, to = 2) eintragen.
    autoMigrations = [],
)
abstract class WorldPulseDb : RoomDatabase() {
    abstract fun sources(): SourceDao
    abstract fun documents(): DocumentDao
    abstract fun entities(): EntityDao
    abstract fun claims(): ClaimDao
    abstract fun events(): EventDao
    abstract fun dossiers(): DossierDao
    abstract fun links(): ClaimLinkDao
    abstract fun resubmissions(): ResubmissionDao
    abstract fun feedback(): FeedbackDao
    abstract fun runLog(): RunLogDao
    abstract fun uiEvents(): UiEventDao
    abstract fun embeddings(): EmbeddingDao

    companion object {
        const val VERSION = 1
        const val NAME = "worldpulse.db"

        fun open(ctx: Context): WorldPulseDb =
            Room.databaseBuilder(ctx.applicationContext, WorldPulseDb::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // KEIN fallbackToDestructiveMigration: Datenverlust nie stillschweigend.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

/**
 * Migrationsgerüst. Regeln:
 * 1. VERSION in WorldPulseDb erhöhen, Build erzeugt schemas/<neu>.json -> einchecken.
 * 2. Einfache Fälle: AutoMigration; Umbenennen/Teilen/Datenumbau: manuelle Migration hier.
 * 3. Für jede Migration einen Fall in MigrationTest (androidTest) ergänzen.
 */
object Migrations {
    /** Beispiel-Vorlage (nicht aktiv): Spalte ergänzen. */
    @Suppress("unused")
    private val TEMPLATE_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE claim ADD COLUMN example_col TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf()
}
