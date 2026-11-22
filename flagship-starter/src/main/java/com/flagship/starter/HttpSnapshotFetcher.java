package com.flagship.starter;

import com.flagship.core.model.Environment;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Downloads the snapshot from the FlagShip server using the JDK's built-in HTTP client. */
public final class HttpSnapshotFetcher implements SnapshotFetcher {

    private final HttpClient client;
    private final URI uri;
    private final Duration timeout;

    public HttpSnapshotFetcher(String serverUrl, String project,
                               Environment environment, Duration timeout) {
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("flagship.project must be set");
        }
        String base = serverUrl.endsWith("/")
                ? serverUrl.substring(0, serverUrl.length() - 1)
                : serverUrl;
        this.uri = URI.create(base + "/api/projects/"
                + URLEncoder.encode(project, StandardCharsets.UTF_8)
                + "/snapshot?env=" + environment.name());
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public String fetch() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Server answered " + response.statusCode() + " for " + uri);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching the snapshot", e);
        }
    }
}