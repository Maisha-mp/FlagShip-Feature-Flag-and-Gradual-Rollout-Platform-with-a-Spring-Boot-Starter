package com.flagship.server.api;

import com.flagship.core.model.Environment;
import com.flagship.server.change.ChangeInfo;
import com.flagship.server.service.FlagService;
import com.flagship.server.service.Outcome;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
                               @Valid @RequestBody FlagRequest request,
                               @RequestHeader(name = "X-Author", required = false) String author,
                               @RequestHeader(name = "X-Reason", required = false) String reason) {
        return service.create(projectKey, request, ChangeInfo.of(author, reason));
    }

    @GetMapping
    public List<FlagResponse> list(@PathVariable String projectKey) {
        return service.list(projectKey);
    }

    @GetMapping("/{flagKey}")
    public FlagResponse get(@PathVariable String projectKey, @PathVariable String flagKey) {
        return service.get(projectKey, flagKey);
    }

    /** 200 with the flag when applied now; 202 with a change request when it waits for approval or its time. */
    @PutMapping("/{flagKey}/environments/{env}")
    public ResponseEntity<Object> updateEnvironment(@PathVariable String projectKey,
                                                    @PathVariable String flagKey,
                                                    @PathVariable Environment env,
                                                    @Valid @RequestBody EnvironmentRequest request,
                                                    @RequestParam(name = "applyAt", required = false) String applyAt,
                                                    @RequestHeader(name = "X-Author", required = false) String author,
                                                    @RequestHeader(name = "X-Reason", required = false) String reason) {
        Outcome<FlagResponse> outcome = service.updateEnvironment(projectKey, flagKey, env, request,
                ChangeInfo.of(author, reason), ApplyAtParser.parse(applyAt));
        if (outcome.queued() != null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(outcome.queued());
        }
        return ResponseEntity.ok(outcome.applied());
    }
}