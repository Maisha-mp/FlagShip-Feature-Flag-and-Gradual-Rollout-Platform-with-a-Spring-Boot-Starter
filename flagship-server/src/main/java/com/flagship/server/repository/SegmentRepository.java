package com.flagship.server.repository;

import com.flagship.server.entity.Project;
import com.flagship.server.entity.Segment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SegmentRepository extends JpaRepository<Segment, Long> {

    List<Segment> findByProjectOrderByKeyAsc(Project project);

    Optional<Segment> findByProjectAndKey(Project project, String key);
}