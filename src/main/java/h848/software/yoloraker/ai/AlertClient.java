package h848.software.yoloraker.ai;

import h848.software.yoloraker.model.Printer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
                "{\"event\": \"spaghetti_detected\", \"printerId\": \"%s\", \"printerName\": \"%s\", \"confidence\": %.2f}",
                printer.getId(),
                printer.getName().replace("\"", "\\\""),
                result.getMaxConfidence()
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
        } catch (Exception e) {
            logger.error("Exception while sending webhook for printer {}", printer.getName(), e);
        }
    }
}
