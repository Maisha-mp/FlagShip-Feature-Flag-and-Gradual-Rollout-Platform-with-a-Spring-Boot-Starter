package com.flagship.server.api;

import com.flagship.server.entity.Project;
import com.flagship.server.repository.ProjectRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projects;

    public ProjectController(ProjectRepository projects) {
        this.projects = projects;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request) {
        if (projects.existsByKey(request.key())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A project with key '" + request.key() + "' already exists");
        }
        Project p = new Project();
        p.setKey(request.key());
        p.setName(request.name());
        return ProjectResponse.from(projects.save(p));
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projects.findAll().stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{key}")
    public ProjectResponse get(@PathVariable String key) {
        return projects.findByKey(key)
                .map(ProjectResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No project with key '" + key + "'"));
    }
}