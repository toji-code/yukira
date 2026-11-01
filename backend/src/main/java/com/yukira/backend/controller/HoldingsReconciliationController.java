package com.yukira.backend.controller;

import com.yukira.backend.dto.holdings.HoldingsReconciliationSummaryDto;
import com.yukira.backend.service.HoldingsReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/holdings")
public class HoldingsReconciliationController {

    private final HoldingsReconciliationService reconciliationService;

    public HoldingsReconciliationController(HoldingsReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Retrieves portfolio holdings parsing and reconciliation records for an AMC (Tasks 176–180).
     */
    @GetMapping("/amc/{amcId}/reconciliations")
    public ResponseEntity<List<HoldingsReconciliationSummaryDto>> getAmcReconciliations(@PathVariable Long amcId) {
        List<HoldingsReconciliationSummaryDto> recs = reconciliationService.getReconciliationsForAmc(amcId);
        return ResponseEntity.ok(recs);
    }

    /**
     * Retrieves specific holdings reconciliation record by id.
     */
    @GetMapping("/reconciliations/{id}")
    public ResponseEntity<HoldingsReconciliationSummaryDto> getReconciliation(@PathVariable Long id) {
        return reconciliationService.getReconciliation(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

