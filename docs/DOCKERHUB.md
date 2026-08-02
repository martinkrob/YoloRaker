# YoloRaker

**Short description (100 char field):**

> AI print-failure detection for Klipper/Moonraker. Runs locally on CPU and pauses failing prints.

---

**Full description (paste everything below this line):**

---

# YoloRaker

AI-powered print monitoring for 3D printers running **Klipper** and **Moonraker**. YoloRaker watches your webcam feed, spots print failures, and pauses the print via Moonraker before it turns into a bird's nest.

Everything runs on your own hardware, on CPU, with **no cloud service and no internet access required** — the whole UI works on an isolated printer network.

![YoloRaker dashboard](https://raw.githubusercontent.com/martinkrob/YoloRaker/master/docs/dashboard.png)

![YoloRaker Live View](https://raw.githubusercontent.com/martinkrob/YoloRaker/master/docs/liveview.png)

---

## Quick start

```bash
docker run -d \
  --name yoloraker \
  --restart unless-stopped \
  -p 8080:8080 \
  -v /path/on/host/yoloraker:/data \
  -e TZ=Europe/Prague \
  -e YOLORAKER_DB_PATH=/data/yoloraker \
  -e YOLORAKER_DATA_PATH=/data \
  h848/yoloraker:latest
```

Then open `http://<your-ip>:8080` and log in with **`admin` / `admin`**. Change the password in Settings straight away.

### docker-compose

```yaml
services:
  yoloraker:
    image: h848/yoloraker:latest
    container_name: yoloraker
    restart: unless-stopped
    ports:
      - "8080:8080"
    volumes:
      - ./data:/data
    environment:
      - TZ=Europe/Prague
      - YOLORAKER_DB_PATH=/data/yoloraker
      - YOLORAKER_DATA_PATH=/data
```

---

## ⚠️ Both path variables matter

`YOLORAKER_DB_PATH` and `YOLORAKER_DATA_PATH` must **both** point inside the mounted volume.

They cover different things — the database on one hand, snapshots and uploaded AI models on the other. Set only the first and your timelapse snapshots and custom `.onnx` models are written to the container filesystem and disappear the next time the container is recreated.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `YOLORAKER_DB_PATH` | `./data/yoloraker` | H2 database path, **without** a file extension |
| `YOLORAKER_DATA_PATH` | `./data` | Snapshots and uploaded models |
| `YOLORAKER_PORT` | `8080` | HTTP port inside the container |
| `YOLORAKER_TELEMETRY_INTERVAL_MS` | `1000` | Telemetry sampling period. Minimum 200 ms. `0` disables continuous sampling and falls back to querying on demand — useful on a very constrained host. |
| `TZ` | UTC | Timezone, so charts and history show your local time |

---

## Supported platforms

YoloRaker bundles ONNX Runtime, which ships native libraries for **linux/amd64** and **linux/arm64** only.

* ✅ x86-64 servers, NAS boxes, mini PCs
* ✅ Raspberry Pi 4 / 5 **running a 64-bit OS**
* ❌ 32-bit ARM (`armv7`/`armhf`) — including Raspberry Pi OS 32-bit, even on a Pi 4. There is no 32-bit ONNX Runtime native, so AI detection cannot run.

If you are on a Pi and unsure, `uname -m` should report `aarch64`, not `armv7l`.

## Resource requirements

Measured with two printers configured and inference running every 10 seconds:

| | |
|---|---|
| Memory | **~500 MB RSS** |
| CPU | Idle between cycles; one inference per printer per 10 s |
| Disk | Grows with retained prints — mostly timelapse snapshots |

Most of that memory is the ONNX Runtime's off-heap allocation, not the Java heap: capping the heap with `-Xmx256m` only brought total usage from 506 MB down to 482 MB. **Limiting the JVM heap will not meaningfully shrink this** — give the container at least 768 MB.

Each printer loads its own inference session, so budget more if you monitor several.

---

## What it does

* **AI failure detection** — spaghetti, stringing and zits, from your existing webcam
* **Sensor fusion** — every detection is cross-checked against printer telemetry *and* against what this particular print has looked like so far. This is what suppresses the false alarms that a purge line in frame, exposed infill or tree supports would otherwise cause.
* **Only real failures stop a print** — spaghetti pauses via Moonraker; stringing and zits are cosmetic defects that pausing cannot repair, so they are reported and logged while the print continues
* **Shadow mode** — fusion can record its verdicts without acting on them, so you can see what it *would* have done before trusting it
* **Notifications** — Webhooks and MQTT for Home Assistant, Node-RED, Discord
* **Timelapse** — periodic snapshots, played back in the history viewer
* **Custom models** — upload your own `.onnx` and assign it per printer

---

## When a detection becomes a real failure

A model that shouts at every suspicious frame gets muted within a week. YoloRaker puts four
things between "the model saw something" and "your print stops".

**1. It has to clear the threshold.** Each class has its own, set per printer. Default 0.60 for
spaghetti, 0.70 for stringing and zits.

**2. It has to stand out from this print.** A purge line parked in the camera's view, exposed
zigzag infill, tree supports — the model reads all of them as defects, and printer telemetry
cannot help, because the printer is behaving perfectly in every one of those cases. What separates
them is time. YoloRaker continuously learns what *this* print normally scores and asks how far a
detection rises above that. Scenery pulls the baseline up with it and stays silent; a failure
developing over a minute or two leaves the baseline behind.

**3. It has to make sense against the telemetry.** The ten seconds of printer data leading up to
the frame can slow a detection down: nothing was extruded, the nozzle is still heating, the print
started ninety seconds ago, it just resumed from a pause. These only slow the count — a genuine
failure persists frame after frame and still gets there, just later. They can delay an alarm; they
can never suppress one.

**4. It has to persist.** Every frame adds to a confirmation level, weighted by how sure the model
was. A clean frame subtracts. Only when that level fills does anything happen:

| Model confidence | Time to act |
|---|---|
| Just over the threshold | ~50 s |
| Around 0.70–0.75 | ~40 s |
| 0.80 and above | ~30 s |

The dashboard shows that level filling, so you see a failure being confirmed before the print
stops, not after.

**Then, and only then:** spaghetti pauses the print via Moonraker. Stringing and zits are surface
defects — pausing cannot repair them and the print is usually still usable — so they are reported
and logged, once per class per print, and the printer carries on.

Two rules are fixed and not configurable. A detection at 0.90 or above ignores every telemetry
excuse, because those are inferences about context and inferences can be wrong. It still does not
ignore the learned baseline, because that is a measurement of what this print actually looks like.

![Analytics with fusion baselines](https://raw.githubusercontent.com/martinkrob/YoloRaker/master/docs/analytics.png)

*Analytics: one panel per class, each with the baseline this print has established (shaded), its
threshold (dashed) and hatching where telemetry held a detection back.*

![Per-printer AI settings](https://raw.githubusercontent.com/martinkrob/YoloRaker/master/docs/edit_1.png)

---

## Upgrading to 1.1.0

Database migrations run automatically on startup. Stop the container, pull, start — no manual steps, no data loss.

**One thing to check afterwards.** Retention used to be three separate limits (telemetry rows, alarms, print jobs). It is now a single setting: **how many recent prints to keep**, with everything belonging to them — telemetry, incidents, snapshots — kept together.

On first start your previous print-job limit is carried over so nothing is deleted. But under the new meaning that number is probably far larger than you want: the old default of 1000 now means full telemetry and snapshots for a thousand prints, where the old row caps used to bound it.

👉 Open **Settings → Data Retention** and set it to something sensible, e.g. 20–50.

### Also new in 1.1.0

* **Confirmation meters** replace raw confidence percentages. The dashboard shows how close each class is to acting, so you get a warning *before* a pause instead of a number that spikes and means nothing.
* **Printer cards** with a live camera preview, progress, elapsed and estimated remaining time, temperatures and per-class meters.
* **Analytics rebuilt around fusion** — one panel per class with its learned baseline, threshold and the stretches where telemetry held a detection back. Hovering shows exactly which rules fired.
* **Live View as a standalone page**, bookmarkable and suited to a second monitor.
* **Incident review** — mark each incident a real failure or a false alarm. Reviewed incidents are kept permanently, whatever the retention setting, and build a labelled dataset for future model tuning.
* Detection class switches moved into Edit Printer, each paired with its own threshold.
* A pile of fixes: the Moonraker client now reads the real toolhead position rather than the planned one, an unreachable printer is logged once per outage instead of once per poll, and the baseline survives an AI pause.

---

## Getting a webcam URL

YoloRaker wants a **snapshot** URL, not a stream. With the usual `mjpg-streamer` / `crowsnest` setup that is:

```
http://<printer-ip>:8080/?action=snapshot
```

A `?action=stream` URL is accepted and converted automatically.

The webcam is optional — without one you still get full telemetry, print history and analytics, just no AI detection.

---

## License

MIT.
