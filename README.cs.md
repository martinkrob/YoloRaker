# YoloRaker

![Docker Pulls](https://img.shields.io/docker/pulls/h848/yoloraker?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)

*🌐 [English](README.md) | [Čeština](README.cs.md)*

YoloRaker je nenáročná aplikace pro 3D tiskárny s firmwarem Klipper a Moonraker, využívající umělou inteligenci. Hlídá obraz z webkamery, sleduje telemetrii a dokáže pozastavit tisk dřív, než se z něj stane chuchvalec — celé to běží na vašem vlastním hardwaru, na procesoru, bez jakékoli cloudové služby.

![YoloRaker Dashboard](./docs/dashboard.png)

![YoloRaker Live View](./docs/liveview.png)

![YoloRaker Analytics](./docs/analytics.png)

![YoloRaker Edit Printer](./docs/edit_1.png)

## Klíčové vlastnosti

* **AI detekce chyb tisku** — Vizuální analýza obrazu z webkamery v reálném čase: špagety, stringing a zity. 100 % lokálně, pouze na CPU.
* **Sensor fusion** — Každá detekce se ověřuje proti telemetrii tiskárny *a* proti tomu, jak dosud vypadal právě tenhle tisk. Právě to potlačuje falešné poplachy z purge line, exponované výplně a stromových podpor. Viz [Jak se z detekce stane alarm](#jak-se-z-detekce-stane-alarm).
* **Tisk zastaví jen skutečná porucha** — Spaghetti pozastaví tisk přes Moonraker. Stringing a zity jsou vady povrchu, které pauza nespraví, takže se jen ohlásí a zaznamenají a tisk pokračuje.
* **Shadow mód** — Fúze umí běžet v režimu, kdy jen zapisuje. Uvidíte, co by *byla* rozhodla, dřív než jí necháte rozhodovat.
* **Míra potvrzení místo okamžitých procent** — Dashboard ukazuje, jak blízko je každá třída k zásahu. Dostanete varování před pauzou, ne procento, které vyskakuje a nic neznamená.
* **Posouzení incidentů** — U každého incidentu označíte, jestli šlo o skutečnou chybu, nebo o falešný poplach. Posouzené incidenty se uchovávají natrvalo jako trénovací data pro budoucí ladění modelu.
* **Průběžná telemetrie** — Tiskárny se vzorkují jednou za sekundu nezávisle na UI. Grafy mají skutečné rozlišení a zátěž tiskárny zůstává konstantní bez ohledu na počet otevřených dashboardů.
* **Upozornění** — Webhooky a MQTT pro Home Assistant, Node-RED, Discord a spol.
* **Timelapse a snímky** — Pravidelné snímky během tisku, přehratelné v prohlížeči historie.
* **Jednoduchá retence** — Zvolíte, kolik posledních tisků chcete držet. Všechno, co k nim patří, zůstane; všechno starší se smaže.

## Technologie

* **Backend:** Java 25, Javalin (webový framework), JDBI v3 (práce s databází), H2 (integrovaná databáze)
* **Frontend:** Čistý JavaScript, HTML5, CSS3, Chart.js
* **AI/Strojové učení:** ONNX Runtime pro Javu (modely YOLOv8/YOLOv11)

Frontend nemá build step ani závislosti na CDN — celé rozhraní funguje i na izolované tiskové síti bez přístupu k internetu.

## Jak se z detekce stane alarm

Tuhle část stojí za to pochopit, protože rozhoduje o tom, jestli je z monitoringu užitečný nástroj, nebo něco, co se člověk naučí ignorovat.

**1. Model ohodnotí snímek.** ONNX model vrátí jistotu pro každou třídu.

**2. Kontrola proti baseline.** Purge line v záběru kamery, exponovaná zigzag výplň nebo stromové podpory vypadají pro model jako vada — a telemetrie je od skutečné poruchy nerozliší, protože tiskárna se ve všech těch případech chová bezchybně. Rozliší je **čas**. YoloRaker drží klouzavý nízký percentil skóre každé třídy za posledních 20 minut aktuálního tisku a posuzuje detekci podle toho, o kolik vystoupá **nad tuhle baseline**. Kulisa si baseline vytáhne s sebou a zůstane zticha; porucha, která vznikne za minutu nebo dvě, ji nechá za sebou a vyčnívá.

**3. Telemetrické tlumiče.** Desetivteřinové okno telemetrie končící u snímku z kamery umí detekci zpomalit — nic se neextrudovalo, tryska se ještě zahřívá, prvních devadesát vteřin tisku, právě obnoveno po pauze. Podstatné je, že **tlumiče škálují rychlost, nikdy neupravují skóre**: skutečná porucha přetrvává snímek po snímku, takže se nasčítá tak jako tak, jen později. Tlumení zdrží, umlčet nedokáže.

**4. Míra potvrzení.** Každý snímek přidá k úrovni dané třídy podle toho, jak jistý si model byl; čistý snímek ubere. Alarm se spustí při dosažení 5. Hraniční detekce se potvrdí zhruba za 50 vteřin, jistá zhruba za 30 — starý pevný čítač potřeboval 50 vteřin v obou případech.

**5. Akce.** Spaghetti pozastaví tisk. Stringing a zity upozorní, nejvýš jednou na třídu a tisk.

Dvě bezpečnostní vlastnosti jsou záměrné a nedají se nastavit:

* Velmi jistá detekce (≥ 0,90) ignoruje všechny telemetrické tlumiče. Tlumiče jsou odhady kontextu a můžou se mýlit.
* Baseline **neignoruje**. To je měření toho, jak tenhle tisk skutečně vypadá, a má přednost před jakýmkoli odhadem.

### Režimy fúze

Nastavuje se v **Settings → AI Engine**:

| Režim | Chování |
|---|---|
| `OFF` | Fúze se nevyhodnocuje. Rozhoduje holé skóre modelu. |
| `SHADOW` *(výchozí)* | Fúze se vyhodnocuje a zapisuje, ale rozhoduje pořád holé skóre. Bezpečný způsob, jak nejdřív posbírat pár dní reálných dat. |
| `ACTIVE` | Rozhoduje fúze. |

Rozdělení pauza/notifikace platí ve **všech** režimech — je to politika, ne součást fúze.

Až budete mít pár dní dat ze shadow módu, **History & Analytics → Analytics** ukáže, jak často by která cesta zasáhla, i rozpad podle toho, která pravidla fúzi zadržela.

## Docker Image

Předpřipravený kontejner je na Docker Hubu. Návod na instalaci, konfiguraci a příklady pro `docker-compose`:

**[https://hub.docker.com/r/h848/yoloraker](https://hub.docker.com/r/h848/yoloraker)**

### Proměnné prostředí

| Proměnná | Výchozí | Význam |
|---|---|---|
| `YOLORAKER_PORT` | `8080` | HTTP port |
| `YOLORAKER_DB_PATH` | `./data/yoloraker` | Cesta k H2 databázi (bez přípony) |
| `YOLORAKER_DATA_PATH` | `./data` | Snímky a nahrané modely — nasměrujte na připojený volume |
| `YOLORAKER_TELEMETRY_INTERVAL_MS` | `1000` | Perioda vzorkování telemetrie. Minimum 200 ms; `0` vypne průběžný sběr a vrátí se k dotazování na vyžádání. |

## Konfigurace

1. Otevřete `http://<vase-ip-adresa>:8080`
2. Přihlaste se výchozími údaji — **`admin` / `admin`**
3. Běžte do Settings a okamžitě si změňte heslo
4. Klikněte na **Add Printer** a zadejte IP adresu / hostname Moonrakeru a URL webkamery

Detekční třídy, jejich prahy a AI model se nastavují pro každou tiskárnu zvlášť v **Edit Printer → AI Detection**.

## Vývoj

### Požadavky
* Java JDK 25+
* Maven 3.8+

### Sestavení a spuštění
```bash
# Naklonujte repozitář, poté:
mvn clean package
java -jar target/YoloRaker-1.1.0.jar
```

Aplikace se spustí na portu 8080. Data se ukládají do složky `./data` vůči pracovnímu adresáři. Migrace databáze proběhnou automaticky při startu — upgrade na místě je bezpečný a o žádná stávající data nepřijdete.

## Známá omezení

* **„Allow access without auth" stejně vyžaduje hlavičku `Authorization`.** Kontrola tohohle nastavení proběhne až po kontrole hlavičky, takže prohlížeč (který si přihlašovací údaje pamatuje) funguje, ale holý API klient dostane 401.
* **Pauza od AI rozdělí tisk v historii.** Pozastavení přes AI uzavře záznam úlohy, takže po obnovení vznikne druhý záznam pro jeden fyzický tisk.
* **Skóre detekce je maximum přes všechny anchory**, bez NMS a bez ohledu na plochu, takže jedna zašuměná oblast kdekoli v obraze umí vytáhnout celou třídu nahoru. Vedle toho se zaznamenávají počty anchorů (`anchors_*` v `telemetry_logs`), aby šlo vyhodnotit robustnější agregaci.

## Seznam změn (Changelog)

### v1.1.0 (Nejnovější)

Hlavní novinkou téhle verze je **sensor fusion**: detekce se už neposuzují jen podle skóre modelu.

**Sensor fusion**
* **Průběžný sběr telemetrie.** Nová telemetrická služba vzorkuje každou tiskárnu jednou za sekundu a agreguje desetivteřinová okna (ujetá dráha, spotřebovaný filament, změna Z, drift a rozptyl teplot, výkon topení). Okna jsou zarovnaná na okamžik pořízení snímku, ne na okamžik jeho zpracování.
* **Adaptivní baseline.** Klouzavý 20. percentil skóre každé třídy za posledních 20 minut aktuálního tisku. Detekce se posuzuje podle překročení téhle baseline, což je to, co neutralizuje purge line, exponovanou výplň a stromové podpory.
* **Telemetrické tlumiče** — `NO_EXTRUSION`, `NOT_AT_TEMP`, `EARLY_PRINT`, `JUST_RESUMED` pro spaghetti; `TRAVEL_HEAVY` naopak stringing zesiluje, protože struny vznikají právě při travel movech.
* **Tlumiče škálují rychlost potvrzení, ne skóre.** Násobit skóre dolů znamená riziko, že skutečnou poruchu natrvalo protlačíte pod práh. Škálování rychlosti způsobí, že tlumení alarm zdrží, ale nikdy mu nezabrání.
* **Override při vysoké jistotě** od 0,90 ignoruje všechny tlumiče, ale nikdy ne baseline.
* **Potvrzení vážené jistotou** nahrazuje pevných „5 detekcí po sobě". Jistá detekce se teď potvrdí zhruba za 30 vteřin místo 50; hraniční pořád potřebuje celých 50.
* **Upozornění na zasycenou baseline.** Když naučená baseline nenechá modelu žádný prostor, karta tiskárny to řekne a vysvětlí, že řešením je úhel kamery, ne práh.
* **Režimy Shadow / Active / Off**, výchozí je Shadow.

**Politika detekce**
* **Tisk pozastavuje jen spaghetti.** Stringing a zity jsou vady povrchu; nově se jen ohlásí a zaznamenají a tisk pokračuje, nejvýš jedna notifikace na třídu a tisk. Platí ve všech režimech fúze.
* `ai_alarms` zaznamenává, co se skutečně stalo (`PAUSED` / `NOTIFIED`). Starší záznamy se čtou jako `PAUSED`, což odpovídá tomu, co dělaly.

**Posuzování incidentů (základ pro přetrénování modelu)**
* Každý incident lze v **History & Analytics → Incidents** označit jako skutečnou chybu nebo falešný poplach.
* **Posouzené incidenty retence nikdy nesmaže**, bez ohledu na stáří — jsou to trénovací data.

**Zjednodušená retence**
* Tři samostatné limity počtu řádků (telemetrie, alarmy, tiskové úlohy) nahradilo **jedno nastavení: kolik posledních tisků držet**. Všechno, co k nim patří — telemetrie, incidenty, snímky — zůstává; všechno starší se maže.
* Upgrade převezme váš dosavadní limit tiskových úloh, takže se nic tiše nesmaže. Zkontrolujte hodnotu v Settings; v novém významu je nejspíš vyšší, než potřebujete.
* Osiřelé složky se snímky po starších verzích se nově uklízejí.
* Interní strop omezuje telemetrii tiskárny, která nikdy netiskla, kde není k čemu retenci ukotvit.

**Moonraker klient**
* Rozdělen na samostatné dotazy na info a na objekty. Rychlá 1Hz cesta už nestahuje stav Klipperu při každém ticku, obnovuje se jednou za 10 vteřin.
* Čte `motion_report.live_position` — tedy kde tryska skutečně je — místo plánované pozice, která utíká dopředu před tím, co je fyzicky vytištěno. Když `motion_report` chybí, použije se plánovaná pozice.
* Čte `extruder.power`, bez kterého nelze odlišit „chladne, protože se změnil cíl" od „chladne při plném výkonu topení", tedy ucpání.
* Nedostupná tiskárna se hlásí jednou za výpadek, ne jednou za dotaz.

**Uživatelské rozhraní**
* **Karty tiskáren** místo tabulky: živý náhled kamery, progress bar, uplynulý a odhadovaný zbývající čas, filament, rychlost, výška, teploty, ventilátor a aktivní model — a k tomu ukazatel potvrzení pro každou třídu.
* **Hlavním číslem je míra potvrzení**, ne okamžitá jistota, doplněná srozumitelným důvodem (`idle`, `at scenery level`, `building`, `pausing in ~15 s`).
* **Analytics přestavěné na kokpit fúze** — tři panely nad společnou časovou osou, každý s pásem baseline, čárou prahu a šrafurou v úsecích, kde zabíraly tlumiče. Události jsou napíchnuté špendlíkem a po najetí myší se ukážou pravidla, která se uplatnila. Pruh porovnání hlásí, jak často by která rozhodovací cesta zasáhla. Tabulkový výpis je vždy k dispozici.
* **Graf Analytics už nemá dvě osy y.** Teploty mají vlastní graf za přepínačem místo sdílení rámu s jistotou AI na druhé škále.
* **Live View lze otevřít jako celou stránku** (`#live/<printerId>`), bookmarkovatelnou a vhodnou na druhý monitor. Obsah je zhruba o 20 % hustší.
* Přepínače detekčních tříd se přesunuly z přehledu do **Edit Printer → AI Detection**, každý spárovaný se svým prahem.
* Incidenty mají odznak `PAUSED` / `NOTIFIED`.
* History & Analytics už při přepínání záložek nemění velikost.

**Diagnostika**
* Řádky telemetrie nově zaznamenávají i baseline pro každou třídu, faktor tlumení, uplatněná pravidla, agregované telemetrické okno a počet anchorů nad 0,5 pro každou třídu — poslední z toho kvůli vyhodnocení, jestli není počet lepším signálem než maximum.

**Opravy**
* Zástupný text u kamery už nepřidává svou kopii při každé obnově nedostupného náhledu.
* Baseline fúze přežije pauzu od AI. Byla klíčovaná na řádek tiskové úlohy, který pauza uzavírá, takže se mazala přesně ve chvíli, kdy se scéna nejméně pravděpodobně změnila.
* Tlačítko Cancel v Edit Printer už nehází `ReferenceError`; funkce, kterou volalo, nikdy neexistovala.
* Dlouhé názvy souborů už neroztáhnou kartu tiskárny přes její sloupec.
* Agregované telemetrické okno se už neserializuje do odpovědí historie, kde přidávalo megabajty a nikdo ho nečetl.

### v1.0.5
**Spolehlivost a opravy chyb:**
* **Odolnost proti výpadku kamery:** Výpadek webkamery (timeout nebo chyba HTTP) už neshodí celý detekční cyklus. Logování telemetrie, evidence tiskových úloh i notifikace fungují dál, pouze se dočasně vypne AI detekce. Webkamera je nyní zcela volitelná.
* **Oddělená telemetrie:** Telemetrie a historie tisku se nyní zaznamenávají nezávisle na webkameře, takže i tiskárny bez kamery mají plnou historii a analytiku.
* **Integrita tiskových úloh:** Pozastavení a obnovení tisku už nerozdělí jeden fyzický tisk do více záznamů v historii — pauza je nově správně považována za probíhající úlohu.
* **Oprava mazání tiskárny (HTTP 500):** Smazání tiskárny selhávalo kvůli porušení referenční integrity, protože mazací událost odkazovala na právě odstraněnou tiskárnu. Událost se nyní loguje bez cizího klíče.
* **Dashboard funkční i offline:** Chart.js je nyní přibalen lokálně místo načítání z CDN, takže všechny grafy fungují i na izolovaných tiskových sítích bez přístupu k internetu.
* **Únik paměti ONNX:** Nativní ONNX inferenční session se nyní správně uvolňují při změně modelu tiskárny i při ukončení aplikace.
* **Paralelní detekční smyčka:** Kontroly tiskáren nyní běží na fondu vláken s pojistkou pro každou tiskárnu, takže jedna pomalá nebo nedostupná tiskárna už nezdržuje detekci ostatních.
* **Robustnost vlastních modelů:** Post-processing inference nyní ověřuje tvar výstupního tenzoru a zvládá i transponovaný výstup, což zvyšuje spolehlivost nahraných vlastních modelů `.onnx`.
* **Oprava UI:** Dialog Nastavení systému už při přepínání mezi záložkami nemění svou velikost.

**Bezpečnost:**
* **Autentizace zapnutá ve výchozím stavu:** Nové instalace se nyní spouští se zapnutou autentizací (dříve byla ve výchozím stavu vypnutá).
* **Hashované heslo administrátora:** Heslo administrátora se ukládá jako solený PBKDF2 hash místo otevřeného textu. Stávající hesla v otevřeném textu se při dalším úspěšném přihlášení automaticky převedou na hash.
* **Ochrana proti path-traversal:** Endpointy pro čtení snapshotů nyní odmítají nebezpečné segmenty cesty.

**Docker:**
* **Perzistentní snapshoty a modely:** Snapshoty a nahrané vlastní modely se nyní zapisují na připojený volume `/data` (`YOLORAKER_DATA_PATH`), což zabraňuje jejich ztrátě při znovuvytvoření kontejneru.

### v1.0.4
**Nové funkce a vylepšení:**
* **Podpora vlastních AI modelů**: Možnost nahrávat a spravovat vlastní YOLO (.onnx) modely v Nastavení systému.
* **Přiřazení AI modelu podle tiskárny**: Nyní můžete v dialogu úpravy tiskárny vybrat konkrétní AI model pro každou tiskárnu zvlášť, což umožňuje používat různé modely pro různé kamery nebo světelné podmínky.
* **Integrace s displeji**: Byla přidána možnost odesílat stav AI detekce přímo do rozhraní Mainsail, Fluidd nebo KlipperScreen přes Moonraker (pomocí příkazů M117).
* **Přepracovaný Live Dashboard**: Rozhraní Live View bylo vylepšeno. Náhled kamery a telemetrie jsou nyní přehledně umístěny vedle sebe s nativním poměrem stran 16:9.
* **Živá AI Analytika**: Na hlavní panel Dashboardu byly přidány tři živé grafy s průběhem detekce (Spaghetti, Stringing, Zits) pro okamžité sledování hodnot umělé inteligence z posledních měření.
* **Vylepšení UI**: Sjednocena velikost modálních dialogů pro zabránění uskakování oken při přepínání záložek, mírně zmenšeno globální písmo pro decentnější vzhled a nahrazen velký indikátor stavu API za elegantní stavovou tečku.

### v1.0.3
**Nové funkce a vylepšení:**
* **Granulární přepínače detekce AI:** Nyní můžete přímo v tabulce nástěnky povolit nebo zakázat sledování specifických chyb (Spaghetti, Stringing, Zits).
* **Modernizované uživatelské rozhraní (UI):** Primární barva byla změněna na moderní fialovou (Violet) a design přepínačů byl sjednocen pro prémiovější vzhled.
* **Zlepšená stabilita AI:** Zvýšen minimální počet po sobě jdoucích pozitivních detekcí ze 3 na 5, což výrazně omezuje falešné poplachy (zejména u stringingu).
* **Robustní správa MQTT:** URL adresa MQTT brokeru se nyní automaticky validuje a v případě potřeby sama doplní chybějící protokol (např. `tcp://`).
* **Automatizované sestavování (Docker):** `Dockerfile` nyní využívá zástupné znaky pro automatické zpracování nových verzí bez nutnosti úprav souboru.

**Opravy chyb:**
* **Oprava komunikace Webhooků:** Vynucen protokol `HTTP_1_1` k vyřešení náhodných chyb `EOFException` / `received no bytes` při komunikaci s Node-RED a dalšími webhook receivery.
* **Oprava struktury JSON:** Vynucena lokalizace `Locale.US` při serializaci JSON zpráv, což zabraňuje poškození dat (záměna teček a čárek u desetinných čísel) na evropských hostitelských systémech.
* **Oprava pauzování (Moonraker):** Aplikace nyní místo standardního Moonraker API odesílá přímo G-Code makro `PAUSE` pro 100% spolehlivé zastavení tisku.
* **Opravy uživatelského rozhraní:** Byla opravena "poskakující" výška modálního okna pro úpravu tiskárny, tlačítko "Test Notifications" přesunuto na správné záložky a opravena chyba v odesílání testovacích dat.
* **Korekce textů:** Matoucí telemetrická hodnota "Extrusion Volume" (Objem extruze) byla přejmenována na korektní "Filament Used" (Spotřebovaný filament v mm).

## Licence
Tento projekt je licencován pod MIT licencí - podrobnosti viz soubor LICENSE.
