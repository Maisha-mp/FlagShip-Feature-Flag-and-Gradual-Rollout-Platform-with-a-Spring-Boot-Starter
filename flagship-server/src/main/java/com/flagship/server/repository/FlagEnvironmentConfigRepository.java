package com.flagship.server.repository;

import com.flagship.core.model.Environment;
import com.flagship.server.entity.FlagEntity;
import com.flagship.server.entity.FlagEnvironmentConfig;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagEnvironmentConfigRepository extends JpaRepository<FlagEnvironmentConfig, Long> {

    List<FlagEnvironmentConfig> findByFlag(FlagEntity flag);

    List<FlagEnvironmentConfig> findByFlagIn(Collection<FlagEntity> flags);

    Optional<FlagEnvironmentConfig> findByFlagAndEnvironment(FlagEntity flag, Environment environment);
}