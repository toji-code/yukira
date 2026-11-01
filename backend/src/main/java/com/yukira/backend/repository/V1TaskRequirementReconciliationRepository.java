package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.V1TaskRequirementReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface V1TaskRequirementReconciliationRepository extends JpaRepository<V1TaskRequirementReconciliation, Long> {

    Optional<V1TaskRequirementReconciliation> findByTaskNumber(Integer taskNumber);

    List<V1TaskRequirementReconciliation> findAllByOrderByTaskNumberAsc();

    List<V1TaskRequirementReconciliation> findByImplementationStatus(String status);
}
