# YoloRaker

![Docker Pulls](https://img.shields.io/docker/pulls/h848/yoloraker?style=for-the-badge)
![License](https://img.shields.io/github/license/h848/yoloraker?style=for-the-badge)

*🌐 [English](README.md) | [Čeština](README.cs.md)*

YoloRaker je nenáročná aplikace pro 3D tiskárny s firmwarem Klipper a Moonraker, využívající umělou inteligenci. Poskytuje přehledný ovládací panel pro monitorování v reálném čase, sledování telemetrie a automatickou detekci chyb tisku pomocí lokálního strojového učení.

![YoloRaker Dashboard](./docs/dashboard.png)

![YoloRaker Edit](./docs/edit.png)

![YoloRaker Live View](./docs/liveview.png)

![YoloRaker History](./docs/history_analytics.png)

## Klíčové vlastnosti

* **AI detekce chyb tisku:** Vizuální analýza obrazu z webkamery v reálném čase, která detekuje „špagety“, „stringing“ a další vady. Vše běží 100% lokálně na procesoru (CPU) bez závislosti na cloudu.
* **Automatické pozastavení:** Automaticky pozastaví tisk přes Moonraker, pokud jistota AI (confidence) překročí vámi definované limity.
* **Telemetrie a analytika:** Sleduje teploty trysky/podložky, rychlost tisku, průběh a úroveň jistoty AI v průběhu času.
* **Upozornění (Notifikace):** Zabudovaná podpora Webhooků a MQTT pro zasílání upozornění přes Home Assistant, Node-RED nebo Discord.
* **Webové uživatelské rozhraní:** Jasný a intuitivní ovládací panel pro monitorování tiskáren a jejich AI stavů.
* **Timelapse a Snímky:** Pravidelně ukládá snímky z webkamery během aktivního tisku a umožňuje jejich následné přehrání ve formě timelapse v prohlížeči historie.
* **Chytré uchovávání dat:** Automaticky promazává starou telemetrii, alarmy i uložené snímky, ale bezpečně uchová stanovený počet nedávných tisků bez ohledu na jejich stáří.

## Technologie

* **Backend:** Java 25, Javalin (Webový framework), JDBI v3 (Práce s databází), H2 (Integrovaná databáze)
* **Frontend:** Čistý JavaScript (Vanilla JS), HTML5, CSS3, Chart.js
* **AI/Strojové učení:** ONNX Runtime pro Javu (YOLOv8 modely)

## Docker Image

Předpřipravený kontejner je k dispozici na Docker Hubu. Návod na instalaci, konfiguraci a příklady pro `docker-compose` najdete v oficiálním repozitáři na Docker Hubu:

**[https://hub.docker.com/r/h848/yoloraker](https://hub.docker.com/r/h848/yoloraker)**

## Konfigurace

1. Otevřete prohlížeč a přejděte na `http://<vase-ip-adresa>:8080`
2. Přihlaste se výchozími údaji:
   * **Uživatelské jméno:** `admin`
   * **Heslo:** `admin`
3. Běžte do sekce Settings a okamžitě si změňte heslo!
4. Klikněte na Add Printer a zadejte IP adresu / hostname Moonrakeru a URL webkamery.

## Vývoj

Pokud si chcete zkompilovat YoloRaker ze zdrojových kódů:

### Požadavky
* Java JDK 25+
* Maven 3.8+

### Sestavení a spuštění
```bash
# Naklonování repozitáře
git clone https://github.com/h848/yoloraker.git
cd yoloraker

# Kompilace a zabalení
mvn clean package

# Spuštění aplikace
java -jar target/YoloRaker-1.0.3.jar
```

Aplikace se ve výchozím stavu spustí na portu 8080. Data se lokálně ukládají do složky `./data` vůči složce, ze které se aplikace spouští.

## Seznam změn (Changelog)

### v1.0.3 (Nejnovější)
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
