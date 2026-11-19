package com.flagship.server.repository;

import com.flagship.server.entity.FlagEntity;
import com.flagship.server.entity.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagRepository extends JpaRepository<FlagEntity, Long> {

    Optional<FlagEntity> findByProjectAndKey(Project project, String key);

    boolean existsByProjectAndKey(Project project, String key);

    List<FlagEntity> findByProjectOrderByKeyAsc(Project project);
}