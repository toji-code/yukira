package com.yukira.backend.controller;

import com.yukira.backend.dto.coverage.SchemeBenchmarkMappingDto;
import com.yukira.backend.dto.coverage.SchemeDataCoverageDto;
import com.yukira.backend.dto.scheme.SchemeLifecycleRecordDto;
import com.yukira.backend.service.SchemeBenchmarkMappingService;
import com.yukira.backend.service.SchemeDataCoverageService;
import com.yukira.backend.service.SchemeLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schemes")
public class SchemeCoverageController {

    private final SchemeBenchmarkMappingService benchmarkMappingService;
    private final SchemeDataCoverageService coverageService;
    private final SchemeLifecycleService lifecycleService;

    public SchemeCoverageController(
        SchemeBenchmarkMappingService benchmarkMappingService,
        SchemeDataCoverageService coverageService,
        SchemeLifecycleService lifecycleService
    ) {
        this.benchmarkMappingService = benchmarkMappingService;
        this.coverageService = coverageService;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Retrieves all benchmark associations for a scheme (Primary, Secondary, Fallback with TRI priority).
     */
    @GetMapping("/{schemeId}/benchmarks")
    public ResponseEntity<List<SchemeBenchmarkMappingDto>> getSchemeBenchmarks(@PathVariable Long schemeId) {
        List<SchemeBenchmarkMappingDto> mappings = benchmarkMappingService.getMappingsForScheme(schemeId);
        return ResponseEntity.ok(mappings);
    }

    /**
     * Retrieves analytical data completeness ledger for a scheme across all 5 dependency dimensions.
     */
    @GetMapping("/{schemeId}/coverage")
    public ResponseEntity<SchemeDataCoverageDto> getSchemeCoverage(@PathVariable Long schemeId) {
        return coverageService.getCoverageForScheme(schemeId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Retrieves lifecycle continuity history for a scheme (Active, Merged, Renamed, Suspended).
     */
    @GetMapping("/{schemeId}/lifecycle")
    public ResponseEntity<List<SchemeLifecycleRecordDto>> getSchemeLifecycle(@PathVariable Long schemeId) {
        List<SchemeLifecycleRecordDto> records = lifecycleService.getLifecycleRecords(schemeId);
        return ResponseEntity.ok(records);
    }
}
