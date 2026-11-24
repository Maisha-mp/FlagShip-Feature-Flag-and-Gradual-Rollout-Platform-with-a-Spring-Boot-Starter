package com.flagship.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import com.flagship.core.eval.FlagEvaluator;
import com.flagship.core.model.Environment;
import com.flagship.core.model.Flag;
import com.flagship.server.api.EnvironmentResponse;
import com.flagship.server.api.EvaluationRequest;
import com.flagship.server.api.FlagResponse;
import com.flagship.server.api.SnapshotResponse;
import com.flagship.server.entity.FlagEntity;
import com.flagship.server.entity.FlagEnvironmentConfig;
import com.flagship.server.entity.Project;
import com.flagship.server.repository.FlagEnvironmentConfigRepository;
import com.flagship.server.repository.FlagRepository;
import com.flagship.server.repository.ProjectRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final SegmentService segments;

    // The SAME evaluator class the starter will use (core is plain Java).
    private final FlagEvaluator evaluator = new FlagEvaluator();

    public EvaluationService(ProjectRepository projects, FlagRepository flags,
                             FlagEnvironmentConfigRepository configs,
                             FlagConverter converter, FlagService flagService,
                             SegmentService segments) {
        this.projects = projects;
        this.flags = flags;
        this.configs = configs;
        this.converter = converter;
        this.flagService = flagService;
        this.segments = segments;
    }

    @Transactional(readOnly = true)
    public EvaluationResult preview(String projectKey, String flagKey,
                                    Environment env, EvaluationRequest request) {
        Project project = projects.findByKey(projectKey).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with key '" + projectKey + "'"));
        FlagEntity flag = flags.findByProjectAndKey(project, flagKey).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No flag with key '" + flagKey + "'"));

        // Detached copies with the segments filled in; the stored configs are not touched.
        Map<String, JsonNode> conditions = segments.loadConditions(projectKey);
        List<FlagEnvironmentConfig> expanded = new ArrayList<>();
        for (FlagEnvironmentConfig c : configs.findByFlag(flag)) {
            FlagEnvironmentConfig copy = new FlagEnvironmentConfig();
            copy.setFlag(flag);
            copy.setEnvironment(c.getEnvironment());
            copy.setKillSwitchOn(c.isKillSwitchOn());
            copy.setOffVariation(c.getOffVariation());
            copy.setDefaultVariation(c.getDefaultVariation());
            copy.setUserTargetsJson(c.getUserTargetsJson());
            copy.setRulesJson(expandRules(c.getRulesJson(), conditions));
            copy.setRolloutJson(c.getRolloutJson());
            expanded.add(copy);
        }
        Flag core = converter.toCoreFlag(flag, expanded);

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
        Map<String, JsonNode> conditions = segments.loadConditions(projectKey);
        List<SnapshotResponse.Item> items = all.stream()
                .map(f -> new SnapshotResponse.Item(
                        f.key(), f.type(), f.variations(), withSegments(f.environments().get(env), conditions)))
                .toList();
        return new SnapshotResponse(projectKey, env, Instant.now(), items);
    }

    /** The environment's rules with every segment reference replaced by the segment's condition. */
    private EnvironmentResponse withSegments(EnvironmentResponse c, Map<String, JsonNode> conditions) {
        if (c == null) {
            return null;
        }
        return new EnvironmentResponse(c.killSwitchOn(), c.offVariation(), c.defaultVariation(),
                c.userTargets(), segments.expandTree(c.rules(), conditions, false), c.rollout());
    }

    private String expandRules(String rulesJson, Map<String, JsonNode> conditions) {
        if (rulesJson == null || !rulesJson.contains("\"segment\"")) {
            return rulesJson;
        }
        try {
            return segments.expandTree(new com.fasterxml.jackson.databind.ObjectMapper().readTree(rulesJson),
                    conditions, false).toString();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON is invalid", e);
        }
    }
}