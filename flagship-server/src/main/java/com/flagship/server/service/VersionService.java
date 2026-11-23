package com.flagship.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.Environment;
import com.flagship.server.api.DiffResponse;
import com.flagship.server.api.VersionResponse;
import com.flagship.server.change.ChangeCommand;
import com.flagship.server.change.ChangeInfo;
import com.flagship.server.change.ConfigDiff;
import com.flagship.server.change.ConfigState;
import com.flagship.server.change.SetConfigCommand;
import com.flagship.server.entity.ConfigVersion;
import com.flagship.server.entity.FlagEntity;
import com.flagship.server.entity.FlagEnvironmentConfig;
import com.flagship.server.entity.Project;
import com.flagship.server.push.FlagsChangedEvent;
import com.flagship.server.repository.ConfigVersionRepository;
import com.flagship.server.repository.FlagEnvironmentConfigRepository;
import com.flagship.server.repository.FlagRepository;
import com.flagship.server.repository.ProjectRepository;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs change commands and keeps the version history: every change is saved as a numbered
 * version with author, reason and the full configuration. Also reads history, diffs and restores.
 */
@Service
public class VersionService {

    private final ProjectRepository projects;
    private final FlagRepository flags;
    private final FlagEnvironmentConfigRepository configs;
    private final ConfigVersionRepository versions;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    public VersionService(ProjectRepository projects, FlagRepository flags,
                          FlagEnvironmentConfigRepository configs, ConfigVersionRepository versions,
                          ObjectMapper mapper, ApplicationEventPublisher events) {
        this.projects = projects;
        this.flags = flags;
        this.configs = configs;
        this.versions = versions;
        this.mapper = mapper;
        this.events = events;
    }

    // ---------- writing ----------

    /** Records version 1 of a brand-new environment configuration. */
    @Transactional
    public ConfigVersion recordInitial(FlagEntity flag, FlagEnvironmentConfig config, ChangeInfo info) {
        return record(flag, config.getEnvironment(), ConfigState.from(config), info, null);
    }

    /** Executes the command, saves the target and records the new state as the next version. */
    @Transactional
    public ConfigVersion run(FlagEntity flag, ChangeCommand command, ChangeInfo info, Integer restoredFrom) {
        FlagEnvironmentConfig target = command.target();
        Environment env = target.getEnvironment();
        if (versions.countByFlagAndEnvironment(flag, env) == 0) {
            // A flag from before version history existed: keep its old state as the first version.
            record(flag, env, ConfigState.from(target),
                    ChangeInfo.system("Baseline before version history started"), null);
        }
        command.execute();
        configs.save(target);
        return record(flag, env, ConfigState.from(target), info, restoredFrom);
    }

    /** Makes an old version current again. The restore itself becomes a new version. */
    @Transactional
    public VersionResponse restore(String projectKey, String flagKey, Environment env,
                                   int versionNumber, ChangeInfo info) {
        FlagEntity flag = findFlag(projectKey, flagKey);
        FlagEnvironmentConfig target = configs.findByFlagAndEnvironment(flag, env)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Flag '" + flagKey + "' has no config for " + env));
        ConfigVersion old = findVersion(flag, env, versionNumber);

        ConfigState state = ConfigState.fromTree(tree(old.getConfigJson()));
        ChangeCommand command = new SetConfigCommand(target, state);
        ConfigVersion created = run(flag, command,
                info.withDefaultReason("Restored version " + versionNumber), versionNumber);

        events.publishEvent(new FlagsChangedEvent(projectKey, flagKey, env));
        return toResponse(created);
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public List<VersionResponse> history(String projectKey, String flagKey, Environment env) {
        FlagEntity flag = findFlag(projectKey, flagKey);
        return versions.findByFlagAndEnvironmentOrderByVersionNumberDesc(flag, env).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionResponse get(String projectKey, String flagKey, Environment env, int versionNumber) {
        FlagEntity flag = findFlag(projectKey, flagKey);
        return toResponse(findVersion(flag, env, versionNumber));
    }

    @Transactional(readOnly = true)
    public DiffResponse diff(String projectKey, String flagKey, Environment env, int from, int to) {
        FlagEntity flag = findFlag(projectKey, flagKey);
        ConfigVersion a = findVersion(flag, env, from);
        ConfigVersion b = findVersion(flag, env, to);
        return new DiffResponse(from, to,
                ConfigDiff.between(tree(a.getConfigJson()), tree(b.getConfigJson())));
    }

    // ---------- helpers ----------

    private ConfigVersion record(FlagEntity flag, Environment env, ConfigState state,
                                 ChangeInfo info, Integer restoredFrom) {
        int next = versions.findFirstByFlagAndEnvironmentOrderByVersionNumberDesc(flag, env)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);
        ConfigVersion v = new ConfigVersion();
        v.setFlag(flag);
        v.setEnvironment(env);
        v.setVersionNumber(next);
        v.setAuthor(info.author());
        v.setReason(info.reason());
        v.setRestoredFrom(restoredFrom);
        v.setConfigJson(state.toTree(mapper).toString());
        return versions.save(v);
    }

    private FlagEntity findFlag(String projectKey, String flagKey) {
        Project project = projects.findByKey(projectKey).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with key '" + projectKey + "'"));
        return flags.findByProjectAndKey(project, flagKey).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No flag with key '" + flagKey + "'"));
    }

    private ConfigVersion findVersion(FlagEntity flag, Environment env, int versionNumber) {
        return versions.findByFlagAndEnvironmentAndVersionNumber(flag, env, versionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No version " + versionNumber + " for " + env));
    }

    private VersionResponse toResponse(ConfigVersion v) {
        return new VersionResponse(v.getVersionNumber(), v.getEnvironment(), v.getAuthor(), v.getReason(),
                v.getCreatedAt(), v.getRestoredFrom(), tree(v.getConfigJson()));
    }

    private JsonNode tree(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON is invalid", e);
        }
    }
}