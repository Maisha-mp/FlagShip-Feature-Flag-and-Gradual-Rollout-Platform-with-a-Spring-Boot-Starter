package com.flagship.server.repository;

import com.flagship.core.model.Environment;
import com.flagship.server.entity.ConfigVersion;
import com.flagship.server.entity.FlagEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Long> {

    List<ConfigVersion> findByFlagAndEnvironmentOrderByVersionNumberDesc(FlagEntity flag, Environment environment);

    Optional<ConfigVersion> findByFlagAndEnvironmentAndVersionNumber(FlagEntity flag, Environment environment,
                                                                     int versionNumber);

    Optional<ConfigVersion> findFirstByFlagAndEnvironmentOrderByVersionNumberDesc(FlagEntity flag,
                                                                                  Environment environment);

    long countByFlagAndEnvironment(FlagEntity flag, Environment environment);
}