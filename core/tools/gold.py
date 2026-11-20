import json, sys
out=sys.argv[1]
def ev(d):
    srcs={s["id"]:s for s in d["sources"]}
    for c in d["claims"]:
        assert c["span"] in srcs[c["source_id"]]["text"], (c["id"], c["span"])
    return d
E=[]
E.append(ev({
 "schema_version":1,"id":"gold-0001","title":"Neckar-Hochwasser in Bad Wimpfen","category":"unwetter",
 "event_time":"2026-11-14T06:00:00+01:00",
 "location":{"name":"Bad Wimpfen","lat":49.2297,"lon":9.1584,"precision":"STADT"},
 "sources":[
  {"id":"s1","publisher":"Hochwasservorhersagezentrale BW (synthetisch)","tier":"A","url":"https://example.org/hvz/neckar","published_at":"2026-11-14T07:10:00+01:00","language":"de",
   "text":"Am Pegel Bad Wimpfen wurde um 6 Uhr ein Wasserstand von 5,9 Metern gemessen. Bis Sonntag wird ein Anstieg auf bis zu 6,3 Meter erwartet. Die Meldestufe 2 ist überschritten."},
  {"id":"s2","publisher":"Regionalzeitung (synthetisch)","tier":"B","url":"https://example.org/zeitung/hochwasser","published_at":"2026-11-14T09:30:00+01:00","language":"de","echo_group":"dpa-hw-1114",
   "text":"Nach tagelangem Regen hat der Neckar in Bad Wimpfen einen Pegelstand von 5,9 Metern erreicht. Die Stadt hat die Uferstraße gesperrt und Sandsäcke ausgegeben. Bürgermeister Weber sagte, man sei \"gut vorbereitet\"."},
  {"id":"s3","publisher":"Radiosender (synthetisch, Agenturkopie)","tier":"B","url":"https://example.org/radio/hochwasser","published_at":"2026-11-14T09:45:00+01:00","language":"de","echo_group":"dpa-hw-1114",
   "text":"Der Neckar hat in Bad Wimpfen einen Pegelstand von 5,9 Metern erreicht. Die Stadt sperrte die Uferstraße."}
 ],
 "claims":[
  {"id":"g1-c1","source_id":"s1","text":"Am Pegel Bad Wimpfen wurden um 6 Uhr 5,9 Meter gemessen","speaker":"Hochwasservorhersagezentrale","type":"FAKT","span":"Am Pegel Bad Wimpfen wurde um 6 Uhr ein Wasserstand von 5,9 Metern gemessen","entities":["e1","e2"],"event_time":"2026-11-14T06:00:00+01:00"},
  {"id":"g1-c2","source_id":"s1","text":"Bis Sonntag wird ein Anstieg auf bis zu 6,3 Meter erwartet","speaker":"Hochwasservorhersagezentrale","type":"BEHAUPTUNG","span":"Bis Sonntag wird ein Anstieg auf bis zu 6,3 Meter erwartet","entities":["e2"]},
  {"id":"g1-c3","source_id":"s1","text":"Die Meldestufe 2 ist überschritten","speaker":"Hochwasservorhersagezentrale","type":"FAKT","span":"Die Meldestufe 2 ist überschritten","entities":[]},
  {"id":"g1-c4","source_id":"s2","text":"Der Neckar erreichte in Bad Wimpfen 5,9 Meter","speaker":"Regionalzeitung","type":"FAKT","span":"hat der Neckar in Bad Wimpfen einen Pegelstand von 5,9 Metern erreicht","entities":["e1","e2"]},
  {"id":"g1-c5","source_id":"s2","text":"Die Stadt sperrte die Uferstraße und gab Sandsäcke aus","speaker":"Regionalzeitung","type":"FAKT","span":"Die Stadt hat die Uferstraße gesperrt und Sandsäcke ausgegeben","entities":["e1"]},
  {"id":"g1-c6","source_id":"s2","text":"Bürgermeister Weber sagte, man sei gut vorbereitet","speaker":"Bürgermeister Weber","type":"ZITAT","span":"man sei \"gut vorbereitet\"","entities":["e3"]},
  {"id":"g1-c7","source_id":"s3","text":"Der Neckar erreichte in Bad Wimpfen 5,9 Meter","speaker":"Radiosender","type":"FAKT","span":"Der Neckar hat in Bad Wimpfen einen Pegelstand von 5,9 Metern erreicht","entities":["e1","e2"]},
  {"id":"g1-c8","source_id":"s3","text":"Die Stadt sperrte die Uferstraße","speaker":"Radiosender","type":"FAKT","span":"Die Stadt sperrte die Uferstraße","entities":["e1"]}
 ],
 "entities":[
  {"id":"e1","name":"Bad Wimpfen","type":"ORT","aliases":[],"wikidata_id":"Q502611"},
  {"id":"e2","name":"Neckar","type":"ORT","aliases":["Neckarfluss"],"wikidata_id":"Q1673"},
  {"id":"e3","name":"Bürgermeister Weber","type":"PERSON","aliases":["Weber"]}
 ],
 "reference_dossier":[
  {"text":"Der Neckar hat in Bad Wimpfen am Morgen einen Pegelstand von 5,9 Metern erreicht.","claim_ids":["g1-c1","g1-c4","g1-c7"]},
  {"text":"Die Hochwasservorhersagezentrale erwartet bis Sonntag einen Anstieg auf bis zu 6,3 Meter.","claim_ids":["g1-c2"]},
  {"text":"Die Stadt hat die Uferstraße gesperrt und Sandsäcke ausgegeben.","claim_ids":["g1-c5","g1-c8"]}
 ],
 "notes":"Synthetisch. s2 und s3 sind dieselbe Agenturmeldung (echo_group) und zählen als EINE Quelle; Wikidata-IDs ungeprüft.",
 "checked_by":"nutzer","checked_at":"2026-11-19"
}))
E.append(ev({
 "schema_version":1,"id":"gold-0002","title":"Drohnenangriff Region Belgorod – widersprüchliche Angaben","category":"konflikt",
 "event_time":"2026-11-16T02:00:00+03:00",
 "location":{"name":"Oblast Belgorod","lat":50.6,"lon":36.6,"precision":"REGION"},
 "sources":[
  {"id":"s1","publisher":"Russisches Verteidigungsministerium (Telegram, synthetisch)","tier":"C","published_at":"2026-11-16T07:00:00+03:00","language":"de","translated_from":"ru",
   "text":"Die Luftabwehr hat in der Nacht 41 ukrainische Drohnen über der Region Belgorod abgeschossen. In einem Dorf wurde ein Wohnhaus beschädigt."},
  {"id":"s2","publisher":"Ukrainischer Generalstab (synthetisch)","tier":"C","published_at":"2026-11-16T10:00:00+02:00","language":"de","translated_from":"uk",
   "text":"In der Nacht wurde ein Munitionslager in der Region Belgorod getroffen. Es kam zu sekundären Explosionen."},
  {"id":"s3","publisher":"Internationale Nachrichtenagentur (synthetisch)","tier":"A","published_at":"2026-11-16T12:00:00+00:00","language":"en",
   "text":"Russia said it downed 41 drones over the Belgorod region overnight, while Ukraine's General Staff said it struck an ammunition depot there. Neither claim could be independently verified."}
 ],
 "claims":[
  {"id":"g2-c1","source_id":"s1","text":"Die Luftabwehr schoss 41 ukrainische Drohnen über der Region Belgorod ab","speaker":"Russisches Verteidigungsministerium","type":"BEHAUPTUNG","span":"41 ukrainische Drohnen über der Region Belgorod abgeschossen","entities":["e1","e2"]},
  {"id":"g2-c2","source_id":"s1","text":"In einem Dorf wurde ein Wohnhaus beschädigt","speaker":"Russisches Verteidigungsministerium","type":"BEHAUPTUNG","span":"In einem Dorf wurde ein Wohnhaus beschädigt","entities":[]},
  {"id":"g2-c3","source_id":"s2","text":"Ein Munitionslager in der Region Belgorod wurde getroffen","speaker":"Ukrainischer Generalstab","type":"BEHAUPTUNG","span":"In der Nacht wurde ein Munitionslager in der Region Belgorod getroffen","entities":["e1","e3"]},
  {"id":"g2-c4","source_id":"s2","text":"Es kam zu sekundären Explosionen","speaker":"Ukrainischer Generalstab","type":"BEHAUPTUNG","span":"Es kam zu sekundären Explosionen","entities":[]},
  {"id":"g2-c5","source_id":"s3","text":"Russia said it downed 41 drones over the Belgorod region","speaker":"Nachrichtenagentur","type":"FAKT","span":"Russia said it downed 41 drones over the Belgorod region overnight","entities":["e1","e2"]},
  {"id":"g2-c6","source_id":"s3","text":"Ukraine's General Staff said it struck an ammunition depot","speaker":"Nachrichtenagentur","type":"FAKT","span":"Ukraine's General Staff said it struck an ammunition depot there","entities":["e3"]},
  {"id":"g2-c7","source_id":"s3","text":"Neither claim could be independently verified","speaker":"Nachrichtenagentur","type":"FAKT","span":"Neither claim could be independently verified","entities":[]}
 ],
 "entities":[
  {"id":"e1","name":"Belgorod","type":"ORT","aliases":["Белгород","Bilhorod","Белгородская область"],"wikidata_id":"Q17761"},
  {"id":"e2","name":"Russisches Verteidigungsministerium","type":"ORGANISATION","aliases":["Минобороны России","Russian Defense Ministry","Russland"]},
  {"id":"e3","name":"Ukrainischer Generalstab","type":"ORGANISATION","aliases":["General Staff of Ukraine","Генштаб ВСУ","ukrainische Generalstab"]}
 ],
 "reference_dossier":[
  {"text":"Russland gibt an, in der Nacht 41 ukrainische Drohnen über der Region Belgorod abgeschossen zu haben.","claim_ids":["g2-c1","g2-c5"]},
  {"text":"Der ukrainische Generalstab meldet dagegen einen Treffer auf ein Munitionslager in der Region.","claim_ids":["g2-c3","g2-c6"]},
  {"text":"Keine der beiden Angaben ließ sich unabhängig überprüfen.","claim_ids":["g2-c7"]}
 ],
 "notes":"Synthetisch. Konfliktfall: Parteiangaben = Stufe C; Status-Ring 'umstritten'. Wikidata-IDs ungeprüft.",
 "checked_by":"nutzer","checked_at":"2026-11-19"
}))
E.append(ev({
 "schema_version":1,"id":"gold-0003","title":"Erdbeben M6.2 vor Nordchile","category":"erdbeben",
 "event_time":"2026-11-15T14:22:00Z",
 "location":{"name":"Küste Nordchile (Region Antofagasta)","lat":-23.1,"lon":-70.9,"precision":"REGION"},
 "sources":[
  {"id":"s1","publisher":"USGS (synthetisch)","tier":"A","url":"https://example.org/usgs/event","published_at":"2026-11-15T14:40:00Z","language":"en",
   "text":"M 6.2 - 48 km W of Antofagasta, Chile. Origin time 2026-11-15 14:22 UTC. Depth 35.0 km."},
  {"id":"s2","publisher":"Chilenische Marine SHOA (synthetisch)","tier":"A","published_at":"2026-11-15T14:50:00Z","language":"en","translated_from":"es",
   "text":"The characteristics of the earthquake do not meet the conditions to generate a tsunami on the coast of Chile."},
  {"id":"s3","publisher":"Lokalmedium (synthetisch)","tier":"B","published_at":"2026-11-15T16:00:00Z","language":"de","translated_from":"es",
   "text":"In Antofagasta fiel nach dem Beben in mehreren Stadtteilen für etwa zwei Stunden der Strom aus. Verletzte wurden nicht gemeldet."}
 ],
 "claims":[
  {"id":"g3-c1","source_id":"s1","text":"Ein Erdbeben der Magnitude 6.2 ereignete sich 48 km westlich von Antofagasta","speaker":"USGS","type":"FAKT","span":"M 6.2 - 48 km W of Antofagasta, Chile","entities":["e1","e2"]},
  {"id":"g3-c2","source_id":"s1","text":"Das Beben ereignete sich um 14:22 UTC in 35 km Tiefe","speaker":"USGS","type":"FAKT","span":"Origin time 2026-11-15 14:22 UTC. Depth 35.0 km","entities":[]},
  {"id":"g3-c3","source_id":"s2","text":"Es besteht keine Tsunami-Gefahr für die Küste Chiles","speaker":"SHOA","type":"FAKT","span":"do not meet the conditions to generate a tsunami on the coast of Chile","entities":["e3"]},
  {"id":"g3-c4","source_id":"s3","text":"In Antofagasta fiel in mehreren Stadtteilen etwa zwei Stunden der Strom aus","speaker":"Lokalmedium","type":"FAKT","span":"In Antofagasta fiel nach dem Beben in mehreren Stadtteilen für etwa zwei Stunden der Strom aus","entities":["e2"]},
  {"id":"g3-c5","source_id":"s3","text":"Verletzte wurden nicht gemeldet","speaker":"Lokalmedium","type":"FAKT","span":"Verletzte wurden nicht gemeldet","entities":[]}
 ],
 "entities":[
  {"id":"e1","name":"USGS","type":"ORGANISATION","aliases":["U.S. Geological Survey"],"wikidata_id":"Q193755"},
  {"id":"e2","name":"Antofagasta","type":"ORT","aliases":[],"wikidata_id":"Q2903"},
  {"id":"e3","name":"Chile","type":"ORT","aliases":[],"wikidata_id":"Q298"}
 ],
 "reference_dossier":[
  {"text":"Ein Erdbeben der Magnitude 6.2 hat sich am 15. November 48 km westlich von Antofagasta in 35 km Tiefe ereignet.","claim_ids":["g3-c1","g3-c2"]},
  {"text":"Die chilenische Marine sieht keine Tsunami-Gefahr.","claim_ids":["g3-c3"]},
  {"text":"In Antofagasta fiel in mehreren Stadtteilen etwa zwei Stunden der Strom aus; Verletzte wurden nicht gemeldet.","claim_ids":["g3-c4","g3-c5"]}
 ],
 "notes":"Synthetisch. Primärquelle (USGS) -> Status 'bestätigt'. Wikidata-IDs ungeprüft.",
 "checked_by":"nutzer","checked_at":"2026-11-19"
}))
names=[]
for e in E:
    fn=e["id"]+".json"; names.append(fn)
    json.dump(e, open(out+"/events/"+fn,"w"), ensure_ascii=False, indent=2)
open(out+"/events/index.txt","w").write("# Liste der Goldstandard-Ereignisse (eine Datei pro Zeile)\n"+"\n".join(names)+"\n")
print("ok")
