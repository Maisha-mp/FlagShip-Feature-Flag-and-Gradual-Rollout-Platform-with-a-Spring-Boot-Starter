package com.flagship.server.api;

import com.flagship.core.eval.EvaluationResult;
import com.flagship.core.model.Environment;
import com.flagship.server.service.EvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectKey}")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @PostMapping("/flags/{flagKey}/evaluate")
    public EvaluationResult evaluate(@PathVariable String projectKey,
                                     @PathVariable String flagKey,
                                     @RequestParam("env") Environment env,
                                     @Valid @RequestBody EvaluationRequest request) {
        return service.preview(projectKey, flagKey, env, request);
    }

    @GetMapping("/snapshot")
    public SnapshotResponse snapshot(@PathVariable String projectKey,
                                     @RequestParam("env") Environment env) {
        return service.snapshot(projectKey, env);
    }
}