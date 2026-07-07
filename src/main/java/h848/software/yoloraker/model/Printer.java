package h848.software.yoloraker.model;

import java.util.UUID;

public class Printer {

    private String id;
    private String name;
    private String hostname;
    private String apiKey;
    private String webcamUrl;
    private String webhookUrl;
    private boolean webhookTelemetryEnabled;
    private boolean enabled;
    
    // AI Thresholds
    private float thresholdSpaghetti = 0.60f;
    private float thresholdStringing = 0.70f;
    private float thresholdZits = 0.70f;
    
    private boolean detectSpaghetti = true;
    private boolean detectStringing = true;
    private boolean detectZits = true;

    private String mqttBroker;
    private String mqttTopic;
    private String mqttUsername;
    private String mqttPassword;
    private String mqttClientId;
    private boolean mqttTelemetryEnabled;

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

    public boolean isWebhookTelemetryEnabled() {
        return webhookTelemetryEnabled;
    }

    public void setWebhookTelemetryEnabled(boolean webhookTelemetryEnabled) {
        this.webhookTelemetryEnabled = webhookTelemetryEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getThresholdSpaghetti() {
        return thresholdSpaghetti;
    }

    public void setThresholdSpaghetti(float thresholdSpaghetti) {
        this.thresholdSpaghetti = thresholdSpaghetti;
    }

    public float getThresholdStringing() {
        return thresholdStringing;
    }

    public void setThresholdStringing(float thresholdStringing) {
        this.thresholdStringing = thresholdStringing;
    }

    public float getThresholdZits() {
        return thresholdZits;
    }

    public void setThresholdZits(float thresholdZits) {
        this.thresholdZits = thresholdZits;
    }

    public String getMqttBroker() {
        return mqttBroker;
    }

    public void setMqttBroker(String mqttBroker) {
        this.mqttBroker = mqttBroker;
    }

    public String getMqttTopic() {
        return mqttTopic;
    }

    public void setMqttTopic(String mqttTopic) {
        this.mqttTopic = mqttTopic;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public void setMqttUsername(String mqttUsername) {
        this.mqttUsername = mqttUsername;
    }

    public String getMqttPassword() {
        return mqttPassword;
    }

    public void setMqttPassword(String mqttPassword) {
        this.mqttPassword = mqttPassword;
    }

    public String getMqttClientId() {
        return mqttClientId;
    }

    public void setMqttClientId(String mqttClientId) {
        this.mqttClientId = mqttClientId;
    }

    public boolean isMqttTelemetryEnabled() {
        return mqttTelemetryEnabled;
    }

    public void setMqttTelemetryEnabled(boolean mqttTelemetryEnabled) {
        this.mqttTelemetryEnabled = mqttTelemetryEnabled;
    }
    
    public boolean isDetectSpaghetti() {
        return detectSpaghetti;
    }

    public void setDetectSpaghetti(boolean detectSpaghetti) {
        this.detectSpaghetti = detectSpaghetti;
    }

    public boolean isDetectStringing() {
        return detectStringing;
    }

    public void setDetectStringing(boolean detectStringing) {
        this.detectStringing = detectStringing;
    }

    public boolean isDetectZits() {
        return detectZits;
    }

    public void setDetectZits(boolean detectZits) {
        this.detectZits = detectZits;
    }
}
