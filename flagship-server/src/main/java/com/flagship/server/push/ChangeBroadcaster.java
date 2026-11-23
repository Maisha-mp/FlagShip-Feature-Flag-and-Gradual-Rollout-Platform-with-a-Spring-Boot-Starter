package com.flagship.server.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.Environment;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps track of the connected apps and tells them when flags change.
 * Events are sent only AFTER the database transaction has committed.
 */
@Component
public class ChangeBroadcaster {

    private record Registration(Environment environment, ChangeSink sink) { }

    private final ObjectMapper mapper;
    private final Map<String, Set<Registration>> byProject = new ConcurrentHashMap<>();

    /** Counts changes since the server started. Only used to order events; apps re-download everything anyway. */
    private final AtomicLong version = new AtomicLong();

    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "flagship-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public ChangeBroadcaster(ObjectMapper mapper,
                             @Value("${flagship.push.heartbeat:15s}") Duration heartbeatInterval) {
        this.mapper = mapper;
        long millis = Math.max(10, heartbeatInterval.toMillis());
        heartbeat.scheduleWithFixedDelay(this::pingAll, millis, millis, TimeUnit.MILLISECONDS);
    }

    /** Adds a receiver. Returns a Runnable that removes it again. */
    public Runnable register(String projectKey, Environment environment, ChangeSink sink) {
        Registration registration = new Registration(environment, sink);
        byProject.computeIfAbsent(projectKey, k -> ConcurrentHashMap.newKeySet()).add(registration);
        Runnable unregister = () -> remove(projectKey, registration);

        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put("project", projectKey);
        hello.put("environment", environment.name());
        hello.put("version", version.get());
        if (!sink.send("hello", toJson(hello))) {
            unregister.run();
        }
        return unregister;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChange(FlagsChangedEvent event) {
        long v = version.incrementAndGet();
        Set<Registration> registrations = byProject.get(event.projectKey());
        if (registrations == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project", event.projectKey());
        payload.put("flag", event.flagKey());
        payload.put("environment", event.environment() == null ? "ALL" : event.environment().name());
        payload.put("version", v);
        String json = toJson(payload);

        for (Registration r : registrations) {
            boolean relevant = event.environment() == null || event.environment() == r.environment();
            if (relevant && !r.sink().send("change", json)) {
                registrations.remove(r);
            }
        }
    }

    public int subscriberCount(String projectKey) {
        Set<Registration> registrations = byProject.get(projectKey);
        return registrations == null ? 0 : registrations.size();
    }

    @PreDestroy
    public void close() {
        heartbeat.shutdownNow();
    }

    private void pingAll() {
        try {
            for (Set<Registration> registrations : byProject.values()) {
                for (Registration r : registrations) {
                    if (!r.sink().ping()) {
                        registrations.remove(r);
                    }
                }
            }
        } catch (RuntimeException e) {
            // never let one bad receiver stop the heartbeat
        }
    }

    private void remove(String projectKey, Registration registration) {
        Set<Registration> registrations = byProject.get(projectKey);
        if (registrations != null) {
            registrations.remove(registration);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write event JSON", e);
        }
    }
}