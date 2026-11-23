package com.flagship.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** FR7: approval by a second person and scheduled changes, through the real HTTP API. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:changes;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false",
        "flagship.enabled=false",           // the starter must not configure itself inside the server
        "flagship.scheduler.interval=200"   // check for due changes every 200 ms in tests
})
class ChangeRequestApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    // ---------- helpers ----------

    private ResponseEntity<String> call(HttpMethod method, String path, String body,
                                        String author, String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (author != null) {
            headers.set("X-Author", author);
        }
        if (reason != null) {
            headers.set("X-Reason", reason);
        }
        return rest.exchange("http://127.0.0.1:" + port + path, method, new HttpEntity<>(body, headers),
                String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return mapper.readTree(response.getBody());
    }

    private static int code(ResponseEntity<String> response) {
        return response.getStatusCode().value();
    }

    private static String env(boolean kill, String defaultVariation) {
        return "{\"killSwitchOn\":" + kill + ",\"offVariation\":\"off\",\"defaultVariation\":\""
                + defaultVariation + "\",\"userTargets\":{},\"rules\":[],\"rollout\":null}";
    }

    private void setup(String project, boolean requireApproval) {
        assertEquals(201, code(call(HttpMethod.POST, "/api/projects",
                "{\"key\":\"" + project + "\",\"name\":\"Changes\"}", null, null)));
        if (requireApproval) {
            assertEquals(200, code(call(HttpMethod.PUT, "/api/projects/" + project + "/settings",
                    "{\"requireApproval\":true}", null, null)));
        }
        String e = env(false, "off");
        assertEquals(201, code(call(HttpMethod.POST, "/api/projects/" + project + "/flags",
                "{\"key\":\"chg-flag\",\"name\":\"Flag\",\"type\":\"BOOLEAN\","
                        + "\"variations\":{\"on\":true,\"off\":false},"
                        + "\"environments\":{\"DEVELOPMENT\":" + e + ",\"STAGING\":" + e
                        + ",\"PRODUCTION\":" + e + "}}", "creator", null)));
    }

    private static String prodPath(String project) {
        return "/api/projects/" + project + "/flags/chg-flag/environments/PRODUCTION";
    }

    private boolean killSwitch(String project, String environment) throws Exception {
        return json(call(HttpMethod.GET, "/api/projects/" + project + "/flags/chg-flag", null, null, null))
                .get("environments").get(environment).get("killSwitchOn").asBoolean();
    }

    private JsonNode history(String project) throws Exception {
        return json(call(HttpMethod.GET, prodPath(project) + "/versions", null, null, null));
    }

    private String status(String project, long id) throws Exception {
        return json(call(HttpMethod.GET, "/api/projects/" + project + "/change-requests/" + id,
                null, null, null)).get("status").asText();
    }

    private void waitForStatus(String project, long id, String expected, long millis) throws Exception {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline && !expected.equals(status(project, id))) {
            Thread.sleep(100);
        }
        assertEquals(expected, status(project, id));
    }

    /** Sends a production change that must be queued (202) and returns the change request's id. */
    private long queue(String project, boolean kill, String author, String reason) throws Exception {
        ResponseEntity<String> r = call(HttpMethod.PUT, prodPath(project), env(kill, "on"), author, reason);
        assertEquals(202, code(r));
        return json(r).get("id").asLong();
    }

    private static String requests(String project, long id) {
        return "/api/projects/" + project + "/change-requests/" + id;
    }

    // ---------- approval ----------

    @Test
    void productionChangeNeedsASecondPersonToApprove() throws Exception {
        setup("appr-1", true);

        // a reason is required
        assertEquals(400, code(call(HttpMethod.PUT, prodPath("appr-1"), env(true, "on"), "alice", null)));

        ResponseEntity<String> queued = call(HttpMethod.PUT, prodPath("appr-1"), env(true, "on"),
                "alice", "incident");
        assertEquals(202, code(queued));
        JsonNode cr = json(queued);
        long id = cr.get("id").asLong();
        assertEquals("PENDING_APPROVAL", cr.get("status").asText());
        assertEquals("alice", cr.get("author").asText());
        Set<String> fields = new HashSet<>();
        cr.get("changes").forEach(c -> fields.add(c.get("field").asText()));
        assertTrue(fields.contains("killSwitchOn"));
        assertFalse(killSwitch("appr-1", "PRODUCTION"));   // nothing changed yet

        // the author cannot approve; a name is required
        assertEquals(403, code(call(HttpMethod.POST, requests("appr-1", id) + "/approve", null, "alice", null)));
        assertEquals(400, code(call(HttpMethod.POST, requests("appr-1", id) + "/approve", null, null, null)));
        assertFalse(killSwitch("appr-1", "PRODUCTION"));

        // a second person approves: the change is applied as a new version
        ResponseEntity<String> ok = call(HttpMethod.POST, requests("appr-1", id) + "/approve", null, "bob", null);
        assertEquals(200, code(ok));
        JsonNode approved = json(ok);
        assertEquals("APPLIED", approved.get("status").asText());
        assertEquals("bob", approved.get("decidedBy").asText());
        assertEquals(2, approved.get("appliedVersion").asInt());
        assertTrue(killSwitch("appr-1", "PRODUCTION"));

        JsonNode versions = history("appr-1");
        assertEquals(2, versions.size());
        assertEquals("alice", versions.get(0).get("author").asText());
        assertTrue(versions.get(0).get("reason").asText().contains("approved by bob"));

        // deciding twice is a conflict
        assertEquals(409, code(call(HttpMethod.POST, requests("appr-1", id) + "/approve", null, "carol", null)));

        // other environments are not affected by the rule
        assertEquals(200, code(call(HttpMethod.PUT,
                "/api/projects/appr-1/flags/chg-flag/environments/DEVELOPMENT", env(true, "on"), "alice", null)));
        assertTrue(killSwitch("appr-1", "DEVELOPMENT"));
    }

    @Test
    void rejectedAndCancelledRequestsNeverChangeTheFlag() throws Exception {
        setup("appr-2", true);

        long first = queue("appr-2", true, "alice", "first idea");
        ResponseEntity<String> rejected = call(HttpMethod.POST, requests("appr-2", first) + "/reject", null,
                "bob", "not now");
        assertEquals(200, code(rejected));
        assertEquals("REJECTED", json(rejected).get("status").asText());
        assertEquals("not now", json(rejected).get("decisionNote").asText());
        assertEquals(409, code(call(HttpMethod.POST, requests("appr-2", first) + "/approve", null, "bob", null)));

        long second = queue("appr-2", true, "alice", "second idea");
        assertEquals(403, code(call(HttpMethod.POST, requests("appr-2", second) + "/cancel", null, "bob", null)));
        ResponseEntity<String> cancelled = call(HttpMethod.POST, requests("appr-2", second) + "/cancel", null,
                "alice", null);
        assertEquals(200, code(cancelled));
        assertEquals("CANCELLED", json(cancelled).get("status").asText());

        assertFalse(killSwitch("appr-2", "PRODUCTION"));
        assertEquals(1, history("appr-2").size());

        assertEquals(0, json(call(HttpMethod.GET,
                "/api/projects/appr-2/change-requests?status=PENDING_APPROVAL", null, null, null)).size());
        assertEquals(2, json(call(HttpMethod.GET,
                "/api/projects/appr-2/change-requests", null, null, null)).size());
    }

    @Test
    void restoringProductionNeedsApprovalToo() throws Exception {
        setup("appr-3", true);
        long id = queue("appr-3", true, "alice", "turn it on");
        assertEquals(200, code(call(HttpMethod.POST, requests("appr-3", id) + "/approve", null, "bob", null)));
        assertTrue(killSwitch("appr-3", "PRODUCTION"));

        // restore version 1 (the original state): queued, not applied
        ResponseEntity<String> r = call(HttpMethod.POST, prodPath("appr-3") + "/versions/1/restore", null,
                "alice", null);
        assertEquals(202, code(r));
        JsonNode cr = json(r);
        assertEquals("PENDING_APPROVAL", cr.get("status").asText());
        assertEquals(1, cr.get("restoredFrom").asInt());
        assertTrue(killSwitch("appr-3", "PRODUCTION"));

        assertEquals(200, code(call(HttpMethod.POST, requests("appr-3", cr.get("id").asLong()) + "/approve",
                null, "bob", null)));
        assertFalse(killSwitch("appr-3", "PRODUCTION"));
        JsonNode versions = history("appr-3");
        assertEquals(3, versions.size());
        assertEquals(1, versions.get(0).get("restoredFrom").asInt());
    }

    // ---------- scheduling ----------

    @Test
    void scheduledChangeIsAppliedWhenItIsDue() throws Exception {
        setup("sched-1", false);
        String at = Instant.now().plusSeconds(2).toString();

        ResponseEntity<String> r = call(HttpMethod.PUT, prodPath("sched-1") + "?applyAt=" + at,
                env(true, "on"), "alice", "planned");
        assertEquals(202, code(r));
        JsonNode cr = json(r);
        assertEquals("SCHEDULED", cr.get("status").asText());
        assertFalse(cr.get("applyAt").isNull());
        long id = cr.get("id").asLong();
        assertFalse(killSwitch("sched-1", "PRODUCTION"));

        waitForStatus("sched-1", id, "APPLIED", 15_000);
        assertTrue(killSwitch("sched-1", "PRODUCTION"));
        JsonNode versions = history("sched-1");
        assertEquals(2, versions.size());
        assertEquals("alice", versions.get(0).get("author").asText());
        assertTrue(versions.get(0).get("reason").asText().contains("scheduled"));
    }

    @Test
    void cancelledScheduledChangeIsNeverApplied() throws Exception {
        setup("sched-2", false);
        String at = Instant.now().plusSeconds(3).toString();

        long id = json(call(HttpMethod.PUT, prodPath("sched-2") + "?applyAt=" + at,
                env(true, "on"), "alice", "planned")).get("id").asLong();
        assertEquals(200, code(call(HttpMethod.POST, requests("sched-2", id) + "/cancel", null, "alice", null)));

        Thread.sleep(4500);   // well past the planned time

        assertEquals("CANCELLED", status("sched-2", id));
        assertFalse(killSwitch("sched-2", "PRODUCTION"));
        assertEquals(1, history("sched-2").size());
    }

    @Test
    void approvedChangeCanStillWaitForItsTime() throws Exception {
        setup("sched-3", true);
        String at = Instant.now().plusSeconds(5).toString();

        ResponseEntity<String> r = call(HttpMethod.PUT, prodPath("sched-3") + "?applyAt=" + at,
                env(true, "on"), "alice", "planned and reviewed");
        assertEquals(202, code(r));
        assertEquals("PENDING_APPROVAL", json(r).get("status").asText());
        long id = json(r).get("id").asLong();

        ResponseEntity<String> approved = call(HttpMethod.POST, requests("sched-3", id) + "/approve", null,
                "bob", null);
        assertEquals(200, code(approved));
        assertEquals("SCHEDULED", json(approved).get("status").asText());   // approved, but not due yet
        assertFalse(killSwitch("sched-3", "PRODUCTION"));

        waitForStatus("sched-3", id, "APPLIED", 20_000);
        assertTrue(killSwitch("sched-3", "PRODUCTION"));
        assertTrue(history("sched-3").get(0).get("reason").asText().contains("approved by bob"));
    }

    @Test
    void aBadApplyAtIsRejected() {
        setup("sched-4", false);
        assertEquals(400, code(call(HttpMethod.PUT, prodPath("sched-4") + "?applyAt=tomorrow",
                env(true, "on"), "alice", null)));
    }

    // ---------- settings ----------

    @Test
    void approvalSettingCanBeReadAndChanged() throws Exception {
        setup("sett-1", false);
        String path = "/api/projects/sett-1/settings";

        assertFalse(json(call(HttpMethod.GET, path, null, null, null)).get("requireApproval").asBoolean());
        assertEquals(200, code(call(HttpMethod.PUT, path, "{\"requireApproval\":true}", null, null)));
        assertTrue(json(call(HttpMethod.GET, path, null, null, null)).get("requireApproval").asBoolean());
        assertEquals(404, code(call(HttpMethod.GET, "/api/projects/no-such-project/settings", null, null, null)));
    }
}