package com.flagship.server.service;

import com.flagship.server.api.MemberResponse;
import com.flagship.server.api.ProjectResponse;
import com.flagship.server.entity.AppUser;
import com.flagship.server.entity.Project;
import com.flagship.server.entity.ProjectMember;
import com.flagship.server.entity.ProjectRole;
import com.flagship.server.repository.AppUserRepository;
import com.flagship.server.repository.ProjectMemberRepository;
import com.flagship.server.repository.ProjectRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Projects people belong to, and their roles. */
@Service
public class MemberService {

    private final ProjectRepository projects;
    private final AppUserRepository users;
    private final ProjectMemberRepository members;

    public MemberService(ProjectRepository projects, AppUserRepository users, ProjectMemberRepository members) {
        this.projects = projects;
        this.users = users;
        this.members = members;
    }

    /** Creates the project and makes its creator the first ADMIN, in one transaction. */
    @Transactional
    public Project createProject(String key, String name, String creator) {
        AppUser user = users.findByUsername(creator)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user '" + creator + "'"));
        Project project = new Project();
        project.setKey(key);
        project.setName(name);
        projects.save(project);

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole(ProjectRole.ADMIN);
        members.save(member);
        return project;
    }

    /** Every project for a site admin; otherwise only the projects the person belongs to. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> projectsOf(String username, boolean all) {
        if (all) {
            return projects.findAll().stream().map(ProjectResponse::from).toList();
        }
        return members.findByUser_UsernameOrderByProject_KeyAsc(username).stream()
                .map(m -> ProjectResponse.from(m.getProject()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(String projectKey) {
        requireProject(projectKey);
        return members.findByProject_KeyOrderByUser_UsernameAsc(projectKey).stream()
                .map(m -> new MemberResponse(m.getUser().getUsername(), m.getRole()))
                .toList();
    }

    @Transactional
    public MemberResponse setRole(String projectKey, String username, ProjectRole role) {
        Project project = requireProject(projectKey);
        AppUser user = users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No user '" + username + "'"));
        ProjectMember member = members.findByProject_KeyAndUser_Username(projectKey, username).orElse(null);
        if (member == null) {
            member = new ProjectMember();
            member.setProject(project);
            member.setUser(user);
        } else if (member.getRole() == ProjectRole.ADMIN && role != ProjectRole.ADMIN) {
            requireAnotherAdmin(projectKey);
        }
        member.setRole(role);
        members.save(member);
        return new MemberResponse(username, role);
    }

    @Transactional
    public void remove(String projectKey, String username) {
        requireProject(projectKey);
        ProjectMember member = members.findByProject_KeyAndUser_Username(projectKey, username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "'" + username + "' is not a member"));
        if (member.getRole() == ProjectRole.ADMIN) {
            requireAnotherAdmin(projectKey);
        }
        members.delete(member);
    }

    private Project requireProject(String key) {
        return projects.findByKey(key).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with key '" + key + "'"));
    }

    /** A project must always keep at least one ADMIN. */
    private void requireAnotherAdmin(String projectKey) {
        if (members.countByProject_KeyAndRole(projectKey, ProjectRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A project must keep at least one ADMIN; make someone else ADMIN first");
        }
    }
}