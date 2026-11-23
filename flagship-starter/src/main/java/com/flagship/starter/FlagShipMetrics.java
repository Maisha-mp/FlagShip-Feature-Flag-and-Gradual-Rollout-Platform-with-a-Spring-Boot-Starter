package com.flagship.starter;

import com.flagship.codec.Snapshot;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Publishes the starter's numbers to Micrometer. Spring Boot binds every MeterBinder bean
 * to the application's registry automatically. Only created when Micrometer is on the classpath.
 *
 * Tags: project and environment (one value each per application) and, for evaluations, the reason
 * (6 possible values). Flag keys and users are NOT used as tags, to keep cardinality low.
 */
public class FlagShipMetrics implements MeterBinder {

    private final FlagShipProperties props;
    private final SnapshotStore store;
    private final FlagShipStats stats;
    private final ObjectProvider<SseChangeClient> push;

    public FlagShipMetrics(FlagShipProperties props, SnapshotStore store, FlagShipStats stats,
                           ObjectProvider<SseChangeClient> push) {
        this.props = props;
        this.store = store;
        this.stats = stats;
        this.push = push;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Tags common = Tags.of(
                "project", String.valueOf(props.getProject()),
                "environment", String.valueOf(props.getEnvironment()));

        for (String kind : stats.evaluationKinds()) {
            FunctionCounter.builder("flagship.evaluations", stats, s -> s.evaluations(kind))
                    .description("Flag evaluations, by reason")
                    .tags(common).tag("reason", kind)
                    .register(registry);
        }

        FunctionCounter.builder("flagship.cache.requests", stats, FlagShipStats::cacheHits)
                .description("Evaluation cache lookups")
                .tags(common).tag("result", "hit")
                .register(registry);
        FunctionCounter.builder("flagship.cache.requests", stats, FlagShipStats::cacheMisses)
                .description("Evaluation cache lookups")
                .tags(common).tag("result", "miss")
                .register(registry);

        FunctionCounter.builder("flagship.refresh", store, SnapshotStore::refreshSuccessCount)
                .description("Snapshot downloads from the server")
                .tags(common).tag("result", "success")
                .register(registry);
        FunctionCounter.builder("flagship.refresh", store, SnapshotStore::refreshFailureCount)
                .description("Snapshot downloads from the server")
                .tags(common).tag("result", "failure")
                .register(registry);

        Gauge.builder("flagship.flags.loaded", () -> {
                    Snapshot snapshot = store.current();
                    return snapshot == null ? 0 : snapshot.flags().size();
                })
                .description("Number of flags in the current snapshot")
                .tags(common)
                .register(registry);

        Gauge.builder("flagship.push.connected", () -> {
                    SseChangeClient sse = push.getIfAvailable();
                    return sse != null && sse.isConnected() ? 1 : 0;
                })
                .description("1 if the push stream to the server is connected, else 0")
                .tags(common)
                .register(registry);
    }
}