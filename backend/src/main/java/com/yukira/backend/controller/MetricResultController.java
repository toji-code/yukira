package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.repository.MetricResultRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metric-results")
public class MetricResultController {

    private final MetricResultRepository metricResultRepository;

    public MetricResultController(MetricResultRepository metricResultRepository) {
        this.metricResultRepository = metricResultRepository;
    }

    @GetMapping
    public ResponseEntity<List<MetricResult>> getMetricResults(
        @RequestParam(required = false) Long calculationRunId,
        @RequestParam(required = false) String metricCode
    ) {
        if (calculationRunId != null && metricCode != null) {
            return ResponseEntity.ok(metricResultRepository.findByCalculationRunIdAndMetricCode(calculationRunId, metricCode));
        } else if (calculationRunId != null) {
            return ResponseEntity.ok(metricResultRepository.findByCalculationRunId(calculationRunId));
        } else {
            return ResponseEntity.ok(metricResultRepository.findAll());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<MetricResult> getMetricResultById(@PathVariable Long id) {
        return metricResultRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
