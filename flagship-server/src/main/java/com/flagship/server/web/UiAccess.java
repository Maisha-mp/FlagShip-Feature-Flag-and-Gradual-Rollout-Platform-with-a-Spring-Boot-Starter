package com.flagship.server.web;

import com.flagship.server.entity.ProjectRole;
import com.flagship.server.security.ProjectAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** The same role rules as the API, for the web pages. A site admin counts as ADMIN everywhere. */
@Component
public class UiAccess {

    private final ProjectAccess access;

    public UiAccess(ProjectAccess access) {
        this.access = access;
    }

    public boolean isSiteAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_SITE_ADMIN".equals(a.getAuthority()));
    }

    /** Returns the person's role, or answers 403 when they have no access or too low a role. */
    public ProjectRole require(Authentication auth, String projectKey, ProjectRole needed) {
        ProjectRole role = isSiteAdmin(auth)
                ? ProjectRole.ADMIN
                : access.roleOf(auth.getName(), projectKey).orElse(null);
        if (role == null || !role.atLeast(needed)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have access to this page of project '" + projectKey + "'");
        }
        return role;
    }
}