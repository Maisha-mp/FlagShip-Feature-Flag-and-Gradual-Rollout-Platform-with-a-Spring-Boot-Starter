package com.flagship.server.repository;

import com.flagship.server.entity.ProjectMember;
import com.flagship.server.entity.ProjectRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProject_KeyAndUser_Username(String projectKey, String username);

    List<ProjectMember> findByProject_KeyOrderByUser_UsernameAsc(String projectKey);

    List<ProjectMember> findByUser_UsernameOrderByProject_KeyAsc(String username);

    long countByProject_KeyAndRole(String projectKey, ProjectRole role);
}