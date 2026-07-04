package h848.software.yoloraker.model;

import java.util.UUID;

public class Printer {

    private String id;
    private String name;
    private String hostname;
    private String apiKey;
    private String webcamUrl;
    private String webhookUrl;
    private boolean enabled;

    public Printer() {
        // Default constructor for Jackson
    }

    public Printer(String name, String hostname, String apiKey, String webcamUrl, String webhookUrl, boolean enabled) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.hostname = hostname;
        this.apiKey = apiKey;
        this.webcamUrl = webcamUrl;
        this.webhookUrl = webhookUrl;
        this.enabled = enabled;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebcamUrl() {
        return webcamUrl;
    }

    public void setWebcamUrl(String webcamUrl) {
        this.webcamUrl = webcamUrl;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
