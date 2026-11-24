package com.flagship.server.security;

import com.flagship.server.entity.ProjectRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * The one place that decides who may call which API address.
 * The project comes from the URL, the needed role from the address and the HTTP method.
 */
@Component
public class ApiAuthorization implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Pattern PROJECT_PATH = Pattern.compile("^/api/projects/([^/]+)(/.*)?$");
    private static final Pattern DECISION = Pattern.compile("^/change-requests/[^/]+/(approve|reject)$");

    private final ProjectAccess access;

    public ApiAuthorization(ProjectAccess access) {
        this.access = access;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> supplier, RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean read = method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS");

        Matcher project = PROJECT_PATH.matcher(path);
        boolean inProject = project.matches();
        String rest = inProject && project.group(2) != null ? project.group(2) : "";

        // Applications using the starter read the snapshot and the push stream without logging in.
        if (inProject && read && (rest.equals("/snapshot") || rest.equals("/stream"))) {
            return new AuthorizationDecision(true);
        }

        Authentication auth = supplier.get();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new AuthorizationDecision(false);
        }

        boolean siteAdmin = isSiteAdmin(auth);
        if (path.equals("/api/users") || path.startsWith("/api/users/")) {
            return new AuthorizationDecision(siteAdmin);
        }
        if (!inProject) {
            return new AuthorizationDecision(true);   // e.g. /api/projects: any logged-in user
        }
        if (siteAdmin) {
            return new AuthorizationDecision(true);
        }

        ProjectRole needed = neededRole(rest, read);
        boolean allowed = access.roleOf(auth.getName(), project.group(1))
                .map(role -> role.atLeast(needed))
                .orElse(false);
        return new AuthorizationDecision(allowed);
    }

    private static ProjectRole neededRole(String rest, boolean read) {
        if (DECISION.matcher(rest).matches()) {
            return ProjectRole.APPROVER;
        }
        if (rest.equals("/settings") || rest.equals("/members") || rest.startsWith("/members/")) {
            return read ? ProjectRole.VIEWER : ProjectRole.ADMIN;
        }
        if (rest.endsWith("/evaluate")) {
            return ProjectRole.VIEWER;   // a preview: it changes nothing
        }
        return read ? ProjectRole.VIEWER : ProjectRole.EDITOR;
    }

    private static boolean isSiteAdmin(Authentication auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_SITE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}