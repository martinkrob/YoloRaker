# YoloRaker

![Docker Pulls](https://img.shields.io/docker/pulls/h848/yoloraker?style=for-the-badge)
![License](https://img.shields.io/github/license/h848/yoloraker?style=for-the-badge)

*🌐 [English](README.md) | [Čeština](README.cs.md)*

YoloRaker je nenáročná aplikace pro 3D tiskárny s firmwarem Klipper a Moonraker, využívající umělou inteligenci. Poskytuje přehledný ovládací panel pro monitorování v reálném čase, sledování telemetrie a automatickou detekci chyb tisku pomocí lokálního strojového učení.


## Klíčové vlastnosti

* **AI detekce chyb tisku:** Vizuální analýza obrazu z webkamery v reálném čase, která detekuje „špagety“, „stringing“ a další vady. Vše běží 100% lokálně na procesoru (CPU) bez závislosti na cloudu.
* **Automatické pozastavení:** Automaticky pozastaví tisk přes Moonraker, pokud jistota AI (confidence) překročí vámi definované limity.
* **Telemetrie a analytika:** Sleduje teploty trysky/podložky, rychlost tisku, průběh a úroveň jistoty AI v průběhu času.
* **Upozornění (Notifikace):** Zabudovaná podpora Webhooků a MQTT pro zasílání upozornění přes Home Assistant, Node-RED nebo Discord.
* **Webové uživatelské rozhraní:** Jasný a intuitivní ovládací panel pro monitorování tiskáren a jejich AI stavů.
* **Chytré uchovávání dat:** Automaticky promazává starou telemetrii a snímky z alarmů, ale bezpečně uchová stanovený počet nedávných tisků bez ohledu na jejich stáří.

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
java -jar target/YoloRaker-1.0.0-jar-with-dependencies.jar
```

Aplikace se ve výchozím stavu spustí na portu 8080. Data se lokálně ukládají do složky `./data` vůči složce, ze které se aplikace spouští.

## Licence
Tento projekt je licencován pod MIT licencí - podrobnosti viz soubor LICENSE.
