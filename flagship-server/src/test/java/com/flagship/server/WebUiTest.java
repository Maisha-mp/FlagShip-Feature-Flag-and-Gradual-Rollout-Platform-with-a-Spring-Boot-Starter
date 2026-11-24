package com.flagship.server;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** FR10: the admin web pages (part 1). Data is prepared through the API, the pages are called with MockMvc. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:webui;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false",
        "flagship.enabled=false",   // the starter must not configure itself inside the server
        "flagship.security.admin-password=admin-test-pw-1"
})
@AutoConfigureMockMvc
class WebUiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();
    private TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest, port);
        for (String user : List.of("alice", "eddie", "vera", "nina")) {
            api.ensureUser(user);
        }
    }

    /** A person logged in to the web pages. The roles in the projects come from the database. */
    private static RequestPostProcessor as(String name) {
        return user(name).roles("USER");
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return mapper.readTree(response.getBody());
    }

    /** alice creates the project (ADMIN) and a flag; eddie is an EDITOR, vera a VIEWER. */
    private void projectWithTeam(String project) {
        api.createProject(project, "alice");
        api.addMember(project, "eddie", "EDITOR", "alice");
        api.addMember(project, "vera", "VIEWER", "alice");
        api.createFlag(project, "ui-flag", "alice", null);
    }

    private static String killSwitchUrl(String project) {
        return "/projects/" + project + "/flags/ui-flag/environments/PRODUCTION/kill-switch";
    }

    private boolean productionKillSwitch(String project) throws Exception {
        return json(api.call(HttpMethod.GET, "/api/projects/" + project + "/flags/ui-flag", null, "alice", null))
                .get("environments").get("PRODUCTION").get("killSwitchOn").asBoolean();
    }

    @Test
    void theLoginPageIsOpen() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")));
    }

    @Test
    void pagesNeedALogin() throws Exception {
        mvc.perform(get("/projects"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void theProjectListShowsOnlyOwnProjects() throws Exception {
        api.createProject("ui-a", "alice");
        api.createProject("ui-b", "nina");

        mvc.perform(get("/projects").with(as("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ui-a")))
                .andExpect(content().string(not(containsString("ui-b"))));
    }

    @Test
    void theRoleDecidesWhoCanUseTheKillSwitch() throws Exception {
        projectWithTeam("ui-c");

        mvc.perform(get("/projects/ui-c").with(as("vera")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ui-flag")));
        mvc.perform(get("/projects/ui-c/flags/ui-flag").with(as("vera")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PRODUCTION")))
                .andExpect(content().string(not(containsString("Turn kill switch ON"))));   // no button for a viewer

        // a viewer is refused
        mvc.perform(post(killSwitchUrl("ui-c")).param("on", "true").with(as("vera")).with(csrf()))
                .andExpect(status().isForbidden());
        assertTrue(!productionKillSwitch("ui-c"));

        // an editor can: the page shows the button and the click changes the flag
        mvc.perform(get("/projects/ui-c/flags/ui-flag").with(as("eddie")))
                .andExpect(content().string(containsString("Turn kill switch ON")));
        mvc.perform(post(killSwitchUrl("ui-c")).param("on", "true").param("reason", "incident")
                        .with(as("eddie")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/ui-c/flags/ui-flag"))
                .andExpect(flash().attributeExists("message"));
        assertTrue(productionKillSwitch("ui-c"));

        // it is a normal versioned change by the logged-in person
        JsonNode history = json(api.call(HttpMethod.GET,
                "/api/projects/ui-c/flags/ui-flag/environments/PRODUCTION/versions", null, "alice", null));
        assertEquals(2, history.size());
        assertEquals("eddie", history.get(0).get("author").asText());
        assertEquals("incident", history.get(0).get("reason").asText());

        // a request without the CSRF token is refused
        mvc.perform(post(killSwitchUrl("ui-c")).param("on", "false").with(as("eddie")))
                .andExpect(status().isForbidden());
        assertTrue(productionKillSwitch("ui-c"));
    }

    @Test
    void approvalIsRespectedByThePage() throws Exception {
        projectWithTeam("ui-d");
        TestApi.expect(200, api.call(HttpMethod.PUT, "/api/projects/ui-d/settings",
                "{\"requireApproval\":true}", "alice", null));

        // a reason is required: the page shows the error and nothing changes
        mvc.perform(post(killSwitchUrl("ui-d")).param("on", "true").with(as("eddie")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertTrue(!productionKillSwitch("ui-d"));

        // with a reason a change request is created instead of changing the flag
        mvc.perform(post(killSwitchUrl("ui-d")).param("on", "true").param("reason", "needs a look")
                        .with(as("eddie")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"));
        assertTrue(!productionKillSwitch("ui-d"));
        JsonNode requests = json(api.call(HttpMethod.GET, "/api/projects/ui-d/change-requests", null,
                "alice", null));
        assertEquals(1, requests.size());
        assertEquals("PENDING_APPROVAL", requests.get(0).get("status").asText());
        assertEquals("eddie", requests.get(0).get("author").asText());
    }

    @Test
    void nonMembersAreRefusedAndTheSiteAdminSeesEverything() throws Exception {
        projectWithTeam("ui-e");

        mvc.perform(get("/projects/ui-e").with(as("nina"))).andExpect(status().isForbidden());
        mvc.perform(get("/projects/ui-e/flags/ui-flag").with(as("nina"))).andExpect(status().isForbidden());
        mvc.perform(post(killSwitchUrl("ui-e")).param("on", "true").with(as("nina")).with(csrf()))
                .andExpect(status().isForbidden());

        RequestPostProcessor siteAdmin = user("admin").roles("USER", "SITE_ADMIN");
        mvc.perform(get("/projects").with(siteAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ui-e")));
        mvc.perform(get("/projects/ui-e").with(siteAdmin)).andExpect(status().isOk());
    }

    @Test
    void aProjectCanBeCreatedFromThePage() throws Exception {
        mvc.perform(post("/projects").param("key", "ui-new").param("name", "New one")
                        .with(as("alice")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/ui-new"));

        // the creator is the ADMIN of the new project
        JsonNode members = json(api.call(HttpMethod.GET, "/api/projects/ui-new/members", null, "alice", null));
        assertEquals("alice", members.get(0).get("username").asText());
        assertEquals("ADMIN", members.get(0).get("role").asText());

        // a bad key and a duplicate are refused with a message
        mvc.perform(post("/projects").param("key", "Bad Key").param("name", "x").with(as("alice")).with(csrf()))
                .andExpect(redirectedUrl("/projects"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/projects").param("key", "ui-new").param("name", "again").with(as("alice")).with(csrf()))
                .andExpect(redirectedUrl("/projects"))
                .andExpect(flash().attributeExists("error"));
    }
}