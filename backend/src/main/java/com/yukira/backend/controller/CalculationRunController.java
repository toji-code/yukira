package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.service.CalculationOrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/calculation-runs")
public class CalculationRunController {

    private final CalculationOrchestratorService calculationOrchestratorService;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;

    public CalculationRunController(
        CalculationOrchestratorService calculationOrchestratorService,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository
    ) {
        this.calculationOrchestratorService = calculationOrchestratorService;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
    }

    public record TriggerCalculationRequest(
        Long schemeOptionId,
        Long benchmarkId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime,
        List<String> metricCodes,
        String methodologyTag,
        Map<String, Object> parameters
    ) {}

    @PostMapping
    public ResponseEntity<CalculationRun> triggerCalculationRun(@RequestBody TriggerCalculationRequest request) {
        if (request.schemeOptionId() == null || request.benchmarkId() == null ||
            request.asOfDate() == null || request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().build();
        }

        CalculationRun run = calculationOrchestratorService.executeCalculationRun(
            request.schemeOptionId(),
            request.benchmarkId(),
            request.asOfDate(),
            request.knowledgeCutoffTime(),
            request.metricCodes() != null ? request.metricCodes() : List.of("RET-01", "RET-02", "RSK-01"),
            request.methodologyTag() != null ? request.methodologyTag() : "CANDIDATE-V1",
            request.parameters()
        );

        return ResponseEntity.ok(run);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CalculationRun> getCalculationRunById(@PathVariable Long id) {
        return calculationRunRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<List<MetricResult>> getResultsForRun(@PathVariable Long id) {
        return ResponseEntity.ok(metricResultRepository.findByCalculationRunId(id));
    }
}
