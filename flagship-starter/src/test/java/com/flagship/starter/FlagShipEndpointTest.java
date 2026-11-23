package com.flagship.starter;

import static com.flagship.starter.TestSnapshots.flag;
import static com.flagship.starter.TestSnapshots.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
            assertThat((List<?>) info.get("flags")).isEmpty();
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

    @Test
    void endpointListsTheLoadedFlags() throws Exception {
        // A copy saved by an earlier run; the server is not running, so the app starts from this copy.
        Files.writeString(dir.resolve("snapshot.json"),
                snapshot(flag("new-checkout", "on"), flag("alpha", "off")));

        runner("management.endpoints.web.exposure.include=flagship").run(context -> {
            FlagShipEndpoint endpoint = context.getBean(FlagShipEndpoint.class);

            Map<String, Object> info = endpoint.flagship();
            assertThat(info.get("loaded")).isEqualTo(true);
            assertThat(info.get("flagCount")).isEqualTo(2);

            List<?> flags = (List<?>) info.get("flags");
            assertThat(flags).hasSize(2);
            Map<?, ?> first = (Map<?, ?>) flags.get(0);   // sorted by key: "alpha" comes first
            assertThat(first.get("key")).isEqualTo("alpha");
            assertThat(first.get("type")).isEqualTo("BOOLEAN");
            Map<?, ?> second = (Map<?, ?>) flags.get(1);
            assertThat(second.get("key")).isEqualTo("new-checkout");
        });
    }
}