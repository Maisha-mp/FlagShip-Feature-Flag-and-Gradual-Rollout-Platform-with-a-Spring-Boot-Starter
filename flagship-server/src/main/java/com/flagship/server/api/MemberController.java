package com.flagship.server.api;

import com.flagship.server.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Who is in the project and with which role. Changing members needs the ADMIN role (see ApiAuthorization). */
@RestController
@RequestMapping("/api/projects/{projectKey}/members")
public class MemberController {

    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberResponse> list(@PathVariable String projectKey) {
        return service.list(projectKey);
    }

    /** Adds the person or changes their role. */
    @PutMapping("/{username}")
    public MemberResponse set(@PathVariable String projectKey, @PathVariable String username,
                              @Valid @RequestBody MemberRequest request) {
        return service.setRole(projectKey, username, request.role());
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String projectKey, @PathVariable String username) {
        service.remove(projectKey, username);
    }
}