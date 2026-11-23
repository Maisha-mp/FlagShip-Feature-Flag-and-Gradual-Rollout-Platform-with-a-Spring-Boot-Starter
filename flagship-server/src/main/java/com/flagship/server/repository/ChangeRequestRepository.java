package com.flagship.server.repository;

import com.flagship.server.entity.ChangeRequest;
import com.flagship.server.entity.ChangeRequestStatus;
import com.flagship.server.entity.Project;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {

    List<ChangeRequest> findByFlag_ProjectOrderByIdDesc(Project project);

    List<ChangeRequest> findByFlag_ProjectAndStatusOrderByIdDesc(Project project, ChangeRequestStatus status);

    Optional<ChangeRequest> findByIdAndFlag_Project(Long id, Project project);

    List<ChangeRequest> findByStatusAndApplyAtLessThanEqualOrderByApplyAtAsc(ChangeRequestStatus status,
                                                                             Instant now);
}