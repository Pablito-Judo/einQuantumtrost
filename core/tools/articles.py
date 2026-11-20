import json, sys
A = []
def art(id, lang, text, claims, translated_from=None, original=None):
    exp=[]
    for i,(t,sp,ty,span) in enumerate(claims,1):
        assert span in text, (id, span)
        exp.append({"id":f"{id}-c{i}","text":t,"speaker":sp,"type":ty.upper(),"span":span})
    d={"id":id,"language":lang,"synthetic":True,"text":text,"expected":exp}
    if translated_from: d["translated_from"]=translated_from
    if original: d["original"]=original
    A.append(d)

# ---------- Deutsch (8) ----------
art("de01","de","Bei einem Großbrand in einer Lagerhalle im Heilbronner Industriegebiet sind am Dienstagabend zwei Feuerwehrleute leicht verletzt worden. Nach Angaben der Polizei entstand ein Schaden von rund 1,5 Millionen Euro. Die Brandursache ist noch unklar, die Kriminalpolizei ermittelt.",[
 ("In einer Lagerhalle im Heilbronner Industriegebiet brannte es am Dienstagabend","Polizei","Fakt","einem Großbrand in einer Lagerhalle im Heilbronner Industriegebiet"),
 ("Zwei Feuerwehrleute wurden leicht verletzt","Polizei","Fakt","zwei Feuerwehrleute leicht verletzt worden"),
 ("Der Schaden beträgt rund 1,5 Millionen Euro","Polizei","Fakt","ein Schaden von rund 1,5 Millionen Euro"),
 ("Die Brandursache ist unklar, die Kriminalpolizei ermittelt","Polizei","Fakt","Die Brandursache ist noch unklar, die Kriminalpolizei ermittelt"),
])
art("de02","de","Der Gemeinderat von Neckarsulm hat am Mittwoch mit 18 zu 9 Stimmen den Bau einer neuen Grundschule beschlossen. Die Kosten werden auf 24 Millionen Euro geschätzt. Bürgermeister Klaus Brenner nannte die Entscheidung \"überfällig\". Die Fraktion der Freien Wähler kritisierte, die Finanzierung sei nicht gesichert.",[
 ("Der Gemeinderat von Neckarsulm beschloss mit 18 zu 9 Stimmen den Bau einer neuen Grundschule","Artikel","Fakt","mit 18 zu 9 Stimmen den Bau einer neuen Grundschule beschlossen"),
 ("Die Kosten werden auf 24 Millionen Euro geschätzt","Artikel","Fakt","Die Kosten werden auf 24 Millionen Euro geschätzt"),
 ("Bürgermeister Klaus Brenner nannte die Entscheidung überfällig","Klaus Brenner","Zitat","nannte die Entscheidung \"überfällig\""),
 ("Die Finanzierung sei nicht gesichert","Freie Wähler","Behauptung","die Finanzierung sei nicht gesichert"),
])
art("de03","de","Das Statistische Landesamt meldet für Oktober eine Inflationsrate von 2,4 Prozent in Baden-Württemberg. Im September hatte sie bei 2,1 Prozent gelegen. Besonders stark stiegen die Preise für Nahrungsmittel mit einem Plus von 4,8 Prozent.",[
 ("Die Inflationsrate in Baden-Württemberg lag im Oktober bei 2,4 Prozent","Statistisches Landesamt","Fakt","eine Inflationsrate von 2,4 Prozent in Baden-Württemberg"),
 ("Im September lag die Inflationsrate bei 2,1 Prozent","Statistisches Landesamt","Fakt","Im September hatte sie bei 2,1 Prozent gelegen"),
 ("Nahrungsmittelpreise stiegen um 4,8 Prozent","Statistisches Landesamt","Fakt","Preise für Nahrungsmittel mit einem Plus von 4,8 Prozent"),
])
art("de04","de","Auf der A6 zwischen Heilbronn und Sinsheim ist es am Freitagmorgen zu einem Unfall mit drei Lastwagen gekommen. Ein Fahrer wurde schwer verletzt und mit einem Rettungshubschrauber in eine Klinik gebracht. Die Autobahn war in Richtung Mannheim für etwa vier Stunden gesperrt.",[
 ("Auf der A6 zwischen Heilbronn und Sinsheim verunglückten am Freitagmorgen drei Lastwagen","Artikel","Fakt","Auf der A6 zwischen Heilbronn und Sinsheim ist es am Freitagmorgen zu einem Unfall mit drei Lastwagen gekommen"),
 ("Ein Fahrer wurde schwer verletzt und per Rettungshubschrauber in eine Klinik gebracht","Artikel","Fakt","Ein Fahrer wurde schwer verletzt und mit einem Rettungshubschrauber in eine Klinik gebracht"),
 ("Die A6 war Richtung Mannheim etwa vier Stunden gesperrt","Artikel","Fakt","in Richtung Mannheim für etwa vier Stunden gesperrt"),
])
art("de05","de","Das Bundesverteidigungsministerium hat bestätigt, dass weitere 12 Schützenpanzer an die Ukraine geliefert wurden. Ein Sprecher sagte, die Lieferung sei bereits in der vergangenen Woche erfolgt. Russland bezeichnete die Lieferung als \"Eskalation\". Angaben zum genauen Typ machte das Ministerium nicht.",[
 ("Weitere 12 Schützenpanzer wurden an die Ukraine geliefert","Bundesverteidigungsministerium","Fakt","weitere 12 Schützenpanzer an die Ukraine geliefert wurden"),
 ("Die Lieferung erfolgte in der vergangenen Woche","Sprecher des Ministeriums","Behauptung","die Lieferung sei bereits in der vergangenen Woche erfolgt"),
 ("Russland bezeichnete die Lieferung als Eskalation","Russland","Meinung","Russland bezeichnete die Lieferung als \"Eskalation\""),
 ("Das Ministerium machte keine Angaben zum genauen Typ","Artikel","Fakt","Angaben zum genauen Typ machte das Ministerium nicht"),
])
art("de06","de","Nach tagelangem Regen hat der Neckar in Bad Wimpfen einen Pegelstand von 5,9 Metern erreicht. Die Hochwasservorhersagezentrale rechnet bis Sonntag mit einem weiteren Anstieg auf bis zu 6,3 Meter. Die Stadt hat die Uferstraße gesperrt und Sandsäcke ausgegeben.",[
 ("Der Neckar erreichte in Bad Wimpfen einen Pegelstand von 5,9 Metern","Artikel","Fakt","hat der Neckar in Bad Wimpfen einen Pegelstand von 5,9 Metern erreicht"),
 ("Bis Sonntag wird ein Anstieg auf bis zu 6,3 Meter erwartet","Hochwasservorhersagezentrale","Behauptung","rechnet bis Sonntag mit einem weiteren Anstieg auf bis zu 6,3 Meter"),
 ("Die Stadt sperrte die Uferstraße","Stadt Bad Wimpfen","Fakt","Die Stadt hat die Uferstraße gesperrt"),
 ("Die Stadt gab Sandsäcke aus","Stadt Bad Wimpfen","Fakt","Sandsäcke ausgegeben"),
])
art("de07","de","Die Gewerkschaft Verdi hat für Montag zu einem ganztägigen Warnstreik im Nahverkehr in Stuttgart aufgerufen. Betroffen sind nach Angaben der Gewerkschaft rund 2.300 Beschäftigte. Der Arbeitgeberverband nannte den Streik \"unverhältnismäßig\" und verwies auf ein bereits vorgelegtes Angebot von 5 Prozent mehr Lohn.",[
 ("Verdi rief für Montag zu einem ganztägigen Warnstreik im Stuttgarter Nahverkehr auf","Verdi","Fakt","zu einem ganztägigen Warnstreik im Nahverkehr in Stuttgart aufgerufen"),
 ("Rund 2.300 Beschäftigte sind betroffen","Verdi","Behauptung","rund 2.300 Beschäftigte"),
 ("Der Arbeitgeberverband nannte den Streik unverhältnismäßig","Arbeitgeberverband","Meinung","nannte den Streik \"unverhältnismäßig\""),
 ("Die Arbeitgeber haben 5 Prozent mehr Lohn angeboten","Arbeitgeberverband","Behauptung","ein bereits vorgelegtes Angebot von 5 Prozent mehr Lohn"),
])
art("de08","de","Ein Erdbeben der Stärke 3,1 ist in der Nacht zu Donnerstag auf der Schwäbischen Alb registriert worden. Das Epizentrum lag nach Angaben des Landeserdbebendienstes nahe Albstadt in etwa 8 Kilometern Tiefe. Berichte über Schäden lagen zunächst nicht vor.",[
 ("Auf der Schwäbischen Alb wurde ein Erdbeben der Stärke 3,1 registriert","Landeserdbebendienst","Fakt","Ein Erdbeben der Stärke 3,1 ist in der Nacht zu Donnerstag auf der Schwäbischen Alb registriert worden"),
 ("Das Epizentrum lag nahe Albstadt in etwa 8 Kilometern Tiefe","Landeserdbebendienst","Fakt","nahe Albstadt in etwa 8 Kilometern Tiefe"),
 ("Schäden wurden zunächst nicht gemeldet","Artikel","Fakt","Berichte über Schäden lagen zunächst nicht vor"),
])
# ---------- Englisch (7) ----------
art("en01","en","The U.S. Geological Survey said a magnitude 6.2 earthquake struck off the coast of northern Chile on Saturday at a depth of 35 kilometers. Local authorities reported no immediate casualties. The Chilean navy said there was no tsunami threat.",[
 ("A magnitude 6.2 earthquake struck off northern Chile on Saturday","U.S. Geological Survey","Fakt","a magnitude 6.2 earthquake struck off the coast of northern Chile on Saturday"),
 ("The quake was at a depth of 35 kilometers","U.S. Geological Survey","Fakt","at a depth of 35 kilometers"),
 ("No immediate casualties were reported","Local authorities","Fakt","Local authorities reported no immediate casualties"),
 ("There was no tsunami threat","Chilean navy","Behauptung","there was no tsunami threat"),
])
art("en02","en","Protesters gathered outside parliament in Tbilisi for a third consecutive night, with organizers claiming more than 20,000 participants. Police said about 8,000 people attended and that 14 people were detained. The interior ministry accused opposition parties of organizing the rallies.",[
 ("Protesters gathered outside parliament in Tbilisi for a third consecutive night","Article","Fakt","Protesters gathered outside parliament in Tbilisi for a third consecutive night"),
 ("More than 20,000 people participated","Organizers","Behauptung","organizers claiming more than 20,000 participants"),
 ("About 8,000 people attended","Police","Behauptung","Police said about 8,000 people attended"),
 ("14 people were detained","Police","Fakt","14 people were detained"),
 ("Opposition parties organized the rallies","Interior ministry","Behauptung","accused opposition parties of organizing the rallies"),
])
art("en03","en","The World Health Organization said on Monday that a cholera outbreak in the region has infected at least 3,400 people since August. At least 61 deaths have been recorded. A WHO spokesperson said vaccination campaigns would begin next week in the most affected districts.",[
 ("A cholera outbreak has infected at least 3,400 people since August","World Health Organization","Fakt","a cholera outbreak in the region has infected at least 3,400 people since August"),
 ("At least 61 deaths have been recorded","World Health Organization","Fakt","At least 61 deaths have been recorded"),
 ("Vaccination campaigns will begin next week in the most affected districts","WHO spokesperson","Behauptung","vaccination campaigns would begin next week in the most affected districts"),
])
art("en04","en","Firefighters in southern California were battling a wildfire that has burned more than 4,500 hectares, officials said on Wednesday. The blaze was 15 percent contained. About 1,200 residents were ordered to evacuate. \"Conditions remain extremely dangerous,\" the county fire chief said.",[
 ("A wildfire in southern California has burned more than 4,500 hectares","Officials","Fakt","a wildfire that has burned more than 4,500 hectares"),
 ("The fire was 15 percent contained","Officials","Fakt","The blaze was 15 percent contained"),
 ("About 1,200 residents were ordered to evacuate","Officials","Fakt","About 1,200 residents were ordered to evacuate"),
 ("Conditions remain extremely dangerous","County fire chief","Zitat","\"Conditions remain extremely dangerous,\" the county fire chief said"),
])
art("en05","en","The central bank raised its key interest rate by 0.25 percentage points to 4.5 percent on Thursday, its third increase this year. The governor said inflation remained \"too high\". Analysts had expected the move.",[
 ("The central bank raised its key interest rate by 0.25 percentage points to 4.5 percent","Article","Fakt","raised its key interest rate by 0.25 percentage points to 4.5 percent"),
 ("It was the third increase this year","Article","Fakt","its third increase this year"),
 ("Inflation remained too high","Governor","Meinung","inflation remained \"too high\""),
 ("Analysts had expected the move","Article","Fakt","Analysts had expected the move"),
])
art("en06","en","A drone strike hit a fuel depot near the port city on Tuesday night, the regional governor said, adding that a fire broke out but no one was injured. The defense ministry of the neighboring country said it had intercepted 27 drones overnight. Neither claim could be independently verified.",[
 ("A drone strike hit a fuel depot near the port city on Tuesday night","Regional governor","Behauptung","A drone strike hit a fuel depot near the port city on Tuesday night"),
 ("A fire broke out but no one was injured","Regional governor","Behauptung","a fire broke out but no one was injured"),
 ("27 drones were intercepted overnight","Defense ministry of the neighboring country","Behauptung","it had intercepted 27 drones overnight"),
 ("Neither claim could be independently verified","Article","Fakt","Neither claim could be independently verified"),
])
art("en07","en","The European Commission proposed on Wednesday to cut the bloc's emissions by 90 percent by 2040 compared with 1990 levels. Environmental groups called the target insufficient, while several member states warned it could harm industry.",[
 ("The European Commission proposed cutting emissions by 90 percent by 2040 compared with 1990","European Commission","Fakt","cut the bloc's emissions by 90 percent by 2040 compared with 1990 levels"),
 ("The target is insufficient","Environmental groups","Meinung","Environmental groups called the target insufficient"),
 ("The target could harm industry","Several member states","Behauptung","several member states warned it could harm industry"),
])
# ---------- Russisch -> übersetzt (5) ----------
art("ru01","de","Das russische Verteidigungsministerium erklärte, die Luftabwehr habe in der Nacht 41 ukrainische Drohnen über der Region Belgorod abgeschossen. Gouverneur Gladkow teilte mit, in einem Dorf sei ein Wohnhaus beschädigt worden. Opfer habe es nicht gegeben.",[
 ("Die Luftabwehr schoss in der Nacht 41 ukrainische Drohnen über der Region Belgorod ab","Russisches Verteidigungsministerium","Behauptung","die Luftabwehr habe in der Nacht 41 ukrainische Drohnen über der Region Belgorod abgeschossen"),
 ("In einem Dorf wurde ein Wohnhaus beschädigt","Gouverneur Gladkow","Behauptung","in einem Dorf sei ein Wohnhaus beschädigt worden"),
 ("Es gab keine Opfer","Gouverneur Gladkow","Behauptung","Opfer habe es nicht gegeben"),
], translated_from="ru", original="Министерство обороны России заявило, что ночью ПВО сбили 41 украинский беспилотник над Белгородской областью. Губернатор Гладков сообщил, что в одном из сёл повреждён жилой дом. Пострадавших нет.")
art("ru02","en","The Russian state news agency TASS reported that the government approved a budget with defense spending of 13.5 trillion rubles for next year. Finance Minister Siluanov said the deficit would stay below 1 percent of GDP.",[
 ("The government approved a budget with defense spending of 13.5 trillion rubles for next year","TASS","Behauptung","the government approved a budget with defense spending of 13.5 trillion rubles for next year"),
 ("The deficit will stay below 1 percent of GDP","Finance Minister Siluanov","Behauptung","the deficit would stay below 1 percent of GDP"),
], translated_from="ru", original="Государственное агентство ТАСС сообщило, что правительство утвердило бюджет с расходами на оборону в размере 13,5 трлн рублей на следующий год. Министр финансов Силуанов заявил, что дефицит останется ниже 1% ВВП.")
art("ru03","de","Laut der Zeitung Kommersant wurden in Moskau 3 Mitarbeiter eines Rüstungsbetriebs wegen Bestechung festgenommen. Ein Gericht ordnete Untersuchungshaft für zwei Monate an. Der Betrieb selbst äußerte sich nicht.",[
 ("In Moskau wurden 3 Mitarbeiter eines Rüstungsbetriebs wegen Bestechung festgenommen","Kommersant","Fakt","in Moskau 3 Mitarbeiter eines Rüstungsbetriebs wegen Bestechung festgenommen"),
 ("Ein Gericht ordnete zwei Monate Untersuchungshaft an","Kommersant","Fakt","Ein Gericht ordnete Untersuchungshaft für zwei Monate an"),
 ("Der Betrieb äußerte sich nicht","Artikel","Fakt","Der Betrieb selbst äußerte sich nicht"),
], translated_from="ru", original="По данным газеты «Коммерсантъ», в Москве задержаны трое сотрудников оборонного предприятия по делу о взятке. Суд арестовал их на два месяца. Само предприятие ситуацию не комментирует.")
art("ru04","en","Kremlin spokesman Dmitry Peskov said Moscow was \"open to dialogue\" but that Western sanctions made talks \"impossible in the current form\". He did not say whether a meeting between the foreign ministers was planned.",[
 ("Moscow is open to dialogue","Dmitry Peskov","Zitat","Moscow was \"open to dialogue\""),
 ("Western sanctions make talks impossible in the current form","Dmitry Peskov","Meinung","Western sanctions made talks \"impossible in the current form\""),
 ("Peskov did not say whether a foreign ministers' meeting was planned","Article","Fakt","He did not say whether a meeting between the foreign ministers was planned"),
], translated_from="ru", original="Пресс-секретарь Кремля Дмитрий Песков заявил, что Москва «открыта к диалогу», однако западные санкции делают переговоры «невозможными в нынешнем формате». Он не уточнил, планируется ли встреча министров иностранных дел.")
art("ru05","de","Nach Angaben des russischen Katastrophenschutzministeriums brach in einem Einkaufszentrum in Samara ein Feuer auf einer Fläche von 2.000 Quadratmetern aus. 150 Menschen wurden in Sicherheit gebracht. Nach vorläufigen Informationen war ein Kurzschluss die Ursache.",[
 ("In einem Einkaufszentrum in Samara brach ein Feuer auf 2.000 Quadratmetern aus","Russisches Katastrophenschutzministerium","Fakt","in einem Einkaufszentrum in Samara ein Feuer auf einer Fläche von 2.000 Quadratmetern aus"),
 ("150 Menschen wurden in Sicherheit gebracht","Russisches Katastrophenschutzministerium","Fakt","150 Menschen wurden in Sicherheit gebracht"),
 ("Ursache war vermutlich ein Kurzschluss","Russisches Katastrophenschutzministerium","Behauptung","Nach vorläufigen Informationen war ein Kurzschluss die Ursache"),
], translated_from="ru", original="По данным МЧС России, в торговом центре в Самаре произошёл пожар на площади 2000 квадратных метров. Эвакуированы 150 человек. По предварительной информации, причиной стало короткое замыкание.")

assert len(A)==20
PAIRS = [
 {"id":"p01","same":True,"a":"Ein Erdbeben der Stärke 6,2 erschütterte am Samstag die Küste Nordchiles.","b":"A magnitude 6.2 earthquake struck off northern Chile on Saturday."},
 {"id":"p02","same":True,"a":"Verdi ruft für Montag zum Warnstreik im Stuttgarter Nahverkehr auf.","b":"Am Montag streiken Busse und Bahnen in Stuttgart, Verdi hat zum Ausstand aufgerufen."},
 {"id":"p03","same":True,"a":"Russland meldet den Abschuss von 41 Drohnen über Belgorod.","b":"Над Белгородской областью сбит 41 беспилотник, сообщает Минобороны России."},
 {"id":"p04","same":True,"a":"The central bank lifted rates to 4.5 percent, its third hike this year.","b":"Die Zentralbank erhöhte den Leitzins zum dritten Mal in diesem Jahr auf 4,5 Prozent."},
 {"id":"p05","same":True,"a":"Neckar-Hochwasser: Pegel in Bad Wimpfen bei 5,9 Metern, Uferstraße gesperrt.","b":"In Bad Wimpfen steigt der Neckar auf fast sechs Meter, die Stadt sperrt die Straße am Ufer."},
 {"id":"p06","same":False,"a":"Ein Erdbeben der Stärke 6,2 erschütterte am Samstag die Küste Nordchiles.","b":"Ein Erdbeben der Stärke 3,1 wurde nachts auf der Schwäbischen Alb registriert."},
 {"id":"p07","same":False,"a":"Verdi ruft für Montag zum Warnstreik im Stuttgarter Nahverkehr auf.","b":"Der Gemeinderat Neckarsulm beschließt den Bau einer neuen Grundschule."},
 {"id":"p08","same":False,"a":"Russland meldet den Abschuss von 41 Drohnen über Belgorod.","b":"A drone strike hit a fuel depot near the port city on Tuesday night."},
 {"id":"p09","same":False,"a":"The central bank lifted rates to 4.5 percent, its third hike this year.","b":"Inflation in Baden-Württemberg rose to 2.4 percent in October."},
 {"id":"p10","same":False,"a":"Wildfire in southern California burns more than 4,500 hectares.","b":"Ein Großbrand in einer Heilbronner Lagerhalle verursacht 1,5 Millionen Euro Schaden."},
]
out=sys.argv[1]
json.dump({"version":1,"description":"S0-Test 6/7: 20 synthetische Kurzmeldungen (DE/EN/RU-übersetzt) mit handgeschriebenen Soll-Claims; Ereignisse frei erfunden.","articles":A}, open(out+"/articles.json","w"), ensure_ascii=False, indent=1)
json.dump({"version":1,"description":"10 Satzpaare gleiche/andere Meldung (inkl. sprachübergreifend und harte Negative).","pairs":PAIRS}, open(out+"/embedding_pairs.json","w"), ensure_ascii=False, indent=1)
print(sum(len(a["expected"]) for a in A), "Soll-Claims")
