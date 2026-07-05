package h848.software.yoloraker.moonraker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import h848.software.yoloraker.model.Printer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class MoonrakerClient {
    private static final Logger logger = LoggerFactory.getLogger(MoonrakerClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public MoonrakerClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.mapper = new ObjectMapper();
    }

    public PrinterTelemetry getTelemetry(Printer printer) {
        PrinterTelemetry telemetry = new PrinterTelemetry();
        String baseUrl = formatBaseUrl(printer.getHostname());
        
        try {
            // 1. Fetch Printer Info (Klipper State)
            HttpRequest infoReq = buildRequest(baseUrl + "/printer/info", printer.getApiKey());
            HttpResponse<String> infoRes = httpClient.send(infoReq, HttpResponse.BodyHandlers.ofString());
            
            if (infoRes.statusCode() == 200) {
                JsonNode root = mapper.readTree(infoRes.body());
                JsonNode result = root.path("result");
                telemetry.setKlipperState(result.path("state").asText("unknown"));
                telemetry.setKlipperMessage(result.path("state_message").asText(""));
            } else {
                telemetry.setKlipperState("error");
                telemetry.setKlipperMessage("HTTP " + infoRes.statusCode());
            }

            // 2. Fetch Detailed Objects (Temps, Position, Print Stats)
            String queryUrl = baseUrl + "/printer/objects/query?print_stats&display_status&extruder&heater_bed&toolhead&fan&motion_report";
            HttpRequest queryReq = buildRequest(queryUrl, printer.getApiKey());
            HttpResponse<String> queryRes = httpClient.send(queryReq, HttpResponse.BodyHandlers.ofString());
            
            if (queryRes.statusCode() == 200) {
                JsonNode root = mapper.readTree(queryRes.body());
                JsonNode status = root.path("result").path("status");
                
                // Print Stats
                JsonNode printStats = status.path("print_stats");
                telemetry.setPrintState(printStats.path("state").asText("unknown"));
                telemetry.setFilename(printStats.path("filename").asText(""));
                telemetry.setPrintDuration(printStats.path("print_duration").asDouble(0.0));
                telemetry.setFilamentUsed(printStats.path("filament_used").asDouble(0.0));
                
                // Display Status (Progress)
                JsonNode displayStatus = status.path("display_status");
                telemetry.setProgress(displayStatus.path("progress").asDouble(0.0) * 100.0); // Convert to percentage
                
                // Temperatures
                JsonNode extruder = status.path("extruder");
                telemetry.setExtruderTemp(extruder.path("temperature").asDouble(0.0));
                telemetry.setExtruderTarget(extruder.path("target").asDouble(0.0));
                
                JsonNode heaterBed = status.path("heater_bed");
                telemetry.setBedTemp(heaterBed.path("temperature").asDouble(0.0));
                telemetry.setBedTarget(heaterBed.path("target").asDouble(0.0));
                
                // Toolhead Position
                JsonNode toolhead = status.path("toolhead");
                JsonNode pos = toolhead.path("position");
                if (pos.isArray() && pos.size() >= 3) {
                    telemetry.setX(pos.get(0).asDouble(0.0));
                    telemetry.setY(pos.get(1).asDouble(0.0));
                    telemetry.setZ(pos.get(2).asDouble(0.0));
                }
                
                // Fan
                JsonNode fan = status.path("fan");
                telemetry.setFanSpeed(fan.path("speed").asDouble(0.0) * 100.0); // Convert to percentage
                
                // Motion Report (Speed)
                JsonNode motionReport = status.path("motion_report");
                telemetry.setPrintSpeed(motionReport.path("live_velocity").asDouble(0.0));
            }
        } catch (Exception e) {
            logger.warn("Failed to connect to printer {}: {}", printer.getName(), e.getMessage());
            telemetry.setKlipperState("offline");
            telemetry.setKlipperMessage("Connection refused or timeout: " + e.getMessage());
        }
        
        return telemetry;
    }

    /**
     * Sends a pause command to the printer via Moonraker API.
     */
    public boolean pausePrint(Printer printer) {
        try {
            String baseUrl = formatBaseUrl(printer.getHostname());
            String targetUrl = baseUrl + "/printer/print/pause";
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.noBody());
                    
            if (printer.getApiKey() != null && !printer.getApiKey().trim().isEmpty()) {
                requestBuilder.header("X-Api-Key", printer.getApiKey());
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                logger.info("Successfully paused printer: {}", printer.getName());
                return true;
            } else {
                logger.error("Failed to pause printer {}. HTTP {}: {}", printer.getName(), response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            logger.error("Exception while pausing printer {}", printer.getName(), e);
            return false;
        }
    }

    private HttpRequest buildRequest(String url, String apiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(2))
                .GET();
                
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.header("X-Api-Key", apiKey.trim());
        }
        return builder.build();
    }

    private String formatBaseUrl(String hostname) {
        // Simple heuristic to format hostname to a valid URL.
        // Assuming Moonraker runs on port 7125 if no port is specified.
        String url = hostname.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        if (!url.matches(".*:[0-9]+.*")) {
            url = url + ":7125"; // Default Moonraker port
        }
        return url;
    }
}
