# einQuantumtrost – WorldPulse (Arbeitsname)

Private News-Intelligence-App für das Galaxy S23, alles lokal (siehe `docs/SPEC.md`).

| Ordner | Inhalt | Status |
|---|---|---|
| `core/` | Reine Kotlin/JVM-Bibliothek: Extraktions-Prompts (3 Formate), Parser, Claim-Auswerter, Goldstandard (JSON-Schema + 3 Ereignisse + Loader/Auswerter), Duty-Cycling, CSV, Vektoren, 20 Test-Artikel + 10 Embedding-Satzpaare | `./gradlew test` – 29 Tests grün |
| `bench/` | S0 Benchmark-App (Compose, LiteRT-LM 0.19.0), Tests 1–9 aus dem Startprompt, CSV + Teilen, Auswerte-Skript | Code fertig, **noch nicht auf dem S23 gebaut/gemessen** |
| `worldpulse/data` | S0b Room-Schema (16 Tabellen inkl. UI-Events, Lauf-Protokoll), DAOs, Migrationsgerüst, Tests | Code fertig, Instrumented-Tests laufen auf dem S23 |
| `docs/` | SPEC.md (Abschnitt 12 aktualisiert), Startprompt | |

Schnellstart: `bench/README.md`.

```bash
cd core && ./gradlew test                            # JVM-Tests (überall lauffähig)
cd bench && ./gradlew :app:installRelease            # Benchmark-App aufs S23
cd worldpulse && ./gradlew :data:testDebugUnitTest :data:connectedAndroidTest   # Room-Tests (S23 per USB)
```
