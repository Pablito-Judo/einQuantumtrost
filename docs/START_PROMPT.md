# Startprompt für den Coding-Bereich (zusammen mit SPEC.md einfügen)

Du bist mein Entwickler für die private Android-App "WorldPulse" (Arbeitsname) (siehe SPEC.md, Version 2, vollständig lesen und befolgen). Zielgerät: Samsung Galaxy S23 (8 GB RAM, Snapdragon 8 Gen 2, Android 15/16). Alles läuft lokal, 0 €, nativ Kotlin + Jetpack Compose.

## Regeln für diesen Lauf
- Antworte knapp und handlungsorientiert, keine ungefragten Rechts-/Ethik-/Belehrungstexte (SPEC Abschnitt 13).
- Prüfe Abhängigkeiten, Maven-Koordinaten und API-Namen VOR Gebrauch in der aktuellen Doku (https://ai.google.dev/edge/litert-lm/android, Android-Entwicklerdoku). Nichts aus dem Gedächtnis raten. Schätzungen als Schätzung kennzeichnen.
- Du darfst bauen, kompilieren und, wenn ein Gerät per ADB verbunden ist, auf dem S23 installieren und testen. Wenn etwas nicht kompiliert oder nicht läuft, sag es klar und behebe es, statt es zu verschweigen.
- Wende bei jedem Punkt das Prüfraster an (S23-Machbarkeit, Strukturpassung, Vertiefung, Update-Möglichkeiten) und trage das Ergebnis kurz in SPEC.md Abschnitt 12 ein.
- Am Ende: aktualisierten Abschnitt 12 liefern (Ergebnisse, offene Punkte, nächster Schritt).

## Auftrag 1 – S0: Benchmark-App (eigenes Gradle-Projekt `bench`, minimale Compose-UI, ein Knopf je Test, Ergebnisse als CSV in den App-Ordner + Teilen-Menü)
Messen (jeweils mit Zeitstempel, Temperatur, Akku, Thermal-Status):
1. **Laden getrennt von Prefill und Decode** (Modell laden, erstes Token, Tokens/s Decode, Prefill-Tokens/s bei 500/1000/2000 Eingabe-Token).
2. **Backend:** GPU vs. CPU (NPU nur wenn die API es für das S23 anbietet; sonst vermerken).
3. **Modelle:** Gemma 4 E2B (`.litertlm`, Quelle: Hugging Face `litert-community/gemma-4-E2B-it-litert-lm`), E4B (`…E4B…`), jeweils Standard; zusätzlich das QAT-Mobilformat, falls als LiteRT-LM-Datei verfügbar (sonst vermerken). **E4B RAM-Test:** 10 Minuten Dauerbetrieb, melden ob Android die App beendet; RAM-Verlauf loggen.
4. **MTP/spekulatives Dekodieren** an/aus (falls die Kotlin-API es anbietet).
5. **10-Minuten-Dauerlauf** mit Temperaturlog (Akku-Temperatur per BatteryManager, `PowerManager.getCurrentThermalStatus` + Listener) und Durchsatz je Minute; danach **Duty-Cycling** (z. B. 60 s Arbeit / 30 s Pause, Pause ab Status "moderate") und Vergleich.
6. **Extraktionstest mit 20 mitgelieferten Beispielartikeln** (kurze Nachrichtenabsätze, deutsch/englisch/russisch-übersetzt; Beispiele selbst anlegen, Soll-Claims von Hand daneben): Prompt "atomare Claims mit Belegspanne" in **3 Ausgabeformaten**: (a) erzwungenes JSON-Schema, (b) Zeilenformat `Aussage | Wer sagt es | Typ` mit Code-Parser, (c) kurzes freies Nachdenken (Token-Limit) dann Format. Denkmodus aus. Auswerten: Parse-Erfolgsquote, Claim-Recall/Präzision gegen Soll, Zeit je Artikel.
7. **Embedding:** EmbeddingGemma (Version 1) und, falls verfügbar, EmbeddingGemma 2 (Text): Zeit je Satz, RAM, einfacher Qualitätstest (10 Satzpaare "gleiche Meldung / andere Meldung" → Kosinus).
8. **Bildschirm AUS vs. Karten-Ansicht** (einfache dunkle Canvas-Karte mit pulsierenden Punkten) während eines 10-Minuten-LLM-Laufs: Temperatur- und Durchsatzunterschied.
9. **Android-15-Dienstverhalten:** Vordergrunddienst (dataSync) mit `onTimeout`-Behandlung, Protokoll über einen langen Lauf (Test per `adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS <paket>` möglich).
**Ergebnis:** Tabelle der Messwerte + Empfehlung: welches Modell/Backend/Format für Extraktion, Schreiben, Embedding; realistische Artikel/Stunde und Dossier-Abschnitte/Stunde mit Duty-Cycling; Entscheidung E4B ja/nein; Ersatz der Schätzungen in SPEC Abschnitt 6.

## Auftrag 2 – S0b: Datenmodell
- Room-Schema laut SPEC Abschnitt 5 ("Datenmodell") inkl. UI-Events, Migrationsgerüst, Unit-Tests.
- Goldstandard-Format als JSON-Schema + 3 Beispiel-Ereignisse + Loader/Auswerter (Claim-Recall/Präzision, Zahlen-/Namens-Treffer).
- Lauf-Protokoll-Tabelle (Modell, Prompt-Version, Zeit, Temperatur).

## Danach (nur nach meiner Freigabe)
S1 laut SPEC Abschnitt 11 (zoombare flache Karte mit lokalen PMTiles; Dummy-Pins/Symbole; Canvas-Analyse-Ansicht). Dort zuerst klären: lokale PMTiles per `file://` in MapLibre Native Android (aktuelle Version), `pmtiles extract` für Heilbronn (50 km, bis z14) und Welt (bis z8) – Befehle liefern, ich führe sie am PC aus.

## Was ich vorbereite
Android Studio, USB-Debugging am S23, ADB-Verbindung, Hugging-Face-Konto/Token falls Modelle Zugang verlangen; Modelldateien bei Bedarf per `adb push`.
