package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flagship.core.model.Environment;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpSnapshotFetcherTest {

    private HttpServer server;
    private volatile String lastRequest;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void respond(int status, String body, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastRequest = exchange.getRequestURI().toString();
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void fetchesSnapshotFromServer() throws IOException {
        respond(200, "{\"hello\":\"world\"}", 0);
        HttpSnapshotFetcher fetcher = new HttpSnapshotFetcher(
                url(), "checkout-app", Environment.PRODUCTION, Duration.ofSeconds(2));

        assertEquals("{\"hello\":\"world\"}", fetcher.fetch());
        assertEquals("/api/projects/checkout-app/snapshot?env=PRODUCTION", lastRequest);
    }

    @Test
    void trailingSlashInServerUrlIsFine() throws IOException {
        respond(200, "{}", 0);
        HttpSnapshotFetcher fetcher = new HttpSnapshotFetcher(
                url() + "/", "checkout-app", Environment.STAGING, Duration.ofSeconds(2));

        assertEquals("{}", fetcher.fetch());
        assertEquals("/api/projects/checkout-app/snapshot?env=STAGING", lastRequest);
    }

    @Test
    void serverErrorBecomesAnIOException() throws IOException {
        respond(500, "boom", 0);
        HttpSnapshotFetcher fetcher = new HttpSnapshotFetcher(
                url(), "checkout-app", Environment.PRODUCTION, Duration.ofSeconds(2));

        assertThrows(IOException.class, fetcher::fetch);
    }

    @Test
    void slowServerTimesOutQuickly() throws IOException {
        respond(200, "{}", 1500);
        HttpSnapshotFetcher fetcher = new HttpSnapshotFetcher(
                url(), "checkout-app", Environment.PRODUCTION, Duration.ofMillis(300));

        long start = System.nanoTime();
        assertThrows(IOException.class, fetcher::fetch);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(millis < 1000, "should give up near the 300 ms timeout, took " + millis + " ms");
    }

    @Test
    void unreachableServerBecomesAnIOException() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        HttpSnapshotFetcher fetcher = new HttpSnapshotFetcher(
                "http://127.0.0.1:" + freePort, "checkout-app",
                Environment.PRODUCTION, Duration.ofSeconds(2));

        assertThrows(IOException.class, fetcher::fetch);
    }
}