package com.flagship.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.Environment;
import com.flagship.server.api.ChangeRequestResponse;
import com.flagship.server.api.VersionResponse;
import com.flagship.server.change.ChangeInfo;
import com.flagship.server.change.ConfigDiff;
import com.flagship.server.change.ConfigState;
import com.flagship.server.change.DiffEntry;
import com.flagship.server.change.SetConfigCommand;
import com.flagship.server.entity.ChangeRequest;
import com.flagship.server.entity.ChangeRequestStatus;
import com.flagship.server.entity.ConfigVersion;
import com.flagship.server.entity.FlagEntity;
import com.flagship.server.entity.FlagEnvironmentConfig;
import com.flagship.server.entity.Project;
import com.flagship.server.push.FlagsChangedEvent;
import com.flagship.server.repository.ChangeRequestRepository;
import com.flagship.server.repository.FlagEnvironmentConfigRepository;
import com.flagship.server.repository.FlagRepository;
import com.flagship.server.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Change control: decides whether a change is applied at once or becomes a change request
 * (needs a second person's approval, or waits for its scheduled time), and handles the decisions.
 */
@Service
public class ChangeRequestService {

    private static final int MAX_TEXT = 500;

    private final ProjectRepository projects;
    private final FlagRepository flags;
    private final FlagEnvironmentConfigRepository configs;
    private final ChangeRequestRepository requests;
    private final VersionService versions;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    public ChangeRequestService(ProjectRepository projects, FlagRepository flags,
                                FlagEnvironmentConfigRepository configs, ChangeRequestRepository requests,
                                VersionService versions, ObjectMapper mapper,
                                ApplicationEventPublisher events) {
        this.projects = projects;
        this.flags = flags;
        this.configs = configs;
        this.requests = requests;
        this.versions = versions;
        this.mapper = mapper;
        this.events = events;
    }

    // ---------- submitting ----------

    /**
     * Queues the change if it needs approval (PRODUCTION in a project that requires it) or is
     * scheduled for later. Returns empty when the change should simply be applied now.
     */
    @Transactional
    public Optional<ChangeRequestResponse> queueIfNeeded(Project project, FlagEntity flag, Environment env,
                                                         ConfigState state, ChangeInfo info,
                                                         Instant applyAt, Integer restoredFrom) {
        boolean needsApproval = env == Environment.PRODUCTION && project.isRequireApproval();
        boolean scheduled = applyAt != null && applyAt.isAfter(Instant.now());
        if (!needsApproval && !scheduled) {
            return Optional.empty();
        }
        if (needsApproval && info.reason().isBlank()) {
            throw new IllegalArgumentException(
                    "A reason is required for production changes in a project that needs approval "
                            + "(send the X-Reason header)");
        }

        ChangeRequest cr = new ChangeRequest();
        cr.setFlag(flag);
        cr.setEnvironment(env);
        cr.setStatus(needsApproval ? ChangeRequestStatus.PENDING_APPROVAL : ChangeRequestStatus.SCHEDULED);
        cr.setAuthor(info.author());
        cr.setReason(info.reason());
        cr.setApplyAt(applyAt);
        cr.setRestoredFrom(restoredFrom);
        cr.setConfigJson(state.toTree(mapper).toString());
        requests.save(cr);
        return Optional.of(toResponse(cr));
    }

    /** Restore of an old version: applied at once, or queued like any other production change. */
    @Transactional
    public Outcome<VersionResponse> restore(String projectKey, String flagKey, Environment env,
                                            int versionNumber, ChangeInfo info, Instant applyAt) {
        Project project = findProject(projectKey);
        FlagEntity flag = flags.findByProjectAndKey(project, flagKey).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No flag with key '" + flagKey + "'"));
        VersionResponse old = versions.get(projectKey, flagKey, env, versionNumber);   // 404 if missing

        ChangeInfo withReason = info.withDefaultReason("Restored version " + versionNumber);
        Optional<ChangeRequestResponse> queued = queueIfNeeded(project, flag, env,
                ConfigState.fromTree(old.config()), withReason, applyAt, versionNumber);
        if (queued.isPresent()) {
            return new Outcome<>(null, queued.get());
        }
        return new Outcome<>(versions.restore(projectKey, flagKey, env, versionNumber, info), null);
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public List<ChangeRequestResponse> list(String projectKey, ChangeRequestStatus status) {
        Project project = findProject(projectKey);
        List<ChangeRequest> found = status == null
                ? requests.findByFlag_ProjectOrderByIdDesc(project)
                : requests.findByFlag_ProjectAndStatusOrderByIdDesc(project, status);
        return found.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ChangeRequestResponse get(String projectKey, long id) {
        return toResponse(find(projectKey, id));
    }

    // ---------- deciding ----------

    @Transactional
    public ChangeRequestResponse approve(String projectKey, long id, String approver) {
        ChangeRequest cr = find(projectKey, id);
        if (cr.getStatus() != ChangeRequestStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Change request " + id + " is " + cr.getStatus() + ", it is not waiting for approval");
        }
        if (cr.getAuthor().equalsIgnoreCase(approver)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The author of a change cannot approve it; a second person must");
        }
        Instant now = Instant.now();
        cr.setDecidedBy(approver);
        cr.setDecidedAt(now);
        if (cr.getApplyAt() != null && cr.getApplyAt().isAfter(now)) {
            cr.setStatus(ChangeRequestStatus.SCHEDULED);   // approved, now waits for its time
        } else {
            apply(cr, approver);
        }
        return toResponse(cr);
    }

    @Transactional
    public ChangeRequestResponse reject(String projectKey, long id, String by, String note) {
        ChangeRequest cr = find(projectKey, id);
        if (cr.getStatus() != ChangeRequestStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Change request " + id + " is " + cr.getStatus() + ", it is not waiting for approval");
        }
        cr.setStatus(ChangeRequestStatus.REJECTED);
        cr.setDecidedBy(by);
        cr.setDecidedAt(Instant.now());
        cr.setDecisionNote(note == null || note.isBlank() ? null : limit(note.trim()));
        return toResponse(cr);
    }

    @Transactional
    public ChangeRequestResponse cancel(String projectKey, long id, String by) {
        ChangeRequest cr = find(projectKey, id);
        boolean open = cr.getStatus() == ChangeRequestStatus.PENDING_APPROVAL
                || cr.getStatus() == ChangeRequestStatus.SCHEDULED;
        if (!open) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Change request " + id + " is " + cr.getStatus() + " and cannot be cancelled");
        }
        if (!cr.getAuthor().equalsIgnoreCase(by)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the author can cancel a change request");
        }
        cr.setStatus(ChangeRequestStatus.CANCELLED);
        cr.setDecidedBy(by);
        cr.setDecidedAt(Instant.now());
        return toResponse(cr);
    }

    // ---------- used by the scheduler ----------

    @Transactional(readOnly = true)
    public List<Long> dueIds(Instant now) {
        return requests.findByStatusAndApplyAtLessThanEqualOrderByApplyAtAsc(ChangeRequestStatus.SCHEDULED, now)
                .stream()
                .map(ChangeRequest::getId)
                .toList();
    }

    @Transactional
    public void applyScheduled(Long id) {
        ChangeRequest cr = requests.findById(id).orElse(null);
        if (cr == null
                || cr.getStatus() != ChangeRequestStatus.SCHEDULED
                || cr.getApplyAt() == null
                || cr.getApplyAt().isAfter(Instant.now())) {
            return;   // cancelled or changed in the meantime
        }
        apply(cr, cr.getDecidedBy());
    }

    @Transactional
    public void markFailed(Long id, String message) {
        requests.findById(id).ifPresent(cr -> {
            if (cr.getStatus() == ChangeRequestStatus.SCHEDULED) {
                cr.setStatus(ChangeRequestStatus.FAILED);
                cr.setFailureMessage(limit(message == null ? "unknown error" : message));
            }
        });
    }

    // ---------- helpers ----------

    /** Applies the change through the same command and version history as every other change. */
    private void apply(ChangeRequest cr, String approver) {
        FlagEntity flag = cr.getFlag();
        FlagEnvironmentConfig target = configs.findByFlagAndEnvironment(flag, cr.getEnvironment())
                .orElseThrow(() -> new IllegalStateException(
                        "Flag '" + flag.getKey() + "' has no config for " + cr.getEnvironment()));
        ConfigState state = ConfigState.fromTree(tree(cr.getConfigJson()));

        String suffix = approver != null ? " [approved by " + approver + "]" : " [scheduled]";
        ChangeInfo info = new ChangeInfo(cr.getAuthor(), limit(cr.getReason() + suffix));
        ConfigVersion created = versions.run(flag, new SetConfigCommand(target, state), info,
                cr.getRestoredFrom());

        cr.setStatus(ChangeRequestStatus.APPLIED);
        cr.setAppliedAt(Instant.now());
        cr.setAppliedVersion(created.getVersionNumber());
        events.publishEvent(new FlagsChangedEvent(flag.getProject().getKey(), flag.getKey(), cr.getEnvironment()));
    }

    private Project findProject(String key) {
        return projects.findByKey(key).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with key '" + key + "'"));
    }

    private ChangeRequest find(String projectKey, long id) {
        Project project = findProject(projectKey);
        return requests.findByIdAndFlag_Project(id, project).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No change request " + id));
    }

    private ChangeRequestResponse toResponse(ChangeRequest cr) {
        boolean open = cr.getStatus() == ChangeRequestStatus.PENDING_APPROVAL
                || cr.getStatus() == ChangeRequestStatus.SCHEDULED;
        List<DiffEntry> changes = open ? diffAgainstLive(cr) : List.of();
        return new ChangeRequestResponse(cr.getId(), cr.getFlag().getKey(), cr.getEnvironment(),
                cr.getStatus(), cr.getAuthor(), cr.getReason(), cr.getCreatedAt(), cr.getApplyAt(),
                cr.getDecidedBy(), cr.getDecidedAt(), cr.getDecisionNote(), cr.getAppliedAt(),
                cr.getAppliedVersion(), cr.getRestoredFrom(), cr.getFailureMessage(),
                tree(cr.getConfigJson()), changes);
    }

    /** What the request would change compared with what is live right now. */
    private List<DiffEntry> diffAgainstLive(ChangeRequest cr) {
        return configs.findByFlagAndEnvironment(cr.getFlag(), cr.getEnvironment())
                .map(live -> ConfigDiff.between(ConfigState.from(live).toTree(mapper), tree(cr.getConfigJson())))
                .orElse(List.of());
    }

    private static String limit(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }

    private JsonNode tree(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON is invalid", e);
        }
    }
}