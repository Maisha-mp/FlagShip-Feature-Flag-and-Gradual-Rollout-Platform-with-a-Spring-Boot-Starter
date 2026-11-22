package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flagship.core.model.Environment;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FlagShipAutoConfigurationTest {

    private static final String SNAPSHOT = """
            {"project":"checkout-app","environment":"PRODUCTION","generatedAt":"2026-11-19T07:49:51.554061200Z",
             "flags":[{"key":"new-checkout","type":"BOOLEAN","variations":{"on":true,"off":false},
               "config":{"killSwitchOn":false,"offVariation":"off","defaultVariation":"off",
                 "userTargets":{},"rules":[],"rollout":null}}]}
            """;

    @TempDir
    Path dir;

    /** Only the settings class, to test binding without starting anything. */
    @EnableConfigurationProperties(FlagShipProperties.class)
    static class PropertiesOnly { }

    private final ApplicationContextRunner propertiesRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

    private final ApplicationContextRunner fullRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlagShipAutoConfiguration.class));

    private String snapshotFileProperty() {
        return "flagship.snapshot-file=" + dir.resolve("snapshot.json").toString().replace('\\', '/');
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }

    @Test
    void defaultsAreApplied() {
        propertiesRunner.run(context -> {
            FlagShipProperties p = context.getBean(FlagShipProperties.class);
            assertEquals(Environment.PRODUCTION, p.getEnvironment());
            assertEquals(Duration.ofSeconds(30), p.getPollInterval());
            assertEquals(Duration.ofSeconds(2), p.getStartupTimeout());
            assertEquals(Duration.ofSeconds(5), p.getRequestTimeout());
            assertEquals("http://localhost:8080", p.getServerUrl());
            assertEquals(Path.of(".flagship", "snapshot.json"), p.getSnapshotFile());
        });
    }

    @Test
    void valuesFromPropertiesAreBound() {
        propertiesRunner.withPropertyValues(
                        "flagship.project=checkout-app",
                        "flagship.environment=STAGING",
                        "flagship.poll-interval=10s",
                        "flagship.server-url=http://example:9000")
                .run(context -> {
                    FlagShipProperties p = context.getBean(FlagShipProperties.class);
                    assertEquals("checkout-app", p.getProject());
                    assertEquals(Environment.STAGING, p.getEnvironment());
                    assertEquals(Duration.ofSeconds(10), p.getPollInterval());
                    assertEquals("http://example:9000", p.getServerUrl());
                });
    }

    @Test
    void canBeSwitchedOff() {
        fullRunner.withPropertyValues("flagship.enabled=false")
                .run(context -> {
                    assertEquals(0, context.getBeanNamesForType(FlagShipProperties.class).length);
                    assertEquals(0, context.getBeanNamesForType(SnapshotStore.class).length);
                });
    }

    @Test
    void missingProjectFailsWithAClearMessage() {
        fullRunner.withPropertyValues(snapshotFileProperty())
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertTrue(rootMessage(context.getStartupFailure()).contains("flagship.project"));
                });
    }

    @Test
    void appStartsWithoutServerAndWithoutLocalCopy() {
        fullRunner.withPropertyValues(
                        "flagship.project=checkout-app",
                        "flagship.server-url=http://127.0.0.1:1",
                        snapshotFileProperty())
                .run(context -> {
                    assertNotNull(context.getBean(SnapshotSynchronizer.class));
                    assertFalse(context.getBean(SnapshotStore.class).isLoaded());
                });
    }

    @Test
    void usesLocalCopyWhenServerIsDown() throws IOException {
        Files.writeString(dir.resolve("snapshot.json"), SNAPSHOT);

        fullRunner.withPropertyValues(
                        "flagship.project=checkout-app",
                        "flagship.server-url=http://127.0.0.1:1",
                        snapshotFileProperty())
                .run(context ->
                        assertNotNull(context.getBean(SnapshotStore.class).flag("new-checkout")));
    }

    @Test
    void loadsFromServerAndSavesLocalCopy() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = SNAPSHOT.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            fullRunner.withPropertyValues(
                            "flagship.project=checkout-app",
                            "flagship.server-url=http://127.0.0.1:" + server.getAddress().getPort(),
                            snapshotFileProperty())
                    .run(context -> {
                        assertNotNull(context.getBean(SnapshotStore.class).flag("new-checkout"));
                        assertTrue(Files.exists(dir.resolve("snapshot.json")));
                    });
        } finally {
            server.stop(0);
        }
    }
}