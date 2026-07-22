package com.tracetick.persistence;

import com.tracetick.domain.IngestionConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngestionConfigurationRepository extends JpaRepository<IngestionConfiguration, Long> {

    Optional<IngestionConfiguration> findByUrlToken(String urlToken);

    boolean existsByUrlToken(String urlToken);
}
