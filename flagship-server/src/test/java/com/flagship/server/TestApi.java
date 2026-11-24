package com.flagship.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Calls the real HTTP API of a test server, logged in as a given test user. */
final class TestApi {

    static final String ADMIN_PASSWORD = "admin-test-pw-1";

    private final TestRestTemplate rest;
    private final int port;

    TestApi(TestRestTemplate rest, int port) {
        this.rest = rest;
        this.port = port;
    }

    static String password(String user) {
        return user.equals("admin") ? ADMIN_PASSWORD : user + "-password-1";
    }

    static int code(ResponseEntity<String> response) {
        return response.getStatusCode().value();
    }

    static void expect(int expected, ResponseEntity<String> response) {
        assertEquals(expected, code(response), "response body: " + response.getBody());
    }

    /** A flag environment config with no targets, rules or rollout. */
    static String envJson(boolean killSwitchOn, String defaultVariation) {
        return "{\"killSwitchOn\":" + killSwitchOn + ",\"offVariation\":\"off\",\"defaultVariation\":\""
                + defaultVariation + "\",\"userTargets\":{},\"rules\":[],\"rollout\":null}";
    }

    /** user == null means: not logged in. */
    ResponseEntity<String> call(HttpMethod method, String path, String body, String user, String reason) {
        return callAs(user, user == null ? null : password(user), method, path, body, reason);
    }

    ResponseEntity<String> callAs(String user, String password, HttpMethod method, String path,
                                  String body, String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (reason != null) {
            headers.set("X-Reason", reason);
        }
        TestRestTemplate client = user == null ? rest : rest.withBasicAuth(user, password);
        return client.exchange("http://127.0.0.1:" + port + path, method, new HttpEntity<>(body, headers),
                String.class);
    }

    /** Creates a normal user (the site admin "admin" does it). Fine if it already exists. */
    void ensureUser(String name) {
        ResponseEntity<String> r = call(HttpMethod.POST, "/api/users",
                "{\"username\":\"" + name + "\",\"password\":\"" + password(name) + "\",\"siteAdmin\":false}",
                "admin", null);
        if (code(r) != 201 && code(r) != 409) {
            expect(201, r);
        }
    }

    /** The user creates the project and becomes its ADMIN. */
    void createProject(String key, String user) {
        expect(201, call(HttpMethod.POST, "/api/projects",
                "{\"key\":\"" + key + "\",\"name\":\"Test project\"}", user, null));
    }

    /** A boolean flag (on/off) with the same simple config in all three environments. */
    void createFlag(String project, String flagKey, String user, String reason) {
        String e = envJson(false, "off");
        expect(201, call(HttpMethod.POST, "/api/projects/" + project + "/flags",
                "{\"key\":\"" + flagKey + "\",\"name\":\"Flag\",\"type\":\"BOOLEAN\","
                        + "\"variations\":{\"on\":true,\"off\":false},"
                        + "\"environments\":{\"DEVELOPMENT\":" + e + ",\"STAGING\":" + e
                        + ",\"PRODUCTION\":" + e + "}}", user, reason));
    }

    /** byUser (a project ADMIN or the site admin) gives username a role in the project. */
    void addMember(String project, String username, String role, String byUser) {
        expect(200, call(HttpMethod.PUT, "/api/projects/" + project + "/members/" + username,
                "{\"role\":\"" + role + "\"}", byUser, null));
    }
}