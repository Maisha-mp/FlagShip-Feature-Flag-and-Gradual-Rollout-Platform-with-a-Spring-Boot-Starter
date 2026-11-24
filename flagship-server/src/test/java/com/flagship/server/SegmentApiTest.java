package com.flagship.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** FR10: segments (named, reusable rule conditions), through the real HTTP API. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:segments;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false",
        "flagship.enabled=false",   // the starter must not configure itself inside the server
        "flagship.security.admin-password=admin-test-pw-1"
})
class SegmentApiTest {

    private static final String SOUTH_ASIA = "{\"type\":\"condition\",\"attribute\":\"country\","
            + "\"operator\":\"IN\",\"values\":[\"BD\",\"IN\"]}";
    private static final String US_ONLY = "{\"type\":\"condition\",\"attribute\":\"country\","
            + "\"operator\":\"IN\",\"values\":[\"US\"]}";
    private static final String REFERENCE = "{\"type\":\"segment\",\"key\":\"south-asia\"}";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest, port);
        for (String user : List.of("alice", "eddie", "vera")) {
            api.ensureUser(user);
        }
    }

    private static int code(ResponseEntity<String> response) {
        return TestApi.code(response);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return mapper.readTree(response.getBody());
    }

    /** alice creates the project (ADMIN) and a flag; eddie is an EDITOR, vera a VIEWER. */
    private void projectWithTeam(String project) {
        api.createProject(project, "alice");
        api.addMember(project, "eddie", "EDITOR", "alice");
        api.addMember(project, "vera", "VIEWER", "alice");
        api.createFlag(project, "seg-flag", "alice", null);
    }

    private static String body(String name, String condition) {
        return "{\"name\":\"" + name + "\",\"condition\":" + condition + "}";
    }

    private static String envWithRule(String conditionJson) {
        return "{\"killSwitchOn\":false,\"offVariation\":\"off\",\"defaultVariation\":\"off\",\"userTargets\":{},"
                + "\"rules\":[{\"id\":\"r1\",\"variation\":\"on\",\"condition\":" + conditionJson + "}],"
                + "\"rollout\":null}";
    }

    private static String prod(String project) {
        return "/api/projects/" + project + "/flags/seg-flag/environments/PRODUCTION";
    }

    private String reason(String project, String country) throws Exception {
        return json(api.call(HttpMethod.POST,
                "/api/projects/" + project + "/flags/seg-flag/evaluate?env=PRODUCTION",
                "{\"userKey\":\"u1\",\"attributes\":{\"country\":\"" + country + "\"}}", "vera", null))
                .get("reason").asText();
    }

    @Test
    void segmentsCanBeCreatedChangedAndDeleted() throws Exception {
        projectWithTeam("seg-a");
        String path = "/api/projects/seg-a/segments/south-asia";

        assertEquals(403, code(api.call(HttpMethod.PUT, path, body("South Asia", SOUTH_ASIA), "vera", null)));
        assertEquals(200, code(api.call(HttpMethod.PUT, path, body("South Asia", SOUTH_ASIA), "eddie", null)));

        JsonNode got = json(api.call(HttpMethod.GET, path, null, "vera", null));
        assertEquals("South Asia", got.get("name").asText());
        assertEquals("IN", got.get("condition").get("operator").asText());
        assertEquals(1, json(api.call(HttpMethod.GET, "/api/projects/seg-a/segments", null, "vera", null)).size());

        // replacing it
        assertEquals(200, code(api.call(HttpMethod.PUT, path, body("United States", US_ONLY), "eddie", null)));
        got = json(api.call(HttpMethod.GET, path, null, "vera", null));
        assertEquals("United States", got.get("name").asText());
        assertEquals("US", got.get("condition").get("values").get(0).asText());

        assertEquals(403, code(api.call(HttpMethod.DELETE, path, null, "vera", null)));
        assertEquals(204, code(api.call(HttpMethod.DELETE, path, null, "eddie", null)));
        assertEquals(404, code(api.call(HttpMethod.GET, path, null, "vera", null)));
    }

    @Test
    void invalidSegmentsAreRejected() {
        projectWithTeam("seg-b");
        String base = "/api/projects/seg-b/segments/";
        String badOperator = "{\"type\":\"condition\",\"attribute\":\"country\","
                + "\"operator\":\"NOPE\",\"values\":[\"BD\"]}";

        assertEquals(400, code(api.call(HttpMethod.PUT, base + "Bad_Key", body("x", SOUTH_ASIA), "eddie", null)));
        assertEquals(400, code(api.call(HttpMethod.PUT, base + "bad-operator", body("x", badOperator), "eddie", null)));
        assertEquals(400, code(api.call(HttpMethod.PUT, base + "nested", body("x", REFERENCE), "eddie", null)));
        assertEquals(400, code(api.call(HttpMethod.PUT, base + "no-condition", "{\"name\":\"x\"}", "eddie", null)));
        assertEquals(400, code(api.call(HttpMethod.PUT, base + "no-name", "{\"condition\":" + SOUTH_ASIA + "}",
                "eddie", null)));
    }

    @Test
    void aFlagRuleCanUseASegmentAndFollowsItsChanges() throws Exception {
        projectWithTeam("seg-c");
        String segment = "/api/projects/seg-c/segments/south-asia";
        assertEquals(200, code(api.call(HttpMethod.PUT, segment, body("South Asia", SOUTH_ASIA), "eddie", null)));

        // an unknown segment cannot be used
        assertEquals(400, code(api.call(HttpMethod.PUT, prod("seg-c"),
                envWithRule("{\"type\":\"segment\",\"key\":\"missing\"}"), "eddie", null)));

        // a known one can
        assertEquals(200, code(api.call(HttpMethod.PUT, prod("seg-c"), envWithRule(REFERENCE), "eddie", null)));
        assertEquals("RULE_MATCH", reason("seg-c", "BD"));
        assertEquals("DEFAULT", reason("seg-c", "US"));

        // the flag keeps the reference
        JsonNode rule = json(api.call(HttpMethod.GET, "/api/projects/seg-c/flags/seg-flag", null, "vera", null))
                .get("environments").get("PRODUCTION").get("rules").get(0);
        assertEquals("segment", rule.get("condition").get("type").asText());

        // changing the segment changes the flag's behaviour, without touching the flag
        assertEquals(200, code(api.call(HttpMethod.PUT, segment, body("US", US_ONLY), "eddie", null)));
        assertEquals("DEFAULT", reason("seg-c", "BD"));
        assertEquals("RULE_MATCH", reason("seg-c", "US"));

        // the snapshot (what apps download, with no login) has the segment filled in
        JsonNode snapshotRule = json(api.call(HttpMethod.GET, "/api/projects/seg-c/snapshot?env=PRODUCTION",
                null, null, null)).get("flags").get(0).get("config").get("rules").get(0);
        assertEquals("condition", snapshotRule.get("condition").get("type").asText());
        assertEquals("US", snapshotRule.get("condition").get("values").get(0).asText());
    }

    @Test
    void aSegmentInUseCannotBeDeleted() {
        projectWithTeam("seg-d");
        String segment = "/api/projects/seg-d/segments/south-asia";
        assertEquals(200, code(api.call(HttpMethod.PUT, segment, body("South Asia", SOUTH_ASIA), "eddie", null)));
        assertEquals(200, code(api.call(HttpMethod.PUT, prod("seg-d"), envWithRule(REFERENCE), "eddie", null)));

        assertEquals(409, code(api.call(HttpMethod.DELETE, segment, null, "eddie", null)));

        // once no flag uses it any more, it can go
        assertEquals(200, code(api.call(HttpMethod.PUT, prod("seg-d"), TestApi.envJson(false, "off"),
                "eddie", null)));
        assertEquals(204, code(api.call(HttpMethod.DELETE, segment, null, "eddie", null)));
    }
}