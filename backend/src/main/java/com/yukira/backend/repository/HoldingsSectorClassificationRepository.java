package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.HoldingsSectorClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingsSectorClassificationRepository extends JpaRepository<HoldingsSectorClassification, Long> {
    Optional<HoldingsSectorClassification> findBySectorCode(String sectorCode);
    List<HoldingsSectorClassification> findByIsActiveTrueOrderBySectorNameAsc();
}

