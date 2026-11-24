package com.flagship.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** FR7: version history, diff and restore, through the real HTTP API (logged in). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:versions;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false",
        "flagship.enabled=false",   // the starter must not configure itself inside the server
        "flagship.security.admin-password=admin-test-pw-1"
})
class FlagVersionApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest, port);
        for (String user : List.of("alice", "bob", "carol", "dave")) {
            api.ensureUser(user);
        }
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String body, String user, String reason) {
        return api.call(method, path, body, user, reason);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return mapper.readTree(response.getBody());
    }

    private static String env(boolean kill, String defaultVariation, String targets, String rollout) {
        return "{\"killSwitchOn\":" + kill + ",\"offVariation\":\"off\",\"defaultVariation\":\""
                + defaultVariation + "\",\"userTargets\":" + targets + ",\"rules\":[],\"rollout\":"
                + rollout + "}";
    }

    /** alice creates the project (and becomes its ADMIN); bob, carol and dave are editors. */
    private void createProjectAndFlag(String project, String flag, String reason) {
        api.createProject(project, "alice");
        for (String editor : List.of("bob", "carol", "dave")) {
            api.addMember(project, editor, "EDITOR", "alice");
        }
        api.createFlag(project, flag, "alice", reason);
    }

    @Test
    void historyDiffAndRestoreWork() throws Exception {
        String base = "/api/projects/ver-app/flags/ver-flag/environments/PRODUCTION";
        createProjectAndFlag("ver-app", "ver-flag", "first version");

        // creation is version 1
        JsonNode history = json(call(HttpMethod.GET, base + "/versions", null, "alice", null));
        assertEquals(1, history.size());
        assertEquals(1, history.get(0).get("version").asInt());
        assertEquals("alice", history.get(0).get("author").asText());
        assertEquals("first version", history.get(0).get("reason").asText());

        // two changes by other people
        assertEquals(200, TestApi.code(call(HttpMethod.PUT, base,
                env(true, "on", "{\"u1\":\"on\"}", "null"), "bob", "incident")));
        assertEquals(200, TestApi.code(call(HttpMethod.PUT, base,
                env(false, "off", "{}", "{\"variation\":\"on\",\"percent\":10}"), "carol", "ramp")));

        history = json(call(HttpMethod.GET, base + "/versions", null, "alice", null));
        assertEquals(3, history.size());
        assertEquals(3, history.get(0).get("version").asInt());   // newest first
        assertEquals("carol", history.get(0).get("author").asText());
        assertEquals("ramp", history.get(0).get("reason").asText());
        assertEquals(1, history.get(2).get("version").asInt());

        // diff between version 1 and 2
        JsonNode diff = json(call(HttpMethod.GET, base + "/versions/diff?from=1&to=2", null, "alice", null));
        Set<String> fields = new HashSet<>();
        diff.get("changes").forEach(c -> fields.add(c.get("field").asText()));
        assertEquals(Set.of("killSwitchOn", "defaultVariation", "userTargets.u1"), fields);

        // restore version 1 in one action
        ResponseEntity<String> restored = call(HttpMethod.POST, base + "/versions/1/restore", null, "dave", null);
        assertEquals(201, TestApi.code(restored));
        JsonNode created = json(restored);
        assertEquals(4, created.get("version").asInt());      // the restore is a new version
        assertEquals(1, created.get("restoredFrom").asInt());
        assertEquals("dave", created.get("author").asText());
        assertEquals("Restored version 1", created.get("reason").asText());

        // the flag really is back to version 1's state
        JsonNode prod = json(call(HttpMethod.GET, "/api/projects/ver-app/flags/ver-flag", null, "alice", null))
                .get("environments").get("PRODUCTION");
        assertEquals(false, prod.get("killSwitchOn").asBoolean());
        assertEquals("off", prod.get("defaultVariation").asText());
        assertEquals(0, prod.get("userTargets").size());
        assertTrue(prod.path("rollout").isNull() || prod.path("rollout").isMissingNode());

        // other environments have their own, separate history
        JsonNode dev = json(call(HttpMethod.GET,
                "/api/projects/ver-app/flags/ver-flag/environments/DEVELOPMENT/versions", null, "alice", null));
        assertEquals(1, dev.size());
    }

    @Test
    void unknownVersionsAreNotFound() {
        String base = "/api/projects/ver-app-2/flags/ver-flag/environments/PRODUCTION/versions";
        createProjectAndFlag("ver-app-2", "ver-flag", null);

        assertEquals(404, TestApi.code(call(HttpMethod.GET, base + "/99", null, "alice", null)));
        assertEquals(404, TestApi.code(call(HttpMethod.GET, base + "/diff?from=1&to=99", null, "alice", null)));
        assertEquals(404, TestApi.code(call(HttpMethod.POST, base + "/99/restore", null, "alice", null)));
    }
}