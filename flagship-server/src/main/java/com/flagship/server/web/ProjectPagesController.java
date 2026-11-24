package com.flagship.server.web;

import com.flagship.core.model.Environment;
import com.flagship.server.api.EnvironmentRequest;
import com.flagship.server.api.EnvironmentResponse;
import com.flagship.server.api.FlagResponse;
import com.flagship.server.api.ProjectResponse;
import com.flagship.server.change.ChangeInfo;
import com.flagship.server.entity.Project;
import com.flagship.server.entity.ProjectRole;
import com.flagship.server.repository.ProjectRepository;
import com.flagship.server.service.FlagService;
import com.flagship.server.service.MemberService;
import com.flagship.server.service.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin pages, part 1: projects, flag list, flag detail and the kill-switch button. */
@Controller
public class ProjectPagesController {

    private static final Pattern KEY = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    /** One line of the flag list. */
    public record FlagRow(String key, String name, String type, String killSwitchIn) { }

    /** One environment of a flag, in the form the page shows it. */
    public record EnvView(String name, boolean killSwitchOn, String offVariation, String defaultVariation,
                          int userTargets, int rules, String rollout) { }

    private final ProjectRepository projects;
    private final MemberService members;
    private final FlagService flags;
    private final UiAccess access;

    public ProjectPagesController(ProjectRepository projects, MemberService members, FlagService flags,
                                  UiAccess access) {
        this.projects = projects;
        this.members = members;
        this.flags = flags;
        this.access = access;
    }

    // ---------- project list ----------

    @GetMapping("/projects")
    public String projectList(Authentication auth, Model model) {
        model.addAttribute("username", auth.getName());
        model.addAttribute("projects", members.projectsOf(auth.getName(), access.isSiteAdmin(auth)));
        return "projects";
    }

    @PostMapping("/projects")
    public String createProject(@RequestParam String key, @RequestParam String name,
                                Authentication auth, RedirectAttributes redirect) {
        String k = key.trim();
        String n = name.trim();
        String problem = null;
        if (k.isEmpty() || k.length() > 100 || !KEY.matcher(k).matches()) {
            problem = "The key must be lowercase letters, digits and single dashes, e.g. checkout-app";
        } else if (n.isEmpty() || n.length() > 200) {
            problem = "The name is required (at most 200 characters)";
        } else if (projects.existsByKey(k)) {
            problem = "A project with key '" + k + "' already exists";
        }
        if (problem != null) {
            redirect.addFlashAttribute("error", problem);
            return "redirect:/projects";
        }
        members.createProject(k, n, auth.getName());
        redirect.addFlashAttribute("message", "Project '" + k + "' created. You are its ADMIN.");
        return "redirect:/projects/" + k;
    }

    // ---------- one project ----------

    @GetMapping("/projects/{key}")
    public String project(@PathVariable String key, Authentication auth, Model model) {
        ProjectRole role = access.require(auth, key, ProjectRole.VIEWER);
        Project project = findProject(key);
        List<FlagRow> rows = flags.list(key).stream().map(this::row).toList();

        model.addAttribute("username", auth.getName());
        model.addAttribute("project", ProjectResponse.from(project));
        model.addAttribute("role", role.name());
        model.addAttribute("requireApproval", project.isRequireApproval());
        model.addAttribute("flags", rows);
        return "project";
    }

    // ---------- one flag ----------

    @GetMapping("/projects/{key}/flags/{flagKey}")
    public String flag(@PathVariable String key, @PathVariable String flagKey,
                       Authentication auth, Model model) {
        ProjectRole role = access.require(auth, key, ProjectRole.VIEWER);
        Project project = findProject(key);
        FlagResponse flag = flags.get(key, flagKey);

        List<EnvView> envs = new ArrayList<>();
        for (Environment env : Environment.values()) {
            EnvironmentResponse config = flag.environments().get(env);
            if (config != null) {
                envs.add(view(env, config));
            }
        }

        model.addAttribute("username", auth.getName());
        model.addAttribute("project", ProjectResponse.from(project));
        model.addAttribute("flag", flag);
        model.addAttribute("envs", envs);
        model.addAttribute("role", role.name());
        model.addAttribute("canEdit", role.atLeast(ProjectRole.EDITOR));
        model.addAttribute("requireApproval", project.isRequireApproval());
        return "flag";
    }

    /** Turns the kill switch of one environment on or off, like the API does (versioned, approval-aware). */
    @PostMapping("/projects/{key}/flags/{flagKey}/environments/{env}/kill-switch")
    public String killSwitch(@PathVariable String key, @PathVariable String flagKey,
                             @PathVariable Environment env, @RequestParam boolean on,
                             @RequestParam(defaultValue = "") String reason,
                             Authentication auth, RedirectAttributes redirect) {
        access.require(auth, key, ProjectRole.EDITOR);
        FlagResponse flag = flags.get(key, flagKey);
        EnvironmentResponse c = flag.environments().get(env);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Flag '" + flagKey + "' has no config for " + env);
        }

        // The whole config again, with only the kill switch changed.
        EnvironmentRequest request = new EnvironmentRequest(on, c.offVariation(), c.defaultVariation(),
                c.userTargets(), c.rules(), c.rollout());
        try {
            Outcome<FlagResponse> outcome = flags.updateEnvironment(key, flagKey, env, request,
                    ChangeInfo.of(auth.getName(), reason), null);
            if (outcome.queued() != null) {
                redirect.addFlashAttribute("message", "Change request #" + outcome.queued().id()
                        + " was created and waits for approval by a second person.");
            } else {
                redirect.addFlashAttribute("message",
                        "Kill switch is now " + (on ? "ON" : "OFF") + " in " + env + ".");
            }
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/projects/" + key + "/flags/" + flagKey;
    }

    // ---------- helpers ----------

    private Project findProject(String key) {
        return projects.findByKey(key).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with key '" + key + "'"));
    }

    private FlagRow row(FlagResponse f) {
        String killed = f.environments().entrySet().stream()
                .filter(e -> e.getValue().killSwitchOn())
                .map(e -> e.getKey().name())
                .collect(Collectors.joining(", "));
        return new FlagRow(f.key(), f.name(), f.type().name(), killed.isEmpty() ? "none" : killed);
    }

    private EnvView view(Environment env, EnvironmentResponse c) {
        boolean hasRollout = c.rollout() != null && !c.rollout().isNull();
        String rollout = hasRollout
                ? c.rollout().path("percent").asText() + "% to " + c.rollout().path("variation").asText()
                : "none";
        return new EnvView(env.name(), c.killSwitchOn(), c.offVariation(), c.defaultVariation(),
                c.userTargets() == null ? 0 : c.userTargets().size(),
                c.rules() == null ? 0 : c.rules().size(),
                rollout);
    }
}