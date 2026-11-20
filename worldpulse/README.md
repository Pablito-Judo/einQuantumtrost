# worldpulse (Haupt-App, ab S1)

Aktuell nur Modul `:data` (S0b): Room-Datenbank `WorldPulseDb` laut SPEC 5.

- Tabellen: source, document, entity, entity_alias, event, claim, dossier, dossier_version, dossier_sentence, sentence_claim, claim_link, resubmission, feedback, run_log, ui_event, embedding.
- Enums als TEXT (Room-Standard); Namen per `EnumStabilityTest` eingefroren.
- `schemas/` wird beim ersten Build befüllt (`schemas/de.worldpulse.data.db.WorldPulseDb/1.json`) -> **einchecken**.
- Migration: `WorldPulseDb.VERSION` erhöhen, AutoMigration oder `Migrations.ALL` ergänzen, Fall in `MigrationTest`.
- Tests: `./gradlew :data:testDebugUnitTest` (JVM), `./gradlew :data:connectedAndroidTest` (S23 per USB).
