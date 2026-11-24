package com.flagship.server.api;

import com.flagship.server.repository.ProjectRepository;
import com.flagship.server.service.MemberService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
    private final MemberService members;

    public ProjectController(ProjectRepository projects, MemberService members) {
        this.projects = projects;
        this.members = members;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request, Principal principal) {
        if (projects.existsByKey(request.key())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A project with key '" + request.key() + "' already exists");
        }
        return ProjectResponse.from(members.createProject(request.key(), request.name(), principal.getName()));
    }

    /** Your own projects; a site admin sees all of them. */
    @GetMapping
    public List<ProjectResponse> list(Authentication authentication) {
        boolean all = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SITE_ADMIN".equals(a.getAuthority()));
        return members.projectsOf(authentication.getName(), all);
    }

    @GetMapping("/{key}")
    public ProjectResponse get(@PathVariable String key) {
        return projects.findByKey(key)
                .map(ProjectResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No project with key '" + key + "'"));
    }
}