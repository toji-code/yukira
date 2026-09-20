package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.MethodologyChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MethodologyChangeLogRepository extends JpaRepository<MethodologyChangeLog, Long> {
    List<MethodologyChangeLog> findByMethodologyVersionIdOrderByCreatedAtAsc(Long methodologyVersionId);
}
