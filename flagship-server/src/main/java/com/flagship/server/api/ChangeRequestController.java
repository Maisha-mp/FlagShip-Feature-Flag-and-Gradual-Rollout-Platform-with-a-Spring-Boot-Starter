package com.flagship.server.api;

import com.flagship.server.entity.ChangeRequestStatus;
import com.flagship.server.service.ChangeRequestService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectKey}/change-requests")
public class ChangeRequestController {

    private final ChangeRequestService service;

    public ChangeRequestController(ChangeRequestService service) {
        this.service = service;
    }

    /** All change requests of the project, newest first; optionally only one status. */
    @GetMapping
    public List<ChangeRequestResponse> list(@PathVariable String projectKey,
                                            @RequestParam(required = false) ChangeRequestStatus status) {
        return service.list(projectKey, status);
    }

    @GetMapping("/{id}")
    public ChangeRequestResponse get(@PathVariable String projectKey, @PathVariable long id) {
        return service.get(projectKey, id);
    }

    @PostMapping("/{id}/approve")
    public ChangeRequestResponse approve(@PathVariable String projectKey, @PathVariable long id,
                                         Principal principal) {
        return service.approve(projectKey, id, principal.getName());
    }

    @PostMapping("/{id}/reject")
    public ChangeRequestResponse reject(@PathVariable String projectKey, @PathVariable long id,
                                        Principal principal,
                                        @RequestHeader(name = "X-Reason", required = false) String note) {
        return service.reject(projectKey, id, principal.getName(), note);
    }

    @PostMapping("/{id}/cancel")
    public ChangeRequestResponse cancel(@PathVariable String projectKey, @PathVariable long id,
                                        Principal principal) {
        return service.cancel(projectKey, id, principal.getName());
    }
}