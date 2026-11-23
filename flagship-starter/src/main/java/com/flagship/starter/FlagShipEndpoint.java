package com.flagship.starter;

import com.flagship.codec.Snapshot;
import com.flagship.core.model.Flag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Actuator endpoint /actuator/flagship: shows what FlagShip is doing inside this application,
 * including the list of loaded flags and the last sync times.
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
        info.put("flags", listFlags(snapshot));
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

    /** Key and type of every loaded flag, sorted by key. Rules and users are not shown. */
    private static List<Map<String, String>> listFlags(Snapshot snapshot) {
        List<Map<String, String>> list = new ArrayList<>();
        if (snapshot == null) {
            return list;
        }
        List<Flag> sorted = new ArrayList<>(snapshot.flags().values());
        sorted.sort(Comparator.comparing(Flag::key));
        for (Flag flag : sorted) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("key", flag.key());
            entry.put("type", flag.type().name());
            list.add(entry);
        }
        return list;
    }

    private static String text(Instant time) {
        return time == null ? null : time.toString();
    }
}