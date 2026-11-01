package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorStressFindingTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestorStressFindingTransitionRepository extends JpaRepository<InvestorStressFindingTransition, Long> {

    @Query("SELECT t FROM InvestorStressFindingTransition t WHERE t.finding.id = :findingRecordId ORDER BY t.createdAt DESC")
    List<InvestorStressFindingTransition> findByFindingRecordIdOrderByCreatedAtDesc(@Param("findingRecordId") Long findingRecordId);

    List<InvestorStressFindingTransition> findByFindingIdOrderByCreatedAtDesc(String findingId);
}

