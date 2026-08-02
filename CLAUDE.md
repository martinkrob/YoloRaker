# YoloRaker

Java mikroslužba pro monitoring 3D tiskáren s Klipper/Moonraker. Lokální ONNX inference
(YOLOv11) nad snímky z webkamery → detekce spaghetti / stringing / zits → pauza tisku
přes Moonraker a notifikace webhookem nebo MQTT.

Zdrojáky komentuj anglicky (zbytek kódu je anglicky). Tenhle soubor je česky záměrně.

---

## Jak pracovat

Pracuj kontinuálně. Neptej se na svolení k pokračování mezi kroky vlastního plánu
a nečekej na potvrzení u věcí, které si můžeš odvodit z kódu nebo z rozumného defaultu.

**Rozhodni sám a jen zmiň v souhrnu:** pojmenování, strukturu balíčků, umístění souborů,
pořadí implementace, refaktory v rámci zadaného rozsahu, volbu testovací strategie,
opravy chyb ve vlastním čerstvém kódu.

**Zastav se a zeptej jen když:**
- jde o nevratnou nebo vnější akci — commit, push, publikování, mazání dat, zásah do `./data`
- je to produktové rozhodnutí, které z kódu neplyne (co má pauzovat tisk, jak má vypadat historie)
- narazíš na přednou chybu mimo zadání — nahlas ji, neopravuj

Souhrn až na konci celého bloku, ne po každém kroku.

---

## Build

`java`, `javac`, `mvn` ani `git` **nejsou v PATH**. Před buildem vždy:

```bash
export JAVA_HOME=/usr/java/jdk-25.0.3
export PATH="$JAVA_HOME/bin:/usr/lib/apache-netbeans/java/maven/bin:$PATH"
mvn -o package -DskipTests     # -o funguje, ~/.m2 je kompletní
```

Maven je jen ten přibalený k NetBeans, samostatný nikde není. `git` na stroji chybí úplně —
neplánuj kroky, které ho vyžadují. `/home/martin/NetBeansJDKs/` obsahuje jen nerozbalený tarball.

---

## Architektura — co není vidět z kódu

Tři nezávislé smyčky, každá s vlastní kadencí:

```
PollingTelemetrySource  1 Hz   → TelemetryAggregator (ring buffer 60 s)
                               → TelemetryService (cache poslední plné telemetrie)
DetectionService       10 s    → snímek → AiDetector → FusionEngine → integrátor → alarm
RetentionService        1 h    → mazání starých dat
```

**`TelemetryService` je jediný zdroj stavu tiskárny.** Detekční smyčka i webové UI čtou
jeho cache, samy se Moonrakeru neptají. Zátěž tiskárny je tím konstantní ~1,1 req/s
bez ohledu na počet otevřených dashboardů. Vypínač: `YOLORAKER_TELEMETRY_INTERVAL_MS=0`
(pak se obě cesty vrátí k přímému dotazu).

**Fúze má dvě nezávislé vstupní osy.** Telemetrické okno (co dělala tiskárna posledních
10 s) a historii skóre (jak tenhle tisk vypadal doteď). Ta druhá dělá většinu práce —
purge line v záběru, exponovaná zigzag výplň a stromové podpory vypadají pro model jako
vada a **telemetrie je od sebe nerozliší**, protože tiskárna se chová bezchybně. Rozliší
je jen čas: kulisa je tu od začátku nebo roste hodiny, porucha vznikne za minuty.

Proměnné prostředí: `YOLORAKER_DB_PATH`, `YOLORAKER_DATA_PATH`, `YOLORAKER_PORT`,
`YOLORAKER_TELEMETRY_INTERVAL_MS`.

---

## Invarianty, které se nesmí rozbít

Tohle jsou vědomá rozhodnutí, ne náhody. Než některé změníš, přečti si proč.

1. **Tlumiče škálují rychlost integrátoru, nikdy skóre.** Násobit jistotu dolů umí protlačit
   skutečnou poruchu natrvalo pod práh — z opravy falešných poplachů se stane zmeškaná
   detekce. Škálování rychlosti jen zdrží: reálná porucha přetrvává napříč snímky, takže
   se nasčítá vždycky.

2. **Override při vysoké jistotě ignoruje tlumiče, ale ne referenci.** Reference je
   *měření* toho, jak tenhle tisk vypadá. Tlumiče jsou *odhady* kontextu a můžou se mýlit.
   Měření přebíjí odhad.

3. **Pauzuje jen spaghetti.** Stringing a zits jsou kvalitativní vady — pauza je nespraví
   a tisk je většinou použitelný. Jen notifikují, max. jednou na třídu a tisk. Platí
   ve všech režimech `fusion_mode`, i v `OFF`. Sloupec `ai_alarms.action` = `PAUSED|NOTIFIED`.

4. **`DetectionHistory` klíčuje na identitu tisku, ne na `print_jobs.id`.** AI pauza svůj
   job uzavře a další cyklus založí nový — klíčování na řádek by smazalo baseline při každé
   pauze, tedy přesně když je scéna nejméně změněná a nejspíš je v záběru ještě neuklizený
   nepořádek. Identita = název souboru + rewind počítadla `print_duration`.

5. **Nespolehlivé okno = žádné tlumení.** `TelemetryWindow.isReliable()` je false při
   `coverage < 0.5` — pak se neuplatní žádný tlumič a skóre modelu stojí samo.

6. **`fusion_mode` má default `SHADOW`.** Fúze se počítá a zapisuje, ale rozhoduje staré
   skóre. Přepnutí na `ACTIVE` je vědomý krok po vyhodnocení dat.

---

## Testování

Projekt **nemá JUnit ani žádný test framework** a `pom.xml` zatím žádný nepřidává.
Ověřuje se samostatnými harness programy ve scratchpadu, které se linkují proti
`target/classes`:

- čisté funkce (`TelemetryWindow.of`, `FusionEngine.assess`, `DetectionHistory`) — přímé asserty
- HTTP vrstva — falešný Moonraker na `com.sun.net.httpserver.HttpServer`
- celý řetěz — falešná tiskárna **včetně kamery** (syntetický „zamotaný" JPEG, reálný model
  mu dává ~0,87, takže projde skutečná ONNX inference)

**Nikdy nespouštěj aplikaci proti `./data`.** Zkopíruj `data/yoloraker.mv.db` do scratchpadu
a pusť to s `YOLORAKER_DB_PATH` a `YOLORAKER_PORT=18080` na kopii.

Pozor na `pkill -f` / `pgrep -f`: vzorec sedí i na vlastní příkazovou řádku a sestřelí ti
shell. Skládej hledaný řetězec z proměnných, ať se v `ps` výpisu neobjeví celý.

---

## Známé chyby — nahlášené, neopravené

**„Allow access without auth" nefunguje.** `WebServer.handleAuth()` odmítne požadavek bez
hlavičky `Authorization` dřív, než se podívá na `auth_disabled` (ta se testuje až
ve `verifyAdmin()`). Prohlížeč hlavičku po prvním přihlášení posílá, takže si toho nikdo
nevšimne, ale API klient bez ní dostane 401. Oprava = přesunout test na začátek `handleAuth()`.

**AI pauza tříští tisk v historii.** `fireAlarm()` uzavře job (`end_time` + `paused_by_ai`),
takže po ručním resume `getLatestActivePrintJob()` nic nenajde a založí **druhý záznam pro
jeden fyzický tisk**. Stejná třída chyby, jakou v 1.0.5 opravuje pro ruční pauzy. Oprava
nejspíš = nenastavovat `end_time`, jen status. Mění to sémantiku historie → vyžaduje rozhodnutí.

**Agregace v `AiDetector` je hrubá.** Skóre = maximum přes všech ~8400 anchorů, bez NMS
a bez ohledu na plochu. Stačí jeden šumový anchor kdekoli v obraze. Do `telemetry_logs` se
proto vedle toho měří `anchors_*` (počet anchorů nad 0,5) — spaghetti je velký souvislý
objekt, zigzag výplň je rozprostřený vzor. Až budou data, porovnat a případně přepnout.
