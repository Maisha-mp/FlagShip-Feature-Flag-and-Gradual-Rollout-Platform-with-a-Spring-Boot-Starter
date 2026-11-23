package com.flagship.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FlagShipEndpointTest {

    @TempDir
    Path dir;

    private ApplicationContextRunner runner(String... extra) {
        String file = dir.resolve("snapshot.json").toString().replace('\\', '/');
        ApplicationContextRunner base = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlagShipAutoConfiguration.class))
                .withPropertyValues(
                        "flagship.project=demo-project",
                        "flagship.server-url=http://localhost:1",
                        "flagship.startup-timeout=300ms",
                        "flagship.push-enabled=false",
                        "flagship.snapshot-file=" + file);
        return base.withPropertyValues(extra);
    }

    @Test
    void endpointShowsStateWhenExposed() {
        runner("management.endpoints.web.exposure.include=flagship").run(context -> {
            assertThat(context).hasSingleBean(FlagShipEndpoint.class);
            FlagShipEndpoint endpoint = context.getBean(FlagShipEndpoint.class);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(endpoint.flagship().get("lastRefreshFailure")).isNotNull());

            Map<String, Object> info = endpoint.flagship();
            assertThat(info.get("project")).isEqualTo("demo-project");
            assertThat(info.get("loaded")).isEqualTo(false);
            assertThat(info.get("flagCount")).isEqualTo(0);
            assertThat(info.get("lastRefreshSuccess")).isNull();
            assertThat(info.get("lastRefreshError")).isNotNull();
            assertThat(info.get("pushConnected")).isEqualTo(false);
            assertThat(info.get("cacheEntries")).isEqualTo(0);
        });
    }

    @Test
    void endpointIsNotCreatedWhenNotExposed() {
        runner().run(context ->
                assertThat(context).doesNotHaveBean(FlagShipEndpoint.class));
    }
}