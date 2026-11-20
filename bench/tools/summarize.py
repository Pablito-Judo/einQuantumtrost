#!/usr/bin/env python3
"""
Fasst Benchmark-CSV(s) der WP-Bench-App zu Markdown-Tabellen zusammen (Ersatz der Schätzungen in SPEC 6).
Nur Standardbibliothek. Aufruf:  python3 summarize.py bench_*.csv > ergebnis.md
"""
import csv
import statistics as st
import sys
from collections import defaultdict


def load(paths):
    rows = []
    for p in paths:
        with open(p, newline="", encoding="utf-8") as f:
            rows += list(csv.DictReader(f))
    return rows


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def med(xs):
    xs = [x for x in xs if x is not None]
    return st.median(xs) if xs else None


def f(x, d=1):
    return "–" if x is None else f"{x:.{d}f}"


def variant_part(v, key):
    for part in v.split(";"):
        if part.startswith(key + "="):
            return part.split("=", 1)[1]
    return None


def perf_table(rows):
    tests = {"load_prefill_decode", "backend_matrix", "model_matrix"}
    g = defaultdict(lambda: defaultdict(list))
    for r in rows:
        if r["test"] not in tests:
            continue
        mtp = variant_part(r["variant"], "mtp") or "?"
        key = (r["model"], r["backend"], mtp)
        v = r["variant"]
        m = r["metric"]
        if m == "load_error":
            g[key]["error"].append(r["value"])
        elif m == "load_ms":
            g[key]["load_ms"].append(num(r["value"]))
        elif m == "rss_peak_mb":
            g[key]["rss"].append(num(r["value"]))
        elif m == "ttft_s" and "cold_short" in v:
            g[key]["ttft_cold"].append(num(r["value"]))
        elif m == "prefill_tps" and "in=" in v:
            g[key]["pf" + variant_part(v, "in")].append(num(r["value"]))
        elif m == "ttft_s" and "in=1000" in v:
            g[key]["ttft1000"].append(num(r["value"]))
        elif m == "decode_tps" and "decode256" in v:
            g[key]["dec"].append(num(r["value"]))
    out = ["| Modell | Backend | MTP | Laden s | TTFT kalt s | Prefill tok/s @500 | @1000 | @2000 | TTFT @1000 s | Decode tok/s | RSS max MB | Fehler |",
           "|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for (model, backend, mtp), d in sorted(g.items()):
        lm = med(d["load_ms"])
        out.append(f"| {model} | {backend} | {mtp} | {f(lm / 1000 if lm else None)} | {f(med(d['ttft_cold']), 2)} | "
                   f"{f(med(d['pf500']))} | {f(med(d['pf1000']))} | {f(med(d['pf2000']))} | {f(med(d['ttft1000']), 2)} | "
                   f"{f(med(d['dec']))} | {f(med(d['rss']), 0)} | {'; '.join(d['error'])[:60]} |")
    return "\n".join(out)


def mtp_table(rows):
    g = defaultdict(list)
    for r in rows:
        if r["test"] == "mtp_compare" and r["metric"] == "decode_tps":
            g[(r["model"], r["backend"], variant_part(r["variant"], "mtp"))].append(num(r["value"]))
    out = ["| Modell | Backend | MTP | Decode tok/s (Median, n) |", "|---|---|---|---|"]
    for k, v in sorted(g.items()):
        out.append(f"| {k[0]} | {k[1]} | {k[2]} | {f(med(v))} ({len(v)}) |")
    return "\n".join(out)


def extraction_table(rows):
    g = defaultdict(dict)
    for r in rows:
        if r["test"] == "extraction" and r["variant"].endswith("SUMMARY"):
            fmt = r["variant"].split(";")[0]
            g[(r["model"], r["backend"], fmt)][r["metric"]] = num(r["value"])
    out = ["| Modell | Backend | Format | Parse-Quote | Recall | Präzision | F1 | Belegspanne ok | s/Artikel |",
           "|---|---|---|---|---|---|---|---|---|"]
    for k, d in sorted(g.items()):
        out.append(f"| {k[0]} | {k[1]} | {k[2]} | {f(d.get('parse_rate'), 2)} | {f(d.get('micro_recall'), 2)} | "
                   f"{f(d.get('micro_precision'), 2)} | {f(d.get('micro_f1'), 2)} | {f(d.get('span_rate'), 2)} | {f(d.get('mean_time_s'))} |")
    return "\n".join(out), g


def embedding_table(rows):
    g = defaultdict(dict)
    for r in rows:
        if r["test"] != "embedding":
            continue
        pref = variant_part(r["variant"], "prefix") or ""
        if r["variant"].endswith("SUMMARY"):
            dim = variant_part(r["variant"], "dim")
            g[(r["model"], r["backend"], pref, dim)][r["metric"]] = num(r["value"])
        elif r["metric"] in ("ms_per_sentence_mean", "load_ms", "rss_delta_mb"):
            g[(r["model"], r["backend"], pref or "-", "alle")][r["metric"]] = num(r["value"])
    out = ["| Modell | Backend | Präfix | Dim | ms/Satz | Laden ms | RAM +MB | cos gleich | cos anders | Abstand min | Trefferquote |",
           "|---|---|---|---|---|---|---|---|---|---|---|"]
    for k, d in sorted(g.items(), key=lambda kv: tuple(str(x) for x in kv[0])):
        out.append(f"| {k[0]} | {k[1]} | {k[2]} | {k[3]} | {f(d.get('ms_per_sentence_mean'))} | {f(d.get('load_ms'), 0)} | "
                   f"{f(d.get('rss_delta_mb'), 0)} | {f(d.get('mean_same'), 3)} | {f(d.get('mean_different'), 3)} | "
                   f"{f(d.get('margin_min_same_minus_max_diff'), 3)} | {f(d.get('best_threshold_accuracy'), 2)} |")
    return "\n".join(out)


def sustained_table(rows):
    tests = {"sustained_duty", "screen", "ram_sustain"}
    g = defaultdict(lambda: defaultdict(list))
    for r in rows:
        if r["test"] not in tests:
            continue
        base = r["variant"].split(";")[0]
        key = (r["test"], r["model"], r["backend"], base)
        if r["metric"] == "total_tokens":
            g[key]["total"].append(num(r["value"]))
        elif r["metric"] == "paused_s":
            g[key]["paused"].append(num(r["value"]))
        elif r["metric"] == "tokens_per_minute":
            g[key]["tpm"].append(num(r["value"]))
        elif r["metric"] == "rss_mb":
            g[key]["rss"].append(num(r["value"]))
        t = num(r.get("battery_temp_c"))
        if t is not None:
            g[key]["temp"].append(t)
        g[key]["thermal"].append(r.get("thermal", ""))
    out = ["| Test | Modell | Backend | Variante | Token gesamt | tok/min 1. Min | tok/min letzte | Pause s | Temp. Start→Max °C | max Thermal | RSS max MB |",
           "|---|---|---|---|---|---|---|---|---|---|---|"]
    order = ["NONE", "LIGHT", "MODERATE", "SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN"]
    for k, d in sorted(g.items()):
        th = max((x for x in d["thermal"] if x in order), key=order.index, default="–")
        tpm = d["tpm"]
        temps = d["temp"]
        out.append(f"| {k[0]} | {k[1]} | {k[2]} | {k[3]} | {f(sum(d['total']) if d['total'] else None, 0)} | "
                   f"{f(tpm[0] if tpm else None, 0)} | {f(tpm[-1] if tpm else None, 0)} | {f(sum(d['paused']) if d['paused'] else None, 0)} | "
                   f"{f(temps[0] if temps else None)}→{f(max(temps) if temps else None)} | {th} | {f(max(d['rss']) if d['rss'] else None, 0)} |")
    return "\n".join(out)


def events_table(rows):
    out = ["| Zeit | Test | Metrik | Wert | Notiz |", "|---|---|---|---|---|"]
    for r in rows:
        if r["metric"] in ("killed", "exit_reason", "fgs_on_timeout", "completed_hours", "constrained_error", "error") or \
                (r["test"] == "fgs_long" and r["metric"] == "heartbeat_min" and r["value"].endswith("0")):
            out.append(f"| {r['iso_time'][:19]} | {r['test']} | {r['metric']} | {r['value']} | {r['note'][:80]} |")
    return "\n".join(out)


def throughput_estimate(rows, ext):
    """Artikel/Stunde = 3600 / s je Artikel × Duty-Faktor (Token gesamt duty / continuous aus Test 5)."""
    tot = defaultdict(float)
    for r in rows:
        if r["test"] == "sustained_duty" and r["metric"] == "total_tokens":
            tot[r["variant"].split(";")[0]] += num(r["value"]) or 0
    duty_factor = (tot["duty_60_30_pause_moderate"] / tot["continuous"]) if tot.get("continuous") else None
    lines = [f"Duty-Faktor (Token duty / Token durchgehend, Test 5): {f(duty_factor, 2)}"]
    for (model, backend, fmt), d in sorted(ext.items()):
        t = d.get("mean_time_s")
        if t:
            per_h = 3600 / t
            lines.append(f"- {model} {backend} {fmt}: {per_h:.0f} Artikel/h durchgehend"
                         + (f", {per_h * duty_factor:.0f} Artikel/h mit Duty-Cycling, {per_h * duty_factor * 8:.0f} pro 8-h-Nacht" if duty_factor else ""))
    # Dossier-Abschnitt (Schätzformel): ~3000 Token Claims/Kontext rein, ~300 Token Text raus (SPEC 5: <= 4k je Aufruf).
    pf = defaultdict(list); dec = defaultdict(list)
    for r in rows:
        if r["test"] in ("load_prefill_decode", "model_matrix", "backend_matrix"):
            if r["metric"] == "prefill_tps" and "in=2000" in r["variant"]:
                pf[(r["model"], r["backend"])].append(num(r["value"]))
            if r["metric"] == "decode_tps" and "decode256" in r["variant"]:
                dec[(r["model"], r["backend"])].append(num(r["value"]))
    for k in sorted(set(pf) & set(dec)):
        p, d = med(pf[k]), med(dec[k])
        if p and d:
            sec = 3000 / p + 300 / d
            lines.append(f"- Dossier-Abschnitt {k[0]} {k[1]}: ~{sec:.0f} s -> {3600 / sec:.0f}/h durchgehend"
                         + (f", {3600 / sec * duty_factor:.0f}/h mit Duty-Cycling" if duty_factor else ""))
    return "\n".join(lines)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    rows = load(sys.argv[1:])
    ext_md, ext = extraction_table(rows)
    print("## S0-Messwerte (S23)\n")
    print("### 1–3 Laden / Prefill / Decode je Modell × Backend\n" + perf_table(rows) + "\n")
    print("### 4 MTP\n" + mtp_table(rows) + "\n")
    print("### 5/8/3b Dauerlauf, Duty-Cycling, Bildschirm, RAM\n" + sustained_table(rows) + "\n")
    print("### 6 Extraktion (20 Artikel)\n" + ext_md + "\n")
    print("### 7 Embedding\n" + embedding_table(rows) + "\n")
    print("### 9 / Kills / Fehler\n" + events_table(rows) + "\n")
    print("### Hochrechnung\n" + throughput_estimate(rows, ext))


if __name__ == "__main__":
    main()
