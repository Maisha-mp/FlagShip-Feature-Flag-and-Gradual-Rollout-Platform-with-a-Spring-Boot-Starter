package com.flagship.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.flagship.core.eval.EvaluationContext;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FlagShipMetricsTest {

    @TempDir
    Path dir;

    private ApplicationContextRunner runner() {
        String file = dir.resolve("snapshot.json").toString().replace('\\', '/');
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlagShipAutoConfiguration.class))
                .withPropertyValues(
                        "flagship.project=demo-project",
                        "flagship.server-url=http://localhost:1",
                        "flagship.startup-timeout=300ms",
                        "flagship.push-enabled=false",
                        "flagship.snapshot-file=" + file);
    }

    @Test
    void countsEvaluationsCacheAndRefreshes() {
        runner().run(context -> {
            SnapshotStore store = context.getBean(SnapshotStore.class);
            FlagClient client = context.getBean(FlagClient.class);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(store.refreshFailureCount()).isGreaterThanOrEqualTo(1));

            EvaluationContext user = EvaluationContext.builder("u1").build();
            client.evaluate("no-such-flag", user);
            client.evaluate("no-such-flag", user);

            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            context.getBean(FlagShipMetrics.class).bindTo(registry);

            assertThat(registry.get("flagship.evaluations").tag("reason", "UNKNOWN")
                    .functionCounter().count()).isEqualTo(2.0);
            assertThat(registry.get("flagship.evaluations").tag("reason", "ROLLOUT")
                    .functionCounter().count()).isEqualTo(0.0);
            assertThat(registry.get("flagship.cache.requests").tag("result", "miss")
                    .functionCounter().count()).isEqualTo(1.0);
            assertThat(registry.get("flagship.cache.requests").tag("result", "hit")
                    .functionCounter().count()).isEqualTo(1.0);
            assertThat(registry.get("flagship.refresh").tag("result", "failure")
                    .functionCounter().count()).isGreaterThanOrEqualTo(1.0);
            assertThat(registry.get("flagship.refresh").tag("result", "success")
                    .functionCounter().count()).isEqualTo(0.0);
        });
    }

    @Test
    void gaugesShowFlagsAndPushState() {
        runner().run(context -> {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            context.getBean(FlagShipMetrics.class).bindTo(registry);

            assertThat(registry.get("flagship.flags.loaded").gauge().value()).isEqualTo(0.0);
            assertThat(registry.get("flagship.push.connected").gauge().value()).isEqualTo(0.0);
        });
    }

    @Test
    void appStillStartsWithoutMicrometer() {
        runner().withClassLoader(new FilteredClassLoader(MeterBinder.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FlagClient.class);
            assertThat(context).doesNotHaveBean("flagShipMetrics");
        });
    }
}
