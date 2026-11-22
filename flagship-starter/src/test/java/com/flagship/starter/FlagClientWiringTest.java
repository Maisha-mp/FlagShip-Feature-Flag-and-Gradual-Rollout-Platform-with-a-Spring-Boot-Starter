package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FlagClientWiringTest {

    @TempDir
    Path dir;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlagShipAutoConfiguration.class));

    private String snapshotFileProperty() {
        return "flagship.snapshot-file=" + dir.resolve("snapshot.json").toString().replace('\\', '/');
    }

    @Test
    void theAppGetsTheCachingClientByDefault() {
        runner.withPropertyValues(
                        "flagship.project=checkout-app",
                        "flagship.server-url=http://127.0.0.1:1",
                        snapshotFileProperty())
                .run(context ->
                        assertInstanceOf(CachingFlagClient.class, context.getBean(FlagClient.class)));
    }

    @Test
    void cacheSizeZeroGivesThePlainClient() {
        runner.withPropertyValues(
                        "flagship.project=checkout-app",
                        "flagship.server-url=http://127.0.0.1:1",
                        "flagship.cache-size=0",
                        snapshotFileProperty())
                .run(context ->
                        assertInstanceOf(DefaultFlagClient.class, context.getBean(FlagClient.class)));
    }
}