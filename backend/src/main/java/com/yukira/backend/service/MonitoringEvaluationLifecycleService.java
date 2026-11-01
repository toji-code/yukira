package com.yukira.backend.service;

import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MonitoringEvaluationRun;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MonitoringEvaluationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service managing continuous monitoring evaluation lifecycles, point-in-time knowledge cutoffs,
 * and idempotency audit tracking (Task 304).
 */
@Service
@Transactional
public class MonitoringEvaluationLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringEvaluationLifecycleService.class);

    private final MonitoringEvaluationRunRepository evaluationRunRepository;
    private final CalculationRunRepository calculationRunRepository;

    public record MonitoringEvaluationRunDto(
        Long id,
        OffsetDateTime evaluationTimestamp,
        LocalDate dataCutoff,
        OffsetDateTime knowledgeCutoff,
        Long calculationRunId,
        String idempotencyKey,
        int triggersEvaluated,
        int alertsGenerated,
        String status,
        long executionDurationMs
    ) {}

    public MonitoringEvaluationLifecycleService(
        MonitoringEvaluationRunRepository evaluationRunRepository,
        CalculationRunRepository calculationRunRepository
    ) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.calculationRunRepository = calculationRunRepository;
    }

    public MonitoringEvaluationRunDto recordEvaluationRun(
        LocalDate dataCutoff,
        OffsetDateTime knowledgeCutoff,
        Long calculationRunId,
        String idempotencyKey,
        int triggersEvaluated,
        int alertsGenerated,
        String status,
        long durationMs
    ) {
        Optional<MonitoringEvaluationRun> existing = evaluationRunRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Evaluation run already recorded for idempotency key: {}", idempotencyKey);
            return toDto(existing.get());
        }

        MonitoringEvaluationRun run = new MonitoringEvaluationRun();
        run.setDataCutoff(dataCutoff != null ? dataCutoff : LocalDate.now());
        run.setKnowledgeCutoff(knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now());
        
        if (calculationRunId != null) {
            calculationRunRepository.findById(calculationRunId).ifPresent(run::setCalculationRun);
        }

        run.setIdempotencyKey(idempotencyKey);
        run.setTriggersEvaluated(triggersEvaluated);
        run.setAlertsGenerated(alertsGenerated);
        run.setStatus(status != null ? status : "COMPLETED");
        run.setExecutionDurationMs(durationMs);

        MonitoringEvaluationRun saved = evaluationRunRepository.save(run);
        log.info("Recorded monitoring evaluation run {} (dataCutoff={}, triggers={}, alerts={})",
            saved.getId(), saved.getDataCutoff(), saved.getTriggersEvaluated(), saved.getAlertsGenerated());

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<MonitoringEvaluationRunDto> getRecentEvaluationRuns(int limit) {
        return evaluationRunRepository.findTop20ByOrderByEvaluationTimestampDesc().stream()
            .limit(limit)
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    private MonitoringEvaluationRunDto toDto(MonitoringEvaluationRun r) {
        return new MonitoringEvaluationRunDto(
            r.getId(),
            r.getEvaluationTimestamp(),
            r.getDataCutoff(),
            r.getKnowledgeCutoff(),
            r.getCalculationRun() != null ? r.getCalculationRun().getId() : null,
            r.getIdempotencyKey(),
            r.getTriggersEvaluated(),
            r.getAlertsGenerated(),
            r.getStatus(),
            r.getExecutionDurationMs()
        );
    }
}

