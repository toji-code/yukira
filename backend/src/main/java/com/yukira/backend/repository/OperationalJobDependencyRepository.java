package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.OperationalJobDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OperationalJobDependencyRepository extends JpaRepository<OperationalJobDependency, Long> {
    Optional<OperationalJobDependency> findByJobIdentifier(String jobIdentifier);
    List<OperationalJobDependency> findByProviderConfigStatus(String providerConfigStatus);
    List<OperationalJobDependency> findAllByOrderByJobIdentifierAsc();
}

