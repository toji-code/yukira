package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.BackgroundJobExecutionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BackgroundJobExecutionRecordRepository extends JpaRepository<BackgroundJobExecutionRecord, Long> {

    Optional<BackgroundJobExecutionRecord> findByExecutionId(String executionId);

    List<BackgroundJobExecutionRecord> findByJobIdentifierOrderByStartTimeDesc(String jobIdentifier);

    @Query("SELECT r FROM BackgroundJobExecutionRecord r WHERE r.jobIdentifier = :jobIdentifier ORDER BY r.startTime DESC")
    List<BackgroundJobExecutionRecord> findLatestByJobIdentifier(@Param("jobIdentifier") String jobIdentifier);

    Optional<BackgroundJobExecutionRecord> findTopByJobIdentifierOrderByStartTimeDesc(String jobIdentifier);

    Optional<BackgroundJobExecutionRecord> findByIdempotencyKey(String idempotencyKey);

    long countByJobIdentifierAndExecutionStatus(String jobIdentifier, String executionStatus);
}

