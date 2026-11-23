package com.flagship.starter;

import com.flagship.codec.Snapshot;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Actuator endpoint /actuator/flagship: shows what FlagShip is doing inside this application.
 * Only created when Actuator is on the classpath (see FlagShipAutoConfiguration).
 */
@Endpoint(id = "flagship")
public class FlagShipEndpoint {

    private final FlagShipProperties props;
    private final SnapshotStore store;
    private final ObjectProvider<SseChangeClient> push;
    private final ObjectProvider<FlagClient> client;

    public FlagShipEndpoint(FlagShipProperties props, SnapshotStore store,
                            ObjectProvider<SseChangeClient> push, ObjectProvider<FlagClient> client) {
        this.props = props;
        this.store = store;
        this.push = push;
        this.client = client;
    }

    @ReadOperation
    public Map<String, Object> flagship() {
        Snapshot snapshot = store.current();
        SseChangeClient sse = push.getIfAvailable();
        FlagClient flags = client.getIfAvailable();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("project", props.getProject());
        info.put("environment", props.getEnvironment());
        info.put("loaded", snapshot != null);
        info.put("flagCount", snapshot == null ? 0 : snapshot.flags().size());
        info.put("snapshotGeneratedAt",
                snapshot == null || snapshot.generatedAt() == null ? null : snapshot.generatedAt().toString());
        info.put("lastRefreshSuccess", text(store.lastRefreshSuccess()));
        info.put("lastRefreshFailure", text(store.lastRefreshFailure()));
        info.put("lastRefreshError", store.lastRefreshError());
        info.put("pushConnected", sse != null && sse.isConnected());
        info.put("cacheEntries",
                flags instanceof CachingFlagClient caching ? caching.cachedEntries() : null);
        return info;
    }

    private static String text(Instant time) {
        return time == null ? null : time.toString();
    }
}