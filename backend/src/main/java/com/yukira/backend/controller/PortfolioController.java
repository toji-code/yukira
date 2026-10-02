package com.yukira.backend.controller;

import com.yukira.backend.dto.portfolio.HoldingDto;
import com.yukira.backend.service.PortfolioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schemes/options")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/{id}/holdings")
    public ResponseEntity<List<HoldingDto>> getSchemeHoldings(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        List<HoldingDto> holdings = portfolioService.getHoldings(id, cutoff);
        return ResponseEntity.ok(holdings);
    }
}
