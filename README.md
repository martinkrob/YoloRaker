# YoloRaker

![Docker Pulls](https://img.shields.io/docker/pulls/h848/yoloraker?style=for-the-badge)
![License](https://img.shields.io/github/license/h848/yoloraker?style=for-the-badge)

*🌐 [English](README.md) | [Čeština](README.cs.md)*

YoloRaker is a lightweight, AI-powered companion application for 3D printers running Klipper and Moonraker. It provides a clear dashboard for real-time monitoring, telemetry tracking, and automated print failure detection using local machine learning.

![YoloRaker Dashboard](./docs/dashboard.png)

![YoloRaker Edit](./docs/edit.png)

![YoloRaker Live View](./docs/liveview.png)

![YoloRaker History](./docs/history_analytics.png)

## Key Features

* **AI Print Failure Detection:** Real-time visual analysis of your webcam feed to detect spaghetti, stringing, and zits before they ruin your print. Runs 100% locally on CPU without external cloud services.
* **Auto-Pause:** Automatically pauses the print via Moonraker if AI confidence exceeds your defined thresholds.
* **Telemetry & Analytics:** Tracks extruder/bed temperatures, print speed, progress, and AI confidence levels over time.
* **Notifications:** Built-in support for Webhooks and MQTT to alert you via Home Assistant, Node-RED, or Discord.
* **Web UI:** A clear and intuitive dashboard to monitor your printers and their AI detection status.
* **Timelapse & Snapshots:** Periodically saves webcam snapshots during active prints and allows playing them back as a timelapse in the history viewer.
* **Smart Data Retention:** Automatically cleans up old telemetry, snapshots, and alarm data while keeping a defined number of recent prints safe.

## Tech Stack

* **Backend:** Java 25, Javalin (Web Framework), JDBI v3 (Database Mapping), H2 (Embedded Database)
* **Frontend:** Vanilla JavaScript, HTML5, CSS3, Chart.js
* **AI/ML:** ONNX Runtime for Java (YOLOv8 models)

## Docker Image

A pre-built, ready-to-use Docker container is available on Docker Hub. For installation instructions, configuration details, and docker-compose examples, please visit the official Docker Hub repository:

**[https://hub.docker.com/r/h848/yoloraker](https://hub.docker.com/r/h848/yoloraker)**

## Configuration

1. Open your browser and navigate to `http://<your-ip>:8080`
2. Log in with the default credentials:
   * **Username:** `admin`
   * **Password:** `admin`
3. Immediately go to Settings to change your default password!
4. Click Add Printer and enter your Moonraker IP/Hostname and Webcam URL.

## Development

If you want to build YoloRaker from source:

### Prerequisites
* Java JDK 25+
* Maven 3.8+

### Build & Run
```bash
# Clone the repository
git clone https://github.com/h848/yoloraker.git
cd yoloraker

# Compile and package
mvn clean package

# Run the application
java -jar target/YoloRaker-1.0.3.jar
```

The application will start on port 8080 by default. Data is stored locally in the `./data` directory relative to the execution path.

## Changelog

### v1.0.3 (Latest)
**New Features & Enhancements:**
* **Granular AI Detection Toggles**: You can now enable or disable specific detection classes (Spaghetti, Stringing, Zits) directly from the dashboard table.
* **Modernized UI**: Updated the primary color scheme to a modern Violet/Purple and unified the UI toggle switches for a more premium look.
* **Improved AI Stability**: Increased the required number of consecutive detections from 3 to 5 to significantly reduce false positive alarms (especially for stringing).
* **Robust MQTT Handling**: The MQTT broker URL now automatically corrects missing protocols (e.g. prepends `tcp://` automatically).
* **Automated Docker Builds**: The `Dockerfile` now uses a wildcard to automatically handle version bumps.

**Bug Fixes:**
* **Webhook Communication Fix**: Forced `HTTP_1_1` to resolve random `EOFException` / `received no bytes` errors when communicating with Node-RED and other webhook receivers.
* **JSON Payload Fix**: Enforced `Locale.US` during JSON serialization to prevent payload corruption (commas vs. dots in decimal numbers) on non-US host systems.
* **Moonraker Pause Fix**: Switched from Moonraker's standard pause API to directly executing the `PAUSE` G-Code script for 100% reliable print pausing.
* **UI Bug Fixes**: Fixed the jumping height of the Edit Printer modal, moved the "Test Notifications" button to the correct tabs, and fixed the JavaScript bindings for the test button.
* **Label Correction**: Renamed the confusing "Extrusion Volume" telemetry metric to "Filament Used" (mm).

## License
This project is licensed under the MIT License - see the LICENSE file for details.
