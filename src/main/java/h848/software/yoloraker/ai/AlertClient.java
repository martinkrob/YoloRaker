package h848.software.yoloraker.ai;

import h848.software.yoloraker.model.Printer;
import h848.software.yoloraker.moonraker.PrinterTelemetry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlertClient {

    private static final Logger logger = LoggerFactory.getLogger(AlertClient.class);
    private final HttpClient client;

    public AlertClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public void sendWebhook(Printer printer, DetectionResult result) {
        if (printer.getWebhookUrl() == null || printer.getWebhookUrl().trim().isEmpty()) {
            return;
        }

        try {
            // Build a simple JSON payload
            String jsonPayload = String.format(
                    "{\"event\": \"print_failure_detected\", \"type\": \"%s\", \"printerId\": \"%s\", \"printerName\": \"%s\", \"confidence\": %.2f}",
                    result.getHighestFailureType().name().toLowerCase(),
                    printer.getId(),
                    printer.getName().replace("\"", "\\\""),
                    result.getHighestConfidence()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(printer.getWebhookUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Webhook sent successfully for printer {}", printer.getName());
            } else {
                logger.error("Failed to send webhook for printer {}. HTTP {}: {}", printer.getName(), response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Exception while sending webhook for printer {}", printer.getName(), e);
        }
    }

    public void sendMqttMessage(Printer printer, DetectionResult result) {
        if (printer.getMqttBroker() == null || printer.getMqttBroker().trim().isEmpty() || 
            printer.getMqttTopic() == null || printer.getMqttTopic().trim().isEmpty()) {
            return;
        }

        MqttClient mqttClient = null;
        try {
            String jsonPayload = String.format(
                    "{\"event\": \"print_failure_detected\", \"type\": \"%s\", \"printerId\": \"%s\", \"printerName\": \"%s\", \"confidence\": %.2f}",
                    result.getHighestFailureType().name().toLowerCase(),
                    printer.getId(),
                    printer.getName().replace("\"", "\\\""),
                    result.getHighestConfidence()
            );

            // Create client with custom or random client ID
            String clientId = (printer.getMqttClientId() != null && !printer.getMqttClientId().trim().isEmpty()) 
                                ? printer.getMqttClientId().trim() 
                                : "YoloRaker_" + System.currentTimeMillis();
            mqttClient = new MqttClient(printer.getMqttBroker(), clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(3);
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);

            if (printer.getMqttUsername() != null && !printer.getMqttUsername().isEmpty()) {
                options.setUserName(printer.getMqttUsername());
                if (printer.getMqttPassword() != null) {
                    options.setPassword(printer.getMqttPassword().toCharArray());
                }
            }

            mqttClient.connect(options);
            
            MqttMessage message = new MqttMessage(jsonPayload.getBytes());
            message.setQos(1);
            mqttClient.publish(printer.getMqttTopic(), message);
            
            logger.info("MQTT alert published successfully for printer {} to topic {}", printer.getName(), printer.getMqttTopic());

        } catch (MqttException e) {
            logger.error("Exception while sending MQTT alert for printer {} to broker {}", printer.getName(), printer.getMqttBroker(), e);
        } finally {
            if (mqttClient != null && mqttClient.isConnected()) {
                try {
                    mqttClient.disconnect();
                    mqttClient.close();
                } catch (MqttException ex) {
                    logger.error("Failed to disconnect MQTT client for printer {}", printer.getName(), ex);
                }
            }
        }
    }

    public void sendTelemetryWebhook(Printer printer, PrinterTelemetry telemetry, DetectionResult result) {
        if (printer.getWebhookUrl() == null || printer.getWebhookUrl().trim().isEmpty() || !printer.isWebhookTelemetryEnabled()) {
            return;
        }

        try {
            String jsonPayload = String.format(
                    "{\"event\": \"telemetry\", \"printerId\": \"%s\", \"printerName\": \"%s\", \"printState\": \"%s\", \"progress\": %.2f, \"extruderTemp\": %.1f, \"bedTemp\": %.1f, \"aiSpaghetti\": %.2f, \"aiStringing\": %.2f, \"aiZits\": %.2f}",
                    printer.getId(),
                    printer.getName().replace("\"", "\\\""),
                    telemetry.getPrintState() != null ? telemetry.getPrintState() : "unknown",
                    telemetry.getProgress(),
                    telemetry.getExtruderTemp(),
                    telemetry.getBedTemp(),
                    result != null ? result.getConfSpaghetti() : 0.0,
                    result != null ? result.getConfStringing() : 0.0,
                    result != null ? result.getConfZits() : 0.0
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(printer.getWebhookUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            logger.error("Exception while sending telemetry webhook for printer {}", printer.getName(), e);
        }
    }

    public void sendTelemetryMqtt(Printer printer, PrinterTelemetry telemetry, DetectionResult result) {
        if (printer.getMqttBroker() == null || printer.getMqttBroker().trim().isEmpty() || 
            printer.getMqttTopic() == null || printer.getMqttTopic().trim().isEmpty() ||
            !printer.isMqttTelemetryEnabled()) {
            return;
        }

        MqttClient mqttClient = null;
        try {
            String jsonPayload = String.format(
                    "{\"event\": \"telemetry\", \"printerId\": \"%s\", \"printerName\": \"%s\", \"printState\": \"%s\", \"progress\": %.2f, \"extruderTemp\": %.1f, \"bedTemp\": %.1f, \"aiSpaghetti\": %.2f, \"aiStringing\": %.2f, \"aiZits\": %.2f}",
                    printer.getId(),
                    printer.getName().replace("\"", "\\\""),
                    telemetry.getPrintState() != null ? telemetry.getPrintState() : "unknown",
                    telemetry.getProgress(),
                    telemetry.getExtruderTemp(),
                    telemetry.getBedTemp(),
                    result != null ? result.getConfSpaghetti() : 0.0,
                    result != null ? result.getConfStringing() : 0.0,
                    result != null ? result.getConfZits() : 0.0
            );

            String clientId = (printer.getMqttClientId() != null && !printer.getMqttClientId().trim().isEmpty()) 
                                ? printer.getMqttClientId().trim() + "_tel" 
                                : "YoloRaker_Tel_" + System.currentTimeMillis();
            mqttClient = new MqttClient(printer.getMqttBroker(), clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(3);
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);

            if (printer.getMqttUsername() != null && !printer.getMqttUsername().isEmpty()) {
                options.setUserName(printer.getMqttUsername());
                if (printer.getMqttPassword() != null) {
                    options.setPassword(printer.getMqttPassword().toCharArray());
                }
            }

            mqttClient.connect(options);
            
            MqttMessage message = new MqttMessage(jsonPayload.getBytes());
            message.setQos(0); // QoS 0 for telemetry is fine
            mqttClient.publish(printer.getMqttTopic(), message);
            
        } catch (MqttException e) {
            logger.error("Exception while sending telemetry MQTT for printer {} to broker {}", printer.getName(), printer.getMqttBroker(), e);
        } finally {
            if (mqttClient != null && mqttClient.isConnected()) {
                try {
                    mqttClient.disconnect();
                    mqttClient.close();
                } catch (MqttException ex) {
                    // ignore
                }
            }
        }
    }
}
