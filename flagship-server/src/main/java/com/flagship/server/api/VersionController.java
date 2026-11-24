package com.flagship.server.api;

import com.flagship.core.model.Environment;
import com.flagship.server.change.ChangeInfo;
import com.flagship.server.service.ChangeRequestService;
import com.flagship.server.service.Outcome;
import com.flagship.server.service.VersionService;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectKey}/flags/{flagKey}/environments/{env}/versions")
public class VersionController {

    private final VersionService service;
    private final ChangeRequestService changeRequests;

    public VersionController(VersionService service, ChangeRequestService changeRequests) {
        this.service = service;
        this.changeRequests = changeRequests;
    }

    /** All versions, newest first. */
    @GetMapping
    public List<VersionResponse> history(@PathVariable String projectKey, @PathVariable String flagKey,
                                         @PathVariable Environment env) {
        return service.history(projectKey, flagKey, env);
    }

    @GetMapping("/{version}")
    public VersionResponse get(@PathVariable String projectKey, @PathVariable String flagKey,
                               @PathVariable Environment env, @PathVariable int version) {
        return service.get(projectKey, flagKey, env, version);
    }

    /** What changed between two versions. */
    @GetMapping("/diff")
    public DiffResponse diff(@PathVariable String projectKey, @PathVariable String flagKey,
                             @PathVariable Environment env,
                             @RequestParam int from, @RequestParam int to) {
        return service.diff(projectKey, flagKey, env, from, to);
    }

    /**
     * Restores one version in one action. 201 with the new version when done now;
     * 202 with a change request when production approval or a future applyAt applies.
     */
    @PostMapping("/{version}/restore")
    public ResponseEntity<Object> restore(@PathVariable String projectKey, @PathVariable String flagKey,
                                          @PathVariable Environment env, @PathVariable int version,
                                          @RequestParam(name = "applyAt", required = false) String applyAt,
                                          Principal principal,
                                          @RequestHeader(name = "X-Reason", required = false) String reason) {
        Outcome<VersionResponse> outcome = changeRequests.restore(projectKey, flagKey, env, version,
                ChangeInfo.of(principal.getName(), reason), ApplyAtParser.parse(applyAt));
        if (outcome.queued() != null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(outcome.queued());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(outcome.applied());
    }
}