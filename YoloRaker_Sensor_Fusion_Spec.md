# Architektonická specifikace: YoloRaker - Sensor Fusion & Active Learning

Tento dokument slouží jako detailní technický návrh (blueprint) pro implementaci pokročilých analytických a učících funkcí do existujícího projektu **YoloRaker**. 

## 1. Cíle rozvoje
1. **Sensor Fusion:** Propojení vizuální inference (YOLOv11) s agregovanou telemetrií z tiskárny (Moonraker API) ke snížení falešných poplachů (False Positives).
2. **Časová synchronizace:** Implementace 10vteřinového agregovaného časového okna pro telemetrii, které bude lícovat se snímkovací frekvencí kamery.
3. **Active Learning (Human-in-the-loop):** Využití asynchronní zpětné vazby od uživatele prostřednictvím MQTT a Node-RED pro klasifikaci incidentů a sběr trénovacích dat pro budoucí fine-tuning modelu.

---

## 2. Návrh nových a upravených komponent v Javě

### 2.1. Telemetry Aggregator (Rozšíření `MoonrakerClient.java`)
Místo jednorázového dotazování na stav tiskárny bude klient průběžně agregovat data.
* **Proces:** Přes WebSocket bude naslouchat změnám stavu (`toolhead`, `extruder`, `heater_bed`).
* **Časové okno (Time Window):** Udržuje historii za posledních 10 vteřin (např. pomocí kruhové fronty nebo objektu uchovávajícího min/max/avg hodnoty za probíhající interval).
* **Výstup okna:** Kdykoliv kamera pořídí snímek, Aggregator vygeneruje statistický "snapshot" posledních 10s:
  * `distance_travelled_mm` (odhad na základě X,Y,Z pohybů)
  * `extruded_filament_mm`
  * `z_height_current` a `z_height_delta`
  * `bed_temp_variance` a `extruder_temp_variance` (pro detekci odlepení nebo ucpání)

### 2.2. Decision Layer (`FusionEngine.java`)
Nová vrstva stojící mezi stávajícím `AiDetector.java` a generováním alarmů.
* **Vstup:** Skóre z YOLO modelu (např. `Spaghetti: 0.65`) + `Telemetry Snapshot`.
* **Logika:** Křížová validace. Příklad: *Pokud YOLO detekuje anomálii s nízkou jistotou, ale telemetrie ukazuje, že extruder ztrácí teplotu nebo tiskárna je ve fázi `purge`, sníží se celkové skóre rizika a zabrání se falešnému poplachu.*
* **Výstup:** Vygenerování objektu `Incident`.

### 2.3. Incident Management & DB (`RetentionService.java` & Zápis)
* Každý vyhodnocený `Incident` (nad určitou hranici podezření) je uložen do databáze (`yoloraker.mv.db`).
* **Obsah záznamu:** Snímek (Base64 nebo cesta k souboru), JSON agregované telemetrie, skóre modelu a stav `status = PENDING_REVIEW`.
* `RetentionService` zajišťuje, že se ukládají primárně okrajové případy (Edge cases) a staré nepotvrzené incidenty se promazávají, aby nedošlo k zaplnění disku.

---

## 3. Integrace třetích stran: MQTT a Node-RED

YoloRaker bude fungovat jako čistá mikroslužba. Veškerou interakci s uživatelem (Telegram, Discord) řeší Node-RED.

### 3.1. MQTT Payload: Odeslání incidentu (YoloRaker -> Node-RED)
Když `FusionEngine` vyhodnotí reálné riziko, YoloRaker publikuje zprávu na topic `yoloraker/alerts/anomaly`.

```json
{
  "incident_id": "INC-20260801-1234",
  "timestamp": 1722525539000,
  "yolo_confidence": 0.82,
  "anomaly_type": "spaghetti",
  "printer_state": "paused",
  "telemetry_window_10s": {
    "z_height": 45.2,
    "extrusion_mm": 12.5,
    "bed_temp_variance": -2.1,
    "extruder_temp_variance": 0.4
  },
  "image_url": "http://<yoloraker_ip>:<port>/api/incidents/INC-20260801-1234/image"
}
```
*Node-RED si pomocí `image_url` stáhne obrázek, přidá uživatelská tlačítka a odešle notifikaci na mobil.*

### 3.2. MQTT Payload: Zpětná vazba (Node-RED -> YoloRaker)
Jakmile uživatel v notifikaci klikne na tlačítko (např. "Falešný poplach"), Node-RED přes Moonraker obnoví tisk a následně pošle YoloRakeru zpětnou vazbu do topicu `yoloraker/feedback`.

```json
{
  "incident_id": "INC-20260801-1234",
  "ground_truth": "false_positive",
  "user_action": "resumed"
}
```
*YoloRaker na základě této zprávy najde incident v databázi, označí jej jako `false_positive` a uzavře ho. Záznam tak zůstává perfektně anotovaný pro budoucí offline přetrénování modelu.*

---

## 4. Vývojové kroky pro zahájení implementace (Antigravity IDE)

1. **Infrastruktura zpráv (MQTT):**
   * Vytvořit/upravit MQTT publisher v projektu, který umí odesílat definovaný JSON incidentu.
   * Vytvořit MQTT subscribera pro naslouchání na topicu `yoloraker/feedback` a propojit ho s DB vrstvou.
2. **Sběr telemetrie:**
   * Upravit `MoonrakerClient.java` pro kontinuální sběr (WebSocket listener) vybraných stavů.
   * Vytvořit třídu `TelemetryAggregator` (sběr do bufferu a generování 10s snapshotu).
3. **Logika fúze:**
   * Založit třídu `FusionEngine.java`.
   * Propojit timer z `CameraClient` s `TelemetryAggregator` -> zajistit synchronizaci `[Snímek + 10s Telemetrický snapshot]`.
4. **Rozšíření DB modelu:**
   * V `yoloraker.mv.db` upravit strukturu (nebo vytvořit entitu `Incident`), aby dokázala pojmout JSON telemetrie a anotaci `ground_truth`.
5. **Node-RED konfigurace (Mimo Java projekt):**
   * Po nasazení kódu naklikat flow v Node-REDu (MQTT In -> HTTP Request pro obrázek -> Telegram bot sendPhoto s inline tlačítky -> Webhook webhook return -> MQTT Out).
