package com.flagship.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.SnapshotCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Loaded automatically by Spring Boot when this jar is on the classpath.
 * Creates the snapshot store, keeps it fresh (polling plus server push), and offers a FlagClient.
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

    @Bean
    @ConditionalOnMissingBean
    public FlagClient flagShipClient(SnapshotStore store, SnapshotSynchronizer synchronizer,
                                     FlagShipProperties props) {
        // The synchronizer parameter makes Spring start it first, so the first
        // download has already been tried before the client is handed out.
        FlagClient plain = new DefaultFlagClient(store);
        if (props.getCacheSize() <= 0) {
            return plain;
        }
        return new CachingFlagClient(plain, store::current, props.getCacheSize());
    }
}