package com.flagship.server.api;

import com.flagship.server.entity.Project;
import com.flagship.server.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Project-wide settings. Day 8 will restrict changing them to the admin role. */
@RestController
@RequestMapping("/api/projects/{key}/settings")
public class ProjectSettingsController {

    /** requireApproval: production changes must be approved by a second person. */
    public record Settings(boolean requireApproval) { }

    private final ProjectRepository projects;

    public ProjectSettingsController(ProjectRepository projects) {
        this.projects = projects;
    }

    @GetMapping
    public Settings get(@PathVariable String key) {
        return new Settings(find(key).isRequireApproval());
    }

    @PutMapping
    public Settings update(@PathVariable String key, @RequestBody Settings settings) {
        Project project = find(key);
        project.setRequireApproval(settings.requireApproval());
        projects.save(project);
        return settings;
    }

    private Project find(String key) {
        return projects.findByKey(key).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with key '" + key + "'"));
    }
}