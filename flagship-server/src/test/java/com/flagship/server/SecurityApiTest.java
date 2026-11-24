package com.flagship.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** FR10: login and roles per project, through the real HTTP API. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:security;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false",
        "flagship.enabled=false",   // the starter must not configure itself inside the server
        "flagship.security.admin-password=admin-test-pw-1"
})
class SecurityApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest, port);
        for (String user : List.of("alice", "vera", "eddie", "paula", "nina", "owen")) {
            api.ensureUser(user);
        }
    }

    private static int code(ResponseEntity<String> response) {
        return TestApi.code(response);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return mapper.readTree(response.getBody());
    }

    private static String prod(String project) {
        return "/api/projects/" + project + "/flags/sec-flag/environments/PRODUCTION";
    }

    /** alice creates the project and a flag; vera is a VIEWER, eddie an EDITOR, paula an APPROVER. */
    private void projectWithTeam(String project) {
        api.createProject(project, "alice");
        api.createFlag(project, "sec-flag", "alice", null);
        api.addMember(project, "vera", "VIEWER", "alice");
        api.addMember(project, "eddie", "EDITOR", "alice");
        api.addMember(project, "paula", "APPROVER", "alice");
    }

    private static boolean hasKey(JsonNode projects, String key) {
        for (JsonNode p : projects) {
            if (key.equals(p.get("key").asText())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void noLoginOrAWrongPasswordIsRefusedButTheSnapshotStaysOpen() {
        api.createProject("sec-a", "alice");
        api.createFlag("sec-a", "sec-flag", "alice", null);

        assertEquals(401, code(api.call(HttpMethod.GET, "/api/projects", null, null, null)));
        assertEquals(401, code(api.call(HttpMethod.PUT, prod("sec-a"), TestApi.envJson(true, "on"), null, null)));
        assertEquals(401, code(api.callAs("alice", "wrong-password", HttpMethod.GET, "/api/projects",
                null, null)));

        // applications using the starter need no login for the snapshot
        assertEquals(200, code(api.call(HttpMethod.GET, "/api/projects/sec-a/snapshot?env=PRODUCTION",
                null, null, null)));
    }

    @Test
    void theRoleDecidesWhatAPersonMayDo() throws Exception {
        projectWithTeam("sec-b");
        String flag = "/api/projects/sec-b/flags/sec-flag";

        assertEquals(200, code(api.call(HttpMethod.GET, flag, null, "vera", null)));
        assertEquals(403, code(api.call(HttpMethod.PUT, prod("sec-b"), TestApi.envJson(true, "on"), "vera", null)));
        assertEquals(200, code(api.call(HttpMethod.PUT, prod("sec-b"), TestApi.envJson(true, "on"), "eddie", null)));
        assertEquals(200, code(api.call(HttpMethod.PUT, prod("sec-b"), TestApi.envJson(false, "on"), "paula", null)));

        // settings and members need ADMIN
        assertEquals(403, code(api.call(HttpMethod.PUT, "/api/projects/sec-b/settings",
                "{\"requireApproval\":false}", "eddie", null)));
        assertEquals(200, code(api.call(HttpMethod.PUT, "/api/projects/sec-b/settings",
                "{\"requireApproval\":false}", "alice", null)));
        assertEquals(403, code(api.call(HttpMethod.PUT, "/api/projects/sec-b/members/owen",
                "{\"role\":\"VIEWER\"}", "eddie", null)));
        api.addMember("sec-b", "owen", "VIEWER", "alice");

        // the author of a version is the person who logged in
        JsonNode history = json(api.call(HttpMethod.GET, prod("sec-b") + "/versions", null, "vera", null));
        assertEquals("paula", history.get(0).get("author").asText());
        assertEquals("eddie", history.get(1).get("author").asText());
    }

    @Test
    void onlyAnApproverCanApprove() throws Exception {
        projectWithTeam("sec-c");
        assertEquals(200, code(api.call(HttpMethod.PUT, "/api/projects/sec-c/settings",
                "{\"requireApproval\":true}", "alice", null)));

        ResponseEntity<String> queued = api.call(HttpMethod.PUT, prod("sec-c"), TestApi.envJson(true, "on"),
                "eddie", "needs a look");
        assertEquals(202, code(queued));
        String approve = "/api/projects/sec-c/change-requests/" + json(queued).get("id").asLong() + "/approve";

        assertEquals(403, code(api.call(HttpMethod.POST, approve, null, "vera", null)));    // viewer
        assertEquals(403, code(api.call(HttpMethod.POST, approve, null, "eddie", null)));   // editor and author
        ResponseEntity<String> done = api.call(HttpMethod.POST, approve, null, "paula", null);   // approver
        assertEquals(200, code(done));
        assertEquals("APPLIED", json(done).get("status").asText());
    }

    @Test
    void rolesArePerProject() {
        projectWithTeam("sec-d1");
        api.createProject("sec-d2", "alice");
        api.createFlag("sec-d2", "sec-flag", "alice", null);
        api.addMember("sec-d2", "eddie", "VIEWER", "alice");   // editor in d1, only a viewer in d2

        assertEquals(200, code(api.call(HttpMethod.PUT, prod("sec-d1"), TestApi.envJson(true, "on"), "eddie", null)));
        assertEquals(403, code(api.call(HttpMethod.PUT, prod("sec-d2"), TestApi.envJson(true, "on"), "eddie", null)));
        assertEquals(200, code(api.call(HttpMethod.GET, "/api/projects/sec-d2/flags/sec-flag", null, "eddie", null)));
    }

    @Test
    void theLastAdminCannotBeRemovedOrDowngraded() {
        api.createProject("sec-e", "alice");

        assertEquals(409, code(api.call(HttpMethod.DELETE, "/api/projects/sec-e/members/alice", null, "alice", null)));
        assertEquals(409, code(api.call(HttpMethod.PUT, "/api/projects/sec-e/members/alice",
                "{\"role\":\"EDITOR\"}", "alice", null)));

        api.addMember("sec-e", "eddie", "ADMIN", "alice");   // now there are two admins
        assertEquals(200, code(api.call(HttpMethod.PUT, "/api/projects/sec-e/members/alice",
                "{\"role\":\"EDITOR\"}", "alice", null)));
        assertEquals(204, code(api.call(HttpMethod.DELETE, "/api/projects/sec-e/members/alice", null, "eddie", null)));
    }

    @Test
    void onlySiteAdminsCreateUsersAndProjectListsAreOwn() throws Exception {
        api.createProject("sec-f", "alice");

        assertEquals(403, code(api.call(HttpMethod.POST, "/api/users",
                "{\"username\":\"zoe\",\"password\":\"zoe-password-1\",\"siteAdmin\":false}", "alice", null)));
        assertEquals(400, code(api.call(HttpMethod.POST, "/api/users",
                "{\"username\":\"zoe\",\"password\":\"short\",\"siteAdmin\":false}", "admin", null)));
        assertEquals(201, code(api.call(HttpMethod.POST, "/api/users",
                "{\"username\":\"zoe\",\"password\":\"zoe-password-1\",\"siteAdmin\":false}", "admin", null)));
        assertEquals(409, code(api.call(HttpMethod.POST, "/api/users",
                "{\"username\":\"zoe\",\"password\":\"zoe-password-1\",\"siteAdmin\":false}", "admin", null)));

        // nina belongs to no project; alice sees hers; the site admin sees everything
        assertEquals(0, json(api.call(HttpMethod.GET, "/api/projects", null, "nina", null)).size());
        assertTrue(hasKey(json(api.call(HttpMethod.GET, "/api/projects", null, "alice", null)), "sec-f"));
        assertTrue(hasKey(json(api.call(HttpMethod.GET, "/api/projects", null, "admin", null)), "sec-f"));
        assertFalse(hasKey(json(api.call(HttpMethod.GET, "/api/projects", null, "nina", null)), "sec-f"));
        assertEquals(403, code(api.call(HttpMethod.GET, "/api/projects/sec-f/flags", null, "nina", null)));
    }
}