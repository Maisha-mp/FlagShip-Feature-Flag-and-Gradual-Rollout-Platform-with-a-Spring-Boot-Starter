package com.flagship.server.security;

import com.flagship.server.entity.ProjectRole;
import com.flagship.server.repository.ProjectMemberRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Answers: what is this person's role in this project? */
@Component
public class ProjectAccess {

    private final ProjectMemberRepository members;

    public ProjectAccess(ProjectMemberRepository members) {
        this.members = members;
    }

    public Optional<ProjectRole> roleOf(String username, String projectKey) {
        return members.findByProject_KeyAndUser_Username(projectKey, username)
                .map(m -> m.getRole());
    }
}