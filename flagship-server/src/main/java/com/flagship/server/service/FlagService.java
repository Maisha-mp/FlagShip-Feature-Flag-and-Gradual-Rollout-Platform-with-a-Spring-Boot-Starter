package com.flagship.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.Environment;
import com.flagship.core.model.FlagType;
import com.flagship.core.model.Rollout;
import com.flagship.core.model.Rule;
import com.flagship.core.model.Variation;
import com.flagship.server.api.ChangeRequestResponse;
import com.flagship.server.api.EnvironmentRequest;
import com.flagship.server.api.EnvironmentResponse;
import com.flagship.server.api.FlagRequest;
import com.flagship.server.api.FlagResponse;
import com.flagship.server.change.ChangeInfo;
import com.flagship.server.change.ConfigState;
import com.flagship.server.change.SetConfigCommand;
import com.flagship.server.entity.FlagEntity;
import com.flagship.server.entity.FlagEnvironmentConfig;
import com.flagship.server.entity.Project;
import com.flagship.server.push.FlagsChangedEvent;
import com.flagship.server.repository.FlagEnvironmentConfigRepository;
import com.flagship.server.repository.FlagRepository;
import com.flagship.server.repository.ProjectRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FlagService {

    private final ProjectRepository projects;
    private final FlagRepository flags;
    private final FlagEnvironmentConfigRepository configs;
    private final FlagConverter converter;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final VersionService versions;
    private final ChangeRequestService changeRequests;
    private final SegmentService segments;

    public FlagService(ProjectRepository projects, FlagRepository flags,
                       FlagEnvironmentConfigRepository configs,
                       FlagConverter converter, ObjectMapper mapper,
                       ApplicationEventPublisher events, VersionService versions,
                       ChangeRequestService changeRequests, SegmentService segments) {
        this.projects = projects;
        this.flags = flags;
        this.configs = configs;
        this.converter = converter;
        this.mapper = mapper;
        this.events = events;
        this.versions = versions;
        this.changeRequests = changeRequests;
        this.segments = segments;
    }

    // ---------- create ----------

    @Transactional
    public FlagResponse create(String projectKey, FlagRequest req, ChangeInfo info) {
        Project project = findProject(projectKey);
        if (flags.existsByProjectAndKey(project, req.key())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Flag '" + req.key() + "' already exists in project '" + projectKey + "'");
        }
        checkValuesMatchType(req.type(), req.variations());

        Set<Environment> missing = EnumSet.allOf(Environment.class);
        missing.removeAll(req.environments().keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("environments must also include " + missing);
        }

        Map<String, Variation> variations = new LinkedHashMap<>();
        req.variations().forEach((name, value) -> variations.put(name, new Variation(name, value)));

        // Validate every environment BEFORE saving anything.
        List<FlagEnvironmentConfig> built = new ArrayList<>();
        for (Map.Entry<Environment, EnvironmentRequest> e : req.environments().entrySet()) {
            FlagEnvironmentConfig cfg = new FlagEnvironmentConfig();
            cfg.setEnvironment(e.getKey());
            applyTo(cfg, variations.keySet(), e.getValue(), projectKey);
            built.add(cfg);
        }

        FlagEntity flag = new FlagEntity();
        flag.setProject(project);
        flag.setKey(req.key());
        flag.setName(req.name());
        flag.setType(req.type());
        flag.setVariationsJson(converter.variationsToJson(variations));
        flags.save(flag);

        ChangeInfo created = info.withDefaultReason("Flag created");
        for (FlagEnvironmentConfig cfg : built) {
            cfg.setFlag(flag);
            configs.save(cfg);
            versions.recordInitial(flag, cfg, created);   // version 1 of this environment
        }

        // Delivered to connected apps only AFTER the transaction commits.
        events.publishEvent(new FlagsChangedEvent(projectKey, req.key(), null));
        return toResponse(flag, built);
    }

    // ---------- read ----------

    @Transactional(readOnly = true)
    public List<FlagResponse> list(String projectKey) {
        Project project = findProject(projectKey);
        List<FlagEntity> all = flags.findByProjectOrderByKeyAsc(project);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<Long, List<FlagEnvironmentConfig>> byFlag = configs.findByFlagIn(all).stream()
                .collect(Collectors.groupingBy(c -> c.getFlag().getId()));
        return all.stream()
                .map(f -> toResponse(f, byFlag.getOrDefault(f.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public FlagResponse get(String projectKey, String flagKey) {
        FlagEntity flag = findFlag(findProject(projectKey), flagKey);
        return toResponse(flag, configs.findByFlag(flag));
    }

    // ---------- update one environment (replaces that environment's whole config) ----------

    /**
     * Applies the change now, or queues it as a change request (approval needed or applyAt in the future).
     */
    @Transactional
    public Outcome<FlagResponse> updateEnvironment(String projectKey, String flagKey, Environment env,
                                                   EnvironmentRequest req, ChangeInfo info, Instant applyAt) {
        Project project = findProject(projectKey);
        FlagEntity flag = findFlag(project, flagKey);
        FlagEnvironmentConfig cfg = configs.findByFlagAndEnvironment(flag, env)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Flag '" + flagKey + "' has no config for " + env));

        // Validate the request on a scratch copy; the real config only changes when the command runs.
        FlagEnvironmentConfig scratch = new FlagEnvironmentConfig();
        applyTo(scratch, converter.variationsFromJson(flag.getVariationsJson()).keySet(), req, projectKey);
        ConfigState state = ConfigState.from(scratch);

        Optional<ChangeRequestResponse> queued =
                changeRequests.queueIfNeeded(project, flag, env, state, info, applyAt, null);
        if (queued.isPresent()) {
            return new Outcome<>(null, queued.get());
        }

        versions.run(flag, new SetConfigCommand(cfg, state), info, null);
        events.publishEvent(new FlagsChangedEvent(projectKey, flagKey, env));
        return new Outcome<>(toResponse(flag, configs.findByFlag(flag)), null);
    }

    // ---------- helpers ----------

    private Project findProject(String key) {
        return projects.findByKey(key).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with key '" + key + "'"));
    }

    private FlagEntity findFlag(Project project, String key) {
        return flags.findByProjectAndKey(project, key).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No flag with key '" + key + "'"));
    }

    /** Checks that every variation value fits the flag type. */
    private void checkValuesMatchType(FlagType type, Map<String, Object> variations) {
        variations.forEach((name, value) -> {
            boolean ok = switch (type) {
                case BOOLEAN -> value instanceof Boolean;
                case STRING -> value instanceof String;
                case NUMBER -> value instanceof Number;
                case JSON -> value instanceof Map || value instanceof List;
            };
            if (!ok) {
                throw new IllegalArgumentException(
                        "Variation '" + name + "' does not match flag type " + type);
            }
        });
    }

    /**
     * Validates a request and copies it onto the entity. Throws IllegalArgumentException if bad.
     * Rules that use segments are checked with the segments filled in, but stored with their
     * references, so a later change to a segment reaches every flag that uses it.
     */
    private void applyTo(FlagEnvironmentConfig cfg, Set<String> variationNames, EnvironmentRequest r,
                         String projectKey) {
        Map<String, String> userTargets = r.userTargets() == null ? Map.of() : r.userTargets();
        String rulesText = isMissing(r.rules()) ? "[]" : r.rules().toString();
        String rolloutText = isMissing(r.rollout()) ? null : r.rollout().toString();

        boolean usesSegments = !isMissing(r.rules()) && segments.containsReference(r.rules());
        String checkedRules = usesSegments ? segments.expandText(projectKey, rulesText, true) : rulesText;

        // These throw IllegalArgumentException on a bad operator, bad percent, bad shape...
        List<Rule> rules = converter.rulesFromJson(checkedRules);
        Rollout rollout = converter.rolloutFromJson(rolloutText);

        requireVariation(variationNames, r.offVariation(), "offVariation");
        requireVariation(variationNames, r.defaultVariation(), "defaultVariation");
        for (String v : userTargets.values()) {
            requireVariation(variationNames, v, "userTargets");
        }
        for (Rule rule : rules) {
            requireVariation(variationNames, rule.variation(), "rule '" + rule.id() + "'");
        }
        if (rollout != null) {
            requireVariation(variationNames, rollout.variation(), "rollout");
        }

        cfg.setKillSwitchOn(r.killSwitchOn());
        cfg.setOffVariation(r.offVariation());
        cfg.setDefaultVariation(r.defaultVariation());
        cfg.setUserTargetsJson(converter.userTargetsToJson(userTargets));
        cfg.setRulesJson(usesSegments ? rulesText : converter.rulesToJson(rules));
        cfg.setRolloutJson(converter.rolloutToJson(rollout));
    }

    private boolean isMissing(JsonNode n) {
        return n == null || n.isNull();
    }

    private void requireVariation(Set<String> names, String name, String where) {
        if (name == null || !names.contains(name)) {
            throw new IllegalArgumentException(
                    "Unknown variation '" + name + "' in " + where + " (flag has: " + names + ")");
        }
    }

    private FlagResponse toResponse(FlagEntity flag, List<FlagEnvironmentConfig> flagConfigs) {
        Map<Environment, EnvironmentResponse> envs = new EnumMap<>(Environment.class);
        for (FlagEnvironmentConfig c : flagConfigs) {
            envs.put(c.getEnvironment(), new EnvironmentResponse(
                    c.isKillSwitchOn(),
                    c.getOffVariation(),
                    c.getDefaultVariation(),
                    converter.userTargetsFromJson(c.getUserTargetsJson()),
                    tree(c.getRulesJson()),
                    c.getRolloutJson() == null ? null : tree(c.getRolloutJson())));
        }
        Map<String, Object> values = new LinkedHashMap<>();
        converter.variationsFromJson(flag.getVariationsJson())
                .forEach((name, v) -> values.put(name, v.value()));
        return new FlagResponse(flag.getKey(), flag.getName(), flag.getType(),
                values, envs, flag.getCreatedAt());
    }

    private JsonNode tree(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON is invalid", e);
        }
    }
}