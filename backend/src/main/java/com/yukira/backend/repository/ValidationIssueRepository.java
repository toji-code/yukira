package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.ValidationIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationIssueRepository extends JpaRepository<ValidationIssue, Long> {
    List<ValidationIssue> findByTargetEntityTypeAndTargetEntityId(String targetEntityType, Long targetEntityId);
}
