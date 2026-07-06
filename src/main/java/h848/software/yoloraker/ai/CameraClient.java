package h848.software.yoloraker.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class CameraClient {

    private final HttpClient client;

    public CameraClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Downloads a snapshot image from the given webcam URL. Expected to return
     * JPEG bytes.
     *
     * @param webcamUrl
     * @return
     * @throws java.lang.Exception
     */
    public byte[] getSnapshot(String webcamUrl) throws Exception {
        if (webcamUrl == null || webcamUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Webcam URL is empty");
        }
        
        // Auto-correct common mistake where user provides a stream URL instead of snapshot
        if (webcamUrl.contains("action=stream")) {
            webcamUrl = webcamUrl.replace("action=stream", "action=snapshot");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webcamUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download snapshot: HTTP " + response.statusCode());
        }

        return response.body();
    }
}
