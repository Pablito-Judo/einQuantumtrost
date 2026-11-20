## S0-Messwerte (S23)

### 1–3 Laden / Prefill / Decode je Modell × Backend
| Modell | Backend | MTP | Laden s | TTFT kalt s | Prefill tok/s @500 | @1000 | @2000 | TTFT @1000 s | Decode tok/s | RSS max MB | Fehler |
|---|---|---|---|---|---|---|---|---|---|---|---|
| gemma-4-E2B-it-gpu.litertlm | CPU | DEFAULT | – | – | – | – | – | – | – | – | Failed to create engine: NOT_FOUND: ERROR: [third_party/odml |
| gemma-4-E2B-it-gpu.litertlm | GPU | DEFAULT | 9.2 | 0.18 | 148.0 | 1092.8 | 1300.8 | 0.89 | 27.5 | 3407 |  |
| gemma-4-E2B-it.litertlm | CPU | DEFAULT | 4.1 | 0.56 | 286.5 | 234.1 | 210.1 | 4.02 | 17.9 | 2458 |  |
| gemma-4-E2B-it.litertlm | GPU | DEFAULT | 10.6 | 0.38 | 155.5 | 794.7 | 1031.5 | 1.21 | 20.5 | 1748 |  |
| gemma-4-E2B-it.litertlm | NPU | DEFAULT | 8.9 | 1.94 | 68.4 | 67.8 | 82.9 | 13.81 | 11.8 | 3407 |  |
| gemma-4-E4B-it-gpu.litertlm | CPU | DEFAULT | – | – | – | – | – | – | – | – | Failed to create engine: NOT_FOUND: ERROR: [third_party/odml |
| gemma-4-E4B-it-gpu.litertlm | GPU | DEFAULT | 14.9 | 0.36 | 158.5 | 380.4 | 375.5 | 2.51 | 14.4 | 3657 |  |
| gemma-4-E4B-it.litertlm | CPU | DEFAULT | 70.7 | 2.54 | 64.9 | 78.8 | 67.0 | 11.88 | 9.0 | 3657 |  |
| gemma-4-E4B-it.litertlm | GPU | DEFAULT | 23.1 | 0.63 | 107.7 | 461.5 | 489.7 | 2.08 | 14.8 | 3657 |  |

### 4 MTP
| Modell | Backend | MTP | Decode tok/s (Median, n) |
|---|---|---|---|
| gemma-4-E2B-it.litertlm | GPU | OFF | 21.7 (3) |
| gemma-4-E2B-it.litertlm | GPU | ON | 19.9 (3) |
| gemma-4-E4B-it.litertlm | GPU | OFF | 12.7 (3) |
| gemma-4-E4B-it.litertlm | GPU | ON | 12.9 (3) |

### 5/8/3b Dauerlauf, Duty-Cycling, Bildschirm, RAM
| Test | Modell | Backend | Variante | Token gesamt | tok/min 1. Min | tok/min letzte | Pause s | Temp. Start→Max °C | max Thermal | RSS max MB |
|---|---|---|---|---|---|---|---|---|---|---|
| ram_sustain | gemma-4-E4B-it.litertlm | GPU |  | 6997 | 562 | 192 | 0 | 42.2→43.4 | MODERATE | 571 |
| screen |  |  | cooldown | – | – | – | – | 37.9→39.5 | NONE | – |
| screen | gemma-4-E2B-it.litertlm | GPU | map | 15906 | 1511 | 128 | 0 | 37.9→42.7 | MODERATE | – |
| screen | gemma-4-E2B-it.litertlm | GPU | screen_off | 15677 | 1511 | 113 | 0 | 37.7→42.2 | MODERATE | – |
| sustained_duty |  |  | cooldown | – | – | – | – | 38.5→38.5 | NONE | – |
| sustained_duty | gemma-4-E2B-it.litertlm | GPU |  | – | – | – | – | 42.5→42.5 | MODERATE | – |
| sustained_duty | gemma-4-E2B-it.litertlm | GPU | continuous | 15131 | 1278 | 122 | 0 | 42.5→42.6 | MODERATE | – |
| sustained_duty | gemma-4-E2B-it.litertlm | GPU | duty_60_30_pause_moderate | 8380 | 1025 | 170 | 180 | 38.5→39.5 | NONE | – |

### 6 Extraktion (20 Artikel)
| Modell | Backend | Format | Parse-Quote | Recall | Präzision | F1 | Belegspanne ok | s/Artikel |
|---|---|---|---|---|---|---|---|---|
| gemma-4-E2B-it.litertlm | GPU | json | 0.95 | 0.29 | 0.36 | 0.32 | 1.00 | 7.2 |
| gemma-4-E2B-it.litertlm | GPU | lines | 1.00 | 0.66 | 0.94 | 0.77 | 1.00 | 4.4 |
| gemma-4-E2B-it.litertlm | GPU | think_lines | 1.00 | 0.87 | 0.94 | 0.90 | 1.00 | 9.9 |
| gemma-4-E4B-it.litertlm | GPU | json | 0.80 | 0.54 | 0.78 | 0.64 | 0.94 | 27.7 |
| gemma-4-E4B-it.litertlm | GPU | lines | 1.00 | 0.50 | 0.53 | 0.52 | 1.00 | 14.3 |
| gemma-4-E4B-it.litertlm | GPU | think_lines | 1.00 | 0.71 | 0.73 | 0.72 | 1.00 | 19.2 |

### 7 Embedding
| Modell | Backend | Präfix | Dim | ms/Satz | Laden ms | RAM +MB | cos gleich | cos anders | Abstand min | Trefferquote |
|---|---|---|---|---|---|---|---|---|---|---|
| embeddinggemma-2-740m.litertlm | CPU | - | alle | – | 1471 | 215 | – | – | – | – |
| embeddinggemma-2-740m.litertlm | CPU | none | 128 | – | – | – | 0.939 | 0.856 | 0.007 | 1.00 |
| embeddinggemma-2-740m.litertlm | CPU | none | 256 | – | – | – | 0.911 | 0.788 | 0.026 | 1.00 |
| embeddinggemma-2-740m.litertlm | CPU | none | 768 | – | – | – | 0.901 | 0.767 | 0.039 | 1.00 |
| embeddinggemma-2-740m.litertlm | CPU | none | alle | 112.6 | – | – | – | – | – | – |
| embeddinggemma-2-740m.litertlm | CPU | task_sim | 128 | – | – | – | 0.971 | 0.893 | 0.027 | 1.00 |
| embeddinggemma-2-740m.litertlm | CPU | task_sim | 256 | – | – | – | 0.955 | 0.834 | 0.060 | 1.00 |
| embeddinggemma-2-740m.litertlm | CPU | task_sim | 768 | – | – | – | 0.952 | 0.830 | 0.072 | 1.00 |
| embeddinggemma-2-740m.litertlm | CPU | task_sim | alle | 107.5 | – | – | – | – | – | – |
| embeddinggemma-2-740m.litertlm | GPU | - | alle | – | 6264 | 74 | – | – | – | – |
| embeddinggemma-2-740m.litertlm | GPU | none | 128 | – | – | – | 0.940 | 0.858 | 0.004 | 1.00 |
| embeddinggemma-2-740m.litertlm | GPU | none | 256 | – | – | – | 0.914 | 0.791 | 0.025 | 1.00 |
| embeddinggemma-2-740m.litertlm | GPU | none | 768 | – | – | – | 0.903 | 0.773 | 0.035 | 1.00 |
| embeddinggemma-2-740m.litertlm | GPU | none | alle | 60.9 | – | – | – | – | – | – |
| embeddinggemma-2-740m.litertlm | GPU | task_sim | 128 | – | – | – | 0.969 | 0.893 | 0.026 | 1.00 |
| embeddinggemma-2-740m.litertlm | GPU | task_sim | 256 | – | – | – | 0.954 | 0.834 | 0.065 | 1.00 |
| embeddinggemma-2-740m.litertlm | GPU | task_sim | 768 | – | – | – | 0.951 | 0.832 | 0.075 | 1.00 |
| embeddinggemma-2-740m.litertlm | GPU | task_sim | alle | 62.0 | – | – | – | – | – | – |
| embeddinggemma-2-text-270m.litertlm | CPU | - | alle | – | 2354 | 285 | – | – | – | – |
| embeddinggemma-2-text-270m.litertlm | CPU | none | 128 | – | – | – | 0.939 | 0.856 | 0.007 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | CPU | none | 256 | – | – | – | 0.911 | 0.788 | 0.026 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | CPU | none | 768 | – | – | – | 0.901 | 0.767 | 0.039 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | CPU | none | alle | 153.1 | – | – | – | – | – | – |
| embeddinggemma-2-text-270m.litertlm | CPU | task_sim | 128 | – | – | – | 0.971 | 0.893 | 0.027 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | CPU | task_sim | 256 | – | – | – | 0.955 | 0.834 | 0.060 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | CPU | task_sim | 768 | – | – | – | 0.952 | 0.830 | 0.072 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | CPU | task_sim | alle | 147.7 | – | – | – | – | – | – |
| embeddinggemma-2-text-270m.litertlm | GPU | - | alle | – | 8224 | 85 | – | – | – | – |
| embeddinggemma-2-text-270m.litertlm | GPU | none | 128 | – | – | – | 0.940 | 0.858 | 0.004 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | GPU | none | 256 | – | – | – | 0.914 | 0.791 | 0.025 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | GPU | none | 768 | – | – | – | 0.903 | 0.773 | 0.035 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | GPU | none | alle | 61.1 | – | – | – | – | – | – |
| embeddinggemma-2-text-270m.litertlm | GPU | task_sim | 128 | – | – | – | 0.969 | 0.893 | 0.026 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | GPU | task_sim | 256 | – | – | – | 0.954 | 0.834 | 0.065 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | GPU | task_sim | 768 | – | – | – | 0.951 | 0.832 | 0.075 | 1.00 |
| embeddinggemma-2-text-270m.litertlm | GPU | task_sim | alle | 60.9 | – | – | – | – | – | – |
| embeddinggemma-2-text-vision-440m.litertlm | CPU | - | alle | – | 1526 | 241 | – | – | – | – |
| embeddinggemma-2-text-vision-440m.litertlm | CPU | none | 128 | – | – | – | 0.939 | 0.856 | 0.007 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | CPU | none | 256 | – | – | – | 0.911 | 0.788 | 0.026 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | CPU | none | 768 | – | – | – | 0.901 | 0.767 | 0.039 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | CPU | none | alle | 129.2 | – | – | – | – | – | – |
| embeddinggemma-2-text-vision-440m.litertlm | CPU | task_sim | 128 | – | – | – | 0.971 | 0.893 | 0.027 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | CPU | task_sim | 256 | – | – | – | 0.955 | 0.834 | 0.060 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | CPU | task_sim | 768 | – | – | – | 0.952 | 0.830 | 0.072 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | CPU | task_sim | alle | 123.3 | – | – | – | – | – | – |
| embeddinggemma-2-text-vision-440m.litertlm | GPU | - | alle | – | 7102 | 72 | – | – | – | – |
| embeddinggemma-2-text-vision-440m.litertlm | GPU | none | 128 | – | – | – | 0.940 | 0.858 | 0.004 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | GPU | none | 256 | – | – | – | 0.914 | 0.791 | 0.025 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | GPU | none | 768 | – | – | – | 0.903 | 0.773 | 0.035 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | GPU | none | alle | 61.0 | – | – | – | – | – | – |
| embeddinggemma-2-text-vision-440m.litertlm | GPU | task_sim | 128 | – | – | – | 0.969 | 0.893 | 0.026 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | GPU | task_sim | 256 | – | – | – | 0.954 | 0.834 | 0.065 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | GPU | task_sim | 768 | – | – | – | 0.951 | 0.832 | 0.075 | 1.00 |
| embeddinggemma-2-text-vision-440m.litertlm | GPU | task_sim | alle | 61.2 | – | – | – | – | – | – |

### 9 / Kills / Fehler
| Zeit | Test | Metrik | Wert | Notiz |
|---|---|---|---|---|
| 2026-11-20T01:23:14 | exit_info | exit_reason | EXIT_SELF | ts=1795134192604 importance=400 pss_kb=0 rss_kb=0 desc=stop de.worldpulse.bench  |
| 2026-11-20T03:44:20 | ram_sustain | killed | FALSE |  |
| 2026-11-20T03:44:21 | fgs_long | heartbeat_min | 0 | interactive=false |
| 2026-11-20T03:54:21 | fgs_long | heartbeat_min | 10 | interactive=false |
| 2026-11-20T04:04:21 | fgs_long | heartbeat_min | 20 | interactive=false |
| 2026-11-20T04:14:22 | fgs_long | heartbeat_min | 30 | interactive=false |
| 2026-11-20T04:24:22 | fgs_long | heartbeat_min | 40 | interactive=false |
| 2026-11-20T04:34:23 | fgs_long | heartbeat_min | 50 | interactive=false |
| 2026-11-20T04:44:23 | fgs_long | heartbeat_min | 60 | interactive=false |
| 2026-11-20T04:54:23 | fgs_long | heartbeat_min | 70 | interactive=false |
| 2026-11-20T05:04:23 | fgs_long | heartbeat_min | 80 | interactive=false |
| 2026-11-20T05:14:23 | fgs_long | heartbeat_min | 90 | interactive=false |
| 2026-11-20T05:24:23 | fgs_long | heartbeat_min | 100 | interactive=false |
| 2026-11-20T05:34:23 | fgs_long | heartbeat_min | 110 | interactive=false |
| 2026-11-20T05:44:23 | fgs_long | heartbeat_min | 120 | interactive=false |
| 2026-11-20T05:54:24 | fgs_long | heartbeat_min | 130 | interactive=false |
| 2026-11-20T06:04:24 | fgs_long | heartbeat_min | 140 | interactive=true |
| 2026-11-20T06:14:24 | fgs_long | heartbeat_min | 150 | interactive=true |
| 2026-11-20T06:24:24 | fgs_long | heartbeat_min | 160 | interactive=true |
| 2026-11-20T06:34:24 | fgs_long | heartbeat_min | 170 | interactive=true |
| 2026-11-20T06:44:24 | fgs_long | heartbeat_min | 180 | interactive=true |
| 2026-11-20T06:54:24 | fgs_long | heartbeat_min | 190 | interactive=true |
| 2026-11-20T07:04:25 | fgs_long | heartbeat_min | 200 | interactive=true |
| 2026-11-20T07:14:25 | fgs_long | heartbeat_min | 210 | interactive=true |
| 2026-11-20T07:24:25 | fgs_long | heartbeat_min | 220 | interactive=true |
| 2026-11-20T07:34:25 | fgs_long | heartbeat_min | 230 | interactive=true |
| 2026-11-20T07:44:25 | fgs_long | heartbeat_min | 240 | interactive=true |
| 2026-11-20T07:54:25 | fgs_long | heartbeat_min | 250 | interactive=true |
| 2026-11-20T08:04:25 | fgs_long | heartbeat_min | 260 | interactive=true |
| 2026-11-20T08:14:26 | fgs_long | heartbeat_min | 270 | interactive=true |
| 2026-11-20T08:24:26 | fgs_long | heartbeat_min | 280 | interactive=true |
| 2026-11-20T08:34:26 | fgs_long | heartbeat_min | 290 | interactive=true |
| 2026-11-20T08:44:26 | fgs_long | heartbeat_min | 300 | interactive=true |
| 2026-11-20T08:54:26 | fgs_long | heartbeat_min | 310 | interactive=true |
| 2026-11-20T09:04:26 | fgs_long | heartbeat_min | 320 | interactive=true |
| 2026-11-20T09:14:27 | fgs_long | heartbeat_min | 330 | interactive=true |
| 2026-11-20T09:24:27 | fgs_long | heartbeat_min | 340 | interactive=true |
| 2026-11-20T09:31:05 | fgs_long | fgs_on_timeout | startId=1 type=1 |  |
| 2026-11-20T10:50:44 | exit_info | exit_reason | USER_REQUESTED | ts=1795168241110 importance=400 pss_kb=0 rss_kb=73224 desc=[REMOVE TASK] remove  |

### Hochrechnung
Duty-Faktor (Token duty / Token durchgehend, Test 5): 0.55
- gemma-4-E2B-it.litertlm GPU json: 500 Artikel/h durchgehend, 277 Artikel/h mit Duty-Cycling, 2217 pro 8-h-Nacht
- gemma-4-E2B-it.litertlm GPU lines: 812 Artikel/h durchgehend, 450 Artikel/h mit Duty-Cycling, 3600 pro 8-h-Nacht
- gemma-4-E2B-it.litertlm GPU think_lines: 362 Artikel/h durchgehend, 201 Artikel/h mit Duty-Cycling, 1604 pro 8-h-Nacht
- gemma-4-E4B-it.litertlm GPU json: 130 Artikel/h durchgehend, 72 Artikel/h mit Duty-Cycling, 576 pro 8-h-Nacht
- gemma-4-E4B-it.litertlm GPU lines: 252 Artikel/h durchgehend, 140 Artikel/h mit Duty-Cycling, 1116 pro 8-h-Nacht
- gemma-4-E4B-it.litertlm GPU think_lines: 188 Artikel/h durchgehend, 104 Artikel/h mit Duty-Cycling, 832 pro 8-h-Nacht
- Dossier-Abschnitt gemma-4-E2B-it-gpu.litertlm GPU: ~13 s -> 272/h durchgehend, 151/h mit Duty-Cycling
- Dossier-Abschnitt gemma-4-E2B-it.litertlm CPU: ~31 s -> 116/h durchgehend, 64/h mit Duty-Cycling
- Dossier-Abschnitt gemma-4-E2B-it.litertlm GPU: ~18 s -> 205/h durchgehend, 114/h mit Duty-Cycling
- Dossier-Abschnitt gemma-4-E2B-it.litertlm NPU: ~62 s -> 59/h durchgehend, 32/h mit Duty-Cycling
- Dossier-Abschnitt gemma-4-E4B-it-gpu.litertlm GPU: ~29 s -> 125/h durchgehend, 69/h mit Duty-Cycling
- Dossier-Abschnitt gemma-4-E4B-it.litertlm CPU: ~78 s -> 46/h durchgehend, 26/h mit Duty-Cycling
- Dossier-Abschnitt gemma-4-E4B-it.litertlm GPU: ~26 s -> 136/h durchgehend, 75/h mit Duty-Cycling
