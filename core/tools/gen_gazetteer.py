"""
Erzeugt core/src/main/resources/geo/gazetteer.tsv aus Natural Earth (Public Domain):
  ne_110m_admin_0_countries.geojson, ne_10m_populated_places.geojson
plus Orte der Heimatregion Heilbronn (Koordinaten gerundet, ±1 km).
Spalten: kind(C/P) precision lat lon iso2 population names(|)
Aufruf: python3 gen_gazetteer.py <ne-ordner> <ausgabe.tsv>
"""
import json, sys
src, out = sys.argv[1], sys.argv[2]
rows = []

def names(p, keys):
    ns = []
    for k in keys:
        v = p.get(k)
        if isinstance(v, str) and v.strip():
            for part in v.split("|"):
                part = part.strip()
                if part and part not in ns:
                    ns.append(part)
    return ns

c = json.load(open(f"{src}/ne_110m_admin_0_countries.geojson"))
for f in c["features"]:
    p = f["properties"]
    ns = names(p, ["NAME", "NAME_LONG", "NAME_EN", "NAME_DE", "NAME_RU", "NAME_UK", "NAME_FR", "NAME_ES", "ADMIN"])
    iso = p.get("ISO_A2_EH") or p.get("ISO_A2") or ""
    rows.append(("C", "LAND", round(p["LABEL_Y"], 3), round(p["LABEL_X"], 3), iso, int(p.get("POP_EST") or 0), ns))

pp = json.load(open(f"{src}/pp10.geojson"))
for f in pp["features"]:
    p = f["properties"]
    pop = int(p.get("POP_MAX") or 0)
    cap = "capital" in (p.get("FEATURECLA") or "").lower() or p.get("ADM0CAP") == 1
    if pop < 50000 and not cap:
        continue
    ns = names(p, ["NAME", "NAMEASCII", "NAME_EN", "NAME_DE", "NAME_RU", "NAME_UK", "NAME_FR", "NAMEALT"])
    rows.append(("P", "STADT", round(p["LATITUDE"], 4), round(p["LONGITUDE"], 4), p.get("ISO_A2") or "", pop, ns))

# Heimatregion Heilbronn (gerundete Koordinaten; Bevölkerung grob, nur zur Rangfolge)
local = [
    ("Heilbronn", 49.142, 9.219, 127000), ("Neckarsulm", 49.191, 9.224, 27000), ("Bad Wimpfen", 49.230, 9.158, 7400),
    ("Weinsberg", 49.151, 9.285, 12500), ("Öhringen", 49.200, 9.502, 24500), ("Bad Rappenau", 49.238, 9.103, 21500),
    ("Eppingen", 49.137, 8.910, 22000), ("Brackenheim", 49.077, 9.066, 16500), ("Lauffen am Neckar", 49.073, 9.143, 11800),
    ("Möckmühl", 49.323, 9.358, 8300), ("Bad Friedrichshall", 49.231, 9.215, 19500), ("Leingarten", 49.146, 9.117, 11800),
    ("Schwaigern", 49.144, 9.054, 11900), ("Neuenstadt am Kocher", 49.235, 9.330, 10300), ("Sinsheim", 49.253, 8.877, 36000),
    ("Mosbach", 49.353, 9.151, 23000), ("Ludwigsburg", 48.897, 9.192, 94000), ("Bietigheim-Bissingen", 48.948, 9.131, 43000),
    ("Schwäbisch Hall", 49.112, 9.737, 41000), ("Künzelsau", 49.282, 9.692, 15700), ("Bad Mergentheim", 49.492, 9.773, 24000),
    ("Flein", 49.103, 9.211, 7300), ("Talheim", 49.084, 9.191, 4900), ("Ilsfeld", 49.055, 9.246, 9500), ("Obersulm", 49.137, 9.379, 14200),
    ("Erlenbach", 49.172, 9.268, 5000), ("Nordheim", 49.109, 9.127, 8100), ("Gundelsheim", 49.284, 9.161, 7300),
    ("Besigheim", 49.000, 9.142, 12900), ("Marbach am Neckar", 48.939, 9.264, 16000), ("Schwäbische Alb", 48.40, 9.40, 0),
]
for n, la, lo, pop in local:
    rows.append(("P", "STADT" if pop > 0 else "REGION", la, lo, "DE", pop, [n]))

with open(out, "w", encoding="utf-8") as o:
    o.write("# Ortsverzeichnis (Natural Earth, Public Domain + Heimatregion). kind\tprecision\tlat\tlon\tiso2\tpop\tnames\n")
    for k, pr, la, lo, iso, pop, ns in rows:
        ns = [n.replace("\t", " ") for n in ns if len(n) >= 3]
        if ns:
            o.write(f"{k}\t{pr}\t{la}\t{lo}\t{iso}\t{pop}\t{'|'.join(ns)}\n")
print(len(rows), "Einträge")
