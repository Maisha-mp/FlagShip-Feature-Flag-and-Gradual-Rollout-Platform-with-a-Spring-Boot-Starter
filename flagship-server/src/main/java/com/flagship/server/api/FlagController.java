package com.flagship.server.api;

import com.flagship.core.model.Environment;
import com.flagship.server.service.FlagService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectKey}/flags")
public class FlagController {

    private final FlagService service;

    public FlagController(FlagService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlagResponse create(@PathVariable String projectKey,
                               @Valid @RequestBody FlagRequest request) {
        return service.create(projectKey, request);
    }

    @GetMapping
    public List<FlagResponse> list(@PathVariable String projectKey) {
        return service.list(projectKey);
    }

    @GetMapping("/{flagKey}")
    public FlagResponse get(@PathVariable String projectKey, @PathVariable String flagKey) {
        return service.get(projectKey, flagKey);
    }

    @PutMapping("/{flagKey}/environments/{env}")
    public FlagResponse updateEnvironment(@PathVariable String projectKey,
                                          @PathVariable String flagKey,
                                          @PathVariable Environment env,
                                          @Valid @RequestBody EnvironmentRequest request) {
        return service.updateEnvironment(projectKey, flagKey, env, request);
    }
}