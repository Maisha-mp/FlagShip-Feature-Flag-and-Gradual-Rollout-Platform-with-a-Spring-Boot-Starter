package com.flagship.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.Reason;
import com.flagship.server.push.ChangeBroadcaster;
import com.flagship.starter.FlagClient;
import com.flagship.starter.FlagShipAutoConfiguration;
import com.flagship.starter.SseChangeClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * QA3: a kill-switch change must reach 50 running application instances within 5 seconds at p95.
 * Real server (in-memory database, random port) and 50 real starter instances, each its own Spring context.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:qa3;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false",
        "flagship.enabled=false"   // the starter must not configure itself inside the server
})
class Qa3FanOutTest {

    private static final int INSTANCES = 50;
    private static final int ROUNDS = 5;
    private static final long LIMIT_MILLIS = 5000;
    private static final long GIVE_UP_MILLIS = 15_000;
    private static final String PROJECT = "qa3-app";
    private static final String FLAG = "kill-test";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ChangeBroadcaster broadcaster;

    @TempDir
    Path dir;

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private void send(HttpMethod method, String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(baseUrl() + path, method,
                new HttpEntity<>(json, headers), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                method + " " + path + " answered " + response.getStatusCode() + ": " + response.getBody());
    }

    private static String environmentJson(boolean killSwitchOn) {
        return "{\"killSwitchOn\":" + killSwitchOn + ",\"offVariation\":\"off\",\"defaultVariation\":\"on\","
                + "\"userTargets\":{},\"rules\":[],\"rollout\":null}";
    }

    private void createProjectAndFlag() {
        send(HttpMethod.POST, "/api/projects", "{\"key\":\"" + PROJECT + "\",\"name\":\"QA3 fan-out\"}");
        String env = environmentJson(false);
        send(HttpMethod.POST, "/api/projects/" + PROJECT + "/flags",
                "{\"key\":\"" + FLAG + "\",\"name\":\"Kill test\",\"type\":\"BOOLEAN\","
                        + "\"variations\":{\"on\":true,\"off\":false},"
                        + "\"environments\":{\"DEVELOPMENT\":" + env + ",\"STAGING\":" + env
                        + ",\"PRODUCTION\":" + env + "}}");
    }

    private void putKillSwitch(boolean on) {
        send(HttpMethod.PUT, "/api/projects/" + PROJECT + "/flags/" + FLAG + "/environments/PRODUCTION",
                environmentJson(on));
    }

    /** One application instance: the starter's auto-configuration in its own Spring context. */
    private AnnotationConfigApplicationContext startInstance(int number) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(
                "flagship.project=" + PROJECT,
                "flagship.server-url=" + baseUrl(),
                "flagship.environment=PRODUCTION",
                "flagship.poll-interval=1h",   // only the push stream can explain a quick change
                "flagship.snapshot-file=" + dir.resolve("snapshot-" + number + ".json").toString().replace('\\', '/'))
                .applyTo(context);
        context.register(FlagShipAutoConfiguration.class);
        context.refresh();
        return context;
    }

    private static boolean answers(FlagClient client) {
        return client.evaluate(FLAG, EvaluationContext.builder("u1").build()).isPresent();
    }

    private static boolean killed(FlagClient client) {
        return client.evaluate(FLAG, EvaluationContext.builder("u1").build())
                .map(r -> r.reason() == Reason.KILL_SWITCH)
                .orElse(false);
    }

    private static boolean waitUntil(BooleanSupplier condition, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    @Test
    void killSwitchReachesFiftyInstancesWithinTheLimit() throws Exception {
        createProjectAndFlag();
        List<AnnotationConfigApplicationContext> apps = new ArrayList<>();
        try {
            List<FlagClient> clients = new ArrayList<>();
            List<SseChangeClient> pushes = new ArrayList<>();
            for (int i = 0; i < INSTANCES; i++) {
                AnnotationConfigApplicationContext app = startInstance(i);
                apps.add(app);
                clients.add(app.getBean(FlagClient.class));
                pushes.add(app.getBean(SseChangeClient.class));
            }

            assertTrue(waitUntil(() -> pushes.stream().allMatch(SseChangeClient::isConnected), 20_000),
                    "not all instances connected to the push stream");
            assertTrue(waitUntil(() -> clients.stream().allMatch(c -> answers(c) && !killed(c)), 20_000),
                    "not all instances loaded the flag");
            assertEquals(INSTANCES, broadcaster.subscriberCount(PROJECT));
            Thread.sleep(500);   // let the catch-up refresh after each "hello" finish

            List<Long> times = new ArrayList<>();
            for (int round = 0; round < ROUNDS; round++) {
                times.addAll(measureKillSwitch(clients));
                putKillSwitch(false);   // reset for the next round
                assertTrue(waitUntil(() -> clients.stream().noneMatch(Qa3FanOutTest::killed), GIVE_UP_MILLIS),
                        "the reset did not reach every instance");
            }

            Collections.sort(times);
            long min = times.get(0);
            long median = times.get(times.size() / 2);
            long p95 = times.get((int) Math.ceil(0.95 * times.size()) - 1);
            long max = times.get(times.size() - 1);
            System.out.println("QA3 fan-out, " + INSTANCES + " instances, " + ROUNDS + " kill-switch changes ("
                    + times.size() + " observations): min " + min + " ms, median " + median
                    + " ms, p95 " + p95 + " ms, max " + max + " ms (limit " + LIMIT_MILLIS + " ms at p95)");
            assertTrue(p95 < LIMIT_MILLIS, "p95 was " + p95 + " ms");
        } finally {
            for (AnnotationConfigApplicationContext app : apps) {
                app.close();
            }
        }
    }

    /** Flips the kill switch on and returns, per instance, the milliseconds until it showed the change. */
    private List<Long> measureKillSwitch(List<FlagClient> clients) throws Exception {
        long[] seen = new long[INSTANCES];
        Arrays.fill(seen, -1);
        int remaining = INSTANCES;

        long start = System.nanoTime();   // before the PUT, so the server's own work counts too
        putKillSwitch(true);
        long deadline = start + GIVE_UP_MILLIS * 1_000_000L;
        while (remaining > 0 && System.nanoTime() < deadline) {
            for (int i = 0; i < INSTANCES; i++) {
                if (seen[i] < 0 && killed(clients.get(i))) {
                    seen[i] = (System.nanoTime() - start) / 1_000_000;
                    remaining--;
                }
            }
            if (remaining > 0) {
                Thread.sleep(1);
            }
        }
        assertEquals(0, remaining, remaining + " instances never saw the kill switch");

        List<Long> times = new ArrayList<>();
        for (long millis : seen) {
            times.add(millis);
        }
        return times;
    }
}