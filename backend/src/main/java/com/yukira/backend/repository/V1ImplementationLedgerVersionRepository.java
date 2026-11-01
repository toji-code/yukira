package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.V1ImplementationLedgerVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface V1ImplementationLedgerVersionRepository extends JpaRepository<V1ImplementationLedgerVersion, Long> {
    List<V1ImplementationLedgerVersion> findByTaskNumberOrderByChangedAtDesc(Integer taskNumber);
    List<V1ImplementationLedgerVersion> findByCapabilityIdOrderByChangedAtDesc(String capabilityId);
    List<V1ImplementationLedgerVersion> findAllByOrderByChangedAtDesc();
}

