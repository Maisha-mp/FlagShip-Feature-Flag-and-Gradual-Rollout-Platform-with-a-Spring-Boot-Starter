package com.flagship.server.service;

import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import com.flagship.core.eval.FlagEvaluator;
import com.flagship.core.model.Environment;
import com.flagship.core.model.Flag;
import com.flagship.server.api.EvaluationRequest;
import com.flagship.server.api.FlagResponse;
import com.flagship.server.api.SnapshotResponse;
import com.flagship.server.entity.FlagEntity;
import com.flagship.server.entity.Project;
import com.flagship.server.repository.FlagEnvironmentConfigRepository;
import com.flagship.server.repository.FlagRepository;
import com.flagship.server.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvaluationService {

    private final ProjectRepository projects;
    private final FlagRepository flags;
    private final FlagEnvironmentConfigRepository configs;
    private final FlagConverter converter;
    private final FlagService flagService;

    // The SAME evaluator class the starter will use (core is plain Java).
    private final FlagEvaluator evaluator = new FlagEvaluator();

    public EvaluationService(ProjectRepository projects, FlagRepository flags,
                             FlagEnvironmentConfigRepository configs,
                             FlagConverter converter, FlagService flagService) {
        this.projects = projects;
        this.flags = flags;
        this.configs = configs;
        this.converter = converter;
        this.flagService = flagService;
    }

    @Transactional(readOnly = true)
    public EvaluationResult preview(String projectKey, String flagKey,
                                    Environment env, EvaluationRequest request) {
        Project project = projects.findByKey(projectKey).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with key '" + projectKey + "'"));
        FlagEntity flag = flags.findByProjectAndKey(project, flagKey).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No flag with key '" + flagKey + "'"));

        Flag core = converter.toCoreFlag(flag, configs.findByFlag(flag));

        EvaluationContext.Builder builder = EvaluationContext.builder(request.userKey());
        if (request.attributes() != null) {
            request.attributes().forEach((name, value) -> {
                if (value != null) {
                    builder.attribute(name, value);
                }
            });
        }
        return evaluator.evaluate(core, env, builder.build());
    }

    @Transactional(readOnly = true)
    public SnapshotResponse snapshot(String projectKey, Environment env) {
        List<FlagResponse> all = flagService.list(projectKey);
        List<SnapshotResponse.Item> items = all.stream()
                .map(f -> new SnapshotResponse.Item(
                        f.key(), f.type(), f.variations(), f.environments().get(env)))
                .toList();
        return new SnapshotResponse(projectKey, env, Instant.now(), items);
    }
}