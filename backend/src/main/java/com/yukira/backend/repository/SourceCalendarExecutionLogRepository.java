package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SourceCalendarExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SourceCalendarExecutionLogRepository extends JpaRepository<SourceCalendarExecutionLog, Long> {
    List<SourceCalendarExecutionLog> findBySourceNameOrderByCreatedAtDesc(String sourceName);
    List<SourceCalendarExecutionLog> findTop50ByOrderByCreatedAtDesc();
}

