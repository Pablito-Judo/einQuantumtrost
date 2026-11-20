# WP Bench (S0) – Benchmark-App für das Galaxy S23

Eigenes Gradle-Projekt. Nutzt `../core` (reine Kotlin-Logik: Prompts, Parser, Auswerter, Testdaten) per `includeBuild`.

## 1. Bauen & installieren (am PC)
```bash
cd bench
./gradlew :app:installRelease        # Release-Build (debug-signiert); Debug ist für LLM-Messungen zu langsam
# oder in Android Studio: Ordner bench/ öffnen -> Run
```
Ungeprüfte Versionen (Google Maven war in meiner Build-Umgebung gesperrt): `gradle/libs.versions.toml`
(AGP 8.13.0, Compose-BOM 2025.10.00, AndroidX). Wenn Android Studio meckert: dort auf die vorgeschlagene Version anheben.
LiteRT-LM `0.19.0` (API gegen den Git-Tag v0.19.0 geprüft und kompiliert).

## 2. Modelle aufs Handy
Dateien von Hugging Face am PC laden (ggf. Lizenz auf HF akzeptieren, `hf auth login`):
```bash
pip install -U huggingface_hub
# E4B: Dateiname laut LiteRT-LM-README bestätigt
hf download litert-community/gemma-4-E4B-it-litert-lm gemma-4-E4B-it.litertlm --local-dir models
# E2B: Dateiname analog ABGELEITET – bei Fehler Dateiliste auf der HF-Seite prüfen
hf download litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm --local-dir models
# QAT-Mobilformat / EmbeddingGemma (1 und 2): auf huggingface.co/litert-community nach "qat" bzw. "embeddinggemma"
# suchen und die .litertlm-Datei laden. Embedding-Dateien müssen "embed" im Namen haben (App sortiert danach).
```
Auf das S23 (App einmal starten, damit der Ordner existiert):
```bash
adb push models/*.litertlm /sdcard/Android/data/de.worldpulse.bench/files/models/
```

## 3. Testablauf (Empfehlung: Ladekabel dran, Samsung-Akkuschutz an, keine Hülle, Raumtemperatur notieren)
| # | Knopf | Einstellung | Dauer (Schätzung) |
|---|---|---|---|
| 0 | Modell-Info | alle Dateien wählen | < 1 min |
| 1 | Laden/Prefill/Decode | E2B, GPU | 2–4 min |
| 2 | Backend-Matrix | E2B (NPU-Fehler wird protokolliert) | 10–20 min |
| 3 | Modell-Matrix | E2B, E4B, QAT | 15–30 min |
| 3b | RAM-Test | **nur E4B** wählen, GPU | 10 min |
| 4 | MTP an/aus | E2B oder E4B, GPU | 5–10 min |
| 5 | Dauerlauf + Duty | E2B, GPU | 20 min + Abkühlen |
| 6 | Extraktion | E2B (danach E4B) | 30–60 min je Modell |
| 7 | Embedding | Embedding-Dateien wählen | 2–5 min |
| 8a | Karten-Ansicht | E2B, GPU, Bildschirm an lassen | 10 min |
| 8b | Bildschirm AUS | E2B, GPU, nach Countdown Power-Taste | 10 min |
| 9 | Dienst-Langlauf | App danach in den Hintergrund | 8 h (über Nacht) |

Zwischen 5, 8a, 8b das Gerät abkühlen lassen (App wartet bei Matrix-Tests automatisch auf Thermal NONE).

### Test 9: Android-15-Dienstlimit
Normales Verhalten: dataSync-Dienst bekommt nach 6 h/24 h im Hintergrund `onTimeout` -> App protokolliert und stoppt.
Schnelltest mit verkürzten Limits:
```bash
adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS de.worldpulse.bench
# Lauf starten, App in den Hintergrund, abwarten; danach zurücksetzen:
adb shell am compat reset FGS_INTRODUCE_TIME_LIMITS de.worldpulse.bench
```
Wurde die App von Android beendet (RAM, Dienst), schreibt sie beim nächsten Start `killed_run` + `exit_info` (ApplicationExitInfo) in die CSV.

## 4. Ergebnisse
- CSV: `/sdcard/Android/data/de.worldpulse.bench/files/results/bench_JJJJMMTT.csv` (Knopf "CSV teilen" oder `adb pull`).
- Rohausgaben der Extraktion: `results/extraction_<lauf>.jsonl` (zur Handprüfung).
- Live-Log: `adb logcat -s WPBench`
- Auswertung am PC:
```bash
adb pull /sdcard/Android/data/de.worldpulse.bench/files/results/ .
python3 bench/tools/summarize.py results/bench_*.csv > s0_ergebnis.md
```
`s0_ergebnis.md` enthält alle Tabellen + Hochrechnung (Artikel/h, Dossier-Abschnitte/h mit Duty-Cycling) für SPEC Abschnitt 6.

## CSV-Format (lang, eine Messgröße je Zeile)
`iso_time, epoch_ms, run, test, model, backend, variant, metric, value, unit, battery_temp_c, battery_pct, charging, thermal, thermal_headroom, pss_mb, avail_mem_mb, low_memory, note`

Wichtige Metriken: `load_ms`, `ttft_s`, `prefill_tps`, `decode_tps` (aus LiteRT-LM `BenchmarkInfo`), `first_chunk_ms`/`wall_ms` (Wanduhr), `rss_mb`, `tokens_per_minute`, `parse_rate`, `micro_recall`, `micro_precision`, `span_rate`, `cosine`.
