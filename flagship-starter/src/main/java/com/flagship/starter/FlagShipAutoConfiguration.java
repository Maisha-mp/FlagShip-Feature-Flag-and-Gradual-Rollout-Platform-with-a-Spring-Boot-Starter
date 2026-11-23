package com.flagship.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.SnapshotCodec;
import com.flagship.core.eval.EvaluationContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loaded automatically by Spring Boot when this jar is on the classpath.
 * Creates the snapshot store, keeps it fresh (polling plus server push), and offers a
 * FlagClient, change listeners, the @FlagGuard annotation, the /actuator/flagship endpoint
 * and Micrometer metrics.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "flagship", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FlagShipProperties.class)
public class FlagShipAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SnapshotStore flagShipSnapshotStore(FlagShipProperties props) {
        if (props.getProject() == null || props.getProject().isBlank()) {
            throw new IllegalStateException(
                    "FlagShip: please set flagship.project in application.properties");
        }
        SnapshotFetcher fetcher = new HttpSnapshotFetcher(
                props.getServerUrl(), props.getProject(),
                props.getEnvironment(), props.getRequestTimeout());
        return new SnapshotStore(fetcher,
                new SnapshotFile(props.getSnapshotFile()),
                new SnapshotCodec(new ObjectMapper()));
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    public SnapshotSynchronizer flagShipSnapshotSynchronizer(SnapshotStore store, FlagShipProperties props) {
        return new SnapshotSynchronizer(store, props.getStartupTimeout(), props.getPollInterval());
    }

    /** Listens to the server's push stream. Started only after the first download has been tried. */
    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flagship", name = "push-enabled", havingValue = "true", matchIfMissing = true)
    public SseChangeClient flagShipSseChangeClient(SnapshotStore store, SnapshotSynchronizer synchronizer,
                                                   FlagShipProperties props) {
        return new SseChangeClient(
                props.getServerUrl(), props.getProject(), props.getEnvironment(),
                props.getRequestTimeout(), props.getPushIdleTimeout(),
                new Backoff(props.getPushRetryInitial(), props.getPushRetryMax()),
                store::refresh);
    }

    /** Registers every FlagChangeListener bean of the application with the store. */
    @Bean
    @ConditionalOnMissingBean
    public FlagChangeListenerRegistrar flagShipListenerRegistrar(SnapshotStore store,
                                                                 ObjectProvider<FlagChangeListener> listeners) {
        return new FlagChangeListenerRegistrar(store, listeners);
    }

    /** The plain counters behind the metrics. Always created, it needs no Micrometer. */
    @Bean
    @ConditionalOnMissingBean
    public FlagShipStats flagShipStats() {
        return new FlagShipStats();
    }

    @Bean
    @ConditionalOnMissingBean
    public FlagClient flagShipClient(SnapshotStore store, SnapshotSynchronizer synchronizer,
                                     FlagShipProperties props, FlagShipStats stats) {
        // The synchronizer parameter makes Spring start it first, so the first
        // download has already been tried before the client is handed out.
        if (props.getCacheSize() <= 0) {
            return new DefaultFlagClient(store, stats);
        }
        // With the cache on, the cache counts every evaluation (hits and misses),
        // so the plain client inside it gets no stats (no double counting).
        FlagClient plain = new DefaultFlagClient(store);
        return new CachingFlagClient(plain, store::current, props.getCacheSize(), stats);
    }

    /** Who is the current user? The default says "anonymous"; an application can define its own bean. */
    @Bean
    @ConditionalOnMissingBean
    public FlagContextProvider flagShipContextProvider() {
        return () -> EvaluationContext.builder("anonymous").build();
    }

    /** Only active when AspectJ is on the classpath, so the starter also works without AOP. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    static class GuardConfiguration {

        @Bean
        @ConditionalOnMissingBean
        FlagGuardAspect flagShipGuardAspect(FlagClient flags, FlagContextProvider contexts) {
            return new FlagGuardAspect(flags, contexts);
        }
    }

    /** Only active when Actuator is on the classpath and the endpoint is exposed. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    static class ActuatorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnAvailableEndpoint(endpoint = FlagShipEndpoint.class)
        FlagShipEndpoint flagShipEndpoint(FlagShipProperties props, SnapshotStore store,
                                          ObjectProvider<SseChangeClient> push,
                                          ObjectProvider<FlagClient> client) {
            return new FlagShipEndpoint(props, store, push, client);
        }
    }

    /** Only active when Micrometer is on the classpath. Spring Boot binds the MeterBinder itself. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.binder.MeterBinder")
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        FlagShipMetrics flagShipMetrics(FlagShipProperties props, SnapshotStore store,
                                        FlagShipStats stats, ObjectProvider<SseChangeClient> push) {
            return new FlagShipMetrics(props, store, stats, push);
        }
    }
}