package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.OperationalJobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OperationalJobExecutionRepository extends JpaRepository<OperationalJobExecution, Long> {

    List<OperationalJobExecution> findTop50ByOrderByStartedAtDesc();

    List<OperationalJobExecution> findAllByOrderByStartedAtDesc();

    List<OperationalJobExecution> findByJobNameOrderByStartedAtDesc(String jobName);

    Optional<OperationalJobExecution> findFirstByJobNameOrderByStartedAtDesc(String jobName);
}

